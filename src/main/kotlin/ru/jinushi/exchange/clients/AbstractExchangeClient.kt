package ru.jinushi.exchange.clients

import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.util.logging.Logger
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
import kotlinx.coroutines.sync.Mutex
import org.slf4j.LoggerFactory
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
    protected val logger: Logger = LoggerFactory.getLogger(this::class.java)

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
            logger.info("Successfully connected to websocket")
            connected = true

            for (symbol in flowChannels.keys) {
                try {
                    subscribe(symbol)
                    logger.info("Re-subscribed to {}", symbol)
                } catch (e: Exception) {
                    logger.error("Failed to re-subscribe to {}: {}", symbol, e.message)
                }
            }

            launch {
                for (subscriptionEvent in subscriptionChannel) {
                    try {
                        subscribe(subscriptionEvent)
                    } catch (e: Exception) {
                        logger.error("Failed to subscribe to {}: {}", subscriptionEvent, e.message)
                    }
                }
            }

            launch {
                while (this@webSocket.isActive) {
                    ping()
                }
            }

            for (frame in incoming) {
                if (frame is Frame.Text) {
                    val (ticker, symbol) = parseTickerAndSymbol(frame.readText()) ?: continue
                    flowChannels[symbol.uppercase()]?.tryEmit(ticker)
                }
            }
        }
    }

    private suspend fun connectToServer() {
        while (scope.isActive) {
            try {
                runWebSocket()
            } catch (e: Exception) {
                logger.error("Connection lost or failed: {}. Reconnecting in 5s...", e.message)
            } finally {
                connected = false
            }
            delay(5.seconds)
        }
    }

    @Volatile
    private var connected = false

    private val mutex = Mutex()

    override suspend fun getFlow(currencyPair: CurrencyPair): Flow<Ticker> {
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
            mutex.lock()
            if (!connected) {
                scope.launch { connectToServer() }
            }
            mutex.unlock()
        }

        return sharedFlow
    }

    override fun toString(): String = name
}