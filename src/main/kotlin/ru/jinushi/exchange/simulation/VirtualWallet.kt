package ru.jinushi.exchange.simulation

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.jinushi.exchange.wallet.Asset
import ru.jinushi.exchange.trading.OrderType
import ru.jinushi.exchange.trading.TradeOrder
import ru.jinushi.exchange.trading.TradeResult
import ru.jinushi.exchange.wallet.Wallet
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.concurrent.ConcurrentHashMap

class VirtualWallet(initialBalances: Map<Asset, BigDecimal>) : Wallet {
    private val balances = ConcurrentHashMap(initialBalances)
    override suspend fun getBalances(): Map<Asset, BigDecimal> = balances

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
                spentAmount = order.amount.multiply(order.targetPrice).roundForAsset(spentAsset)
                receivedAmount = order.amount.roundForAsset(receivedAsset)
            }

            OrderType.SELL -> {
                spentAsset = order.asset
                receivedAsset = order.quoteAsset
                spentAmount = order.amount.roundForAsset(spentAsset)
                receivedAmount = order.amount.multiply(order.targetPrice).roundForAsset(receivedAsset)
            }
        }

        val (firstAsset, secondAsset) = if (spentAsset.code < receivedAsset.code) {
            spentAsset to receivedAsset
        } else {
            receivedAsset to spentAsset
        }

        val firstLock = assetLocks.computeIfAbsent(firstAsset) { Mutex() }
        val secondLock = assetLocks.computeIfAbsent(secondAsset) { Mutex() }

        return firstLock.withLock {
            secondLock.withLock {
                val currentSpentBalance = getBalance(spentAsset)
                if (currentSpentBalance < spentAmount) {
                    return TradeResult.Failed("Not enough money in asset ${spentAsset.code}. Has $currentSpentBalance, needs $spentAmount")
                }

                balances[spentAsset] = currentSpentBalance.subtract(spentAmount)
                balances.compute(receivedAsset) { _: Asset, oldValue: BigDecimal? ->
                    val newVal = oldValue?.add(receivedAmount) ?: receivedAmount
                    newVal.roundForAsset(receivedAsset)
                }

                TradeResult.Success(
                    transactionId = "sim-tx-${System.nanoTime()}",
                    actualPrice = order.targetPrice,
                    actualAmount = order.amount
                )
            }
        }
    }

    private fun BigDecimal.roundForAsset(asset: Asset): BigDecimal {
        val scale = if (asset.code.uppercase() == "USD" || asset.code.uppercase() == "USDT") 2 else 8
        return this.setScale(scale, RoundingMode.HALF_UP)
    }
}