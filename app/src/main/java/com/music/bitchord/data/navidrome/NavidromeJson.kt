package com.music.bitchord.data.navidrome

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive

internal object NavidromeJson {
    val parser = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }
}

internal object FlexibleBooleanSerializer : KSerializer<Boolean> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("OpenSubsonicBoolean", PrimitiveKind.BOOLEAN)

    override fun deserialize(decoder: Decoder): Boolean {
        if (decoder !is JsonDecoder) return decoder.decodeBoolean()
        val value = decoder.decodeJsonElement().jsonPrimitive
        return value.booleanOrNull
            ?: value.content.toBooleanStrictOrNull()
            ?: throw SerializationException("Expected an OpenSubsonic boolean")
    }

    override fun serialize(encoder: Encoder, value: Boolean) = encoder.encodeBoolean(value)
}

/** OpenSubsonic encodes some singular-or-plural fields as either an object or an array. */
internal fun flexibleJsonArray(decoder: Decoder): JsonArray {
    val element: JsonElement = (decoder as? JsonDecoder)?.decodeJsonElement()
        ?: throw SerializationException("Expected JSON object or array")
    return when (element) {
        is JsonArray -> element
        is JsonObject -> JsonArray(listOf(element))
        else -> throw SerializationException("Expected JSON object or array")
    }
}

