package ru.jinushi.exchange.analyzer

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import ru.jinushi.exchange.wallet.Asset
import ru.jinushi.exchange.wallet.OrderType
import ru.jinushi.exchange.wallet.TradeOrder
import ru.jinushi.exchange.wallet.TradeResult
import java.math.BigDecimal
import java.util.concurrent.atomic.AtomicReference

class ExecutionManager(
    private val commandChannel: Channel<TradeEvent.OpportunityFound>
) {
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
        val buyWallet = event.opportunity.buyExchange.wallet
        val sellWallet = event.opportunity.sellExchange.wallet
        val buyTicker = event.buyTicker
        val sellTicker = event.sellTicker
        val currencyPair = buyTicker.currencyPair

        val buyResult = buyWallet.executeTrade(
            TradeOrder(
                Asset(currencyPair.second), Asset(currencyPair.first),
                baseAmount, buyTicker.ask, OrderType.BUY
            )
        )
        val sellResult = when (buyResult) {
            is TradeResult.Failed -> return
            is TradeResult.Success -> {
                val amountToSell = buyResult.actualAmount

                sellWallet.executeTrade(
                    TradeOrder(
                        Asset(currencyPair.first), Asset(currencyPair.second),
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