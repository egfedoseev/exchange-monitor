package ru.jinushi.exchange.config

import com.akuleshov7.ktoml.Toml
import io.ktor.client.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.slf4j.LoggerFactory
import ru.jinushi.exchange.CurrencyPair
import ru.jinushi.exchange.analyzer.ArbitrageAnalyzer
import ru.jinushi.exchange.analyzer.TradeEvent
import ru.jinushi.exchange.clients.Binance
import ru.jinushi.exchange.clients.Bybit
import ru.jinushi.exchange.registry.AnalyzerRegistry
import ru.jinushi.exchange.registry.ExchangeRegistry
import ru.jinushi.exchange.registry.WalletRegistry
import ru.jinushi.exchange.simulation.VirtualExchange
import ru.jinushi.exchange.simulation.VirtualWallet
import ru.jinushi.exchange.wallet.Asset
import java.io.File
import java.math.BigDecimal

@Serializable
data class AppConfig(
    val exchanges: List<ExchangeConfig> = emptyList(),
    val wallets: List<WalletConfig> = emptyList(),
    val analyzers: List<AnalyzerConfig> = emptyList()
)

@Serializable
data class ExchangeConfig(
    val name: String,
    val type: String
)

@Serializable
data class WalletConfig(
    val name: String,
    val isVirtual: Boolean,
    val balances: Map<String, String> = emptyMap(),
    val feeRate: String = "0.001",
    val blockchain: String = "Simulated",
    val minimalLimits: Map<String, String> = emptyMap(),
    val transferFees: Map<String, String> = emptyMap()
)

@Serializable
data class AnalyzerConfig(
    val currencyPair: String,
    val executionMappings: Map<String, String> = emptyMap()
)

class ConfigManager(
    private val configFile: File,
    private val httpClient: HttpClient,
    private val exchangeRegistry: ExchangeRegistry,
    private val walletRegistry: WalletRegistry,
    private val analyzerRegistry: AnalyzerRegistry,
    private val commandChannel: Channel<TradeEvent.OpportunityFound>
) {
    private val logger = LoggerFactory.getLogger(ConfigManager::class.java)

    fun loadAndInitialize() {
        if (!configFile.exists()) {
            logger.info("Configuration file {} not found. Creating default configuration...", configFile.name)
            createDefaultConfig()
            return
        }

        try {
            val tomlString = configFile.readText()
            val config = Toml.decodeFromString<AppConfig>(tomlString)

            config.exchanges.forEach { exchangeConfig ->
                val exchange = when (exchangeConfig.type.uppercase()) {
                    "BINANCE" -> Binance(httpClient)
                    "BYBIT" -> Bybit(httpClient)
                    "VIRTUAL" -> VirtualExchange(exchangeConfig.name).apply { updateTicker() }
                    else -> {
                        logger.error("Unknown exchange type: {}", exchangeConfig.type)
                        null
                    }
                }
                if (exchange != null) {
                    exchangeRegistry.register(exchange)
                    logger.info("Loaded and registered exchange: {}", exchange.name)
                }
            }

            config.wallets.forEach { walletConfig ->
                if (walletConfig.isVirtual) {
                    val initialBalances = walletConfig.balances.mapKeys { Asset(it.key) }
                        .mapValues { BigDecimal(it.value) }
                    val minimalLimits = walletConfig.minimalLimits.mapKeys { Asset(it.key) }
                        .mapValues { BigDecimal(it.value) }
                    val transferFees = walletConfig.transferFees.mapKeys { Asset(it.key) }
                        .mapValues { BigDecimal(it.value) }
                    val wallet = VirtualWallet(
                        initialBalances = initialBalances,
                        tradeFeeRate = BigDecimal(walletConfig.feeRate),
                        blockchain = walletConfig.blockchain,
                        id = walletConfig.name,
                        minimalLimits = minimalLimits,
                        transferFees = transferFees
                    )
                    walletRegistry.register(walletConfig.name, wallet)
                    logger.info("Loaded and registered wallet: {}", walletConfig.name)
                } else {
                    logger.warn("Non-virtual wallets are not supported yet: {}", walletConfig.name)
                }
            }

            config.analyzers.forEach { analyzerConfig ->
                val pair = CurrencyPair(analyzerConfig.currencyPair)
                val exchangesMap = analyzerConfig.executionMappings
                    .mapKeys { (exchangeName, _) ->
                        exchangeRegistry.get(exchangeName) ?: return@forEach logger.error(
                            "Cannot start analyzer for {}: exchange {} not found", pair, exchangeName
                        )
                    }
                    .mapValues { (_, walletName) ->
                        walletRegistry.get(walletName) ?: return@forEach logger.error(
                            "Cannot start analyzer for {}: wallet {} not found", pair, walletName
                        )
                    }

                val analyzer = ArbitrageAnalyzer(pair, commandChannel, exchangesMap)
                val job = analyzerRegistry.scope.launch {
                    val flows = exchangesMap.keys.map { it.getFlow(pair) }
                    flows.merge().collect(analyzer::processNewTicker)
                }
                analyzerRegistry.register(pair, analyzer, job)
                logger.info("Loaded and started analyzer for: {}", pair)
            }
        } catch (e: Exception) {
            logger.error("Failed to load configuration: {}", e.message, e)
            throw RuntimeException("Failed to load configuration", e)
        }
    }

    fun saveCurrentState() {
        try {
            val exchangesList = exchangeRegistry.getAll().map { (name, exchange) ->
                val type = when (exchange) {
                    is Binance -> "BINANCE"
                    is Bybit -> "BYBIT"
                    is VirtualExchange -> "VIRTUAL"
                    else -> "UNKNOWN"
                }
                ExchangeConfig(name, type)
            }

            val walletsList = walletRegistry.getAll().map { (name, wallet) ->
                val isVirtual = wallet is VirtualWallet
                val feeRateStr = if (wallet is VirtualWallet) wallet.tradeFeeRate.toPlainString() else "0.0"
                val blockchain = if (wallet is VirtualWallet) wallet.blockchain else "Simulated"
                val minimalLimits = if (wallet is VirtualWallet) {
                    wallet.minimalLimits.mapKeys { it.key.code }.mapValues { it.value.toPlainString() }
                } else emptyMap()
                val transferFees = if (wallet is VirtualWallet) {
                    wallet.transferFees.mapKeys { it.key.code }.mapValues { it.value.toPlainString() }
                } else emptyMap()
                val balances = runBlocking {
                    wallet.getBalances().mapKeys { it.key.code }.mapValues { it.value.toPlainString() }
                }
                WalletConfig(
                    name = name,
                    isVirtual = isVirtual,
                    balances = balances,
                    feeRate = feeRateStr,
                    blockchain = blockchain,
                    minimalLimits = minimalLimits,
                    transferFees = transferFees
                )
            }

            val analyzersList = analyzerRegistry.getAll().map { (pair, analyzer) ->
                val mappings = analyzer.wallets.mapKeys { (exchange, _) ->
                    exchangeRegistry.getAll().entries.firstOrNull { it.value == exchange }?.key ?: "unknown"
                }.mapValues { (_, wallet) ->
                    walletRegistry.getAll().entries.firstOrNull { it.value == wallet }?.key ?: "unknown"
                }
                AnalyzerConfig(pair.raw, mappings)
            }

            val config = AppConfig(exchangesList, walletsList, analyzersList)
            val tomlString = Toml.encodeToString(config)
            configFile.writeText(tomlString)
            logger.info("Successfully saved configuration state to {}", configFile.name)
        } catch (e: Exception) {
            logger.error("Failed to save configuration state: {}", e.message, e)
        }
    }

    private fun createDefaultConfig() {
        val defaultConfig = AppConfig(
            exchanges = listOf(
                ExchangeConfig("Binance", "BINANCE"),
                ExchangeConfig("Bybit", "BYBIT")
            ),
            wallets = listOf(
                WalletConfig(
                    "BinanceWallet-Sim", true, mapOf(
                        "USDT" to "10000.00",
                        "BTC" to "1.00000000"
                    ), "0.001"
                ),
                WalletConfig(
                    "BybitWallet-Sim", true, mapOf(
                        "USDT" to "10000.00",
                        "BTC" to "1.00000000"
                    ), "0.001"
                )
            ),
            analyzers = listOf(
                AnalyzerConfig(
                    "BTC/USDT", mapOf(
                        "Binance" to "BinanceWallet-Sim",
                        "Bybit" to "BybitWallet-Sim"
                    )
                )
            )
        )

        try {
            configFile.parentFile?.mkdirs()
            val tomlString = Toml.encodeToString(defaultConfig)
            configFile.writeText(tomlString)
            logger.info("Created default configuration in {}", configFile.absolutePath)
            loadAndInitialize()
        } catch (e: Exception) {
            logger.error("Failed to create default configuration: {}", e.message, e)
            throw RuntimeException("Failed to create default configuration", e)
        }
    }
}
