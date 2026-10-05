package de.gun.baustellen.reloaded.ui.seiten

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.gun.baustellen.reloaded.daten.Auftrag
import de.gun.baustellen.reloaded.daten.STATUS
import de.gun.baustellen.reloaded.daten.Speicher
import de.gun.baustellen.reloaded.daten.neueId
import de.gun.baustellen.reloaded.geraet.KalenderAbgleich
import de.gun.baustellen.reloaded.logik.alsDE
import de.gun.baustellen.reloaded.logik.auftrag
import de.gun.baustellen.reloaded.logik.betraege
import de.gun.baustellen.reloaded.logik.alsIso
import de.gun.baustellen.reloaded.logik.eur
import de.gun.baustellen.reloaded.logik.isoNachDE
import de.gun.baustellen.reloaded.logik.parseDE
import de.gun.baustellen.reloaded.logik.zahlFeld
import de.gun.baustellen.reloaded.logik.zahlLesen
import de.gun.baustellen.reloaded.logik.zeitNormieren
import de.gun.baustellen.reloaded.ui.Abstand
import de.gun.baustellen.reloaded.ui.Auswahl
import de.gun.baustellen.reloaded.ui.AuftragMaskeStart
import de.gun.baustellen.reloaded.ui.DatumFeld
import de.gun.baustellen.reloaded.ui.Etikett
import de.gun.baustellen.reloaded.ui.Feld
import de.gun.baustellen.reloaded.ui.Hinweis
import de.gun.baustellen.reloaded.ui.Karte
import de.gun.baustellen.reloaded.ui.LocalPalette
import de.gun.baustellen.reloaded.ui.LocalSteuerung
import de.gun.baustellen.reloaded.ui.Mono
import de.gun.baustellen.reloaded.ui.RUND_KLEIN
import de.gun.baustellen.reloaded.ui.Segmente
import de.gun.baustellen.reloaded.ui.ZeitFeld
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun AuftragMaskeEbene(start: AuftragMaskeStart) {
    val p = LocalPalette.current
    val st = LocalSteuerung.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val d = aktuelleDaten()
    val alt = remember(start) { start.id?.let { Speicher.aktuell.auftrag(it) } }

    var titel by remember(start) { mutableStateOf(alt?.titel ?: "") }
    var nummer by remember(start) { mutableStateOf(alt?.nummer ?: "") }
    var kundeId by remember(start) { mutableStateOf(alt?.kundeId ?: start.kundeId ?: "") }
    var status by remember(start) { mutableStateOf(alt?.status ?: "ausstehend") }
    var strasse by remember(start) { mutableStateOf(alt?.strasse ?: "") }
    var plz by remember(start) { mutableStateOf(alt?.plz ?: "") }
    var ort by remember(start) { mutableStateOf(alt?.ort ?: "") }
    var von by remember(start) { mutableStateOf(alt?.let { isoNachDE(it.von) } ?: (start.datum ?: LocalDate.now()).alsDE()) }
    var bis by remember(start) { mutableStateOf(alt?.let { if (it.bis != it.von) isoNachDE(it.bis) else "" } ?: "") }
    var ganztags by remember(start) { mutableStateOf(alt?.ganztags ?: true) }
    var zeitVon by remember(start) { mutableStateOf(alt?.zeitVon ?: "07:00") }
    var zeitBis by remember(start) { mutableStateOf(alt?.zeitBis ?: "16:00") }
    var betrag by remember(start) { mutableStateOf(alt?.let { if (it.betrag != 0.0) zahlFeld(it.betrag) else "" } ?: "") }
    var basis by remember(start) { mutableStateOf(alt?.basis ?: "netto") }
    var mwst by remember(start) { mutableStateOf(zahlFeld(alt?.mwst ?: d.einstellungen.mwst)) }
    var maschine by remember(start) { mutableStateOf(alt?.maschine ?: "") }
    var notiz by remember(start) { mutableStateOf(alt?.notiz ?: "") }

    fun speichern() {
        if (titel.isBlank()) { st.melden("Auftrag", "Bitte eine Bezeichnung eingeben."); return }
        val v = parseDE(von) ?: run { st.melden("Auftrag", "Bitte ein gültiges Startdatum (TT.MM.JJJJ) eingeben."); return }
        val b = if (bis.isBlank()) v else parseDE(bis) ?: run { st.melden("Auftrag", "Das Enddatum ist ungültig."); return }
        if (b.isBefore(v)) { st.melden("Auftrag", "Das Ende liegt vor dem Beginn."); return }
        val zv = zeitNormieren(zeitVon) ?: "07:00"
        val zb = zeitNormieren(zeitBis) ?: "16:00"
        val neu = (alt ?: Auftrag(id = neueId())).copy(
            titel = titel.trim(), nummer = nummer.trim(), kundeId = kundeId, status = status,
            strasse = strasse.trim(), plz = plz.trim(), ort = ort.trim(),
            von = v.alsIso(), bis = b.alsIso(), ganztags = ganztags, zeitVon = zv, zeitBis = zb,
            betrag = zahlLesen(betrag) ?: 0.0, basis = basis, mwst = zahlLesen(mwst) ?: 0.0,
            maschine = maschine.trim(), notiz = notiz.trim(),
        )
        Speicher.aendern { dd ->
            val l = dd.auftraege.eintraege
            dd.copy(auftraege = dd.auftraege.copy(eintraege = if (alt != null) l.map { if (it.id == neu.id) neu else it } else l + neu))
        }
        st.auftragMaske = null
        st.kurz("Auftrag gespeichert")
        scope.launch {
            KalenderAbgleich.automatisch(ctx, Speicher.aktuell.auftrag(neu.id) ?: neu)?.let { st.kurz(it) }
        }
    }

    Column {
        EbenenKopf(if (alt != null) "Auftrag bearbeiten" else "Neuer Auftrag", { st.auftragMaske = null })
        SeitenListe {
            item {
                Karte("Auftrag", "01") {
                    Feld(titel, { titel = it }, "Bezeichnung / Baustelle *", platzhalter = "z. B. Kellerausschachtung Musterweg")
                    Zweier(
                        { m -> Feld(nummer, { nummer = it }, "Auftragsnummer", m, platzhalter = "2026-014") },
                        { m ->
                            Auswahl("Kunde", listOf("" to "— ohne Kunde —") + d.kunden.eintraege.sortedBy { it.name.lowercase() }.map { it.id to it.name },
                                kundeId, m) { kundeId = it }
                        },
                    )
                    Abstand(8.dp)
                    Etikett("Status")
                    Segmente(STATUS.map { it.key to it.value }, status, Modifier.padding(top = 6.dp)) { status = it }
                }
            }
            item {
                Karte("Baustelle", "02") {
                    Feld(strasse, { strasse = it }, "Straße + Nr.", platzhalter = "Musterweg 12")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Feld(plz, { plz = it }, "PLZ", Modifier.weight(0.35f), tastatur = KeyboardType.Number)
                        Feld(ort, { ort = it }, "Ort", Modifier.weight(0.65f), platzhalter = "Musterstadt")
                    }
                    Feld(maschine, { maschine = it }, "Maschine/Gerät (Vorschlag für Regieberichte)", platzhalter = "z. B. Kettenbagger CAT 320")
                }
            }
            item {
                Karte("Zeitraum", "03") {
                    Zweier(
                        { m -> DatumFeld(von, { von = it }, "Beginn *", m) },
                        { m -> DatumFeld(bis, { bis = it }, "Ende", m) },
                    )
                    Abstand(8.dp)
                    Segmente(listOf("1" to "Ganztägig", "0" to "Mit Uhrzeit"), if (ganztags) "1" else "0") { ganztags = it == "1" }
                    if (!ganztags) Zweier(
                        { m -> ZeitFeld(zeitVon, { zeitVon = it }, "Uhrzeit von", m) },
                        { m -> ZeitFeld(zeitBis, { zeitBis = it }, "Uhrzeit bis", m) },
                    )
                    Hinweis(
                        if (d.einstellungen.autoKalender && d.einstellungen.zielKalenderId.isNotBlank())
                            "Der Zeitraum wird beim Speichern in den Zielkalender übertragen."
                        else "Für die automatische Übertragung in den Gerätekalender im Kalender unter ⚙ einen Zielkalender wählen."
                    )
                }
            }
            item {
                Karte("Auftragssumme", "04") {
                    Zweier(
                        { m -> Feld(betrag, { betrag = it }, "Betrag (€)", m, platzhalter = "0,00", tastatur = KeyboardType.Decimal) },
                        { m -> Feld(mwst, { mwst = it }, "MwSt (%)", m, tastatur = KeyboardType.Decimal) },
                    )
                    Abstand(8.dp)
                    Etikett("Betrag ist")
                    Segmente(listOf("netto" to "Netto", "brutto" to "Brutto"), basis, Modifier.padding(top = 6.dp)) { basis = it }
                    val b = betraege(zahlLesen(betrag) ?: 0.0, zahlLesen(mwst) ?: 0.0, basis)
                    Row(
                        Modifier.fillMaxWidth().padding(top = 10.dp).clip(RUND_KLEIN).background(p.panelAlt).padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Mono("Netto " + eur(b.netto), p.text, 12.sp, fett = true)
                        Mono("MwSt " + eur(b.steuer), p.textDim, 12.sp)
                        Mono("Brutto " + eur(b.brutto), p.text, 12.sp, fett = true)
                    }
                }
            }
            item {
                Karte("Notiz", "05") {
                    Feld(notiz, { notiz = it }, "Notiz", platzhalter = "Besonderheiten, Zufahrt, Ansprechpartner vor Ort …", zeilen = 3)
                    FormKnoepfe("Auftrag speichern", ::speichern) { st.auftragMaske = null }
                }
            }
        }
    }
}
