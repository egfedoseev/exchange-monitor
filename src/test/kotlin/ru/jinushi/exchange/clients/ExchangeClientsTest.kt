package ru.jinushi.exchange.clients

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import ru.jinushi.exchange.CurrencyPair
import java.math.BigDecimal
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class ExchangeClientsTest {

    private val httpClient = HttpClient(CIO)

    private fun assertBigDecimalEquals(expected: BigDecimal, actual: BigDecimal, message: String = "") {
        assertEquals(0, expected.compareTo(actual), "$message. Expected: $expected, but got: $actual")
    }

    class MockWebSocketSession : WebSocketSession {
        val outgoingChannel = Channel<Frame>(capacity = 10)

        override val incoming = Channel<Frame>()
        override val outgoing = outgoingChannel
        override val coroutineContext: CoroutineContext = EmptyCoroutineContext
        override val extensions: List<WebSocketExtension<*>> = emptyList()
        override var masking: Boolean = false
        override var maxFrameSize: Long = 0

        override suspend fun flush() {}

        @Deprecated("Deprecated in Java")
        @Suppress("DEPRECATION")
        override fun terminate() {
            outgoingChannel.close()
        }
    }

    @Test
    fun testBinanceParseTickerAndSymbol_valid() {
        val client = Binance(httpClient)
        val json = """
            {
                "s": "BTCUSDT",
                "b": "60000.00",
                "a": "60100.50"
            }
        """.trimIndent()

        val parsed = client.parseTickerAndSymbol(json)
        assertNotNull(parsed)
        assertEquals("BTCUSDT", parsed.symbol)
        assertEquals(client, parsed.ticker.exchange)
        assertBigDecimalEquals(BigDecimal("60000.00"), parsed.ticker.bid)
        assertBigDecimalEquals(BigDecimal("60100.50"), parsed.ticker.ask)
    }

    @Test
    fun testBinanceParseTickerAndSymbol_invalid() {
        val client = Binance(httpClient)
        val json = """{"result":null,"id":1}"""
        val parsed = client.parseTickerAndSymbol(json)
        assertNull(parsed)
    }

    @Test
    fun testBybitParseTickerAndSymbol_valid() {
        val client = Bybit(httpClient)
        val json = """
            {
                "topic": "orderbook.1.BTCUSDT",
                "data": {
                    "s": "BTCUSDT",
                    "b": [["62000.00", "1.0"]],
                    "a": [["62050.25", "2.0"]]
                }
            }
        """.trimIndent()

        val parsed = client.parseTickerAndSymbol(json)
        assertNotNull(parsed)
        assertEquals("BTCUSDT", parsed.symbol)
        assertEquals(client, parsed.ticker.exchange)
        assertBigDecimalEquals(BigDecimal("62000.00"), parsed.ticker.bid)
        assertBigDecimalEquals(BigDecimal("62050.25"), parsed.ticker.ask)
    }

    @Test
    fun testBybitParseTickerAndSymbol_invalid() {
        val client = Bybit(httpClient)
        val json = """{"op":"subscribe","success":true}"""
        val parsed = client.parseTickerAndSymbol(json)
        assertNull(parsed)
    }

    @Test
    fun testBinanceStreamUrlAndName() {
        val client = Binance(httpClient)
        assertEquals("Binance", client.name)
        assertEquals("wss://stream.binance.com:9443/ws", client.streamUrl)
    }

    @Test
    fun testBybitStreamUrlAndName() {
        val client = Bybit(httpClient)
        assertEquals("Bybit", client.name)
        assertEquals("wss://stream.bybit.com/v5/public/spot", client.streamUrl)
    }

    @Test
    fun testBinanceSubscribe() = runTest {
        val client = Binance(httpClient)
        val session = MockWebSocketSession()
        
        with(client) {
            session.subscribe("BTCUSDT")
        }

        val frame = session.outgoingChannel.tryReceive().getOrNull()
        assertNotNull(frame)
        assertTrue(frame is Frame.Text)
        val text = frame.readText()
        
        val json = kotlinx.serialization.json.Json.parseToJsonElement(text).jsonObject
        assertEquals("SUBSCRIBE", json["method"]?.jsonPrimitive?.content)
        val params = json["params"]?.jsonArray
        assertNotNull(params)
        assertEquals(1, params.size)
        assertEquals("btcusdt@bookTicker", params[0].jsonPrimitive.content)
        val id = json["id"]?.jsonPrimitive?.content?.toIntOrNull()
        assertNotNull(id)
        assertTrue(id > 0)
    }

    @Test
    fun testBybitSubscribe() = runTest {
        val client = Bybit(httpClient)
        val session = MockWebSocketSession()

        with(client) {
            session.subscribe("BTCUSDT")
        }

        val frame = session.outgoingChannel.tryReceive().getOrNull()
        assertNotNull(frame)
        assertTrue(frame is Frame.Text)
        val text = frame.readText()
        assertEquals("""{"op":"subscribe","args":["orderbook.1.BTCUSDT"]}""", text)
    }

    @Test
    fun testBybitPing() = runTest {
        val client = Bybit(httpClient)
        val session = MockWebSocketSession()

        val job = launch {
            with(client) {
                session.ping()
            }
        }

        // Advance virtual time by 20 seconds
        testScheduler.advanceTimeBy(20_000)
        
        val frame = session.outgoingChannel.receive()
        assertNotNull(frame)
        assertTrue(frame is Frame.Text)
        assertEquals("""{"op": "ping"}""", frame.readText())
        job.cancel()
    }

    @Test
    fun testBinancePing() = runTest {
        val client = Binance(httpClient)
        val session = MockWebSocketSession()

        val job = launch {
            with(client) {
                session.ping()
            }
        }

        // Advance virtual time by 1 hour (3,600,000 milliseconds)
        testScheduler.advanceTimeBy(3600_000)
        
        // Binance ping delays infinitely and sends nothing client-side, so frame should be null
        val frame = session.outgoingChannel.tryReceive().getOrNull()
        assertNull(frame)
        job.cancel()
    }

    @Test
    @Ignore("Requires internet connection to connect to Binance and Bybit live feeds")
    fun testRealBinanceAndBybitTickerFetch() {
        runBlocking {
            val httpClient = HttpClient(CIO) {
                install(io.ktor.client.plugins.websocket.WebSockets)
            }

            val binance = Binance(httpClient)
            val bybit = Bybit(httpClient)

            val pair = CurrencyPair("BTC/USDT")

            println("Starting real tickers test for BTC/USDT...")

            val binanceTickerJob = runCatching {
                withTimeout(15.seconds) {
                    val flow = binance.getFlow(pair)
                    val ticker = flow.first()
                    println("SUCCESS: Binance ticker received: bid=${ticker.bid}, ask=${ticker.ask}")
                    ticker
                }
            }

            val bybitTickerJob = runCatching {
                withTimeout(15.seconds) {
                    val flow = bybit.getFlow(pair)
                    val ticker = flow.first()
                    println("SUCCESS: Bybit ticker received: bid=${ticker.bid}, ask=${ticker.ask}")
                    ticker
                }
            }

            httpClient.close()

            val binanceResult = binanceTickerJob.getOrNull()
            val bybitResult = bybitTickerJob.getOrNull()

            assertNotNull(binanceResult, "Failed to receive ticker from Binance: ${binanceTickerJob.exceptionOrNull()}")
            assertNotNull(bybitResult, "Failed to receive ticker from Bybit: ${bybitTickerJob.exceptionOrNull()}")
        }
    }
}
