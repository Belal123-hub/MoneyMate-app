package com.example.data.network.wallet.model

import com.example.domain.wallet.model.TotalBalance
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

@Serializable
data class TotalBalanceResponse(
    val total_balance: Double,
    val currency: String,
    @Serializable(with = BreakdownSerializer::class)
    val breakdown: List<BalanceBreakdown> = emptyList()
)

@Serializable
data class BalanceBreakdown(
    val wallet_id: Int,
    val wallet_name: String,
    val wallet_type: String,
    val original_balance: Double,
    val original_currency: String,
    val converted_balance: Double,
    val converted_currency: String,
    val exchange_rate_used: Double
)

// Custom serializer to handle both array and single object for breakdown
object BreakdownSerializer : KSerializer<List<BalanceBreakdown>> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("BreakdownSerializer")

    override fun deserialize(decoder: Decoder): List<BalanceBreakdown> {
        val jsonDecoder = decoder as? JsonDecoder
            ?: throw SerializationException("Expected JsonDecoder")
        
        val element = jsonDecoder.decodeJsonElement()
        
        return when (element) {
            is JsonArray -> {
                // If it's an array, deserialize each item
                element.map { jsonElement ->
                    jsonElement.jsonObject.let { jsonObject ->
                        BalanceBreakdown(
                            wallet_id = (jsonObject["wallet_id"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 0,
                            wallet_name = (jsonObject["wallet_name"] as? JsonPrimitive)?.content ?: "",
                            wallet_type = (jsonObject["wallet_type"] as? JsonPrimitive)?.content ?: "",
                            original_balance = (jsonObject["original_balance"] as? JsonPrimitive)?.content?.toDoubleOrNull() ?: 0.0,
                            original_currency = (jsonObject["original_currency"] as? JsonPrimitive)?.content ?: "",
                            converted_balance = (jsonObject["converted_balance"] as? JsonPrimitive)?.content?.toDoubleOrNull() ?: 0.0,
                            converted_currency = (jsonObject["converted_currency"] as? JsonPrimitive)?.content ?: "",
                            exchange_rate_used = (jsonObject["exchange_rate_used"] as? JsonPrimitive)?.content?.toDoubleOrNull() ?: 0.0
                        )
                    }
                }
            }
            is JsonObject -> {
                // If it's a single object, wrap it in a list
                listOf(
                    BalanceBreakdown(
                        wallet_id = (element["wallet_id"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 0,
                        wallet_name = (element["wallet_name"] as? JsonPrimitive)?.content ?: "",
                        wallet_type = (element["wallet_type"] as? JsonPrimitive)?.content ?: "",
                        original_balance = (element["original_balance"] as? JsonPrimitive)?.content?.toDoubleOrNull() ?: 0.0,
                        original_currency = (element["original_currency"] as? JsonPrimitive)?.content ?: "",
                        converted_balance = (element["converted_balance"] as? JsonPrimitive)?.content?.toDoubleOrNull() ?: 0.0,
                        converted_currency = (element["converted_currency"] as? JsonPrimitive)?.content ?: "",
                        exchange_rate_used = (element["exchange_rate_used"] as? JsonPrimitive)?.content?.toDoubleOrNull() ?: 0.0
                    )
                )
            }
            else -> {
                // If it's neither array nor object, return empty list
                emptyList()
            }
        }
    }

    override fun serialize(encoder: Encoder, value: List<BalanceBreakdown>) {
        // For serialization, always serialize as array
        encoder.encodeSerializableValue(JsonArray.serializer(), JsonArray(value.map { breakdown ->
            JsonObject(mapOf(
                "wallet_id" to JsonPrimitive(breakdown.wallet_id),
                "wallet_name" to JsonPrimitive(breakdown.wallet_name),
                "wallet_type" to JsonPrimitive(breakdown.wallet_type),
                "original_balance" to JsonPrimitive(breakdown.original_balance),
                "original_currency" to JsonPrimitive(breakdown.original_currency),
                "converted_balance" to JsonPrimitive(breakdown.converted_balance),
                "converted_currency" to JsonPrimitive(breakdown.converted_currency),
                "exchange_rate_used" to JsonPrimitive(breakdown.exchange_rate_used)
            ))
        }))
    }
}

fun TotalBalanceResponse.toDomain(): TotalBalance {
    return TotalBalance(
        totalBalance = total_balance,
        currency = currency,
        breakdown = breakdown.map { it.toDomain() }
    )
}

fun BalanceBreakdown.toDomain(): com.example.domain.wallet.model.BalanceBreakdown {
    return com.example.domain.wallet.model.BalanceBreakdown(
        walletId = wallet_id,
        walletName = wallet_name,
        walletType = wallet_type,
        originalBalance = original_balance,
        originalCurrency = original_currency,
        convertedBalance = converted_balance,
        convertedCurrency = converted_currency,
        exchangeRateUsed = exchange_rate_used
    )
}
