package ru.jinushi.exchange

import kotlinx.coroutines.channels.Channel
import java.math.BigDecimal
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import kotlin.collections.component1
import kotlin.collections.component2

class ArbitrageOpportunity(val buyExchange: Exchange, val sellExchange: Exchange, val profit: BigDecimal)

class ArbitrageAnalyzer(
    private val commandChannel: Channel<TradeEvent.OpportunityFound>
) {
    private val latestPrices = ConcurrentHashMap<Exchange, Ticker>()

    fun processNewTicker(ticker: Ticker) {
        latestPrices[ticker.exchange] = ticker

        val opportunity = calculateArbitrage(ticker) ?: return

        val buyTicker = latestPrices[opportunity.buyExchange] ?: return
        val sellTicker = latestPrices[opportunity.sellExchange] ?: return

        val realProfit = sellTicker.bid - buyTicker.ask

        if (realProfit <= BigDecimal.ZERO) {
            println("Trade cancelled")
            return
        }

        commandChannel.trySend(
            TradeEvent.OpportunityFound(opportunity, buyTicker, sellTicker)
        )
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
