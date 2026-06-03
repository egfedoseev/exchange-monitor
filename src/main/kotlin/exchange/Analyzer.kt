package ru.jinushi.exchange

import java.math.BigDecimal
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import kotlin.collections.component1
import kotlin.collections.component2

class ArbitrageOpportunity(val buyExchange: Exchange, val sellExchange: Exchange, val profit: BigDecimal)

class ArbitrageAnalyzer(val currencyPair: CurrencyPair) {
    private val latestPrices = ConcurrentHashMap<Exchange, Ticker>()
    private var totalProfit = AtomicReference(BigDecimal.ZERO)

    private val baseAmount = BigDecimal.ONE

    suspend fun processNewTicker(ticker: Ticker) {
        latestPrices[ticker.exchange] = ticker

        val opportunity = calculateArbitrage(ticker) ?: return

        val buyTicker = latestPrices[opportunity.buyExchange] ?: return
        val sellTicker = latestPrices[opportunity.sellExchange] ?: return

        val realProfit = sellTicker.bid - buyTicker.ask

        if (realProfit <= BigDecimal.ZERO) {
            println("Trade cancelled")
            return
        }

        val buyWallet = opportunity.buyExchange.wallet
        val sellWallet = opportunity.sellExchange.wallet

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
        if (sellResult is TradeResult.Failed) {
            return
        }

        totalProfit.updateAndGet { current -> current.add(realProfit) }
        println("Profit: $realProfit, total profit: ${totalProfit.get()}")
    }

    private fun calculateArbitrage(newTicker: Ticker): ArbitrageOpportunity? =
        latestPrices.asSequence().filter { (exchange, _) -> exchange != newTicker.exchange }
            .flatMap { (_, oldTicker) ->
                buildList {
                    if (oldTicker.bid > newTicker.ask) {
                        add(
                            ArbitrageOpportunity(
                                newTicker.exchange, oldTicker.exchange,
                                oldTicker.bid - newTicker.ask
                            )
                        )
                    }
                    if (newTicker.bid > oldTicker.ask) {
                        add(
                            ArbitrageOpportunity(
                                oldTicker.exchange, newTicker.exchange,
                                newTicker.bid - oldTicker.ask
                            )
                        )
                    }
                }
            }.maxByOrNull { it.profit }
}
