package de.gun.baustellen.reloaded.ui.seiten

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.gun.baustellen.reloaded.daten.Material
import de.gun.baustellen.reloaded.daten.Speicher
import de.gun.baustellen.reloaded.daten.neueId
import de.gun.baustellen.reloaded.logik.alsDE
import de.gun.baustellen.reloaded.logik.alsIso
import de.gun.baustellen.reloaded.logik.auftrag
import de.gun.baustellen.reloaded.logik.einheitText
import de.gun.baustellen.reloaded.logik.eur
import de.gun.baustellen.reloaded.logik.isoNachDE
import de.gun.baustellen.reloaded.logik.kundeName
import de.gun.baustellen.reloaded.logik.materialGesamt
import de.gun.baustellen.reloaded.logik.materialTextliste
import de.gun.baustellen.reloaded.logik.parseDE
import de.gun.baustellen.reloaded.logik.zahl
import de.gun.baustellen.reloaded.logik.zahlFeld
import de.gun.baustellen.reloaded.logik.zahlLesen
import de.gun.baustellen.reloaded.ui.Abstand
import de.gun.baustellen.reloaded.ui.DatumFeld
import de.gun.baustellen.reloaded.ui.Etikett
import de.gun.baustellen.reloaded.ui.Feld
import de.gun.baustellen.reloaded.ui.Fliesstext
import de.gun.baustellen.reloaded.ui.Hinweis
import de.gun.baustellen.reloaded.ui.Karte
import de.gun.baustellen.reloaded.ui.Knopf
import de.gun.baustellen.reloaded.ui.Leer
import de.gun.baustellen.reloaded.ui.LocalPalette
import de.gun.baustellen.reloaded.ui.LocalSteuerung
import de.gun.baustellen.reloaded.ui.MaterialMaskeStart
import de.gun.baustellen.reloaded.ui.Mono
import de.gun.baustellen.reloaded.ui.RUND_KLEIN
import de.gun.baustellen.reloaded.ui.Segmente
import de.gun.baustellen.reloaded.ui.VorschlagFeld
import de.gun.baustellen.reloaded.ui.Zeile
import java.time.LocalDate

@Composable
fun MaterialSeite() {
    val p = LocalPalette.current
    val st = LocalSteuerung.current
    val ctx = LocalContext.current
    val d = aktuelleDaten()
    val liste = materialGesamt(d)
    val bedarf = liste.sumOf { it.kostenBedarf }
    val verbrauch = liste.sumOf { it.kostenVerbrauch }
    SeitenListe {
        item {
            Karte("Material gesamt", "01", aktion = {
                Knopf("⧉ Kopieren", klein = true) {
                    val text = d.auftraege.eintraege.filter { it.material.isNotEmpty() }
                        .joinToString("\n------------------------------\n") { materialTextliste(it, d.kundeName(it.kundeId)) }
                    if (text.isBlank()) st.kurz("Kein Material vorhanden")
                    else { inZwischenablage(ctx, "Materialübersicht — alle Aufträge\n\n$text"); st.kurz("In die Zwischenablage kopiert") }
                }
            }) {
                Column(Modifier.fillMaxWidth().clip(RUND_KLEIN).background(p.panelAlt).padding(10.dp)) {
                    Mono("Bedarf     ${eur(bedarf)}", p.text, 12.sp)
                    Mono("Verbrauch  ${eur(verbrauch)}", p.text, 12.sp)
                    Mono("Differenz  ${eur(verbrauch - bedarf)}", if (verbrauch > bedarf) p.rot else p.gruen, 12.sp, fett = true)
                }
                Hinweis("Material wird im jeweiligen Auftrag erfasst. Hier steht die Summe über alle Aufträge, je Material und Einheit.")
            }
        }
        if (liste.isEmpty()) item { Leer("Noch kein Material erfasst.") }
        items(liste, key = { it.bez + it.einheit }) { z ->
            val e = einheitText(z.einheit)
            val diff = z.verbrauch - z.bedarf
            Zeile(if (diff > 0) p.rot else p.gruen) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Fliesstext(z.bez, fett = true, groesse = 14.sp, modifier = Modifier.weight(1f))
                    Mono((if (diff > 0) "+" else "") + zahl(diff) + " " + e, if (diff > 0) p.rot else p.gruen, 12.sp, fett = true)
                }
                Mono("Bedarf ${zahl(z.bedarf)} $e · ${eur(z.kostenBedarf)}", p.textDim, 11.sp)
                Mono("Verbrauch ${zahl(z.verbrauch)} $e · ${eur(z.kostenVerbrauch)}", p.textDim, 11.sp)
            }
        }
    }
}

@Composable
fun MaterialMaskeEbene(start: MaterialMaskeStart) {
    val p = LocalPalette.current
    val st = LocalSteuerung.current
    val d = aktuelleDaten()
    val a = d.auftrag(start.auftragId)
    val alt = remember(start) { a?.material?.firstOrNull { it.id == start.materialId } }
    var bez by remember(start) { mutableStateOf(alt?.bez ?: "") }
    var art by remember(start) { mutableStateOf(alt?.art ?: "bedarf") }
    var menge by remember(start) { mutableStateOf(alt?.let { zahlFeld(it.menge) } ?: "") }
    var einheit by remember(start) { mutableStateOf(alt?.einheit ?: "t") }
    var preis by remember(start) { mutableStateOf(alt?.let { zahlFeld(it.preis) } ?: "") }
    var datum by remember(start) { mutableStateOf(alt?.let { isoNachDE(it.datum) } ?: LocalDate.now().alsDE()) }
    var lieferant by remember(start) { mutableStateOf(alt?.lieferant ?: "") }

    // Bekannte Materialien und Lieferanten als Vorschlag
    val bekannt = remember(d) { d.auftraege.eintraege.flatMap { it.material }.map { it.bez }.filter { it.isNotBlank() }.distinct().sorted() }
    val lieferanten = remember(d) { d.auftraege.eintraege.flatMap { it.material }.map { it.lieferant }.filter { it.isNotBlank() }.distinct().sorted() }

    fun speichern() {
        if (a == null) { st.materialMaske = null; return }
        if (bez.isBlank()) { st.melden("Material", "Bitte das Material benennen."); return }
        val neu = (alt ?: Material(id = neueId())).copy(
            bez = bez.trim(), art = art, menge = zahlLesen(menge) ?: 0.0, einheit = einheit, preis = zahlLesen(preis) ?: 0.0,
            datum = parseDE(datum)?.alsIso() ?: "", lieferant = lieferant.trim(),
        )
        Speicher.auftrag(a.id) { x -> x.copy(material = if (alt != null) x.material.map { if (it.id == neu.id) neu else it } else x.material + neu) }
        st.materialMaske = null
        st.kurz("Material gespeichert")
    }

    Column {
        EbenenKopf(if (alt != null) "Material bearbeiten" else "Material erfassen", { st.materialMaske = null })
        SeitenListe {
            item {
                Karte(a?.titel ?: "Auftrag", "01") {
                    VorschlagFeld(bez, { bez = it }, "Material *", bekannt.map { it to it })
                    Abstand(8.dp)
                    Etikett("Art")
                    Segmente(listOf("bedarf" to "Bedarf (geplant)", "verbrauch" to "Verbrauch (tatsächlich)"), art, Modifier.padding(top = 6.dp)) { art = it }
                    Abstand(4.dp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Feld(menge, { menge = it }, "Menge *", Modifier.weight(1f), platzhalter = "0,00", tastatur = KeyboardType.Decimal)
                        Feld(preis, { preis = it }, "Preis je Einheit (€)", Modifier.weight(1f), platzhalter = "0,00", tastatur = KeyboardType.Decimal)
                    }
                    Abstand(8.dp)
                    Etikett("Einheit")
                    Segmente(listOf("t" to "Tonne (t)", "m3" to "Kubikmeter (m³)"), einheit, Modifier.padding(top = 6.dp)) { einheit = it }
                    Zweier(
                        { m -> DatumFeld(datum, { datum = it }, "Datum", m) },
                        { m -> Feld(lieferant, { lieferant = it }, "Lieferant / Quelle", m) },
                    )
                    if (lieferanten.isNotEmpty() && lieferant.isBlank()) Segmente(lieferanten.take(6).map { it to it }, "", Modifier.padding(top = 6.dp)) { lieferant = it }
                    val kosten = (zahlLesen(menge) ?: 0.0) * (zahlLesen(preis) ?: 0.0)
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp).clip(RUND_KLEIN).background(p.panelAlt).padding(10.dp)) {
                        Mono("Kosten " + eur(kosten), p.text, 13.sp, fett = true)
                    }
                    FormKnoepfe("Material speichern", ::speichern) { st.materialMaske = null }
                }
            }
        }
    }
}
