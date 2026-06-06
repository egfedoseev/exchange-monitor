package ru.jinushi.exchange

import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.swagger.*
import io.ktor.server.routing.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import ru.jinushi.exchange.clients.Binance
import ru.jinushi.exchange.clients.Bybit
import ru.jinushi.exchange.simulation.VirtualExchange
import ru.jinushi.exchange.simulation.VirtualWallet
import ru.jinushi.exchange.wallet.Asset
import ru.jinushi.exchange.routes.analyzerRoutes
import ru.jinushi.exchange.routes.exchangesRoutes
import ru.jinushi.exchange.routes.tradeRoutes
import ru.jinushi.exchange.routes.walletRoutes
import ru.jinushi.exchange.registry.ExchangeRegistry
import ru.jinushi.exchange.registry.WalletRegistry
import ru.jinushi.exchange.registry.AnalyzerRegistry
import ru.jinushi.exchange.analyzer.TradeEvent
import ru.jinushi.exchange.trading.TradeExecutionManager
import java.math.BigDecimal

fun main() {
    val commandChannel = Channel<TradeEvent.OpportunityFound>(
        capacity = 50,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    val executionManager = TradeExecutionManager(commandChannel)
    executionManager.startWorkers(10)

    val httpClient = HttpClient(io.ktor.client.engine.cio.CIO) {
        install(WebSockets)
    }

    val exchangeRegistry = ExchangeRegistry()
    val walletRegistry = WalletRegistry()
    val analyzerRegistry = AnalyzerRegistry()

    exchangeRegistry.register(Binance(httpClient))
    exchangeRegistry.register(Bybit(httpClient))

    val binanceSim = VirtualExchange("Binance-Sim").apply { updateTicker() }
    val bybitSim = VirtualExchange("Bybit-Sim").apply { updateTicker() }
    exchangeRegistry.register(binanceSim)
    exchangeRegistry.register(bybitSim)

    walletRegistry.register(
        "BinanceWallet-Sim",
        VirtualWallet(
            mapOf(
                Asset("USD") to BigDecimal("10000"),
                Asset("BTC") to BigDecimal("1")
            )
        )
    )
    walletRegistry.register(
        "BybitWallet-Sim",
        VirtualWallet(
            mapOf(
                Asset("USD") to BigDecimal("10000"),
                Asset("BTC") to BigDecimal("1")
            )
        )
    )

    embeddedServer(CIO, port = 8080) {
        install(ContentNegotiation) {
            json()
        }

        routing {
            swaggerUI(path = "swagger", swaggerFile = "openapi/openapi.yaml")

            walletRoutes(walletRegistry)
            exchangesRoutes(exchangeRegistry)
            analyzerRoutes(commandChannel, analyzerRegistry, exchangeRegistry, walletRegistry)
            tradeRoutes()
        }
    }.start(wait = true)
}