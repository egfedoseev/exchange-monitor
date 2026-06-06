package ru.jinushi.exchange.wallet

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.math.BigDecimal

@Serializable(with = AssetSerializer::class)
@JvmInline
value class Asset(val code: String)

object AssetSerializer : KSerializer<Asset> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("Asset", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Asset) {
        encoder.encodeString(value.code)
    }

    override fun deserialize(decoder: Decoder): Asset {
        return Asset(decoder.decodeString())
    }
}

interface Wallet {
    suspend fun getBalance(asset: Asset): BigDecimal
    suspend fun executeTrade(order: TradeOrder): TradeResult
    suspend fun getBalances(): Map<Asset, BigDecimal>
}

data class TradeOrder(
    val asset: Asset,
    val quoteAsset: Asset,
    val amount: BigDecimal,
    val targetPrice: BigDecimal,
    val type: OrderType
)

enum class OrderType { BUY, SELL }

sealed interface TradeResult {
    data class Success(
        val transactionId: String,
        val actualPrice: BigDecimal,
        val actualAmount: BigDecimal
    ) : TradeResult

    data class Failed(val reason: String) : TradeResult
}

