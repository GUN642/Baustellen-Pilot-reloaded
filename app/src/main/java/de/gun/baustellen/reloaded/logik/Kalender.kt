package de.gun.baustellen.reloaded.logik

import de.gun.baustellen.reloaded.daten.AppDaten
import de.gun.baustellen.reloaded.geraet.GeraetKalender
import de.gun.baustellen.reloaded.geraet.GeraetTermin
import java.time.LocalDate
import java.time.LocalDateTime

/** Farben der Auftragsstatus (wie im alten Baustellen Pilot). */
val STATUS_FARBEN = mapOf(
    "ausstehend" to 0xFFFFB020.toInt(),
    "laufend" to 0xFF5FB4FF.toInt(),
    "abgeschlossen" to 0xFF35D488.toInt(),
)

fun statusFarbe(status: String): Int = STATUS_FARBEN[status] ?: STATUS_FARBEN.getValue("ausstehend")

/** Ein Eintrag im Kalenderraster: Auftrag oder Termin aus dem Gerätekalender. */
data class KalEintrag(
    val titel: String,
    val ersterTag: LocalDate,
    val letzterTag: LocalDate,
    val ganztags: Boolean,
    val start: LocalDateTime?,
    val ende: LocalDateTime?,
    val farbe: Int,
    val auftragId: String? = null,
    val geraet: GeraetTermin? = null,
    val quelle: String = "",
)

/** "#rrggbb" (auch #rgb, #aarrggbb, rgb(…) oder ARGB-Zahl) als Farbe. */
fun farbeAusHex(hex: String?, ersatz: Int = 0xFF8B96A5.toInt()): Int {
    if (hex.isNullOrBlank()) return ersatz
    var t = hex.trim().lowercase()
    if (t.matches(Regex("-?\\d+"))) return (t.toLong().toInt()) or 0xFF000000.toInt()
    if (t.startsWith("rgb")) {
        val m = Regex("(\\d+)\\D+(\\d+)\\D+(\\d+)").find(t) ?: return ersatz
        val (r, g, b) = m.destructured
        return (0xFF shl 24) or (r.toInt().coerceIn(0, 255) shl 16) or (g.toInt().coerceIn(0, 255) shl 8) or b.toInt().coerceIn(0, 255)
    }
    t = t.removePrefix("#")
    val h = when (t.length) {
        3 -> t.map { "$it$it" }.joinToString("")
        6 -> t
        8 -> t.substring(2)
        else -> return ersatz
    }
    return h.toLongOrNull(16)?.let { (it.toInt()) or 0xFF000000.toInt() } ?: ersatz
}

fun hexAusFarbe(farbe: Int): String = String.format("#%06x", farbe and 0xFFFFFF)

/** Farbe eines Gerätekalenders: eigene Wahl, sonst die Farbe vom Gerät. */
fun kalenderFarbe(d: AppDaten, k: GeraetKalender): Int =
    d.einstellungen.kalenderFarben[k.id]?.let { farbeAusHex(it, k.farbe) } ?: k.farbe

fun kalenderSichtbar(d: AppDaten, kalenderId: String) = kalenderId !in d.einstellungen.versteckteKalender

private fun geraetEintrag(t: GeraetTermin, farbe: Int, quelle: String): KalEintrag {
    val erster = t.start.toLocalDate()
    val letzter = when {
        t.ende == null -> erster
        t.ganztags -> t.ende.toLocalDate().minusDays(1)
        // Ende genau um Mitternacht gehört zum Vortag
        t.ende.toLocalTime() == java.time.LocalTime.MIDNIGHT && t.ende.isAfter(t.start) -> t.ende.toLocalDate().minusDays(1)
        else -> t.ende.toLocalDate()
    }.let { if (it.isBefore(erster)) erster else it }
    return KalEintrag(t.titel, erster, letzter, t.ganztags, if (t.ganztags) null else t.start, if (t.ganztags) null else t.ende, farbe, geraet = t, quelle = quelle)
}

/**
 * Alle Einträge, die das Fenster [von]..[bis] berühren: Aufträge in ihrer
 * Statusfarbe und sichtbare Gerätetermine. Gerätetermine, die ein Auftrag
 * selbst eingetragen hat, erscheinen nur einmal (als Auftrag).
 */
fun kalenderEintraege(
    d: AppDaten, kalender: List<GeraetKalender>, termine: List<GeraetTermin>, von: LocalDate, bis: LocalDate,
): List<KalEintrag> {
    val liste = mutableListOf<KalEintrag>()
    val eigene = HashSet<Long>()
    d.auftraege.eintraege.forEach { a ->
        a.eventId.toLongOrNull()?.let { eigene += it }
        val s = a.beginn() ?: return@forEach
        val e = a.ende()?.takeIf { !it.isBefore(s) } ?: s
        if (e.isBefore(von) || s.isAfter(bis)) return@forEach
        liste += KalEintrag(
            a.titel.ifBlank { "Auftrag" }, s, e, a.ganztags,
            if (a.ganztags) null else s.atTime(zeitAus(a.zeitVon) ?: java.time.LocalTime.of(7, 0)),
            if (a.ganztags) null else e.atTime(zeitAus(a.zeitBis) ?: java.time.LocalTime.of(16, 0)),
            statusFarbe(a.status), auftragId = a.id, quelle = "Auftrag",
        )
    }
    val nachId = kalender.associateBy { it.id }
    termine.forEach { t ->
        if (t.eventId in eigene || !kalenderSichtbar(d, t.kalenderId)) return@forEach
        val k = nachId[t.kalenderId]
        val e = geraetEintrag(t, k?.let { kalenderFarbe(d, it) } ?: 0xFF8B96A5.toInt(), k?.titel ?: "Gerätekalender")
        if (e.letzterTag.isBefore(von) || e.ersterTag.isAfter(bis)) return@forEach
        liste += e
    }
    return liste.sortedWith(compareBy({ it.ersterTag }, { !it.ganztags }, { it.start }))
}

fun eintraegeAm(alle: List<KalEintrag>, tag: LocalDate): List<KalEintrag> =
    alle.filter { !tag.isBefore(it.ersterTag) && !tag.isAfter(it.letzterTag) }
        .sortedWith(compareBy({ it.auftragId == null }, { !it.ganztags }, { it.start }))

fun uhrzeitText(e: KalEintrag): String = when {
    e.ganztags || e.start == null -> "ganztägig"
    e.ende != null -> zwei(e.start.hour) + ":" + zwei(e.start.minute) + "–" + zwei(e.ende.hour) + ":" + zwei(e.ende.minute)
    else -> zwei(e.start.hour) + ":" + zwei(e.start.minute)
}
