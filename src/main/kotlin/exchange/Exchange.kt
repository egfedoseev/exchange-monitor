package ru.jinushi.exchange

import java.math.BigDecimal
import kotlin.time.Instant

@JvmInline
value class CurrencyPair(val raw: String)

data class Ticker(
    val name: String, val currencyPair: CurrencyPair,
    val bid: BigDecimal, val ask: BigDecimal,
    val timestamp: Instant
)



