package ru.jinushi.exchange.trading

import ru.jinushi.exchange.wallet.Asset
import java.math.BigDecimal

data class TradeOrder(
    val asset: Asset,
    val quoteAsset: Asset,
    val amount: BigDecimal,
    val targetPrice: BigDecimal,
    val type: OrderType
)

enum class OrderType { BUY, SELL }

sealed interface TradeResult {
    data class Success(
        val transactionId: String,
        val actualPrice: BigDecimal,
        val actualAmount: BigDecimal
    ) : TradeResult

    data class Failed(val reason: String) : TradeResult
}