package ru.jinushi.exchange.analyzer

import kotlinx.coroutines.channels.Channel
import ru.jinushi.exchange.CurrencyPair
import ru.jinushi.exchange.Exchange
import ru.jinushi.exchange.Ticker
import ru.jinushi.exchange.wallet.Wallet
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.math.BigDecimal
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.component1
import kotlin.collections.component2

class ArbitrageOpportunity(val buyExchange: Exchange, val sellExchange: Exchange, val profit: BigDecimal)

class ArbitrageAnalyzer(
    val currencyPair: CurrencyPair,
    private val commandChannel: Channel<TradeEvent.OpportunityFound>,
    wallets: Map<Exchange, Wallet>
) : Closeable {
    private val logger = LoggerFactory.getLogger(ArbitrageAnalyzer::class.java)
    private val latestPrices = ConcurrentHashMap<Exchange, Ticker>()
    val wallets = ConcurrentHashMap(wallets)

    fun processNewTicker(ticker: Ticker) {
        logger.debug("Received ticker for {}: exchange={}, bid={}, ask={}", currencyPair.raw, ticker.exchange.name, ticker.bid, ticker.ask)
        latestPrices[ticker.exchange] = ticker

        val opportunity = calculateArbitrage(ticker) ?: return

        val buyTicker = latestPrices[opportunity.buyExchange] ?: return
        val sellTicker = latestPrices[opportunity.sellExchange] ?: return

        val realProfit = sellTicker.bid - buyTicker.ask

        if (realProfit <= BigDecimal.ZERO) {
            return
        }

        val buyWallet = wallets[buyTicker.exchange] ?: return
        val sellWallet = wallets[sellTicker.exchange] ?: return

        commandChannel.trySend(
            TradeEvent.OpportunityFound(currencyPair, buyTicker, sellTicker, buyWallet, sellWallet)
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

    override fun close() {

    }
}
