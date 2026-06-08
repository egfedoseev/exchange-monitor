@file:UseSerializers(BigDecimalSerializer::class)

package ru.jinushi.exchange.routes

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import ru.jinushi.exchange.config.ConfigManager
import ru.jinushi.exchange.registry.WalletRegistry
import ru.jinushi.exchange.serializers.BigDecimalSerializer
import ru.jinushi.exchange.simulation.VirtualWallet
import ru.jinushi.exchange.wallet.Asset
import java.math.BigDecimal

fun Route.walletRoutes(walletRegistry: WalletRegistry, configManager: ConfigManager) {
    route("/wallets") {
        get {
            call.respond(walletRegistry.getAll().mapValues { (_, wallet) -> wallet.getBalances() })
        }

        post {
            val dto = call.receive<CreateWalletDto>()

            if (!dto.isVirtual) {
                return@post call.respond(HttpStatusCode.NotImplemented, "Non-virtual wallets are not implemented yet")
            }

            val initialBalances = dto.initialBalances.mapKeys { (key, _) -> Asset(key) }
                .mapValues { (_, value) -> BigDecimal(value) }
            val minimalLimits = dto.minimalLimits.mapKeys { (key, _) -> Asset(key) }
                .mapValues { (_, value) -> BigDecimal(value) }
            val transferFees = dto.transferFees.mapKeys { (key, _) -> Asset(key) }
                .mapValues { (_, value) -> BigDecimal(value) }

            val wallet = VirtualWallet(
                initialBalances = initialBalances,
                tradeFeeRate = BigDecimal(dto.feeRate),
                blockchain = dto.blockchain,
                id = dto.walletName,
                minimalLimits = minimalLimits,
                transferFees = transferFees
            )
            walletRegistry.register(dto.walletName, wallet)
            configManager.saveCurrentState()
            call.respond(HttpStatusCode.fromValue(201), "Wallet registered")
        }
    }
}

@Serializable
private data class CreateWalletDto(
    val walletName: String,
    val isVirtual: Boolean,
    val initialBalances: Map<String, String> = emptyMap(),
    val feeRate: String = "0.001",
    val blockchain: String = "Simulated",
    val minimalLimits: Map<String, String> = emptyMap(),
    val transferFees: Map<String, String> = emptyMap()
)