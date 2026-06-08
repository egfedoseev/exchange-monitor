package ru.jinushi.exchange.serializers

import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import java.math.BigDecimal

val jsonConfig = Json {
    ignoreUnknownKeys = true
    serializersModule = SerializersModule {
        contextual(BigDecimal::class, BigDecimalSerializer)
    }
}
