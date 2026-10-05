package de.gun.baustellen.reloaded.ui.seiten

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.gun.baustellen.reloaded.daten.AppDaten
import de.gun.baustellen.reloaded.daten.Speicher
import de.gun.baustellen.reloaded.logik.WOCHENTAGE_LANG
import de.gun.baustellen.reloaded.logik.alsIso
import de.gun.baustellen.reloaded.logik.auftraegeAnTag
import de.gun.baustellen.reloaded.logik.kalenderwoche
import de.gun.baustellen.reloaded.logik.kundeName
import de.gun.baustellen.reloaded.logik.kurzDE
import de.gun.baustellen.reloaded.logik.netto
import de.gun.baustellen.reloaded.logik.statusFarbe
import de.gun.baustellen.reloaded.logik.wochenStart
import de.gun.baustellen.reloaded.logik.zahl
import de.gun.baustellen.reloaded.netz.Wetter
import de.gun.baustellen.reloaded.netz.WetterDienst
import de.gun.baustellen.reloaded.netz.fensterWerte
import de.gun.baustellen.reloaded.ui.Feld
import de.gun.baustellen.reloaded.ui.Fliesstext
import de.gun.baustellen.reloaded.ui.Karte
import de.gun.baustellen.reloaded.ui.Kennzahl
import de.gun.baustellen.reloaded.ui.LocalPalette
import de.gun.baustellen.reloaded.ui.LocalSteuerung
import de.gun.baustellen.reloaded.ui.Mono
import de.gun.baustellen.reloaded.ui.Punkt
import de.gun.baustellen.reloaded.ui.RUND_KLEIN
import de.gun.baustellen.reloaded.ui.Reiter
import de.gun.baustellen.reloaded.ui.Symbol
import de.gun.baustellen.reloaded.ui.Zeile
import java.time.LocalDate

@Composable
fun WocheSeite() {
    val d = aktuelleDaten()
    // Wetter einmal für Wochenansicht und Wetterkarte laden
    var wetter by remember { mutableStateOf<Wetter?>(null) }
    var fehler by remember { mutableStateOf<String?>(null) }
    var laedt by remember { mutableStateOf(false) }
    var neu by remember { mutableIntStateOf(0) }
    LaunchedEffect(d.wetter.ort, neu) {
        laedt = true; fehler = null
        try { wetter = WetterDienst.laden(d.wetter.ort, neu > 0) } catch (e: Exception) { fehler = e.message ?: "Nicht erreichbar" }
        laedt = false
    }
    SeitenListe {
        item { Auftragslage(d) }
        item { WochenKarte(d, wetter) }
        item { WetterKarte(wetter, laedt, fehler) { neu++ } }
        item {
            Fliesstext(
                "Baustellen Pilot · Alle Daten liegen auf diesem Gerät. Sicherung über Menü → Sicherung.",
                LocalPalette.current.textFaint, 11.sp, Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun Auftragslage(d: AppDaten) {
    val p = LocalPalette.current
    val st = LocalSteuerung.current
    val l = d.auftraege.eintraege
    val offen = l.filter { it.status != "abgeschlossen" }.sumOf { it.netto() }
    fun zu(filter: String) { st.auftragFilter = filter; st.reiter = Reiter.AUFTRAEGE }
    Karte("Auftragslage", "01") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Kennzahl(l.count { it.status == "ausstehend" }.toString(), "Ausstehend", Color(statusFarbe("ausstehend")), Modifier.weight(1f)) { zu("ausstehend") }
            Kennzahl(l.count { it.status == "laufend" }.toString(), "Laufend", Color(statusFarbe("laufend")), Modifier.weight(1f)) { zu("laufend") }
        }
        Spacer(Modifier.padding(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Kennzahl(l.count { it.status == "abgeschlossen" }.toString(), "Abgeschlossen", Color(statusFarbe("abgeschlossen")), Modifier.weight(1f)) { zu("abgeschlossen") }
            Kennzahl(zahl(offen, 0) + " €", "Offen netto", p.text, Modifier.weight(1f)) { zu("alle") }
        }
    }
}

@Composable
private fun WochenKarte(d: AppDaten, wetter: Wetter?) {
    val p = LocalPalette.current
    val st = LocalSteuerung.current
    val start = wochenStart(st.wochenAnker)
    val ende = start.plusDays(6)
    val heute = LocalDate.now()
    Karte("Woche", "02", aktion = {
        Symbol("‹", p.text) { st.wochenAnker = st.wochenAnker.minusWeeks(1) }
        Symbol("◎", p.akzent) { st.wochenAnker = LocalDate.now() }
        Symbol("›", p.text) { st.wochenAnker = st.wochenAnker.plusWeeks(1) }
    }) {
        Mono("KW ${kalenderwoche(start)} · ${start.kurzDE()}–${ende.kurzDE()}", p.textDim, 12.sp)
        for (i in 0..6) {
            val tag = start.plusDays(i.toLong())
            WochenTag(d, tag, tag == heute, wetter, i)
        }
    }
}

@Composable
private fun WochenTag(d: AppDaten, tag: LocalDate, istHeute: Boolean, wetter: Wetter?, index: Int) {
    val p = LocalPalette.current
    val st = LocalSteuerung.current
    val iso = tag.alsIso()
    val auftraege = auftraegeAnTag(d, tag)
    val tw = wetter?.let { fensterWerte(it.stunden, tag) }
    Column(
        Modifier.fillMaxWidth().padding(top = 10.dp).clip(RUND_KLEIN)
            .background(if (istHeute) p.akzentDim else p.panelAlt)
            .border(1.dp, if (istHeute) p.akzent else p.randLeise, RUND_KLEIN)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Punkt(WOCHENTAGE_LANG[index].uppercase(), 14.sp, if (index >= 5) p.textDim else p.text)
            Spacer(Modifier.width(8.dp))
            Mono(tag.kurzDE() + if (istHeute) " · heute" else "", if (istHeute) p.akzent else p.textFaint, 11.sp, fett = istHeute)
            Spacer(Modifier.weight(1f))
            if (tw != null) {
                Fliesstext(WetterDienst.zeichen(tw.code), groesse = 15.sp)
                Spacer(Modifier.width(4.dp))
                Mono((tw.min?.let { "$it°" } ?: "—") + "–" + (tw.max?.let { "$it°" } ?: "—"), p.text, 11.sp, fett = true)
                if (tw.regen.isNotBlank()) {
                    Spacer(Modifier.width(6.dp))
                    Mono(tw.regen, p.neutral, 10.sp)
                }
            }
        }
        if (auftraege.isEmpty()) Mono("Kein Auftrag", p.textFaint, 11.sp, Modifier.padding(top = 4.dp))
        auftraege.forEach { a ->
            Zeile(Color(statusFarbe(a.status)), onClick = { st.auftragOeffnen(a.id) }) {
                Fliesstext(a.titel, fett = true, groesse = 13.sp, zeilen = 1)
                val zusatz = listOfNotNull(
                    d.kundeName(a.kundeId).takeIf { it.isNotBlank() },
                    if (!a.ganztags) "${a.zeitVon}–${a.zeitBis}" else null,
                    a.ort.takeIf { it.isNotBlank() },
                ).joinToString(" · ")
                if (zusatz.isNotBlank()) Mono(zusatz, p.textDim, 11.sp)
            }
        }
        var notiz by remember(iso) { mutableStateOf(d.tagesnotizen[iso] ?: "") }
        Feld(notiz, { v ->
            notiz = v
            Speicher.aendern { dd ->
                dd.copy(tagesnotizen = if (v.isBlank()) dd.tagesnotizen - iso else dd.tagesnotizen + (iso to v))
            }
        }, "Notiz", platzhalter = "Notiz für diesen Tag …", mehrzeilig = true)
    }
}
