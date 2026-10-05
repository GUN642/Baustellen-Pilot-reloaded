package de.gun.baustellen.reloaded.ui.seiten

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.gun.baustellen.reloaded.daten.STATUS
import de.gun.baustellen.reloaded.daten.Speicher
import de.gun.baustellen.reloaded.logik.MONATE
import de.gun.baustellen.reloaded.logik.eur
import de.gun.baustellen.reloaded.logik.jahreListe
import de.gun.baustellen.reloaded.logik.statistik
import de.gun.baustellen.reloaded.logik.statistikText
import de.gun.baustellen.reloaded.logik.statusFarbe
import de.gun.baustellen.reloaded.logik.zahl
import de.gun.baustellen.reloaded.ui.Auswahl
import de.gun.baustellen.reloaded.ui.Etikett
import de.gun.baustellen.reloaded.ui.Karte
import de.gun.baustellen.reloaded.ui.Kennzahl
import de.gun.baustellen.reloaded.ui.Knopf
import de.gun.baustellen.reloaded.ui.Leer
import de.gun.baustellen.reloaded.ui.LocalPalette
import de.gun.baustellen.reloaded.ui.LocalSteuerung
import de.gun.baustellen.reloaded.ui.Mono
import de.gun.baustellen.reloaded.ui.Segmente
import java.time.LocalDate

/** Zeile mit Beschriftung, Balken und Wert. */
@Composable
private fun BalkenZeile(label: String, anteil: Float, wert: String, farbe: Color, labelBreite: Int = 44) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Mono(label, p.text, 11.sp, Modifier.width(labelBreite.dp))
        Box(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(p.randLeise)) {
            Box(Modifier.fillMaxWidth(anteil.coerceIn(0f, 1f)).fillMaxHeight().background(farbe))
        }
        Spacer(Modifier.width(8.dp))
        Mono(wert, p.textDim, 11.sp)
    }
}

@Composable
fun StatistikSeite() {
    val p = LocalPalette.current
    val st = LocalSteuerung.current
    val ctx = LocalContext.current
    val d = aktuelleDaten()
    val bezug = d.statBezug
    val jahre = jahreListe(d, bezug)
    var jahr by remember { mutableIntStateOf(LocalDate.now().year) }
    if (jahr !in jahre) jahr = jahre.first()
    val s = remember(d, jahr, bezug) { statistik(d, jahr, bezug) }

    SeitenListe {
        item {
            Karte("Jahresübersicht", "01", aktion = {
                Auswahl("Jahr", jahre.map { it.toString() to it.toString() }, jahr.toString(), Modifier.width(110.dp)) { jahr = it.toInt() }
            }) {
                Segmente(listOf("bis" to "nach Auftragsende", "von" to "nach Auftragsbeginn"), bezug) { b ->
                    Speicher.aendern { it.copy(statBezug = b) }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Kennzahl(s.anzahl.toString(), "Aufträge", p.text, Modifier.weight(1f))
                    Kennzahl(zahl(s.netto, 0) + " €", "Einnahmen netto", p.akzent, Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Kennzahl(zahl(s.brutto, 0) + " €", "Einnahmen brutto", p.text, Modifier.weight(1f))
                    Kennzahl(zahl(s.material, 0) + " €", "Materialkosten", p.rot, Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Kennzahl(zahl(s.netto - s.material, 0) + " €", "Netto nach Material", p.gruen, Modifier.weight(1f))
                    Kennzahl(zahl(s.schnitt, 0) + " €", "Ø je Auftrag", p.text, Modifier.weight(1f))
                }
            }
        }
        item {
            Karte("Einnahmen netto je Monat", "02") {
                val max = (s.monate.maxOrNull() ?: 0.0).coerceAtLeast(1.0)
                s.monate.forEachIndexed { i, w ->
                    BalkenZeile(MONATE[i].take(3), (w / max).toFloat(), zahl(w, 0) + " € · " + s.monateAnzahl[i], p.akzent)
                }
                Spacer(Modifier.height(8.dp))
                STATUS.forEach { (id, name) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Farbpunkt(Color(statusFarbe(id)))
                        Spacer(Modifier.width(8.dp))
                        Etikett(name, modifier = Modifier.weight(1f))
                        Mono(eur(s.nachStatus[id] ?: 0.0), p.text, 12.sp, fett = true)
                    }
                }
            }
        }
        item {
            Karte("Jahresvergleich", "03", aktion = {
                Knopf("⧉ Kopieren", klein = true) {
                    inZwischenablage(ctx, statistikText(d, s, bezug))
                    st.kurz("Auswertung in die Zwischenablage kopiert")
                }
            }) {
                if (s.proJahr.isEmpty()) Leer("Noch keine Aufträge erfasst.")
                val max = (s.proJahr.maxOfOrNull { it.second.netto } ?: 0.0).coerceAtLeast(1.0)
                s.proJahr.forEach { (j, w) ->
                    BalkenZeile(j.toString(), (w.netto / max).toFloat(), zahl(w.netto, 0) + " € · " + w.anzahl, if (j == jahr) p.akzent else p.neutral)
                }
            }
        }
        item {
            Karte("Kunden im Jahr", "04") {
                if (s.proKunde.isEmpty()) Leer("Für $jahr liegen keine Aufträge vor.")
                val max = (s.proKunde.maxOfOrNull { it.netto } ?: 0.0).coerceAtLeast(1.0)
                s.proKunde.forEach { k ->
                    Etikett(k.name, p.text, Modifier.padding(top = 4.dp))
                    BalkenZeile("", (k.netto / max).toFloat(), zahl(k.netto, 0) + " € · " + k.anzahl, p.akzent, labelBreite = 0)
                }
            }
        }
    }
}
