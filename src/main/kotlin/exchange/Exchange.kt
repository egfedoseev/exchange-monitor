package ru.jinushi.exchange

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.math.BigDecimal
import kotlin.time.Instant

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
    runBlocking {
        val exchanges = listOf(
            TestExchange(
                "Binance-Sim",
                VirtualWallet(mapOf(Pair(Asset("USD"), BigDecimal.TEN)))
            ),
            TestExchange(
                "Bybit-Sim",
                VirtualWallet(mapOf(Pair(Asset("USD"), BigDecimal.TEN)))
            )
        )
        exchanges.forEach { launch { it.updateTicker() } }
        val currencyPair = CurrencyPair("USD/BTC")
        val analyzer = ArbitrageAnalyzer(currencyPair)
        val exchangesFlow = exchanges.map { it.getFlow(currencyPair) }.merge()
        launch {
            exchangesFlow.collect(analyzer::processNewTicker)
        }
    }
}
