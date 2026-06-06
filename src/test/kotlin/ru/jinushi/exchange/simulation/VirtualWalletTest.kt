package ru.jinushi.exchange.simulation

import kotlinx.coroutines.test.runTest
import ru.jinushi.exchange.trading.OrderType
import ru.jinushi.exchange.trading.TradeOrder
import ru.jinushi.exchange.trading.TradeResult
import ru.jinushi.exchange.wallet.Asset
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VirtualWalletTest {

    @Test
    fun testInitialBalances() = runTest {
        val btc = Asset("BTC")
        val usd = Asset("USD")
        val wallet = VirtualWallet(
            mapOf(
                btc to BigDecimal("1.5"),
                usd to BigDecimal("1000")
            )
        )

        assertEquals(BigDecimal("1.5"), wallet.getBalance(btc))
        assertEquals(BigDecimal("1000"), wallet.getBalance(usd))
        assertEquals(BigDecimal.ZERO, wallet.getBalance(Asset("EUR")))
    }

    @Test
    fun testExecuteTrade_buySuccess() = runTest {
        val btc = Asset("BTC")
        val usd = Asset("USD")
        val wallet = VirtualWallet(
            mapOf(
                btc to BigDecimal.ZERO,
                usd to BigDecimal("1000")
            )
        )

        // Buy 0.5 BTC at price 1500 USD per BTC -> costs 750 USD
        val order = TradeOrder(
            asset = btc,
            quoteAsset = usd,
            amount = BigDecimal("0.5"),
            targetPrice = BigDecimal("1500"),
            type = OrderType.BUY
        )

        val result = wallet.executeTrade(order)
        assertTrue(result is TradeResult.Success)
        assertEquals(BigDecimal("1500"), result.actualPrice)
        assertEquals(BigDecimal("0.5"), result.actualAmount)

        // Balances updated
        assertEquals(BigDecimal("0.5"), wallet.getBalance(btc))
        assertEquals(BigDecimal("250").stripTrailingZeros(), wallet.getBalance(usd).stripTrailingZeros())
    }

    @Test
    fun testExecuteTrade_sellSuccess() = runTest {
        val btc = Asset("BTC")
        val usd = Asset("USD")
        val wallet = VirtualWallet(
            mapOf(
                btc to BigDecimal("2.0"),
                usd to BigDecimal.ZERO
            )
        )

        // Sell 1.5 BTC at price 2000 USD per BTC -> yields 3000 USD
        val order = TradeOrder(
            asset = btc,
            quoteAsset = usd,
            amount = BigDecimal("1.5"),
            targetPrice = BigDecimal("2000"),
            type = OrderType.SELL
        )

        val result = wallet.executeTrade(order)
        assertTrue(result is TradeResult.Success)

        // Balances updated
        assertEquals(BigDecimal("0.5"), wallet.getBalance(btc))
        assertEquals(BigDecimal("3000").stripTrailingZeros(), wallet.getBalance(usd).stripTrailingZeros())
    }

    @Test
    fun testExecuteTrade_insufficientFunds() = runTest {
        val btc = Asset("BTC")
        val usd = Asset("USD")
        val wallet = VirtualWallet(
            mapOf(
                btc to BigDecimal("0.1"),
                usd to BigDecimal("100")
            )
        )

        // Try to buy 1 BTC at price 500 USD (needs 500 USD, only has 100 USD)
        val buyOrder = TradeOrder(
            asset = btc,
            quoteAsset = usd,
            amount = BigDecimal("1.0"),
            targetPrice = BigDecimal("500"),
            type = OrderType.BUY
        )
        val buyResult = wallet.executeTrade(buyOrder)
        assertTrue(buyResult is TradeResult.Failed)

        // Try to sell 0.5 BTC (only has 0.1 BTC)
        val sellOrder = TradeOrder(
            asset = btc,
            quoteAsset = usd,
            amount = BigDecimal("0.5"),
            targetPrice = BigDecimal("2000"),
            type = OrderType.SELL
        )
        val sellResult = wallet.executeTrade(sellOrder)
        assertTrue(sellResult is TradeResult.Failed)

        // Balances should remain unchanged
        assertEquals(BigDecimal("0.1"), wallet.getBalance(btc))
        assertEquals(BigDecimal("100"), wallet.getBalance(usd))
    }
}
