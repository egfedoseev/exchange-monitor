package ru.jinushi.exchange

import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import ru.jinushi.exchange.wallet.Wallet
import java.math.BigDecimal
import kotlin.time.Clock

class Binance(override val wallet: Wallet) : Exchange {
    override val name: String
        get() = "Binance"

    override fun getFlow(currencyPair: CurrencyPair): Flow<Ticker> = channelFlow {
        val formattedPair = (currencyPair.second + currencyPair.first).lowercase()
        val url = "wss://stream.binance.com:9443/ws/$formattedPair@ticker"
        try {
            httpClient.webSocket(urlString = url) {
                println("[$name] Успешно подключились к WebSocket-стриму для $formattedPair")

                for (frame in incoming) {
                    when (frame) {
                        is Frame.Text -> {
                            val jsonText = frame.readText()
                            val ticker = parseBinanceJson(jsonText, currencyPair)

                            send(ticker)
                        }

                        else -> { // ping pong
                        }
                    }
                }
            }
        } catch (e: Exception) {
            println("[$name] Ошибка сокета: ${e.message}")
            // TODO reconnect
        }
    }

    private fun parseBinanceJson(json: String, currencyPair: CurrencyPair): Ticker { // TODO json deserialization
        return Ticker(
            exchange = this,
            currencyPair = currencyPair,
            bid = BigDecimal("65000"),
            ask = BigDecimal("65050"),
            timestamp = Clock.System.now()
        )
    }
}