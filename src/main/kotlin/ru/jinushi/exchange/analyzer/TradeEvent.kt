package ru.jinushi.exchange.analyzer

import ru.jinushi.exchange.CurrencyPair
import ru.jinushi.exchange.Ticker
import ru.jinushi.exchange.wallet.Wallet

sealed interface TradeEvent {
    data class OpportunityFound(
        val currencyPair: CurrencyPair,
        val buyTicker: Ticker,
        val sellTicker: Ticker,
        val buyWallet: Wallet,
        val sellWallet: Wallet
    ) : TradeEvent
}