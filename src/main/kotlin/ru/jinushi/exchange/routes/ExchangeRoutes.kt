package ru.jinushi.exchange.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import ru.jinushi.exchange.registry.ExchangeRegistry

fun Route.exchangesRoutes(exchangeRegistry: ExchangeRegistry) {
    route("/exchanges") {
        get {
            call.respond(HttpStatusCode.OK, exchangeRegistry.getAll().keys)
        }
    }
}