package de.gun.baustellen.reloaded.logik

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.IsoFields
import java.util.Locale

val DE: Locale = Locale.GERMANY

/** "JJJJ-MM-TT" -> Datum, sonst null. */
fun parseIso(text: String?): LocalDate? = try {
    if (text.isNullOrBlank()) null else LocalDate.parse(text.trim().take(10))
} catch (e: Exception) {
    null
}

fun LocalDate.alsIso(): String = toString()
fun heuteIso(): String = LocalDate.now().alsIso()

private val DATUM_RE = Regex("""^(\d{1,2})\.(\d{1,2})\.(\d{4})$""")

/** "TT.MM.JJJJ" -> Datum, sonst null. */
fun parseDE(text: String?): LocalDate? {
    val m = DATUM_RE.matchEntire(text?.trim() ?: return null) ?: return null
    return try {
        LocalDate.of(m.groupValues[3].toInt(), m.groupValues[2].toInt(), m.groupValues[1].toInt())
    } catch (e: Exception) {
        null
    }
}

private val FORMAT_DE = DateTimeFormatter.ofPattern("dd.MM.yyyy")
fun LocalDate.alsDE(): String = format(FORMAT_DE)

/** ISO-Datum als "TT.MM.JJJJ" (leer bleibt leer). */
fun isoNachDE(iso: String?): String = parseIso(iso)?.alsDE() ?: ""
/** "TT.MM.JJJJ" als ISO-Datum (ungültig -> leer). */
fun deNachIso(de: String?): String = parseDE(de)?.alsIso() ?: ""

/** ISO-Datum für die Anzeige, ungültig als Gedankenstrich. */
fun de(iso: String?): String = parseIso(iso)?.alsDE() ?: "—"

fun zwei(n: Int): String = n.toString().padStart(2, '0')

/** "24.09." */
fun LocalDate.kurzDE(): String = zwei(dayOfMonth) + "." + zwei(monthValue) + "."

/** "Montag, 24. September 2026" */
fun LocalDate.lang(): String =
    dayOfWeek.getDisplayName(TextStyle.FULL, DE) + ", " + dayOfMonth + ". " +
        month.getDisplayName(TextStyle.FULL, DE) + " " + year

val MONATE = listOf("Januar", "Februar", "März", "April", "Mai", "Juni", "Juli", "August",
    "September", "Oktober", "November", "Dezember")
val WOCHENTAGE = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")
val WOCHENTAGE_LANG = listOf("Montag", "Dienstag", "Mittwoch", "Donnerstag", "Freitag", "Samstag", "Sonntag")

fun kalenderwoche(d: LocalDate): Int = d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)

/** Montag der Woche. */
fun wochenStart(d: LocalDate): LocalDate = d.minusDays((d.dayOfWeek.value - 1).toLong())

fun istWochenende(d: LocalDate) = d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY

/** Uhrzeit tolerant einlesen: 1300, 13.00, 13 00, 9, 930 … -> "13:00". */
fun zeitNormieren(wert: String?): String? {
    var t = (wert ?: "").trim()
    if (t.isEmpty()) return null
    t = t.replace(Regex("(?i)uhr"), "").trim().replace(Regex("[.,;\\-\\s]+"), ":").trim(':')
    val st: Int
    val mi: Int
    if (t.contains(":")) {
        val teile = t.split(":").filter { it.isNotEmpty() }
        if (teile.isEmpty()) return null
        st = teile[0].toIntOrNull() ?: return null
        mi = if (teile.size > 1) teile[1].toIntOrNull() ?: return null else 0
    } else {
        if (!Regex("""^\d{1,4}$""").matches(t)) return null
        when (t.length) {
            1, 2 -> { st = t.toInt(); mi = 0 }
            3 -> { st = t.substring(0, 1).toInt(); mi = t.substring(1).toInt() }
            else -> { st = t.substring(0, 2).toInt(); mi = t.substring(2).toInt() }
        }
    }
    val s = if (st == 24 && mi == 0) 0 else st
    if (s !in 0..23 || mi !in 0..59) return null
    return zwei(s) + ":" + zwei(mi)
}

fun zeitAus(text: String?): LocalTime? = zeitNormieren(text)?.let {
    LocalTime.of(it.substring(0, 2).toInt(), it.substring(3, 5).toInt())
}

// ---------------- Zahlen ----------------

/** Zahl mit festen Nachkommastellen, deutsches Format (1.234,50). */
fun zahl(n: Double, stellen: Int = 2): String = String.format(DE, "%,.${stellen}f", n)

/** Betrag in Euro: "1.234,50 €". */
fun eur(n: Double): String = zahl(n, 2) + " €"

/** Eingabe mit Komma oder Punkt lesen ("1.234,5" -> 1234.5). */
fun zahlLesen(text: String?): Double? {
    var t = (text ?: "").trim().replace(" ", "").replace("€", "")
    if (t.isEmpty()) return null
    if (t.contains(",")) t = t.replace(".", "").replace(",", ".")
    return t.toDoubleOrNull()
}

/** Zahl für ein Eingabefeld, ohne überflüssige Nachkommastellen. */
fun zahlFeld(d: Double?): String {
    if (d == null) return ""
    if (d == Math.floor(d) && !d.isInfinite() && Math.abs(d) < 1e12) return d.toLong().toString()
    return String.format(DE, "%.2f", d).trimEnd('0').trimEnd(',')
}
