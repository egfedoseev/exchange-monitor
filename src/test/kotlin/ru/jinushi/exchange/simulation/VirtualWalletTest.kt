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

    private fun assertBigDecimalEquals(expected: BigDecimal, actual: BigDecimal, message: String = "") {
        assertEquals(0, expected.compareTo(actual), "$message. Expected: $expected, but got: $actual")
    }

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

        assertBigDecimalEquals(BigDecimal("1.5"), wallet.getBalance(btc))
        assertBigDecimalEquals(BigDecimal("1000"), wallet.getBalance(usd))
        assertBigDecimalEquals(BigDecimal.ZERO, wallet.getBalance(Asset("EUR")))
    }

    @Test
    fun testExecuteTrade_buySuccess() = runTest {
        val btc = Asset("BTC")
        val usd = Asset("USD")
        val wallet = VirtualWallet(
            mapOf(
                btc to BigDecimal.ZERO,
                usd to BigDecimal("1000")
            ),
            BigDecimal.ZERO
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
        assertTrue(result is TradeResult.Success, (result as? TradeResult.Failed)?.reason ?: "")
        assertBigDecimalEquals(BigDecimal("1500"), result.actualPrice)
        assertBigDecimalEquals(BigDecimal("0.5"), result.actualAmount)

        // Balances updated
        assertBigDecimalEquals(BigDecimal("0.5"), wallet.getBalance(btc))
        assertBigDecimalEquals(BigDecimal("250"), wallet.getBalance(usd))
    }

    @Test
    fun testExecuteTrade_buyWithFeeSuccess() = runTest {
        val btc = Asset("BTC")
        val usd = Asset("USD")
        val wallet = VirtualWallet(
            mapOf(
                btc to BigDecimal.ZERO,
                usd to BigDecimal("1000")
            ),
            BigDecimal("0.001") // 0.1% fee
        )

        // Buy 0.5 BTC at price 1500 USD per BTC -> costs 750 USD
        // Fee (0.1%) deducted from received BTC -> 0.5 * 0.999 = 0.4995 BTC
        val order = TradeOrder(
            asset = btc,
            quoteAsset = usd,
            amount = BigDecimal("0.5"),
            targetPrice = BigDecimal("1500"),
            type = OrderType.BUY
        )

        val result = wallet.executeTrade(order)
        assertTrue(result is TradeResult.Success, (result as? TradeResult.Failed)?.reason ?: "")

        // Balances updated (BTC has fee deducted, USD spent is exactly 750)
        assertBigDecimalEquals(BigDecimal("0.49950000"), wallet.getBalance(btc))
        assertBigDecimalEquals(BigDecimal("250.00"), wallet.getBalance(usd))
    }

    @Test
    fun testExecuteTrade_sellSuccess() = runTest {
        val btc = Asset("BTC")
        val usd = Asset("USD")
        val wallet = VirtualWallet(
            mapOf(
                btc to BigDecimal("2.0"),
                usd to BigDecimal.ZERO
            ),
            BigDecimal.ZERO
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
        assertTrue(result is TradeResult.Success, (result as? TradeResult.Failed)?.reason ?: "")
        assertBigDecimalEquals(BigDecimal("2000"), result.actualPrice)
        assertBigDecimalEquals(BigDecimal("1.5"), result.actualAmount)

        // Balances updated
        assertBigDecimalEquals(BigDecimal("0.5"), wallet.getBalance(btc))
        assertBigDecimalEquals(BigDecimal("3000"), wallet.getBalance(usd))
    }

    @Test
    fun testExecuteTrade_sellWithFeeSuccess() = runTest {
        val btc = Asset("BTC")
        val usd = Asset("USD")
        val wallet = VirtualWallet(
            mapOf(
                btc to BigDecimal("2.0"),
                usd to BigDecimal.ZERO
            ),
            BigDecimal("0.001") // 0.1% fee
        )

        // Sell 1.5 BTC at price 2000 USD per BTC -> yields 3000 USD
        // Fee (0.1%) deducted from received USD -> 3000 * 0.999 = 2997 USD
        val order = TradeOrder(
            asset = btc,
            quoteAsset = usd,
            amount = BigDecimal("1.5"),
            targetPrice = BigDecimal("2000"),
            type = OrderType.SELL
        )

        val result = wallet.executeTrade(order)
        assertTrue(result is TradeResult.Success, (result as? TradeResult.Failed)?.reason ?: "")

        // Balances updated (BTC is spent exactly 1.5, USD received has fee deducted)
        assertBigDecimalEquals(BigDecimal("0.50000000"), wallet.getBalance(btc))
        assertBigDecimalEquals(BigDecimal("2997.00"), wallet.getBalance(usd))
    }

    @Test
    fun testExecuteTrade_insufficientFunds() = runTest {
        val btc = Asset("BTC")
        val usd = Asset("USD")
        val wallet = VirtualWallet(
            mapOf(
                btc to BigDecimal("0.1"),
                usd to BigDecimal("100")
            ),
            BigDecimal.ZERO
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
        assertBigDecimalEquals(BigDecimal("0.1"), wallet.getBalance(btc))
        assertBigDecimalEquals(BigDecimal("100"), wallet.getBalance(usd))
    }
}
