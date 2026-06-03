package ru.jinushi.exchange.analyzer

import ru.jinushi.exchange.Exchange
import ru.jinushi.exchange.Ticker
import ru.jinushi.exchange.wallet.Asset

sealed interface TradeEvent {
    data class OpportunityFound(
        val opportunity: ArbitrageOpportunity,
        val buyTicker: Ticker,
        val sellTicker: Ticker
    ) : TradeEvent

    data class InventoryImbalance(
        val exchange: Exchange,
        val asset: Asset
    ) : TradeEvent
}