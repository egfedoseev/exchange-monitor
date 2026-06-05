package ru.jinushi.exchange

import kotlinx.coroutines.flow.Flow
import ru.jinushi.exchange.wallet.Asset
import java.math.BigDecimal
import kotlin.time.Instant

@JvmInline
value class CurrencyPair(val raw: String) {
    val baseAsset: Asset // что покупаем/продаём
        get() = Asset(raw.substringBefore("/"))
    val quoteAsset: Asset // за что прокупаем/продаём
        get() = Asset(raw.substringAfter("/"))

    val merged: String
        get() = baseAsset.code + quoteAsset.code
}

data class Ticker(
    val exchange: Exchange,
    val bid: BigDecimal, val ask: BigDecimal,
    val timestamp: Instant
)

interface Exchange {
    val name: String

    fun getFlow(currencyPair: CurrencyPair): Flow<Ticker>
}
