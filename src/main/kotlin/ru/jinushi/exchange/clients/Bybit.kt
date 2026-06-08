package ru.jinushi.exchange.clients

import io.ktor.client.*
import io.ktor.websocket.*
import kotlinx.coroutines.delay
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import ru.jinushi.exchange.Ticker
import java.math.BigDecimal
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

class Bybit(client: HttpClient) : AbstractExchangeClient(client) {
    override val name: String
        get() = "Bybit"

    val jsonParser = Json { ignoreUnknownKeys = true }

    override val streamUrl: String
        get() = "wss://stream.bybit.com/v5/public/spot"

    override suspend fun WebSocketSession.subscribe(subscriptionEvent: String) {
        val frame = Frame.Text("""{"op":"subscribe","args":["orderbook.1.${subscriptionEvent}"]}""")
        send(frame)
    }

    override suspend fun WebSocketSession.ping() {
        delay(20.seconds)
        send(Frame.Text("""{"op": "ping"}"""))
    }

    override fun parseTickerAndSymbol(jsonText: String): ParsedTicker? {
        if (!jsonText.contains("orderbook.1.") || !jsonText.contains("\"data\"")) {
            return null
        }

        try {
            val parsedDTO = jsonParser.decodeFromString<BybitOrderBookDto>(jsonText)
            val data = parsedDTO.data ?: return null
            val bestBid = data.bids.firstOrNull()?.firstOrNull() ?: return null
            val bestAsk = data.asks.firstOrNull()?.firstOrNull() ?: return null
            val ticker = Ticker(
                exchange = this@Bybit,
                bid = BigDecimal(bestBid),
                ask = BigDecimal(bestAsk),
                timestamp = Clock.System.now()
            )
            return ParsedTicker(ticker, data.symbol)
        } catch (e: Exception) {
            logger.error("Bybit parsing failed for JSON: {}", jsonText, e)
            return null
        }
    }
}

@Serializable
private data class BybitOrderBookDto(
    @SerialName("data") val data: BybitOrderBookDataDto? = null
)

@Serializable
private data class BybitOrderBookDataDto(
    @SerialName("s") val symbol: String,
    @SerialName("b") val bids: List<List<String>>,
    @SerialName("a") val asks: List<List<String>>
)