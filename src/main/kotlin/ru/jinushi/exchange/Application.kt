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
import ru.jinushi.exchange.analyzer.TradeEvent
import ru.jinushi.exchange.config.ConfigManager
import ru.jinushi.exchange.registry.AnalyzerRegistry
import ru.jinushi.exchange.registry.ExchangeRegistry
import ru.jinushi.exchange.registry.WalletRegistry
import ru.jinushi.exchange.routes.analyzerRoutes
import ru.jinushi.exchange.routes.exchangesRoutes
import ru.jinushi.exchange.routes.tradeRoutes
import ru.jinushi.exchange.routes.walletRoutes
import ru.jinushi.exchange.trading.TradeExecutionManager
import kotlin.time.Duration.Companion.seconds

fun main() {
    val commandChannel = Channel<TradeEvent.OpportunityFound>(
        capacity = 50,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    val executionManager = TradeExecutionManager(commandChannel)
    executionManager.startWorkers(10)

    val httpClient = HttpClient(io.ktor.client.engine.cio.CIO) {
        install(WebSockets) {
            pingInterval = 20.seconds
        }
    }

    val exchangeRegistry = ExchangeRegistry()
    val walletRegistry = WalletRegistry()
    val analyzerRegistry = AnalyzerRegistry()

    val configManager = ConfigManager(
        configFile = java.io.File("config.toml"),
        httpClient = httpClient,
        exchangeRegistry = exchangeRegistry,
        walletRegistry = walletRegistry,
        analyzerRegistry = analyzerRegistry,
        commandChannel = commandChannel
    )
    configManager.loadAndInitialize()

    embeddedServer(CIO, port = 8080) {
        install(ContentNegotiation) {
            json()
        }

        monitor.subscribe(ApplicationStopping) {
            analyzerRegistry.close()
            httpClient.close()
        }

        routing {
            swaggerUI(path = "swagger", swaggerFile = "openapi/openapi.yaml")

            walletRoutes(walletRegistry, configManager)
            exchangesRoutes(exchangeRegistry)
            analyzerRoutes(commandChannel, analyzerRegistry, exchangeRegistry, walletRegistry, configManager)
            tradeRoutes()
        }
    }.start(wait = true)
}