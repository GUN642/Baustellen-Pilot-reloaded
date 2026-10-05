package de.gun.baustellen.reloaded.netz

import de.gun.baustellen.reloaded.BuildConfig
import de.gun.baustellen.reloaded.daten.JsonFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object Netz {
    suspend fun text(url: String, kopf: Map<String, String> = emptyMap()): String = withContext(Dispatchers.IO) {
        var ziel = url
        var umleitungen = 0
        while (true) {
            val c = URL(ziel).openConnection() as HttpURLConnection
            c.connectTimeout = 15000
            c.readTimeout = 20000
            c.instanceFollowRedirects = true
            c.setRequestProperty("User-Agent", "BaustellenPilotReloaded/" + BuildConfig.VERSION_NAME)
            kopf.forEach { (k, v) -> c.setRequestProperty(k, v) }
            val code = c.responseCode
            if (code in 300..399 && umleitungen < 5) {
                val neu = c.getHeaderField("Location") ?: break
                ziel = URL(URL(ziel), neu).toString()
                umleitungen++
                c.disconnect()
                continue
            }
            if (code !in 200..299) {
                val fehler = c.errorStream?.bufferedReader()?.use { it.readText() }?.take(120) ?: ""
                c.disconnect()
                throw IllegalStateException("HTTP $code $fehler".trim())
            }
            return@withContext c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        }
        throw IllegalStateException("Zu viele Weiterleitungen.")
    }

    suspend fun json(url: String, kopf: Map<String, String> = emptyMap()): JsonElement =
        JsonFormat.parseToJsonElement(text(url, kopf))

    fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
}

fun JsonElement?.obj(): JsonObject? = this as? JsonObject
fun JsonElement?.arr(): JsonArray? = this as? JsonArray
fun JsonElement?.d(): Double? = (this as? JsonPrimitive)?.doubleOrNull
fun JsonElement?.s(): String? = (this as? JsonPrimitive)?.contentOrNull
fun JsonElement?.i(): Int? = (this as? JsonPrimitive)?.let { it.intOrNull ?: it.doubleOrNull?.toInt() }

// ---------------- Update-Prüfung (GitHub-Releases) ----------------

data class UpdateInfo(val version: String, val groesse: Long, val notizen: String, val seite: String, val apkUrl: String = "")

object UpdateDienst {
    /** > 0, wenn a neuer ist als b. */
    fun vergleichen(a: String, b: String): Int {
        val x = Regex("\\d+").findAll(a).map { it.value.toInt() }.toList()
        val y = Regex("\\d+").findAll(b).map { it.value.toInt() }.toList()
        for (i in 0 until maxOf(x.size, y.size)) {
            val p = x.getOrElse(i) { 0 }
            val q = y.getOrElse(i) { 0 }
            if (p != q) return p - q
        }
        return 0
    }

    /** Liefert ein Update, falls eine neuere Version veröffentlicht ist; sonst null. */
    suspend fun pruefen(repo: String): UpdateInfo? {
        val j = Netz.json("https://api.github.com/repos/$repo/releases/latest", mapOf("Accept" to "application/vnd.github+json")).jsonObject
        val version = (j["tag_name"].s() ?: "").removePrefix("v").removePrefix("V")
        val apk = j["assets"].arr()?.mapNotNull { it.obj() }?.firstOrNull { (it["name"].s() ?: "").endsWith(".apk", true) }
            ?: throw IllegalStateException("Die neueste Veröffentlichung enthält keine APK-Datei.")
        if (vergleichen(version, BuildConfig.VERSION_NAME) <= 0) return null
        return UpdateInfo(
            version, (apk["size"] as? JsonPrimitive)?.longOrNull ?: 0L,
            (j["body"].s() ?: "").lines()
                .filterNot { it.startsWith("Co-Authored-By", true) || it.startsWith("Claude-Session") }
                .joinToString("\n").trim().take(800),
            "https://github.com/$repo/releases",
            apk["browser_download_url"].s() ?: "",
        )
    }
}
