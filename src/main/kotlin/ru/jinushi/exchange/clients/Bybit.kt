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
        val frame = Frame.Text("""{"op":"subscribe","args":["tickers.${subscriptionEvent}"]}""")
        send(frame)
    }

    override suspend fun WebSocketSession.ping() {
        delay(20.seconds)
        send(Frame.Text("""{"op": "ping"}"""))
    }

    override fun parseTickerAndSymbol(jsonText: String): ParsedTicker? {
        if (!jsonText.contains("\"data\"")) {
            return null
        }

        val parsedDTO = jsonParser.decodeFromString<BybitBookTickerDto>(jsonText)
        val data = parsedDTO.data
        val ticker = Ticker(
            exchange = this@Bybit,
            bid = BigDecimal(data.bestBid),
            ask = BigDecimal(data.bestAsk),
            timestamp = Clock.System.now()
        )
        return ParsedTicker(ticker, data.symbol)
    }
}

@Serializable
private data class BybitBookTickerDto(
    @SerialName("data") val data: BybitTickerDataDto
)

@Serializable
private data class BybitTickerDataDto(
    @SerialName("symbol") val symbol: String,
    @SerialName("bid1Price") val bestBid: String,
    @SerialName("ask1Price") val bestAsk: String
)