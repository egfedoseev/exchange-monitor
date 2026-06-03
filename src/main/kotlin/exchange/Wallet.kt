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

class VirtualWallet(initialBalances: Map<Asset, BigDecimal>) : Wallet {
    private val balances = ConcurrentHashMap(initialBalances)

    override suspend fun getBalance(asset: Asset): BigDecimal = balances.getOrDefault(asset, BigDecimal.ZERO)

    override suspend fun executeTrade(order: TradeOrder): TradeResult {
        val spentAsset: Asset
        val receivedAsset: Asset
        val spentAmount: BigDecimal
        val receivedAmount: BigDecimal

        when (order.type) {
            OrderType.BUY -> {
                spentAsset = order.quoteAsset
                receivedAsset = order.asset
                spentAmount = order.amount.multiply(order.targetPrice)
                receivedAmount = order.amount
            }

            OrderType.SELL -> {
                spentAsset = order.asset
                receivedAsset = order.quoteAsset
                spentAmount = order.amount
                receivedAmount = order.amount.multiply(order.targetPrice)
            }
        }

        val currentSpentBalance = getBalance(spentAsset)

        if (currentSpentBalance < spentAmount) {
            return TradeResult.Failed("Недостаточно средств в ассете ${spentAsset.code}")
        }

        balances[spentAsset] = currentSpentBalance.subtract(spentAmount)
        balances[receivedAsset] = getBalance(receivedAsset).add(receivedAmount)

        return TradeResult.Success(
            transactionId = "sim-tx-${System.nanoTime()}",
            actualPrice = order.targetPrice,
            actualAmount = order.amount
        )
    }
}