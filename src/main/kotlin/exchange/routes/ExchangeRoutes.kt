package ru.jinushi.exchange.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import ru.jinushi.exchange.Exchange
import java.util.concurrent.ConcurrentHashMap

private val mutableExchanges = ConcurrentHashMap<String, Exchange>()
val exchanges: Map<String, Exchange> = mutableExchanges

fun Route.exchangesRoutes() {
    route("/exchanges") {
        get {
            call.respond(HttpStatusCode.OK, exchanges.keys)
        }
    }
}