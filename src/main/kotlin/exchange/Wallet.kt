package ru.jinushi.exchange

import java.math.BigDecimal
import java.util.concurrent.ConcurrentHashMap

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

class VirtualWallet(initialBalances: Map<Asset, BigDecimal>) : Wallet {
    private val balances = ConcurrentHashMap(initialBalances)

    override suspend fun getBalance(asset: Asset): BigDecimal = balances.getOrDefault(asset, BigDecimal.ZERO)

    override suspend fun executeTrade(order: TradeOrder): TradeResult {
        var (asset, quoteAsset, amount, type) = order
        if (type == OrderType.SELL) {
            val tmp = asset
            asset = quoteAsset
            quoteAsset = tmp
        }

        return TradeResult.Success(
            transactionId = "sim-tx-${System.nanoTime()}",
            actualPrice = BigDecimal.valueOf(65000)
        )
    }
}