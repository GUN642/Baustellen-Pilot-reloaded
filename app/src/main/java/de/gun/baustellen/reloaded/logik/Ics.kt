package de.gun.baustellen.reloaded.logik

import de.gun.baustellen.reloaded.daten.Auftrag
import java.net.URLEncoder
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** Aufträge als iCalendar-Datei (.ics) – lässt sich in jeden Kalender einlesen. */
object Ics {
    private val VTIMEZONE_BERLIN = listOf(
        "BEGIN:VTIMEZONE", "TZID:Europe/Berlin",
        "BEGIN:DAYLIGHT", "TZOFFSETFROM:+0100", "TZOFFSETTO:+0200", "TZNAME:CEST",
        "DTSTART:19700329T020000", "RRULE:FREQ=YEARLY;BYMONTH=3;BYDAY=-1SU", "END:DAYLIGHT",
        "BEGIN:STANDARD", "TZOFFSETFROM:+0200", "TZOFFSETTO:+0100", "TZNAME:CET",
        "DTSTART:19701025T030000", "RRULE:FREQ=YEARLY;BYMONTH=10;BYDAY=-1SU", "END:STANDARD",
        "END:VTIMEZONE",
    )

    private val TAG = DateTimeFormatter.ofPattern("yyyyMMdd")
    private val ZEIT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")

    fun schutz(v: String): String =
        v.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace(Regex("\r?\n"), "\\n")

    /** Zeilen über 75 Zeichen falten (RFC 5545). */
    fun falten(zeile: String): String {
        if (zeile.length <= 75) return zeile
        val sb = StringBuilder(zeile.substring(0, 75))
        var rest = zeile.substring(75)
        while (rest.length > 74) {
            sb.append("\r\n ").append(rest.substring(0, 74)); rest = rest.substring(74)
        }
        if (rest.isNotEmpty()) sb.append("\r\n ").append(rest)
        return sb.toString()
    }

    /** Beginn und Ende (bei ganztägig: Ende exklusiv). */
    fun spanne(a: Auftrag): Pair<LocalDateTime, LocalDateTime>? {
        val s = a.beginn() ?: return null
        val e = a.ende()?.takeIf { !it.isBefore(s) } ?: s
        if (a.ganztags) return s.atStartOfDay() to e.plusDays(1).atStartOfDay()
        val st = s.atTime(zeitAus(a.zeitVon) ?: java.time.LocalTime.of(7, 0))
        var en = e.atTime(zeitAus(a.zeitBis) ?: java.time.LocalTime.of(16, 0))
        if (!en.isAfter(st)) en = st.plusHours(1)
        return st to en
    }

    private fun vevent(a: Auftrag, kundeName: String, stempel: String): List<String> {
        val (s, e) = spanne(a) ?: return emptyList()
        val z = mutableListOf("BEGIN:VEVENT", "UID:bau-${a.id}@baustellen-dashboard", "DTSTAMP:$stempel")
        if (a.ganztags) {
            z += "DTSTART;VALUE=DATE:" + s.format(TAG)
            z += "DTEND;VALUE=DATE:" + e.format(TAG)
        } else {
            z += "DTSTART;TZID=Europe/Berlin:" + s.format(ZEIT)
            z += "DTEND;TZID=Europe/Berlin:" + e.format(ZEIT)
        }
        z += "SUMMARY:" + schutz(terminTitel(a, kundeName))
        a.anschrift().takeIf { it.isNotBlank() }?.let { z += "LOCATION:" + schutz(it) }
        terminBeschreibung(a, kundeName).takeIf { it.isNotBlank() }?.let { z += "DESCRIPTION:" + schutz(it) }
        z += "END:VEVENT"
        return z
    }

    fun dokument(liste: List<Pair<Auftrag, String>>): String {
        val stempel = ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"))
        val z = mutableListOf("BEGIN:VCALENDAR", "VERSION:2.0", "PRODID:-//Baustellen Pilot Reloaded//DE", "CALSCALE:GREGORIAN", "METHOD:PUBLISH")
        if (liste.any { !it.first.ganztags }) z += VTIMEZONE_BERLIN
        liste.forEach { (a, k) -> z += vevent(a, k, stempel) }
        z += "END:VCALENDAR"
        return z.joinToString("\r\n") { falten(it) } + "\r\n"
    }

    /** Vorlage für einen neuen Termin in Google Kalender (im Browser). */
    fun googleLink(a: Auftrag, kundeName: String): String? {
        val (s, e) = spanne(a) ?: return null
        val bereich = if (a.ganztags) s.format(TAG) + "/" + e.format(TAG) else {
            val f = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
            s.atZone(ZoneId.systemDefault()).withZoneSameInstant(ZoneOffset.UTC).format(f) + "/" +
                e.atZone(ZoneId.systemDefault()).withZoneSameInstant(ZoneOffset.UTC).format(f)
        }
        fun enc(t: String) = URLEncoder.encode(t, "UTF-8").replace("+", "%20")
        return "https://calendar.google.com/calendar/render?action=TEMPLATE" +
            "&text=" + enc(terminTitel(a, kundeName)) + "&dates=" + bereich +
            "&details=" + enc(terminBeschreibung(a, kundeName)) + "&location=" + enc(a.anschrift())
    }
}
