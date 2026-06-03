package ru.jinushi.exchange

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds

class TestExchange(override val name: String, override val wallet: Wallet) : Exchange {
    private val random = Random.Default
    private val multiplier = BigDecimal.valueOf(1.1)
    private val bidState = MutableStateFlow(BigDecimal.TEN)

    override fun getFlow(currencyPair: CurrencyPair): Flow<Ticker> = bidState.map { currentBid ->
        Ticker(
            exchange = this,
            currencyPair = currencyPair,
            bid = currentBid,
            ask = currentBid.multiply(multiplier),
            timestamp = Clock.System.now()
        )
    }

    suspend fun updateTicker() {
        while (true) {
            val tmp = random.nextInt(-5, 5).toLong()
            bidState.value = bidState.value.add(BigDecimal.valueOf(tmp)).max(BigDecimal.TWO)
            delay(1000.milliseconds)
        }
    }

    override fun toString(): String = name
}