package ru.jinushi.exchange

import kotlinx.coroutines.flow.Flow
import ru.jinushi.exchange.wallet.Wallet

object Binance : Exchange {
    override val name: String
        get() = "Binance"
    override val wallet: Wallet
        get() = TODO("Not yet implemented")

    override fun getFlow(currencyPair: CurrencyPair): Flow<Ticker> {
        TODO("Not yet implemented")
        // здесь будет ktor клиент, который посылает запросы
    }
}