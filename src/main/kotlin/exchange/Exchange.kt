package ru.jinushi.exchange

import kotlinx.coroutines.flow.Flow
import ru.jinushi.exchange.wallet.Wallet
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
