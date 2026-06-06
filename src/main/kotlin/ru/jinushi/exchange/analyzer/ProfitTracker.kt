package ru.jinushi.exchange.analyzer

import org.slf4j.LoggerFactory
import ru.jinushi.exchange.CurrencyPair
import ru.jinushi.exchange.wallet.Asset
import java.math.BigDecimal
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Clock
import kotlin.time.Instant

private val logger = LoggerFactory.getLogger("Application")

object ProfitTracker {
    private val profitByAsset = ConcurrentHashMap<Asset, BigDecimal>()

    fun registerTrade(trade: ExecutedTrade) {
        logger.atInfo().log(trade.toString())
        profitByAsset.merge(trade.currencyPair.quoteAsset, trade.netProfit) { old, new -> old.add(new) }
    }

    fun getProfit(asset: Asset): BigDecimal? = profitByAsset[asset]

    fun getProfits(): Map<Asset, BigDecimal> = profitByAsset
}

data class ExecutedTrade(
    val currencyPair: CurrencyPair,
    val buyAmount: BigDecimal,
    val buyPrice: BigDecimal,
    val sellAmount: BigDecimal,
    val sellPrice: BigDecimal,
    val timestamp: Instant = Clock.System.now()
) {
    val netProfit: BigDecimal
        get() = (sellAmount.multiply(sellPrice)).subtract(buyAmount.multiply(buyPrice))
}