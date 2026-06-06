package ru.jinushi.exchange.simulation

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import ru.jinushi.exchange.CurrencyPair
import ru.jinushi.exchange.Exchange
import ru.jinushi.exchange.Ticker
import java.math.BigDecimal
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds

class VirtualExchange(override val name: String) : Exchange {
    private val random = Random.Default
    private val multiplier = BigDecimal.valueOf(1.1)
    private val bidState = MutableStateFlow(BigDecimal.TEN)

    companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    override suspend fun getFlow(currencyPair: CurrencyPair): Flow<Ticker> = bidState.map { currentBid ->
        Ticker(
            exchange = this,
            bid = currentBid,
            ask = currentBid.multiply(multiplier),
            timestamp = Clock.System.now()
        )
    }

    fun updateTicker() {
        scope.launch {
            while (true) {
                val tmp = random.nextInt(-5, 5).toLong()
                bidState.value = bidState.value.add(BigDecimal.valueOf(tmp)).max(BigDecimal.TWO)
                delay(1000.milliseconds)
            }
        }
    }

    override fun toString(): String = name
}