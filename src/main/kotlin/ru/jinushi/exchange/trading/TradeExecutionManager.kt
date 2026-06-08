package ru.jinushi.exchange.trading

import io.ktor.util.logging.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import ru.jinushi.exchange.accounting.ExecutedTrade
import ru.jinushi.exchange.accounting.ProfitTracker
import ru.jinushi.exchange.analyzer.TradeEvent
import ru.jinushi.exchange.wallet.Asset
import ru.jinushi.exchange.wallet.Wallet
import java.math.BigDecimal

class TradeExecutionManager(private val commandChannel: Channel<TradeEvent.OpportunityFound>) {
    private val logger: Logger = LoggerFactory.getLogger(TradeExecutionManager::class.java)
    private val scope = CoroutineScope(Dispatchers.IO)

    private val baseAmount = BigDecimal.valueOf(0.00001) // TODO add dynamic trade amount

    fun startWorkers(workersCount: Int) {
        repeat(workersCount) { id ->
            scope.launch {
                for (event in commandChannel) {
                    try {
                        executeTradeSafely(event)
                    } catch (e: Exception) {
                        logger.error("Worker-$id encountered an error while executing trade: {}", e.message, e)
                    }
                }
            }
        }

        logger.info("Successfully started {} workers", workersCount)
    }

    private suspend fun balanceWallets(fromWallet: Wallet, toWallet: Wallet, asset: Asset) {
        val initialFromBalance = fromWallet.getBalance(asset)
        val initialToBalance = toWallet.getBalance(asset)
        val amountToTransfer = initialFromBalance.multiply(BigDecimal("0.5"))

        logger.info(
            "Attempting to balance wallets for asset {}. Transferring {} from {} (balance: {}) to {} (balance: {})",
            asset.code, amountToTransfer, fromWallet.id, initialFromBalance, toWallet.id, initialToBalance
        )

        val success = fromWallet.sendMoney(asset, amountToTransfer, toWallet)
        if (success) {
            logger.info(
                "Successfully balanced wallets for asset {}. New balance of {}: {}, new balance of {}: {}",
                asset.code, fromWallet.id, fromWallet.getBalance(asset), toWallet.id, toWallet.getBalance(asset)
            )
        } else {
            logger.warn(
                "Failed to balance wallets for asset {}. Transfer of {} from {} to {} was rejected.",
                asset.code, amountToTransfer, fromWallet.id, toWallet.id
            )
        }
    }

    private suspend fun executeTradeSafely(event: TradeEvent.OpportunityFound) {
        val (currencyPair, buyTicker, sellTicker, buyWallet, sellWallet) = event

        logger.info(
            "Executing arbitrage trade for pair {}. Buying on {}, selling on {}",
            currencyPair, buyTicker.exchange, sellTicker.exchange
        )

        val buyResult = buyWallet.executeTrade(
            TradeOrder(
                currencyPair.baseAsset, currencyPair.quoteAsset,
                baseAmount, buyTicker.ask, OrderType.BUY
            )
        )
        val sellResult = when (buyResult) {
            is TradeResult.Failed.NotEnoughMoney -> {
                balanceWallets(sellWallet, buyWallet, currencyPair.quoteAsset)
                return
            }

            is TradeResult.Failed -> {
                logger.warn("Leg 1 (BUY) failed: {}", buyResult.reason)
                return
            }

            is TradeResult.Success -> {
                val amountToSell = buyResult.actualAmount

                sellWallet.executeTrade(
                    TradeOrder(
                        currencyPair.baseAsset, currencyPair.quoteAsset,
                        amountToSell, sellTicker.bid, OrderType.SELL
                    )
                )
            }
        }

        when (sellResult) {
            is TradeResult.Failed.NotEnoughMoney -> {
                logger.error(
                    "CRITICAL: Leg 1 (BUY) succeeded (amount: {}), but Leg 2 (SELL) FAILED. Wallet balances are out of sync!",
                    buyResult.actualAmount
                )
                balanceWallets(buyWallet, sellWallet, currencyPair.baseAsset)
            }

            is TradeResult.Failed -> {
                logger.error(
                    "Leg 1 (BUY) succeeded (amount: {}), but Leg 2 (SELL) FAILED: {}",
                    buyResult.actualAmount, sellResult.reason
                )
            }

            is TradeResult.Success -> {
                val executedTrade = ExecutedTrade(
                    currencyPair = currencyPair,
                    buyAmount = buyResult.actualAmount,
                    buyPrice = buyResult.actualPrice,
                    sellAmount = sellResult.actualAmount,
                    sellPrice = sellResult.actualPrice
                )
                ProfitTracker.registerTrade(executedTrade)
                logger.info(
                    "Successful trade! Total profit in {}: {}",
                    currencyPair.quoteAsset, ProfitTracker.getProfit(currencyPair.quoteAsset)
                )
            }
        }
    }
}