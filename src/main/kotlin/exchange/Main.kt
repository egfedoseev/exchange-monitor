package ru.jinushi.exchange

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import ru.jinushi.exchange.analyzer.ArbitrageAnalyzer
import ru.jinushi.exchange.analyzer.TradeEvent
import ru.jinushi.exchange.analyzer.TradeExecutionManager
import ru.jinushi.exchange.virtual.VirtualExchange
import ru.jinushi.exchange.virtual.VirtualWallet
import ru.jinushi.exchange.wallet.Asset
import java.math.BigDecimal

fun main() {
    val commandChannel = Channel<TradeEvent.OpportunityFound>(
        capacity = 50,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    val executionManager = TradeExecutionManager(commandChannel)
    executionManager.startWorkers(workersCount = 10)

    val binanceWallet = VirtualWallet(
        mapOf(
            Pair(Asset("USD"), BigDecimal.TEN),
            Pair(Asset("BTC"), BigDecimal.TEN)
        )
    )
    val bybitWallet = VirtualWallet(
        mapOf(
            Pair(Asset("USD"), BigDecimal.TEN),
            Pair(Asset("BTC"), BigDecimal.TEN)
        )
    )

    val exchanges = listOf(
        VirtualExchange(
            "Binance-Sim"
        ),
        VirtualExchange(
            "Bybit-Sim"
        )
    )
    exchanges.forEach { it.updateTicker() }
    val currencyPair = CurrencyPair("USD/BTC")
    val analyzer = ArbitrageAnalyzer(
        currencyPair,
        commandChannel,
        mapOf(Pair(exchanges[0], binanceWallet), Pair(exchanges[1], bybitWallet))
    )
    val exchangesFlow = exchanges.map { it.getFlow(currencyPair) }.merge()
    runBlocking {
        launch {
            exchangesFlow.collect(analyzer::processNewTicker)
        }
    }
}
