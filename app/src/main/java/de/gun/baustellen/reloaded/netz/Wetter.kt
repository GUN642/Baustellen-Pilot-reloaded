package de.gun.baustellen.reloaded.netz

import de.gun.baustellen.reloaded.daten.WetterOrt
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

data class WetterStunde(
    val zeit: LocalDateTime, val grad: Double?, val code: Int?,
    val regenWkt: Int?, val regenMm: Double?, val wind: Double?,
)

data class WetterTag(
    val datum: LocalDate, val code: Int?, val max: Double?, val min: Double?,
    val regenWkt: Int?, val regenMm: Double?, val aufgang: LocalDateTime?, val untergang: LocalDateTime?, val boeen: Double?,
)

data class Wetter(
    val ort: String,
    val grad: Double?, val gefuehlt: Double?, val code: Int?, val wind: Double?, val feuchte: Double?,
    val stunden: List<WetterStunde>, val tage: List<WetterTag>,
    val modelle: String,
    val abgerufen: Long,
)

/** Wetter der Arbeitszeit eines Tages, verdichtet aus der Stundenvorhersage. */
data class TagesWetter(val code: Int?, val min: Int?, val max: Int?, val regen: String)

/** Ein fester Zeitpunkt des Arbeitstags (7, 12, 16 Uhr). */
data class ArbeitsStunde(val stunde: Int, val grad: Int?, val code: Int?, val regenWkt: Int?)

/**
 * Wetter über Open-Meteo, ohne Konto. Für die ersten beiden Tage ICON-D2
 * des DWD (feinste Auflösung), für die restliche Woche ECMWF – beide
 * stundenweise zusammengeführt.
 */
object WetterDienst {
    private var zwischen: Wetter? = null

    private fun zeit(s: String?): LocalDateTime? = try { s?.let { LocalDateTime.parse(it) } } catch (e: Exception) { null }
    private fun tag(s: String?): LocalDate? = try { s?.let { LocalDate.parse(it.take(10)) } } catch (e: Exception) { null }

    private fun liste(o: JsonObject?, k: String): JsonArray = o?.get(k).arr() ?: JsonArray(emptyList())

    private fun stundenAus(j: JsonObject?): List<WetterStunde> {
        val h = j?.get("hourly").obj()
        val t = liste(h, "time")
        return t.indices.mapNotNull { i ->
            WetterStunde(
                zeit(t[i].s()) ?: return@mapNotNull null,
                liste(h, "temperature_2m").getOrNull(i).d(), liste(h, "weather_code").getOrNull(i).i(),
                liste(h, "precipitation_probability").getOrNull(i).i(), liste(h, "precipitation").getOrNull(i).d(),
                liste(h, "wind_speed_10m").getOrNull(i).d(),
            )
        }
    }

    private fun tageAus(j: JsonObject?): List<WetterTag> {
        val d = j?.get("daily").obj()
        val t = liste(d, "time")
        return t.indices.mapNotNull { i ->
            WetterTag(
                tag(t[i].s()) ?: return@mapNotNull null,
                liste(d, "weather_code").getOrNull(i).i(),
                liste(d, "temperature_2m_max").getOrNull(i).d(), liste(d, "temperature_2m_min").getOrNull(i).d(),
                liste(d, "precipitation_probability_max").getOrNull(i).i(), liste(d, "precipitation_sum").getOrNull(i).d(),
                zeit(liste(d, "sunrise").getOrNull(i).s()), zeit(liste(d, "sunset").getOrNull(i).s()),
                liste(d, "wind_gusts_10m_max").getOrNull(i).d(),
            )
        }
    }

    /** Nahe Vorhersage zuerst, die ferne nur jenseits ihres letzten Zeitpunkts. */
    fun stundenVereinen(nah: List<WetterStunde>, weit: List<WetterStunde>): List<WetterStunde> {
        val letzte = nah.lastOrNull()?.zeit ?: return weit
        return nah + weit.filter { it.zeit.isAfter(letzte) }
    }

    fun tageVereinen(nah: List<WetterTag>, weit: List<WetterTag>): List<WetterTag> {
        val letzte = nah.lastOrNull()?.datum ?: return weit
        // Tage, für die ICON-D2 nur einen Teil des Tages liefert, aus ECMWF ergänzen
        return nah.map { t -> if (t.max == null) weit.firstOrNull { it.datum == t.datum } ?: t else t } +
            weit.filter { it.datum.isAfter(letzte) }
    }

    suspend fun laden(ort: WetterOrt, neu: Boolean): Wetter {
        val z = zwischen
        if (!neu && z != null && z.ort == ort.name && System.currentTimeMillis() - z.abgerufen < 15 * 60_000) return z
        val grund = "https://api.open-meteo.com/v1/forecast?latitude=${ort.breite}&longitude=${ort.laenge}" +
            "&current=temperature_2m,apparent_temperature,weather_code,wind_speed_10m,relative_humidity_2m&timezone=auto"
        val tagesFelder = "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_sum,sunrise,sunset,wind_gusts_10m_max"
        val urlNah = grund + "&hourly=temperature_2m,weather_code,precipitation_probability,precipitation,wind_speed_10m" +
            tagesFelder + ",precipitation_probability_max&models=icon_d2&forecast_days=2"
        val urlWeit = grund + "&hourly=temperature_2m,weather_code,precipitation,wind_speed_10m" +
            tagesFelder + "&models=ecmwf_ifs025&forecast_days=7"
        val (nah, weit) = coroutineScope {
            val a = async { try { Netz.json(urlNah).obj() } catch (e: Exception) { null } }
            val b = async { try { Netz.json(urlWeit).obj() } catch (e: Exception) { null } }
            a.await() to b.await()
        }
        val w: Wetter = if (nah == null && weit == null) {
            // Ersatz: automatische Modellwahl
            val j = Netz.json(grund + "&hourly=temperature_2m,weather_code,precipitation_probability,precipitation,wind_speed_10m" +
                tagesFelder + ",precipitation_probability_max&forecast_days=7").obj()
            bauen(ort, j, stundenAus(j), tageAus(j), "automatisch")
        } else {
            val modelle = listOfNotNull(nah?.let { "ICON-D2" }, weit?.let { "ECMWF" }).joinToString(" + ")
            bauen(ort, nah ?: weit, stundenVereinen(stundenAus(nah), stundenAus(weit)), tageVereinen(tageAus(nah), tageAus(weit)), modelle)
        }
        zwischen = w
        return w
    }

    private fun bauen(ort: WetterOrt, j: JsonObject?, stunden: List<WetterStunde>, tage: List<WetterTag>, modelle: String): Wetter {
        val cur = j?.get("current").obj()
        return Wetter(
            ort.name, cur?.get("temperature_2m").d(), cur?.get("apparent_temperature").d(), cur?.get("weather_code").i(),
            cur?.get("wind_speed_10m").d(), cur?.get("relative_humidity_2m").d(), stunden, tage, modelle, System.currentTimeMillis(),
        )
    }

    suspend fun ortSuchen(begriff: String): List<Pair<String, WetterOrt>> {
        val j = Netz.json("https://geocoding-api.open-meteo.com/v1/search?name=" + Netz.enc(begriff) + "&count=8&language=de&format=json")
        return (j.obj()?.get("results").arr() ?: return emptyList()).mapNotNull { e ->
            val o = e.obj() ?: return@mapNotNull null
            val name = o["name"].s() ?: return@mapNotNull null
            val zusatz = listOfNotNull(o["admin1"].s(), o["country"].s()).joinToString(", ")
            (name + if (zusatz.isNotBlank()) " · $zusatz" else "") to
                WetterOrt(name, o["latitude"].d() ?: return@mapNotNull null, o["longitude"].d() ?: return@mapNotNull null)
        }
    }

    suspend fun ortsname(breite: Double, laenge: Double): String? = try {
        val j = Netz.json("https://api.bigdatacloud.net/data/reverse-geocode-client?latitude=$breite&longitude=$laenge&localityLanguage=de").obj()
        j?.get("city").s()?.takeIf { it.isNotBlank() } ?: j?.get("locality").s()?.takeIf { it.isNotBlank() }
            ?: j?.get("principalSubdivision").s()
    } catch (e: Exception) {
        null
    }

    private val CODES = mapOf(
        0 to ("☀️" to "klar"), 1 to ("🌤️" to "überwiegend klar"), 2 to ("⛅" to "teils bewölkt"), 3 to ("☁️" to "bedeckt"),
        45 to ("🌫️" to "Nebel"), 48 to ("🌫️" to "Reifnebel"),
        51 to ("🌦️" to "leichter Sprühregen"), 53 to ("🌦️" to "Sprühregen"), 55 to ("🌦️" to "starker Sprühregen"),
        56 to ("🌧️" to "gefrierender Sprühregen"), 57 to ("🌧️" to "gefrierender Sprühregen"),
        61 to ("🌦️" to "leichter Regen"), 63 to ("🌧️" to "Regen"), 65 to ("🌧️" to "starker Regen"),
        66 to ("🌧️" to "gefrierender Regen"), 67 to ("🌧️" to "gefrierender Regen"),
        71 to ("🌨️" to "leichter Schnee"), 73 to ("🌨️" to "Schnee"), 75 to ("❄️" to "starker Schnee"), 77 to ("🌨️" to "Schneegriesel"),
        80 to ("🌦️" to "leichte Schauer"), 81 to ("🌧️" to "Schauer"), 82 to ("⛈️" to "starke Schauer"),
        85 to ("🌨️" to "Schneeschauer"), 86 to ("🌨️" to "starke Schneeschauer"),
        95 to ("⛈️" to "Gewitter"), 96 to ("⛈️" to "Gewitter mit Hagel"), 99 to ("⛈️" to "schweres Gewitter"),
    )

    fun zeichen(code: Int?) = CODES[code]?.first ?: "·"
    fun text(code: Int?) = CODES[code]?.second ?: "unbekannt"
}

/** Werte der Arbeitszeit (vonH bis bisH Uhr) eines Tages. */
fun fensterWerte(stunden: List<WetterStunde>, tag: LocalDate, vonH: Int = 7, bisH: Int = 16): TagesWetter? {
    val im = stunden.filter { it.zeit.toLocalDate() == tag && it.zeit.hour in vonH..bisH }
    if (im.isEmpty()) return null
    val temps = im.mapNotNull { it.grad }
    val regenMax = im.mapNotNull { it.regenWkt }.maxOrNull() ?: 0
    val regenSumme = im.mapNotNull { it.regenMm }.sum()
    val code = im.filter { it.code != null }.minByOrNull { Math.abs(it.zeit.hour - 12) }?.code
    val regen = when {
        regenMax > 0 -> "$regenMax%"
        regenSumme > 0.1 -> String.format(java.util.Locale.GERMANY, "%.1f mm", regenSumme)
        else -> ""
    }
    return TagesWetter(code, temps.minOrNull()?.let { Math.round(it).toInt() }, temps.maxOrNull()?.let { Math.round(it).toInt() }, regen)
}

/** Arbeitsbeginn, Mittag, kurz vor Feierabend – jeweils der nächstgelegene Stundenwert (höchstens 90 Min. daneben). */
fun arbeitsStunden(stunden: List<WetterStunde>, tag: LocalDate, ziele: List<Int> = listOf(7, 12, 16)): List<ArbeitsStunde> =
    ziele.mapNotNull { h ->
        val ziel = tag.atTime(h, 0)
        val beste = stunden.minByOrNull { Math.abs(Duration.between(ziel, it.zeit).toMinutes()) } ?: return@mapNotNull null
        if (Math.abs(Duration.between(ziel, beste.zeit).toMinutes()) > 90) return@mapNotNull null
        ArbeitsStunde(h, beste.grad?.let { Math.round(it).toInt() }, beste.code, beste.regenWkt)
    }

/** Hinweise, die auf der Baustelle zählen (nächste drei Tage). */
fun baustellenWarnungen(tage: List<WetterTag>): List<String> {
    val drei = tage.take(3)
    val w = mutableListOf<String>()
    if (drei.any { (it.min ?: 99.0) <= 0.0 }) w += "Frost in den nächsten Tagen — Erdarbeiten und Beton prüfen."
    if (drei.any { (it.regenMm ?: 0.0) >= 10.0 }) w += "Ergiebiger Regen erwartet — Baugrube und Zufahrt sichern."
    if (drei.any { (it.boeen ?: 0.0) >= 60.0 }) w += "Sturmböen über 60 km/h — Hebearbeiten einplanen."
    return w
}
