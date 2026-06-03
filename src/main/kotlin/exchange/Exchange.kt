package ru.jinushi.exchange

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.math.BigDecimal
import kotlin.time.Instant

@JvmInline
value class CurrencyPair(val raw: String) {
    val first: String
        get() = raw.split("/")[0]
    val second: String
        get() = raw.split("/")[1]
}

data class Ticker(
    val exchange: Exchange, val currencyPair: CurrencyPair,
    val bid: BigDecimal, val ask: BigDecimal,
    val timestamp: Instant
)

sealed interface Exchange {
    fun getFlow(currencyPair: CurrencyPair): Flow<Ticker>
}

class ArbitrageOpportunity(val buyExchange: Exchange, val sellExchange: Exchange, val profit: BigDecimal)

fun main() {
    runBlocking {
        launch { TestExchange.updateTicker() }
        launch {
            val flow = TestExchange.getFlow(CurrencyPair("USD/BTC"))
            flow.collect { ticker -> println(ticker) }
        }
    }
}



