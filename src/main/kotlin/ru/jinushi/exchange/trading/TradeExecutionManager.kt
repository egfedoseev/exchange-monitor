package ru.jinushi.exchange.trading

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import ru.jinushi.exchange.accounting.ExecutedTrade
import ru.jinushi.exchange.accounting.ProfitTracker
import ru.jinushi.exchange.analyzer.TradeEvent
import java.math.BigDecimal

class TradeExecutionManager(private val commandChannel: Channel<TradeEvent.OpportunityFound>) {
    private val scope = CoroutineScope(Dispatchers.IO)

    private val baseAmount = BigDecimal.valueOf(0.001)

    fun startWorkers(workersCount: Int) {
        repeat(workersCount) { _ ->
            scope.launch {
                for (event in commandChannel) {
                    try {
                        executeTradeSafely(event)
                    } catch (_: Exception) {
                    }
                }
            }
        }

        println("Successfully started workers: $workersCount")
    }

    private suspend fun executeTradeSafely(event: TradeEvent.OpportunityFound) {
        val (currencyPair, buyTicker, sellTicker, buyWallet, sellWallet) = event

        val buyResult = buyWallet.executeTrade(
            TradeOrder(
                currencyPair.baseAsset, currencyPair.quoteAsset,
                baseAmount, buyTicker.ask, OrderType.BUY
            )
        )
        val sellResult = when (buyResult) {
            is TradeResult.Failed -> return
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
        if (sellResult is TradeResult.Success) {
            val executedTrade = ExecutedTrade(
                currencyPair = currencyPair,
                buyAmount = buyResult.actualAmount,
                buyPrice = buyResult.actualPrice,
                sellAmount = sellResult.actualAmount,
                sellPrice = sellResult.actualPrice
            )
            ProfitTracker.registerTrade(executedTrade)
            println("Successful trade, total profit ${ProfitTracker.getProfit(currencyPair.quoteAsset)}")
        }
    }
}