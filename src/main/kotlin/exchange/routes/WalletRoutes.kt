@file:UseSerializers(BigDecimalSerializer::class)

package ru.jinushi.exchange.routes

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import ru.jinushi.exchange.serializers.BigDecimalSerializer
import ru.jinushi.exchange.virtual.VirtualWallet
import ru.jinushi.exchange.wallet.Asset
import ru.jinushi.exchange.wallet.Wallet
import java.math.BigDecimal
import java.util.concurrent.ConcurrentHashMap

private val mutableWallets = ConcurrentHashMap<String, Wallet>()
val wallets: Map<String, Wallet> = mutableWallets

fun Route.walletRoutes() {
    route("/wallets") {
        get {
            call.respond(wallets.mapValues { (_, wallet) -> wallet.getBalances() })
        }

        post {
            val dto = call.receive<CreateWalletDto>()

            if (!dto.isVirtual) {
                return@post call.respond(HttpStatusCode.NotImplemented, "Non-virtual wallets are not implemented yet")
            }

            val initialBalances = dto.initialBalances.mapKeys { (key, _) -> Asset(key) }
                .mapValues { (_, value) -> BigDecimal(value) }
            val wallet = VirtualWallet(initialBalances)
            mutableWallets[dto.walletName] = wallet
            call.respond(HttpStatusCode.fromValue(201), "Wallet registered")
        }
    }
}

@Serializable
private data class CreateWalletDto(
    val walletName: String,
    val isVirtual: Boolean,
    val initialBalances: Map<String, String> = emptyMap()
)