package ru.jinushi.exchange

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
import ru.jinushi.exchange.trading.TradeExecutionManager
import ru.jinushi.exchange.routes.analyzerRoutes
import ru.jinushi.exchange.routes.exchangesRoutes
import ru.jinushi.exchange.routes.tradeRoutes
import ru.jinushi.exchange.routes.walletRoutes

fun main() {
    val commandChannel = Channel<TradeEvent.OpportunityFound>(
        capacity = 50,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    val executionManager =
        TradeExecutionManager(commandChannel)
    executionManager.startWorkers(10)

    embeddedServer(CIO, port = 8080) {
        install(ContentNegotiation) {
            json()
        }

        routing {
            swaggerUI(path = "swagger", swaggerFile = "openapi/openapi.yaml")

            walletRoutes()
            exchangesRoutes()
            analyzerRoutes(commandChannel)
            tradeRoutes()
        }
    }.start(wait = true)
}