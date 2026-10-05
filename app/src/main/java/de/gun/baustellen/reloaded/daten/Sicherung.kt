package de.gun.baustellen.reloaded.daten

import android.content.Context
import android.util.JsonReader
import android.util.JsonToken
import de.gun.baustellen.reloaded.BuildConfig
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.time.Instant
import java.util.Base64

/**
 * Sicherungsdatei im Format des alten Baustellen Pilot. Eine hier erstellte
 * Datei lässt sich dort einlesen und umgekehrt. Dateien und Fotos stehen –
 * wie beim alten „Export mit Dateien“ – base64-kodiert unter "dateien".
 */
object SicherungsFormat {

    private fun <T> kodiere(s: KSerializer<T>, wert: T): JsonElement = JsonFormat.encodeToJsonElement(s, wert)

    private val NOTIZEN = MapSerializer(String.serializer(), FlexText)

    /** Alle Bereiche außer den Dateien. */
    fun kopf(d: AppDaten, version: String, zeit: String): JsonObject = buildJsonObject {
        put("app", "Baustellen Pilot")
        put("variante", "Reloaded")
        put("version", version)
        put("exportedAt", zeit)
        put("kunden", kodiere(Kunden.serializer(), d.kunden))
        put("auftraege", kodiere(Auftraege.serializer(), d.auftraege))
        put("einstellungen", kodiere(Einstellungen.serializer(), d.einstellungen))
        put("wetter", kodiere(WetterEinstellung.serializer(), d.wetter))
        put("tagesnotizen", kodiere(NOTIZEN, d.tagesnotizen))
        put("design", kodiere(Design.serializer(), d.design))
    }

    /** Eine eingebettete Datei: Kennung, Typ, Inhalt (base64). */
    data class EingebetteteDatei(val id: String, val typ: String, val daten: String)

    class Gelesen(
        val daten: AppDaten,
        val bereiche: List<String>,
        val probleme: List<String>,
        val dateien: List<EingebetteteDatei>,
        val anzahlDateien: Int = dateien.size,
    )

    /**
     * Liest eine Sicherung. Jeder Bereich wird einzeln übernommen; ein
     * fehlerhafter Bereich reißt die anderen nicht mit. Nicht enthaltene
     * Bereiche bleiben unverändert.
     */
    fun lesen(text: String, basis: AppDaten): Gelesen {
        val obj = try {
            JsonFormat.parseToJsonElement(text.trimStart('\uFEFF')).jsonObject
        } catch (e: Exception) {
            throw IllegalArgumentException("Die Datei ist keine gültige Sicherung (kein JSON).")
        }
        return auswerten(obj, basis, 0)
    }

    /**
     * Wertet den Inhalt einer Sicherung aus. [ausgelagert] zählt Dateien, die
     * beim Einlesen bereits auf den Datenträger geschrieben wurden.
     */
    fun auswerten(obj: JsonObject, basis: AppDaten, ausgelagert: Int): Gelesen {
        val app = (obj["app"] as? JsonPrimitive)?.contentOrNull ?: ""
        if (app.isNotBlank() && !app.contains("Baustellen", true)) {
            throw IllegalArgumentException("Die Datei stammt aus „$app“, nicht aus dem Baustellen Pilot.")
        }
        var d = basis
        val bereiche = mutableListOf<String>()
        val probleme = mutableListOf<String>()

        fun <T> bereich(schluessel: String, name: String, s: KSerializer<T>, uebernehmen: (T) -> Unit) {
            val e = obj[schluessel] ?: return
            if (e is JsonPrimitive) return
            try {
                uebernehmen(JsonFormat.decodeFromJsonElement(s, e))
                bereiche += name
            } catch (ex: Exception) {
                probleme += name + ": " + (ex.message ?: ex.toString()).take(160)
            }
        }

        bereich("kunden", "Kunden", Kunden.serializer()) { d = d.copy(kunden = it) }
        bereich("auftraege", "Aufträge", Auftraege.serializer()) { d = d.copy(auftraege = it) }
        bereich("einstellungen", "Einstellungen", Einstellungen.serializer()) { d = d.copy(einstellungen = it) }
        bereich("wetter", "Wetterort", WetterEinstellung.serializer()) { d = d.copy(wetter = it) }
        bereich("tagesnotizen", "Tagesnotizen", NOTIZEN) { d = d.copy(tagesnotizen = it) }
        bereich("design", "Design", Design.serializer()) { d = d.copy(design = it) }

        val dateien = (obj["dateien"] as? JsonArray).orEmpty().mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val id = (o["id"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
            val daten = (o["daten"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
            EingebetteteDatei(id, (o["typ"] as? JsonPrimitive)?.contentOrNull ?: "", daten)
        }
        val anzahlDateien = dateien.size + ausgelagert
        if (anzahlDateien > 0) bereiche += "$anzahlDateien Dateien/Fotos"

        if (bereiche.isEmpty()) {
            throw IllegalArgumentException(
                if (probleme.isEmpty()) "Die Datei scheint keine Sicherung des Baustellen Pilot zu sein."
                else "Keiner der Bereiche ließ sich lesen:\n" + probleme.joinToString("\n")
            )
        }
        return Gelesen(d, bereiche, probleme, dateien, anzahlDateien)
    }

    /** Inhalt einer eingebetteten Datei (auch mit vorangestelltem data:-Kopf). */
    fun bytes(daten: String): ByteArray = Base64.getMimeDecoder().decode(daten.substringAfter("base64,", daten))
}

object Sicherung {

    /**
     * Schreibt die Sicherung direkt in den Ausgabestrom. Fotos werden einzeln
     * kodiert, damit auch große Bestände nicht den Arbeitsspeicher sprengen.
     */
    fun exportieren(ctx: Context, d: AppDaten, mitDateien: Boolean, aus: OutputStream) {
        val kopf = SicherungsFormat.kopf(d, BuildConfig.VERSION_NAME, Instant.now().toString())
        val w = aus.bufferedWriter(Charsets.UTF_8)
        w.write(JsonFormat.encodeToString(JsonObject.serializer(), kopf).trimEnd().removeSuffix("}"))
        w.write(",\"dateien\":[")
        if (mitDateien) {
            var erste = true
            d.auftraege.eintraege.forEach { a ->
                val liste = a.dateien.map { it.id to it.typ } + a.fotos.map { it.id to "image/jpeg" }
                liste.forEach { (id, typ) ->
                    val f = Dateien.datei(ctx, id)
                    if (!f.exists()) return@forEach
                    if (!erste) w.write(",")
                    erste = false
                    w.write("{\"id\":" + JsonPrimitive(id) + ",\"typ\":" + JsonPrimitive(typ.ifBlank { "application/octet-stream" }) + ",\"daten\":\"")
                    w.write(Base64.getEncoder().encodeToString(f.readBytes()))
                    w.write("\"}")
                }
            }
        }
        w.write("]}")
        w.flush()
    }

    /** Übernimmt eine gelesene Sicherung: Dateien ablegen, Bestand ersetzen. */
    fun anwenden(ctx: Context, g: SicherungsFormat.Gelesen): Int {
        var n = 0
        g.dateien.forEach { e ->
            try {
                Dateien.datei(ctx, e.id).writeBytes(SicherungsFormat.bytes(e.daten))
                n++
            } catch (ex: Exception) { }
        }
        val ziel = Dateien.datei(ctx, "x").parentFile
        zwischenOrdner(ctx).listFiles()?.forEach { f ->
            val z = File(ziel, f.name)
            if (f.renameTo(z) || try { f.copyTo(z, overwrite = true); true } catch (e: Exception) { false }) n++
        }
        zwischenOrdner(ctx).deleteRecursively()
        Speicher.ersetzen(g.daten)
        return n
    }

    /** Zwischenablage für Dateien aus einer Sicherung, bis der Import bestätigt ist. */
    private fun zwischenOrdner(ctx: Context) = File(ctx.cacheDir, "import")

    /** Import abgebrochen: zwischengelagerte Dateien verwerfen. */
    fun verwerfen(ctx: Context) {
        zwischenOrdner(ctx).deleteRecursively()
    }

    /**
     * Liest eine Sicherung als Datenstrom. Eingebettete Dateien und Fotos
     * (alter „Export mit Dateien“) werden einzeln dekodiert und sofort in
     * einen Zwischenordner geschrieben – so passen auch Sicherungen mit
     * hunderten Fotos in den Arbeitsspeicher.
     */
    fun lesen(ctx: Context, ein: InputStream, basis: AppDaten): SicherungsFormat.Gelesen {
        val ordner = zwischenOrdner(ctx)
        ordner.deleteRecursively()
        ordner.mkdirs()
        val gepuffert = BufferedInputStream(ein)
        // Byte-Order-Mark (UTF-8) überspringen
        gepuffert.mark(3)
        val bom = ByteArray(3)
        val gelesen = gepuffert.read(bom)
        if (!(gelesen == 3 && bom[0] == 0xEF.toByte() && bom[1] == 0xBB.toByte() && bom[2] == 0xBF.toByte())) gepuffert.reset()
        val r = JsonReader(InputStreamReader(gepuffert, Charsets.UTF_8))
        r.isLenient = true
        val felder = LinkedHashMap<String, JsonElement>()
        var ausgelagert = 0
        try {
            if (r.peek() != JsonToken.BEGIN_OBJECT) throw IllegalStateException("kein Objekt")
            r.beginObject()
            while (r.hasNext()) {
                val name = r.nextName()
                if (name == "dateien" && r.peek() == JsonToken.BEGIN_ARRAY) {
                    r.beginArray()
                    while (r.hasNext()) {
                        if (r.peek() != JsonToken.BEGIN_OBJECT) { r.skipValue(); continue }
                        var id: String? = null
                        var daten: String? = null
                        r.beginObject()
                        while (r.hasNext()) {
                            when (r.nextName()) {
                                "id" -> id = text(r)
                                "daten" -> daten = text(r)
                                else -> r.skipValue()
                            }
                        }
                        r.endObject()
                        val i = id
                        val b = daten
                        if (i != null && b != null) {
                            try {
                                File(ordner, Dateien.datei(ctx, i).name).writeBytes(SicherungsFormat.bytes(b))
                                ausgelagert++
                            } catch (e: Exception) { }
                        }
                    }
                    r.endArray()
                } else felder[name] = element(r)
            }
            r.endObject()
        } catch (e: IllegalStateException) {
            throw IllegalArgumentException("Die Datei ist keine gültige Sicherung (kein JSON).")
        } catch (e: java.io.IOException) {
            throw IllegalArgumentException("Die Datei ist beschädigt oder unvollständig: " + (e.message ?: ""))
        }
        return SicherungsFormat.auswerten(JsonObject(felder), basis, ausgelagert)
    }

    private fun text(r: JsonReader): String? =
        if (r.peek() == JsonToken.NULL) { r.nextNull(); null } else r.nextString()

    /** Einen JSON-Wert vollständig lesen (für alle Bereiche außer den Dateien). */
    private fun element(r: JsonReader): JsonElement = when (r.peek()) {
        JsonToken.BEGIN_OBJECT -> {
            val m = LinkedHashMap<String, JsonElement>()
            r.beginObject()
            while (r.hasNext()) m[r.nextName()] = element(r)
            r.endObject()
            JsonObject(m)
        }
        JsonToken.BEGIN_ARRAY -> {
            val l = mutableListOf<JsonElement>()
            r.beginArray()
            while (r.hasNext()) l += element(r)
            r.endArray()
            JsonArray(l)
        }
        JsonToken.STRING -> JsonPrimitive(r.nextString())
        JsonToken.NUMBER -> r.nextString().let { t -> t.toLongOrNull()?.let { JsonPrimitive(it) } ?: JsonPrimitive(t.toDoubleOrNull() ?: 0.0) }
        JsonToken.BOOLEAN -> JsonPrimitive(r.nextBoolean())
        JsonToken.NULL -> { r.nextNull(); JsonNull }
        else -> { r.skipValue(); JsonNull }
    }
}
