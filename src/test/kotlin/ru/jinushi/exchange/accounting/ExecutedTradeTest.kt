package ru.jinushi.exchange.accounting

import ru.jinushi.exchange.CurrencyPair
import ru.jinushi.exchange.wallet.Asset
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals

class ExecutedTradeTest {

    @Test
    fun testNetProfitCalculation() {
        val pair = CurrencyPair("BTC/USD")

        val trade = ExecutedTrade(
            currencyPair = pair,
            buyAmount = BigDecimal("0.5"),
            buyPrice = BigDecimal("30000"),
            sellAmount = BigDecimal("0.5"),
            sellPrice = BigDecimal("31000")
        )
        
        assertEquals(BigDecimal("500").stripTrailingZeros(), trade.netProfit.stripTrailingZeros())
    }

    @Test
    fun testProfitTracker() {
        val usd = Asset("USD")
        val pair = CurrencyPair("BTC/USD")

        val trade1 = ExecutedTrade(
            currencyPair = pair,
            buyAmount = BigDecimal("0.5"),
            buyPrice = BigDecimal("30000"),
            sellAmount = BigDecimal("0.5"),
            sellPrice = BigDecimal("31000")
        )
        ProfitTracker.registerTrade(trade1)

        val trade2 = ExecutedTrade(
            currencyPair = pair,
            buyAmount = BigDecimal("1.0"),
            buyPrice = BigDecimal("30000"),
            sellAmount = BigDecimal("1.0"),
            sellPrice = BigDecimal("29900")
        )
        ProfitTracker.registerTrade(trade2)

        // Total profit should be 400 USD
        assertEquals(BigDecimal("400").stripTrailingZeros(), ProfitTracker.getProfit(usd)?.stripTrailingZeros())
    }
}
