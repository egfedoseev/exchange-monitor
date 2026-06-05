package ru.jinushi.exchange.clients

import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import ru.jinushi.exchange.CurrencyPair
import ru.jinushi.exchange.Exchange
import ru.jinushi.exchange.Ticker
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds

data class ParsedTicker(
    val ticker: Ticker,
    val symbol: String
)

abstract class AbstractExchangeClient(private val client: HttpClient) : Exchange {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val subscriptionChannel = Channel<String>(
        capacity = 10,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private val flowChannels = ConcurrentHashMap<String, MutableSharedFlow<Ticker>>()

    protected abstract val streamUrl: String

    protected abstract suspend fun WebSocketSession.subscribe(subscriptionEvent: String)

    protected abstract suspend fun WebSocketSession.ping()

    protected abstract fun parseTickerAndSymbol(jsonText: String): ParsedTicker?

    private suspend fun runWebSocket() {
        client.webSocket(streamUrl) {
            println("[$name] Successfully connected to websocket")
            connected = true

            launch {
                for (subscriptionEvent in subscriptionChannel) {
                    subscribe(subscriptionEvent)
                }
            }

            launch {
                while (this@webSocket.isActive) {
                    ping()
                }
            }

            for (frame in incoming) {
                if (frame is Frame.Text) {
                    val parsed = parseTickerAndSymbol(frame.readText()) ?: continue
                    flowChannels[parsed.symbol.uppercase()]?.tryEmit(parsed.ticker)
                }
            }
        }
    }

    private suspend fun connectToServer() {
        while (scope.isActive) {
            try {
                runWebSocket()
            } catch (e: Exception) {
                println("[$name] Connection lost or failed: ${e.message}. Reconnecting in 5s...")
            } finally {
                connected = false
            }
            delay(5.seconds)
        }
    }

    @Volatile
    private var connected = false

    override fun getFlow(currencyPair: CurrencyPair): Flow<Ticker> {
        val symbol = currencyPair.merged.uppercase()

        val sharedFlow = flowChannels.computeIfAbsent(symbol) {
            subscriptionChannel.trySend(symbol)

            MutableSharedFlow(
                replay = 0,
                extraBufferCapacity = 128,
                onBufferOverflow = BufferOverflow.DROP_OLDEST
            )
        }

        if (!connected) {
            synchronized(this) {
                if (!connected) {
                    scope.launch { connectToServer() }
                }
            }
        }

        return sharedFlow
    }
}