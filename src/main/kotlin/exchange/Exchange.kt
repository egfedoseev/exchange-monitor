package ru.jinushi.exchange

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.math.BigDecimal
import kotlin.time.Instant

val httpClient = HttpClient(CIO) {
    install(ContentNegotiation) {
        json()
    }
}

@JvmInline
value class CurrencyPair(val raw: String) {
    val first: String
        get() = raw.substringBefore("/")
    val second: String
        get() = raw.substringAfter("/")
}

data class Ticker(
    val exchange: Exchange, val currencyPair: CurrencyPair,
    val bid: BigDecimal, val ask: BigDecimal,
    val timestamp: Instant
)

sealed interface Exchange {
    val name: String

    val wallet: Wallet

    fun getFlow(currencyPair: CurrencyPair): Flow<Ticker>
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
