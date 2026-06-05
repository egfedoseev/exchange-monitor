package ru.jinushi.exchange.analyzer

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import ru.jinushi.exchange.wallet.OrderType
import ru.jinushi.exchange.wallet.TradeOrder
import ru.jinushi.exchange.wallet.TradeResult
import java.math.BigDecimal
import java.util.concurrent.atomic.AtomicReference

class TradeExecutionManager(private val commandChannel: Channel<TradeEvent.OpportunityFound>) {
    private val scope = CoroutineScope(Dispatchers.IO)

    private val baseAmount = BigDecimal.valueOf(0.001)

    private val totalProfit = AtomicReference(BigDecimal.ZERO)

    fun startWorkers(workersCount: Int) {
        repeat(workersCount) { workerId ->
            scope.launch {
                for (event in commandChannel) {
                    println("[Worker #$workerId] caught signal, initiating trade...")

                    try {
                        executeTradeSafely(event)
                    } catch (e: Exception) {
                        println("[Worker #$workerId] Exception: ${e.message}")
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
            println(
                "Successful trade, total profit ${
                    totalProfit.updateAndGet { prev ->
                        prev.add(
                            sellResult.actualAmount.multiply(sellResult.actualPrice)
                                .subtract(buyResult.actualAmount.multiply(buyResult.actualPrice))
                        )
                    }
                }"
            )
        }
    }
}