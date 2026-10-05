package de.gun.baustellen.reloaded.geraet

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

object GeraeteKalender {
    private val _kalender = MutableStateFlow<List<GeraetKalender>>(emptyList())
    val kalender: StateFlow<List<GeraetKalender>> = _kalender.asStateFlow()
    private val _termine = MutableStateFlow<List<GeraetTermin>>(emptyList())
    val termine: StateFlow<List<GeraetTermin>> = _termine.asStateFlow()
    private val _stand = MutableStateFlow(0L)
    /** Zeitpunkt des letzten erfolgreichen Einlesens (0 = noch nie). */
    val stand: StateFlow<Long> = _stand.asStateFlow()

    private val mutex = Mutex()

    fun darfLesen(ctx: Context) =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    fun darfSchreiben(ctx: Context) =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /** Fenster: ein Jahr zurück bis zwei Jahre voraus. */
    fun fenster(): Pair<LocalDate, LocalDate> {
        val j = LocalDate.now().year
        return LocalDate.of(j - 1, 1, 1) to LocalDate.of(j + 2, 12, 31)
    }

    suspend fun einlesen(ctx: Context): Boolean = withContext(Dispatchers.IO) {
        if (!darfLesen(ctx)) return@withContext false
        mutex.withLock {
            try {
                val kal = kalenderLesen(ctx)
                val (von, bis) = fenster()
                val termine = termineLesen(ctx, von, bis)
                _kalender.value = kal
                _termine.value = termine
                _stand.value = System.currentTimeMillis()
                true
            } catch (e: Exception) {
                false
            }
        }
    }

    private fun Cursor.str(spalte: String): String? {
        val i = getColumnIndex(spalte)
        return if (i >= 0 && !isNull(i)) getString(i) else null
    }

    private fun Cursor.lng(spalte: String): Long? {
        val i = getColumnIndex(spalte)
        return if (i >= 0 && !isNull(i)) getLong(i) else null
    }

    private fun Cursor.int(spalte: String): Int? {
        val i = getColumnIndex(spalte)
        return if (i >= 0 && !isNull(i)) getInt(i) else null
    }

    fun dienstAus(kontoTyp: String, kontoName: String, zugriff: Int): String {
        val typ = kontoTyp.lowercase()
        if (typ.contains("subscribed") || typ.contains("ical") || typ.contains("ics") || typ.contains("webcal")) return "Abonniert"
        var dienst = when {
            typ.contains("google") -> "Google"
            typ.contains("exchange") || typ.contains("outlook") || typ.contains("microsoft") || typ.contains("office") -> "Outlook"
            typ.contains("apple") || typ.contains("icloud") -> "iCloud"
            typ.contains("osp") || typ.contains("samsung") -> "Samsung"
            typ.contains("local") || typ.isEmpty() -> "Lokal"
            else -> "Konto"
        }
        if (Regex("@(outlook|hotmail|live|msn)\\.", RegexOption.IGNORE_CASE).containsMatchIn(kontoName)) dienst = "Outlook"
        if (Regex("@(gmail|googlemail)\\.", RegexOption.IGNORE_CASE).containsMatchIn(kontoName)) dienst = "Google"
        if (Regex("@(icloud|me|mac)\\.", RegexOption.IGNORE_CASE).containsMatchIn(kontoName)) dienst = "iCloud"
        if (zugriff in 1 until CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR && dienst != "Lokal") return "Abonniert"
        return dienst
    }

    private fun kalenderLesen(ctx: Context): List<GeraetKalender> {
        val liste = mutableListOf<GeraetKalender>()
        val felder = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.ACCOUNT_TYPE,
            CalendarContract.Calendars.CALENDAR_COLOR,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
        )
        ctx.contentResolver.query(CalendarContract.Calendars.CONTENT_URI, felder, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val id = c.lng(CalendarContract.Calendars._ID) ?: continue
                val zugriff = c.int(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL) ?: 0
                val kontoName = c.str(CalendarContract.Calendars.ACCOUNT_NAME) ?: ""
                val kontoTyp = c.str(CalendarContract.Calendars.ACCOUNT_TYPE) ?: ""
                liste.add(
                    GeraetKalender(
                        id = id.toString(),
                        titel = c.str(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME) ?: "Kalender $id",
                        kontoName = kontoName,
                        dienst = dienstAus(kontoTyp, kontoName, zugriff),
                        farbe = (c.int(CalendarContract.Calendars.CALENDAR_COLOR) ?: 0xFF5FB4FF.toInt()) or 0xFF000000.toInt(),
                        schreibbar = zugriff >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR,
                    )
                )
            }
        }
        return liste
    }

    /**
     * Ganztägige Termine liegen laut Android auf UTC-Mitternacht, manche
     * Anbieter legen sie aber auf Mitternacht der Ortszeit oder das Ende eine
     * Millisekunde hinter Mitternacht. Das Bezugssystem wird einmal am Start
     * bestimmt und für Start und Ende gleich verwendet; das Ende gilt mit einer
     * Minute Toleranz als exklusiv.
     * Rückgabe: erster Tag 00:00 und Ende exklusiv (Folgetag 00:00).
     */
    fun ganztagsSpanne(startMs: Long, endeMs: Long?): Pair<LocalDateTime, LocalDateTime> {
        val s = Instant.ofEpochMilli(startMs)
        val utc = s.atZone(ZoneOffset.UTC)
        val utcBasiert = utc.hour == 0 && utc.minute == 0
        val zone: ZoneId = if (utcBasiert) ZoneOffset.UTC else ZoneId.systemDefault()
        val start = s.atZone(zone).toLocalDate()
        if (endeMs == null || endeMs <= startMs) return start.atStartOfDay() to start.plusDays(1).atStartOfDay()
        val e = Instant.ofEpochMilli(endeMs).atZone(zone)
        val mitternacht = e.toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
        val abweichung = endeMs - mitternacht
        val letzter = if (abweichung in 0 until 60_000) e.toLocalDate().minusDays(1) else e.toLocalDate()
        val l = if (letzter.isBefore(start)) start else letzter
        return start.atStartOfDay() to l.plusDays(1).atStartOfDay()
    }

    private fun lokal(ms: Long): LocalDateTime =
        Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDateTime()

    /** Zeiten eines Termins deuten, inkl. Erkennung "ganztägig ohne Kennzeichen". */
    private fun zeiten(beginn: Long, ende: Long?, allDay: Boolean): Triple<LocalDateTime, LocalDateTime?, Boolean> {
        var ganztags = allDay
        if (!ganztags && ende != null) {
            val stunden = (ende - beginn) / 3_600_000.0
            val volleTage = stunden >= 23 && Math.abs(stunden - Math.round(stunden)) < 0.01
            val utc = Instant.ofEpochMilli(beginn).atZone(ZoneOffset.UTC)
            val ort = Instant.ofEpochMilli(beginn).atZone(ZoneId.systemDefault())
            val aufMitternacht = (utc.hour == 0 && utc.minute == 0) || (ort.hour == 0 && ort.minute == 0)
            // Nur wenn die Dauer ein Vielfaches ganzer Tage ist (23/25 h an Umstellungstagen)
            val tage = Math.round(stunden / 24.0)
            if (aufMitternacht && volleTage && tage >= 1 && Math.abs(stunden - tage * 24) <= 1.01) ganztags = true
        }
        return if (ganztags) {
            val (s, e) = ganztagsSpanne(beginn, ende)
            Triple(s, e, true)
        } else Triple(lokal(beginn), ende?.let { lokal(it) }, false)
    }

    private fun termineLesen(ctx: Context, von: LocalDate, bis: LocalDate): List<GeraetTermin> {
        val zone = ZoneId.systemDefault()
        val vonMs = von.atStartOfDay(zone).toInstant().toEpochMilli()
        val bisMs = bis.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val ergebnis = mutableListOf<GeraetTermin>()
        
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().let {
            ContentUris.appendId(it, vonMs); ContentUris.appendId(it, bisMs); it.build()
        }
        val felder = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.DESCRIPTION,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.CALENDAR_ID,
            CalendarContract.Instances.RRULE,
            CalendarContract.Instances.STATUS,
        )
        ctx.contentResolver.query(uri, felder, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val status = c.int(CalendarContract.Instances.STATUS)
                if (status == CalendarContract.Events.STATUS_CANCELED) continue
                val eventId = c.lng(CalendarContract.Instances.EVENT_ID) ?: continue
                val beginn = c.lng(CalendarContract.Instances.BEGIN) ?: continue
                val ende = c.lng(CalendarContract.Instances.END)
                val (s, e, gt) = zeiten(beginn, ende, (c.int(CalendarContract.Instances.ALL_DAY) ?: 0) == 1)
                ergebnis.add(
                    GeraetTermin(
                        eventId = eventId,
                        kalenderId = (c.lng(CalendarContract.Instances.CALENDAR_ID) ?: 0L).toString(),
                        titel = c.str(CalendarContract.Instances.TITLE)?.takeIf { it.isNotBlank() } ?: "(ohne Titel)",
                        start = s, ende = e, ganztags = gt,
                        ort = c.str(CalendarContract.Instances.EVENT_LOCATION) ?: "",
                        notiz = c.str(CalendarContract.Instances.DESCRIPTION) ?: "",
                        rrule = c.str(CalendarContract.Instances.RRULE)?.takeIf { it.isNotBlank() },
                        rohBeginn = beginn,
                    )
                )
            }
        }

        val gesehen = HashSet<String>()
        return ergebnis.filter {
            gesehen.add(it.kalenderId + "|" + it.titel + "|" + it.start + "|" + it.ganztags)
        }.sortedBy { it.start }
    }

    // ---------------- Schreiben ----------------

    private fun werte(kalenderId: String?, f: TerminFelder): ContentValues {
        val v = ContentValues()
        if (kalenderId != null) v.put(CalendarContract.Events.CALENDAR_ID, kalenderId.toLong())
        v.put(CalendarContract.Events.TITLE, f.titel)
        v.put(CalendarContract.Events.EVENT_LOCATION, f.ort)
        v.put(CalendarContract.Events.DESCRIPTION, f.notiz)
        val startMs: Long
        val endeMs: Long
        if (f.ganztags) {
            startMs = f.start.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            endeMs = f.ende.toLocalDate().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            v.put(CalendarContract.Events.ALL_DAY, 1)
            v.put(CalendarContract.Events.EVENT_TIMEZONE, "UTC")
        } else {
            val zone = ZoneId.systemDefault()
            startMs = f.start.atZone(zone).toInstant().toEpochMilli()
            var e = f.ende.atZone(zone).toInstant().toEpochMilli()
            if (e <= startMs) e = startMs + 3_600_000
            endeMs = e
            v.put(CalendarContract.Events.ALL_DAY, 0)
            v.put(CalendarContract.Events.EVENT_TIMEZONE, zone.id)
        }
        v.put(CalendarContract.Events.DTSTART, startMs)
        if (f.rrule.isNotBlank()) {
            // Serien verlangen DURATION statt DTEND
            val dauerS = (endeMs - startMs) / 1000
            v.put(CalendarContract.Events.RRULE, f.rrule)
            v.put(CalendarContract.Events.DURATION, if (f.ganztags) "P${dauerS / 86400}D" else "P${dauerS}S")
            v.putNull(CalendarContract.Events.DTEND)
        } else {
            v.putNull(CalendarContract.Events.RRULE)
            v.putNull(CalendarContract.Events.DURATION)
            v.put(CalendarContract.Events.DTEND, endeMs)
        }
        return v
    }

    suspend fun anlegen(ctx: Context, kalenderId: String, f: TerminFelder): Long = withContext(Dispatchers.IO) {
        val uri = ctx.contentResolver.insert(CalendarContract.Events.CONTENT_URI, werte(kalenderId, f))
            ?: throw IllegalStateException("Der Kalender hat den Termin nicht angenommen.")
        ContentUris.parseId(uri)
    }

    suspend fun aendern(ctx: Context, eventId: Long, kalenderId: String?, f: TerminFelder) = withContext(Dispatchers.IO) {
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
        val n = ctx.contentResolver.update(uri, werte(kalenderId, f), null, null)
        if (n <= 0) throw IllegalStateException("Der Termin wurde im Kalender nicht gefunden.")
    }

    suspend fun loeschen(ctx: Context, eventId: Long) = withContext(Dispatchers.IO) {
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
        ctx.contentResolver.delete(uri, null, null)
        _termine.value = _termine.value.filter { it.eventId != eventId }
    }
}
