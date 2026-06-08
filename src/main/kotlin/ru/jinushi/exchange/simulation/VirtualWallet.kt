package ru.jinushi.exchange.simulation

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.jinushi.exchange.trading.OrderType
import ru.jinushi.exchange.trading.TradeOrder
import ru.jinushi.exchange.trading.TradeResult
import ru.jinushi.exchange.wallet.Asset
import ru.jinushi.exchange.wallet.Wallet
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.concurrent.ConcurrentHashMap

class VirtualWallet(
    initialBalances: Map<Asset, BigDecimal>,
    val tradeFeeRate: BigDecimal = BigDecimal("0.001"),
    override val blockchain: String = "Simulated",
    override val id: String = "sim-wallet-${System.nanoTime()}",
    val minimalLimits: Map<Asset, BigDecimal> = emptyMap(),
    val transferFees: Map<Asset, BigDecimal> = emptyMap(),
) : Wallet {
    private val balances = ConcurrentHashMap(initialBalances)
    override suspend fun getBalances(): Map<Asset, BigDecimal> = balances

    private val assetLocks = ConcurrentHashMap<Asset, Mutex>()

    private val multiplier = BigDecimal.ONE.subtract(tradeFeeRate)
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
                receivedAmount = order.amount.multiply(multiplier).roundForAsset(receivedAsset)
            }

            OrderType.SELL -> {
                spentAsset = order.asset
                receivedAsset = order.quoteAsset
                spentAmount = order.amount.roundForAsset(spentAsset)
                receivedAmount =
                    order.amount.multiply(order.targetPrice).multiply(multiplier).roundForAsset(receivedAsset)
            }
        }

        val (firstAsset, secondAsset) = if (spentAsset.code < receivedAsset.code) {
            spentAsset to receivedAsset
        } else {
            receivedAsset to spentAsset
        }

        val firstLock = getLock(firstAsset)
        val secondLock = getLock(secondAsset)

        return firstLock.withLock {
            secondLock.withLock {
                val currentSpentBalance = getBalance(spentAsset)
                if (currentSpentBalance < spentAmount) {
                    return TradeResult.Failed.NotEnoughMoney(currentSpentBalance, spentAmount, spentAsset)
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

    private fun acceptMoney(asset: Asset, amount: BigDecimal): Boolean {
        if (amount <= BigDecimal.ZERO) {
            return false
        }
        balances.compute(asset) {_, value -> (value ?: BigDecimal.ZERO).add(amount).roundForAsset(asset)}
        return true
    }

    override suspend fun sendMoney(
        asset: Asset,
        amount: BigDecimal,
        to: Wallet
    ): Boolean {
        if (to !is VirtualWallet) return false
        val roundedAmount = amount.roundForAsset(asset)
        val fee = transferFees[asset] ?: BigDecimal.ZERO
        val limit = (minimalLimits[asset] ?: BigDecimal.ZERO).max(fee)

        val (firstWallet, secondWallet) = if (this.id < to.id) {
            this to to
        } else {
            to to this
        }

        val firstLock = firstWallet.getLock(asset)
        val secondLock = secondWallet.getLock(asset)

        firstLock.withLock {
            secondLock.withLock {
                val balance = this.balances[asset] ?: BigDecimal.ZERO
                if (roundedAmount !in limit..balance) {
                    return false
                }
                this.balances[asset] = balance.subtract(roundedAmount)
                to.acceptMoney(asset, roundedAmount.subtract(fee))
                return true
            }
        }
    }

    private fun getLock(firstAsset: Asset): Mutex = assetLocks.computeIfAbsent(firstAsset) { Mutex() }
}