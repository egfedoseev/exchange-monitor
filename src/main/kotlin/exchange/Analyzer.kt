package ru.jinushi.exchange

import java.math.BigDecimal
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.component1
import kotlin.collections.component2

object ArbitrageAnalyzer {
    private val latestPrices = ConcurrentHashMap<Exchange, Ticker>()
    private var profit = BigDecimal.ZERO

    fun processNewTicker(ticker: Ticker) {
        latestPrices[ticker.exchange] = ticker

        val opportunity = calculateArbitrage(ticker) ?: return

        profit += latestPrices.getValue(opportunity.sellExchange).bid - latestPrices.getValue(opportunity.buyExchange).ask
        println("$opportunity $profit")
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
