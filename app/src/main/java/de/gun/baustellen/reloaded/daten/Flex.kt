package de.gun.baustellen.reloaded.daten

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.nullable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

/*
 * Die alte App hat ihre Daten aus Formularfeldern übernommen. Je nach Fassung
 * steht dieselbe Angabe mal als Zahl, mal als Text in der Sicherung
 * ("dauerWert": "2" oder 2, "stufe": "1" oder 1). Diese Serializer lesen
 * beides, statt beim Import an einer Kleinigkeit zu scheitern.
 */

private fun JsonElement.textOderNull(): String? = when (this) {
    is JsonNull -> null
    is JsonPrimitive -> this.content
    else -> this.toString()
}

/** Text, der auch aus Zahl, Wahrheitswert oder null gelesen wird (null -> ""). */
object FlexText : KSerializer<String> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("FlexText", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: String) = encoder.encodeString(value)
    override fun deserialize(decoder: Decoder): String {
        val json = decoder as? JsonDecoder ?: return decoder.decodeString()
        return json.decodeJsonElement().textOderNull() ?: ""
    }
}

/** Kommazahl, die auch aus Text ("2,5" oder "2.5") gelesen wird; sonst 0. */
object FlexZahl : KSerializer<Double> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("FlexZahl", PrimitiveKind.DOUBLE)
    override fun serialize(encoder: Encoder, value: Double) = encoder.encodeDouble(value)
    override fun deserialize(decoder: Decoder): Double {
        val json = decoder as? JsonDecoder ?: return decoder.decodeDouble()
        val e = json.decodeJsonElement()
        if (e !is JsonPrimitive) return 0.0
        return e.doubleOrNull ?: e.content.replace(",", ".").trim().toDoubleOrNull() ?: 0.0
    }
}

/** Wie [FlexZahl], aber mit null für "nicht angegeben". */
object FlexZahlOderNull : KSerializer<Double?> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("FlexZahlOderNull", PrimitiveKind.DOUBLE).nullable
    override fun serialize(encoder: Encoder, value: Double?) {
        if (value == null) encoder.encodeNull() else encoder.encodeDouble(value)
    }
    override fun deserialize(decoder: Decoder): Double? {
        val json = decoder as? JsonDecoder ?: return decoder.decodeDouble()
        val e = json.decodeJsonElement()
        if (e !is JsonPrimitive || e is JsonNull) return null
        return e.doubleOrNull ?: e.content.replace(",", ".").trim().toDoubleOrNull()
    }
}

/** Ganzzahl oder null, auch aus Text gelesen. */
object FlexGanzOderNull : KSerializer<Int?> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("FlexGanzOderNull", PrimitiveKind.INT).nullable
    override fun serialize(encoder: Encoder, value: Int?) {
        if (value == null) encoder.encodeNull() else encoder.encodeInt(value)
    }
    override fun deserialize(decoder: Decoder): Int? {
        val json = decoder as? JsonDecoder ?: return decoder.decodeInt()
        val e = json.decodeJsonElement()
        if (e !is JsonPrimitive || e is JsonNull) return null
        return e.intOrNull ?: e.doubleOrNull?.toInt() ?: e.content.trim().toDoubleOrNull()?.toInt()
    }
}

/** Wahrheitswert, auch aus "true"/"1" gelesen; null -> false. */
object FlexBool : KSerializer<Boolean> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("FlexBool", PrimitiveKind.BOOLEAN)
    override fun serialize(encoder: Encoder, value: Boolean) = encoder.encodeBoolean(value)
    override fun deserialize(decoder: Decoder): Boolean {
        val json = decoder as? JsonDecoder ?: return decoder.decodeBoolean()
        val e = json.decodeJsonElement()
        if (e !is JsonPrimitive || e is JsonNull) return false
        return e.booleanOrNull ?: (e.content == "1" || e.content.equals("true", true))
    }
}

/** Gemeinsames JSON-Format: tolerant beim Lesen, vollständig beim Schreiben. */
val JsonFormat = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    isLenient = true
    encodeDefaults = true
    explicitNulls = false
}

/** Neue Kennung (ohne Android-Abhängigkeit, auch in der Logik nutzbar). */
fun neueIdLogik(): String = java.util.UUID.randomUUID().toString()
