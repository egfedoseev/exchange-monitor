package ru.jinushi.exchange.routes

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.json.*
import ru.jinushi.exchange.CurrencyPair
import ru.jinushi.exchange.accounting.ExecutedTrade
import ru.jinushi.exchange.accounting.ProfitTracker
import ru.jinushi.exchange.analyzer.TradeEvent
import ru.jinushi.exchange.config.ConfigManager
import ru.jinushi.exchange.registry.AnalyzerRegistry
import ru.jinushi.exchange.registry.ExchangeRegistry
import ru.jinushi.exchange.registry.WalletRegistry
import ru.jinushi.exchange.simulation.VirtualWallet
import ru.jinushi.exchange.wallet.Asset
import java.io.File
import java.math.BigDecimal
import kotlin.test.*

class ApiRoutesTest {

    private val configFile = File("test_config.toml")
    private val httpClient = io.ktor.client.HttpClient(io.ktor.client.engine.cio.CIO)

    @BeforeTest
    fun setUp() {
        configFile.delete()
    }

    @AfterTest
    fun tearDown() {
        configFile.delete()
        httpClient.close()
    }

    @Test
    fun testAllApiRoutes() = testApplication {
        val commandChannel = Channel<TradeEvent.OpportunityFound>(capacity = 10)
        val exchangeRegistry = ExchangeRegistry()
        val walletRegistry = WalletRegistry()
        val analyzerRegistry = AnalyzerRegistry()
        val configManager = ConfigManager(
            configFile = configFile,
            httpClient = httpClient,
            exchangeRegistry = exchangeRegistry,
            walletRegistry = walletRegistry,
            analyzerRegistry = analyzerRegistry,
            commandChannel = commandChannel
        )

        // Seed some initial data
        walletRegistry.register("BinanceWallet-Sim", VirtualWallet(mapOf(Asset("USDT") to BigDecimal("1000.0"))))

        // Set up the Ktor test application routing
        application {
            this.install(ContentNegotiation) {
                json(ru.jinushi.exchange.serializers.jsonConfig)
            }
            routing {
                walletRoutes(walletRegistry, configManager)
                exchangesRoutes(exchangeRegistry)
                analyzerRoutes(commandChannel, analyzerRegistry, exchangeRegistry, walletRegistry, configManager)
                tradeRoutes()
            }
        }

        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                json(ru.jinushi.exchange.serializers.jsonConfig)
            }
        }

        // 1. GET /exchanges
        val exchangesResponse = client.get("/exchanges")
        assertEquals(HttpStatusCode.OK, exchangesResponse.status)
        val exchangesList = exchangesResponse.bodyAsText()
        assertTrue(exchangesList.startsWith("["))

        // 2. GET /wallets
        val walletsResponse = client.get("/wallets")
        assertEquals(HttpStatusCode.OK, walletsResponse.status)
        val walletsJson = Json.parseToJsonElement(walletsResponse.bodyAsText()).jsonObject
        assertTrue(walletsJson.containsKey("BinanceWallet-Sim"))

        // 3. POST /wallets
        val postWalletResponse = client.post("/wallets") {
            contentType(ContentType.Application.Json)
            setBody(
                buildJsonObject {
                    put("walletName", "BybitWallet-Sim")
                    put("isVirtual", true)
                    putJsonObject("initialBalances") {
                        put("BTC", "1.5")
                    }
                    put("feeRate", "0.002")
                }
            )
        }
        assertEquals(HttpStatusCode.Created, postWalletResponse.status)
        assertTrue(walletRegistry.getAll().containsKey("BybitWallet-Sim"))

        // 4. GET /analyzers
        val analyzersResponse = client.get("/analyzers")
        assertEquals(HttpStatusCode.OK, analyzersResponse.status)
        assertEquals("[]", analyzersResponse.bodyAsText())

        // 5. POST /analyzers (fails since exchanges aren't registered)
        val postAnalyzerResponse = client.post("/analyzers") {
            contentType(ContentType.Application.Json)
            setBody(
                buildJsonObject {
                    put("currencyPair", "BTC/USDT")
                    putJsonObject("executionMappings") {
                        put("Binance", "BinanceWallet-Sim")
                    }
                }
            )
        }
        assertEquals(HttpStatusCode.BadRequest, postAnalyzerResponse.status)

        // 6. GET /analyzers/profit
        // Reset or register a trade to verify BigDecimal serialization
        val trade = ExecutedTrade(
            currencyPair = CurrencyPair("BTC/USDT"),
            buyAmount = BigDecimal("0.1"),
            buyPrice = BigDecimal("50000.0"),
            sellAmount = BigDecimal("0.1"),
            sellPrice = BigDecimal("51000.0")
        )
        ProfitTracker.registerTrade(trade)

        val profitResponse = client.get("/analyzers/profit")
        assertEquals(HttpStatusCode.OK, profitResponse.status)
        val profitJson = Json.parseToJsonElement(profitResponse.bodyAsText()).jsonObject
        assertTrue(profitJson.containsKey("USDT"))
        assertEquals("100.00000000", profitJson["USDT"]?.jsonPrimitive?.content)
    }
}
