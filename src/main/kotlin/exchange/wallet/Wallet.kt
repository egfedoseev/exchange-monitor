package ru.jinushi.exchange.wallet

import java.math.BigDecimal

@JvmInline
value class Asset(val code: String) {
    companion object {
        val BTC = Asset("BTC")
        val USDT = Asset("USDT")
        val USD = Asset("USD")
    }
}

interface Wallet {
    suspend fun getBalance(asset: Asset): BigDecimal
    suspend fun executeTrade(order: TradeOrder): TradeResult
}

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

