package ru.jinushi.exchange

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