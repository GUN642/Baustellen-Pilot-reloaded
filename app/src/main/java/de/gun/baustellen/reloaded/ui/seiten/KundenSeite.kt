package de.gun.baustellen.reloaded.ui.seiten

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.gun.baustellen.reloaded.daten.Kunde
import de.gun.baustellen.reloaded.daten.Speicher
import de.gun.baustellen.reloaded.daten.neueId
import de.gun.baustellen.reloaded.logik.anschrift
import de.gun.baustellen.reloaded.logik.eur
import de.gun.baustellen.reloaded.logik.kunde
import de.gun.baustellen.reloaded.logik.netto
import de.gun.baustellen.reloaded.ui.AuftragMaskeStart
import de.gun.baustellen.reloaded.ui.Feld
import de.gun.baustellen.reloaded.ui.Fliesstext
import de.gun.baustellen.reloaded.ui.Frage
import de.gun.baustellen.reloaded.ui.Karte
import de.gun.baustellen.reloaded.ui.Knopf
import de.gun.baustellen.reloaded.ui.KnopfArt
import de.gun.baustellen.reloaded.ui.Knopfreihe
import de.gun.baustellen.reloaded.ui.KundeMaskeStart
import de.gun.baustellen.reloaded.ui.Leer
import de.gun.baustellen.reloaded.ui.LocalPalette
import de.gun.baustellen.reloaded.ui.LocalSteuerung
import de.gun.baustellen.reloaded.ui.Mono
import de.gun.baustellen.reloaded.ui.Punkt
import de.gun.baustellen.reloaded.ui.Schrift
import de.gun.baustellen.reloaded.ui.Zeile
import de.gun.baustellen.reloaded.ui.textAuf

@Composable
fun KundenSeite() {
    val p = LocalPalette.current
    val st = LocalSteuerung.current
    val ctx = LocalContext.current
    val d = aktuelleDaten()
    var suche by remember { mutableStateOf("") }
    val liste = d.kunden.eintraege
        .filter { k -> suche.isBlank() || listOf(k.name, k.ansprech, k.ort, k.telefon, k.email).any { it.contains(suche.trim(), true) } }
        .sortedBy { it.name.lowercase() }
    Box(Modifier.fillMaxSize()) {
        SeitenListe {
            item {
                Karte("Kunden", "01", aktion = { Mono("${d.kunden.eintraege.size}", p.textDim) }) {
                    Feld(suche, { suche = it }, "Suchen", platzhalter = "Name, Ort, Telefon …")
                }
            }
            if (liste.isEmpty()) item { Leer(if (d.kunden.eintraege.isEmpty()) "Noch kein Kunde angelegt. Unten rechts auf + tippen." else "Kein Treffer.") }
            items(liste, key = { it.id }) { k ->
                val auftraege = d.auftraege.eintraege.filter { it.kundeId == k.id }
                Zeile(p.akzent, onClick = { st.kundeMaske = KundeMaskeStart(k.id) }) {
                    Punkt(k.name.uppercase(), 15.sp, zeilen = 2)
                    if (k.ansprech.isNotBlank()) Fliesstext(k.ansprech, p.textDim, 13.sp)
                    val ort = k.anschrift()
                    if (ort.isNotBlank()) Fliesstext(ort, p.textDim, 12.sp)
                    Mono("${auftraege.size} " + (if (auftraege.size == 1) "Auftrag" else "Aufträge") + " · " + eur(auftraege.sumOf { it.netto() }) + " netto", p.akzent, 11.sp, fett = true)
                    Knopfreihe {
                        if (k.telefon.isNotBlank()) Knopf("☎ " + k.telefon, klein = true) { anrufen(ctx, k.telefon) }
                        if (k.email.isNotBlank()) Knopf("✉ " + k.email, klein = true) { mailen(ctx, k.email) }
                    }
                    if (k.notiz.isNotBlank()) Fliesstext(k.notiz, p.textFaint, 12.sp, zeilen = 3)
                }
            }
        }
        FloatingActionButton(
            onClick = { st.kundeMaske = KundeMaskeStart() },
            containerColor = p.akzent, contentColor = textAuf(p.akzent), shape = CircleShape,
            modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(16.dp).size(52.dp),
        ) { Text("+", fontSize = 24.sp, fontFamily = Schrift.mono) }
    }
}

@Composable
fun KundeMaskeEbene(start: KundeMaskeStart) {
    val p = LocalPalette.current
    val st = LocalSteuerung.current
    val d = aktuelleDaten()
    val alt = remember(start) { start.id?.let { Speicher.aktuell.kunde(it) } }
    var name by remember(start) { mutableStateOf(alt?.name ?: "") }
    var ansprech by remember(start) { mutableStateOf(alt?.ansprech ?: "") }
    var strasse by remember(start) { mutableStateOf(alt?.strasse ?: "") }
    var plz by remember(start) { mutableStateOf(alt?.plz ?: "") }
    var ort by remember(start) { mutableStateOf(alt?.ort ?: "") }
    var telefon by remember(start) { mutableStateOf(alt?.telefon ?: "") }
    var email by remember(start) { mutableStateOf(alt?.email ?: "") }
    var notiz by remember(start) { mutableStateOf(alt?.notiz ?: "") }
    var loeschFrage by remember { mutableStateOf(false) }

    fun speichern() {
        if (name.isBlank()) { st.melden("Kunde", "Bitte einen Namen eingeben."); return }
        val neu = (alt ?: Kunde(id = neueId())).copy(
            name = name.trim(), ansprech = ansprech.trim(), strasse = strasse.trim(), plz = plz.trim(), ort = ort.trim(),
            telefon = telefon.trim(), email = email.trim(), notiz = notiz.trim(),
        )
        Speicher.aendern { dd ->
            val l = dd.kunden.eintraege
            dd.copy(kunden = dd.kunden.copy(eintraege = if (alt != null) l.map { if (it.id == neu.id) neu else it } else l + neu))
        }
        st.kundeMaske = null
        st.kurz("Kunde gespeichert")
    }

    Column {
        EbenenKopf(if (alt != null) "Kunde bearbeiten" else "Neuer Kunde", { st.kundeMaske = null })
        SeitenListe {
            item {
                Karte("Kunde", "01") {
                    Feld(name, { name = it }, "Name / Firma *", platzhalter = "Bauunternehmen Muster GmbH")
                    Feld(ansprech, { ansprech = it }, "Ansprechpartner", platzhalter = "Herr Muster")
                    Feld(strasse, { strasse = it }, "Straße + Nr.")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Feld(plz, { plz = it }, "PLZ", Modifier.weight(0.35f), tastatur = KeyboardType.Number)
                        Feld(ort, { ort = it }, "Ort", Modifier.weight(0.65f))
                    }
                    Zweier(
                        { m -> Feld(telefon, { telefon = it }, "Telefon", m, platzhalter = "0170 1234567", tastatur = KeyboardType.Phone) },
                        { m -> Feld(email, { email = it }, "E-Mail", m, platzhalter = "info@beispiel.de", tastatur = KeyboardType.Email) },
                    )
                    Feld(notiz, { notiz = it }, "Notiz", zeilen = 3)
                    FormKnoepfe("Kunde speichern", ::speichern) { st.kundeMaske = null }
                }
            }
            if (alt != null) item {
                val auftraege = d.auftraege.eintraege.filter { it.kundeId == alt.id }.sortedByDescending { it.von }
                Karte("Aufträge des Kunden", "02", aktion = { Mono("${auftraege.size}", p.textDim) }) {
                    if (auftraege.isEmpty()) Leer("Noch kein Auftrag für diesen Kunden.")
                    auftraege.forEach { a -> AuftragKarte(d, a) { st.kundeMaske = null; st.auftragOeffnen(a.id) } }
                    Knopfreihe {
                        Knopf("+ Neuer Auftrag", klein = true, art = KnopfArt.PRIMAER) { st.kundeMaske = null; st.auftragMaske = AuftragMaskeStart(kundeId = alt.id) }
                        Knopf("✕ Kunde löschen", klein = true, art = KnopfArt.GEFAHR) { loeschFrage = true }
                    }
                }
            }
        }
    }
    if (loeschFrage && alt != null) {
        val anzahl = d.auftraege.eintraege.count { it.kundeId == alt.id }
        Frage(
            "Kunde löschen",
            "„${alt.name}“ löschen?" + if (anzahl > 0) "\n\n$anzahl Auftrag/Aufträge bleiben erhalten, verlieren aber die Zuordnung." else "",
            onJa = {
                loeschFrage = false
                val vorher = Speicher.aktuell
                Speicher.aendern { dd ->
                    dd.copy(
                        kunden = dd.kunden.copy(eintraege = dd.kunden.eintraege.filter { it.id != alt.id }),
                        auftraege = dd.auftraege.copy(eintraege = dd.auftraege.eintraege.map { if (it.kundeId == alt.id) it.copy(kundeId = "") else it }),
                    )
                }
                st.kundeMaske = null
                st.rueckgaengig("Kunde gelöscht", {
                    Speicher.aendern { dd ->
                        val ids = vorher.auftraege.eintraege.filter { it.kundeId == alt.id }.map { it.id }.toSet()
                        dd.copy(
                            kunden = dd.kunden.copy(eintraege = dd.kunden.eintraege + alt),
                            auftraege = dd.auftraege.copy(eintraege = dd.auftraege.eintraege.map { if (it.id in ids) it.copy(kundeId = alt.id) else it }),
                        )
                    }
                })
            },
            onNein = { loeschFrage = false },
        )
    }
}
