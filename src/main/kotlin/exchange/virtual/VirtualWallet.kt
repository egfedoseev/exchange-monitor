package ru.jinushi.exchange.virtual

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.jinushi.exchange.wallet.Asset
import ru.jinushi.exchange.wallet.OrderType
import ru.jinushi.exchange.wallet.TradeOrder
import ru.jinushi.exchange.wallet.TradeResult
import ru.jinushi.exchange.wallet.Wallet
import java.math.BigDecimal
import java.util.concurrent.ConcurrentHashMap

class VirtualWallet(initialBalances: Map<Asset, BigDecimal>) : Wallet {
    private val balances = ConcurrentHashMap(initialBalances)

    private val assetLocks = ConcurrentHashMap<Asset, Mutex>()

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

        val (firstAsset, secondAsset) = if (spentAsset.code < receivedAsset.code) {
            spentAsset to receivedAsset
        } else {
            receivedAsset to spentAsset
        }

        val firstLock = assetLocks.computeIfAbsent(firstAsset) { Mutex() }
        val secondLock = assetLocks.computeIfAbsent(secondAsset) { Mutex() }

        val currentSpentBalance = getBalance(spentAsset)

        if (currentSpentBalance < spentAmount) {
            return TradeResult.Failed("Not enough money in asset $spentAsset")
        }

        balances[spentAsset] = currentSpentBalance.subtract(spentAmount)
        balances[receivedAsset] = getBalance(receivedAsset).add(receivedAmount)

        return firstLock.withLock {
            secondLock.withLock {
                val currentSpentBalance = getBalance(spentAsset)

                if (currentSpentBalance < spentAmount) {
                    return TradeResult.Failed("Not enough money in asset ${spentAsset.code}")
                }

                balances[spentAsset] = currentSpentBalance.subtract(spentAmount)
                balances[receivedAsset] = getBalance(receivedAsset).add(receivedAmount)

                TradeResult.Success(
                    transactionId = "sim-tx-${System.nanoTime()}",
                    actualPrice = order.targetPrice,
                    actualAmount = order.amount
                )
            }
        }
    }
}