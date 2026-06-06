package ru.jinushi.exchange.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.request.requirePathParameter
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.util.getOrFail
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import ru.jinushi.exchange.CurrencyPair
import ru.jinushi.exchange.analyzer.ArbitrageAnalyzer
import ru.jinushi.exchange.accounting.ProfitTracker
import ru.jinushi.exchange.analyzer.TradeEvent
import ru.jinushi.exchange.registry.AnalyzerRegistry
import ru.jinushi.exchange.registry.ExchangeRegistry
import ru.jinushi.exchange.registry.WalletRegistry
import ru.jinushi.exchange.serializers.BigDecimalSerializer

fun Route.analyzerRoutes(
    commandChannel: Channel<TradeEvent.OpportunityFound>,
    analyzerRegistry: AnalyzerRegistry,
    exchangeRegistry: ExchangeRegistry,
    walletRegistry: WalletRegistry
) {
    val currencyPairRegex = Regex("^[A-Z0-9]{2,5}/[A-Z0-9]{2,5}$")
    route("/analyzers") {
        get {
            call.respond(HttpStatusCode.OK, analyzerRegistry.getAll().keys)
        }

        post {
            val request = call.receive<StartAnalyzerRequest>()

            if (!currencyPairRegex.matches(request.currencyPair)) {
                return@post call.respond(HttpStatusCode.BadRequest, "Invalid currency pair")
            }
            val currencyPair = CurrencyPair(request.currencyPair)

            if (analyzerRegistry.contains(currencyPair)) {
                return@post call.respond(HttpStatusCode.OK, "Analyzer for $currencyPair had already started")
            }

            val exchangesMap = request.executionMappings
                .mapKeys { (exchangeName, _) ->
                    exchangeRegistry.get(exchangeName) ?: return@post call.respond(
                        HttpStatusCode.BadRequest, "No such exchange: $exchangeName"
                    )
                }
                .mapValues { (_, walletName) ->
                    walletRegistry.get(walletName) ?: return@post call.respond(
                        HttpStatusCode.BadRequest,
                        "No such wallet: $walletName"
                    )
                }
            val analyzer = ArbitrageAnalyzer(currencyPair, commandChannel, exchangesMap)

            val job = analyzerRegistry.scope.launch {
                val flows = exchangesMap.keys.map { it.getFlow(currencyPair) }
                flows.merge().collect(analyzer::processNewTicker)
            }
            analyzerRegistry.register(currencyPair, analyzer, job)

            call.respond(HttpStatusCode.OK, "Started analyzer")
        }

        delete("/{pair}") {
            call.requirePathParameter("pair")
            val pairToDelete = call.parameters.getOrFail("pair")
            val currencyPair = CurrencyPair(pairToDelete)

            val stopped = analyzerRegistry.stop(currencyPair)
            if (!stopped) {
                return@delete call.respond(
                    HttpStatusCode.BadRequest,
                    "No such analyzer for pair $pairToDelete"
                )
            }

            call.respond(HttpStatusCode.OK, "Stopped analyzer for pair $pairToDelete")
        }

        get("/profit") {
            call.respond(HttpStatusCode.OK, ProfitTracker.getProfits())
        }
    }
}

@Serializable
private data class StartAnalyzerRequest(
    val currencyPair: String,
    val executionMappings: Map<String, String>
)
