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
            is TradeResult.Failed -> {
                logger.error(
                    "CRITICAL: Leg 1 (BUY) succeeded (amount: {}), but Leg 2 (SELL) FAILED: {}. Wallet balances are out of sync!",
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