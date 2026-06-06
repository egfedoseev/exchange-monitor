package ru.jinushi.exchange.analyzer

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runTest
import ru.jinushi.exchange.CurrencyPair
import ru.jinushi.exchange.Exchange
import ru.jinushi.exchange.Ticker
import ru.jinushi.exchange.wallet.Wallet
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Clock

class ArbitrageAnalyzerTest {

    private class MockExchange(override val name: String) : Exchange {
        override suspend fun getFlow(currencyPair: CurrencyPair) = throw UnsupportedOperationException()
    }

    private class MockWallet : Wallet {
        override suspend fun getBalance(asset: ru.jinushi.exchange.wallet.Asset) = BigDecimal.ZERO
        override suspend fun executeTrade(order: ru.jinushi.exchange.trading.TradeOrder) = throw UnsupportedOperationException()
        override suspend fun getBalances() = emptyMap<ru.jinushi.exchange.wallet.Asset, BigDecimal>()
    }

    @Test
    fun testProcessNewTicker_findsOpportunity() = runTest {
        val pair = CurrencyPair("BTC/USD")
        val commandChannel = Channel<TradeEvent.OpportunityFound>(capacity = 10)
        
        val exchangeA = MockExchange("ExchangeA")
        val exchangeB = MockExchange("ExchangeB")
        
        val wallets: Map<Exchange, Wallet> = mapOf(
            exchangeA to MockWallet(),
            exchangeB to MockWallet()
        )
        
        val analyzer = ArbitrageAnalyzer(pair, commandChannel, wallets)
        
        val now = Clock.System.now()
        // Ticker for ExchangeA: Bid=98, Ask=100
        val tickerA = Ticker(exchangeA, bid = BigDecimal("98"), ask = BigDecimal("100"), timestamp = now)
        analyzer.processNewTicker(tickerA)
        
        // Ticker for ExchangeB: Bid=102, Ask=104
        // ExchangeB Bid (102) > ExchangeA Ask (100) -> Profit = 2
        val tickerB = Ticker(exchangeB, bid = BigDecimal("102"), ask = BigDecimal("104"), timestamp = now)
        analyzer.processNewTicker(tickerB)
        
        val event = commandChannel.tryReceive().getOrNull()
        assertNotNull(event)
        assertEquals(pair, event.currencyPair)
        assertEquals(exchangeA, event.buyTicker.exchange)
        assertEquals(exchangeB, event.sellTicker.exchange)
        assertEquals(BigDecimal("100"), event.buyTicker.ask)
        assertEquals(BigDecimal("102"), event.sellTicker.bid)
    }

    @Test
    fun testProcessNewTicker_noOpportunity() = runTest {
        val pair = CurrencyPair("BTC/USD")
        val commandChannel = Channel<TradeEvent.OpportunityFound>(capacity = 10)
        
        val exchangeA = MockExchange("ExchangeA")
        val exchangeB = MockExchange("ExchangeB")
        
        val wallets: Map<Exchange, Wallet> = mapOf(
            exchangeA to MockWallet(),
            exchangeB to MockWallet()
        )
        
        val analyzer = ArbitrageAnalyzer(pair, commandChannel, wallets)
        
        val now = Clock.System.now()
        // Ticker for ExchangeA: Bid=98, Ask=100
        val tickerA = Ticker(exchangeA, bid = BigDecimal("98"), ask = BigDecimal("100"), timestamp = now)
        analyzer.processNewTicker(tickerA)
        
        // Ticker for ExchangeB: Bid=99, Ask=101 (No overlapping profit since BidB (99) < AskA (100) and BidA (98) < AskB (101))
        val tickerB = Ticker(exchangeB, bid = BigDecimal("99"), ask = BigDecimal("101"), timestamp = now)
        analyzer.processNewTicker(tickerB)
        
        val event = commandChannel.tryReceive().getOrNull()
        assertNull(event)
    }
}
