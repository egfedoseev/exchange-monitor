package ru.jinushi.exchange.clients

import io.ktor.client.*
import io.ktor.websocket.*
import kotlinx.coroutines.delay
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import ru.jinushi.exchange.Ticker
import java.math.BigDecimal
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds

class Binance(client: HttpClient) : AbstractExchangeClient(client) {

    override val name: String = "Binance"

    override val streamUrl: String
        get() = "wss://stream.binance.com:9443/ws"

    override suspend fun WebSocketSession.subscribe(subscriptionEvent: String) {
        val text = """
            {
                "method": "SUBSCRIBE",
                "params": [
                    "${subscriptionEvent.lowercase()}@bookTicker"
                ],
                "id": ${Random.nextInt()}
            }
        """.trimIndent()
        send(Frame.Text(text))
    }

    override suspend fun WebSocketSession.ping() {
        delay(Long.MAX_VALUE.milliseconds)
    }

    private val jsonParser = Json { ignoreUnknownKeys = true }

    override fun parseTickerAndSymbol(jsonText: String): ParsedTicker? {
        if (!jsonText.contains("\"b\"")) {
            return null
        }
        val parsed = jsonParser.decodeFromString<BinanceBookTickerDto>(jsonText)
        val ticker = Ticker(
            exchange = this@Binance,
            bid = BigDecimal(parsed.bestBid),
            ask = BigDecimal(parsed.bestAsk),
            timestamp = Clock.System.now()
        )
        return ParsedTicker(ticker, parsed.symbol)
    }
}

@Serializable
data class BinanceBookTickerDto(
    @SerialName("s") val symbol: String,
    @SerialName("b") val bestBid: String,
    @SerialName("a") val bestAsk: String
)
