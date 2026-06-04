package ru.jinushi.exchange.clients

import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import ru.jinushi.exchange.CurrencyPair
import ru.jinushi.exchange.Exchange
import ru.jinushi.exchange.Ticker
import ru.jinushi.exchange.httpClient
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

                            SendChannel.send(ticker)
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