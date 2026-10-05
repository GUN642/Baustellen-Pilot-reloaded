package de.gun.baustellen.reloaded.geraet

import java.time.LocalDateTime

data class GeraetKalender(
    val id: String,
    val titel: String,
    val kontoName: String,
    val dienst: String,
    val farbe: Int,
    val schreibbar: Boolean,
)

data class GeraetTermin(
    val eventId: Long,
    val kalenderId: String,
    val titel: String,
    val start: LocalDateTime,
    val ende: LocalDateTime?,
    val ganztags: Boolean,
    val ort: String,
    val notiz: String,
    val rrule: String?,
    /** Beginn des Vorkommens, wie ihn der Kalender führt. */
    val rohBeginn: Long,
)

/** Angaben zum Anlegen/Ändern eines Termins im Gerätekalender. */
data class TerminFelder(
    val titel: String,
    val ort: String,
    val notiz: String,
    val ganztags: Boolean,
    val start: LocalDateTime,
    /** Bei ganztägig: letzter Tag (einschließlich) um 00:00. */
    val ende: LocalDateTime,
    val rrule: String = "",
)
