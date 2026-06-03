package ru.jinushi.exchange

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds

object TestExchange : Exchange {
    private val random = Random.Default
    private var bid = BigDecimal.TEN

    private val multiplier = BigDecimal.valueOf(1.1)

    private val bidState = MutableStateFlow(BigDecimal.TEN)

    private val wallets = ConcurrentHashMap<String, Wallet>()

    override fun getFlow(currencyPair: CurrencyPair): Flow<Ticker> = bidState.map { currentBid ->
        Ticker(
            exchange = TestExchange,
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
}