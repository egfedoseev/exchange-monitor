package ru.jinushi.exchange

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.websocket.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import ru.jinushi.exchange.analyzer.ArbitrageAnalyzer
import ru.jinushi.exchange.analyzer.ExecutionManager
import ru.jinushi.exchange.analyzer.TradeEvent
import ru.jinushi.exchange.wallet.Asset
import ru.jinushi.exchange.wallet.VirtualWallet
import java.math.BigDecimal

val httpClient = HttpClient(CIO) {
    install(ContentNegotiation) {
        json()
    }
    install(WebSockets)
}

fun main() {
    val commandChannel = Channel<TradeEvent.OpportunityFound>(
        capacity = 50,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    val executionManager = ExecutionManager(commandChannel)
    executionManager.startWorkers(workersCount = 3)

    val exchanges = listOf(
        VirtualExchange(
            "Binance-Sim",
            VirtualWallet(
                mapOf(
                    Pair(Asset("USD"), BigDecimal.TEN),
                    Pair(Asset("BTC"), BigDecimal.TEN)
                )
            )
        ),
        VirtualExchange(
            "Bybit-Sim",
            VirtualWallet(
                mapOf(
                    Pair(Asset("USD"), BigDecimal.TEN),
                    Pair(Asset("BTC"), BigDecimal.TEN)
                )
            )
        )
    )
    exchanges.forEach { it.updateTicker() }
    val analyzer = ArbitrageAnalyzer(commandChannel)
    val currencyPair = CurrencyPair("USD/BTC")
    val exchangesFlow = exchanges.map { it.getFlow(currencyPair) }.merge()
    runBlocking {
        launch {
            exchangesFlow.collect(analyzer::processNewTicker)
        }
    }
}
