package de.gun.baustellen.reloaded.geraet

import android.content.Context
import de.gun.baustellen.reloaded.daten.Auftrag
import de.gun.baustellen.reloaded.daten.Speicher
import de.gun.baustellen.reloaded.logik.Ics
import de.gun.baustellen.reloaded.logik.anschrift
import de.gun.baustellen.reloaded.logik.kundeName
import de.gun.baustellen.reloaded.logik.terminBeschreibung
import de.gun.baustellen.reloaded.logik.terminTitel

/**
 * Überträgt den Zeitraum eines Auftrags direkt in den Zielkalender des
 * Geräts. Liegt dort ein Google- oder Outlook-Konto, überträgt Android den
 * Termin selbst in die Cloud. Änderungen werden nachgeführt.
 */
object KalenderAbgleich {

    fun felder(a: Auftrag): TerminFelder? {
        val (s, e) = Ics.spanne(a) ?: return null
        val k = Speicher.aktuell.kundeName(a.kundeId)
        // Bei ganztägig erwartet TerminFelder den letzten Tag einschließlich
        val ende = if (a.ganztags) e.minusDays(1) else e
        return TerminFelder(terminTitel(a, k), a.anschrift(), terminBeschreibung(a, k), a.ganztags, s, ende)
    }

    /** Ergebnis als Text für die Rückmeldung; wirft bei echten Fehlern. */
    suspend fun abgleichen(ctx: Context, a: Auftrag): String {
        val ziel = Speicher.aktuell.einstellungen.zielKalenderId
        if (ziel.isBlank()) throw IllegalStateException("Bitte zuerst im Kalender unter ⚙ einen Zielkalender wählen.")
        if (!GeraeteKalender.darfSchreiben(ctx)) throw IllegalStateException("Ohne Kalenderberechtigung kann nichts eingetragen werden.")
        val f = felder(a) ?: throw IllegalStateException("Der Auftrag hat kein gültiges Startdatum.")
        val vorhanden = a.eventId.toLongOrNull()
        if (vorhanden != null) {
            try {
                GeraeteKalender.aendern(ctx, vorhanden, null, f)
                GeraeteKalender.einlesen(ctx)
                return "Termin im Kalender aktualisiert"
            } catch (e: Exception) {
                // Termin wurde im Kalender gelöscht: neu anlegen
            }
        }
        val id = GeraeteKalender.anlegen(ctx, ziel, f)
        Speicher.auftrag(a.id) { it.copy(eventId = id.toString()) }
        GeraeteKalender.einlesen(ctx)
        return "In den Kalender übertragen"
    }

    /** Automatisch nach dem Speichern, sofern eingerichtet; Fehler bleiben still. */
    suspend fun automatisch(ctx: Context, a: Auftrag): String? {
        val e = Speicher.aktuell.einstellungen
        if (!e.autoKalender || e.zielKalenderId.isBlank() || !GeraeteKalender.darfSchreiben(ctx)) return null
        return try { abgleichen(ctx, a) } catch (ex: Exception) { null }
    }

    suspend fun entfernen(ctx: Context, a: Auftrag) {
        val id = a.eventId.toLongOrNull() ?: return
        if (!GeraeteKalender.darfSchreiben(ctx)) return
        try { GeraeteKalender.loeschen(ctx, id) } catch (e: Exception) { }
    }
}
