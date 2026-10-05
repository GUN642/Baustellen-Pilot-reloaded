package de.gun.baustellen.reloaded.ui.seiten

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.gun.baustellen.reloaded.daten.AppDaten
import de.gun.baustellen.reloaded.daten.Auftrag
import de.gun.baustellen.reloaded.daten.STATUS
import de.gun.baustellen.reloaded.logik.anschrift
import de.gun.baustellen.reloaded.logik.eur
import de.gun.baustellen.reloaded.logik.kundeName
import de.gun.baustellen.reloaded.logik.materialKosten
import de.gun.baustellen.reloaded.logik.netto
import de.gun.baustellen.reloaded.logik.statusFarbe
import de.gun.baustellen.reloaded.logik.statusText
import de.gun.baustellen.reloaded.logik.zeitraumText
import de.gun.baustellen.reloaded.ui.Etikett
import de.gun.baustellen.reloaded.ui.Fliesstext
import de.gun.baustellen.reloaded.ui.Karte
import de.gun.baustellen.reloaded.ui.Leer
import de.gun.baustellen.reloaded.ui.LocalPalette
import de.gun.baustellen.reloaded.ui.LocalSteuerung
import de.gun.baustellen.reloaded.ui.AuftragMaskeStart
import de.gun.baustellen.reloaded.ui.Mono
import de.gun.baustellen.reloaded.ui.Punkt
import de.gun.baustellen.reloaded.ui.Schrift
import de.gun.baustellen.reloaded.ui.Segmente
import de.gun.baustellen.reloaded.ui.Zeile
import de.gun.baustellen.reloaded.ui.textAuf

@Composable
fun AuftraegeSeite() {
    val p = LocalPalette.current
    val st = LocalSteuerung.current
    val d = aktuelleDaten()
    val filter = st.auftragFilter
    val liste = d.auftraege.eintraege
        .filter { filter == "alle" || it.status == filter }
        .sortedWith(compareBy({ it.von.ifBlank { "9999" } }, { it.titel.lowercase() }))
    val summen = STATUS.keys.associateWith { s -> d.auftraege.eintraege.filter { it.status == s }.sumOf { it.netto() } }

    Box(Modifier.fillMaxSize()) {
        SeitenListe {
            item {
                Karte("Aufträge", "01", aktion = { Mono("${d.auftraege.eintraege.size}", p.textDim) }) {
                    Segmente(listOf("alle" to "Alle") + STATUS.map { it.key to it.value }, filter) { st.auftragFilter = it }
                    Spacer(Modifier.padding(5.dp))
                    STATUS.forEach { (id, name) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Farbpunkt(Color(statusFarbe(id)))
                            Spacer(Modifier.width(8.dp))
                            Etikett(name, modifier = Modifier.weight(1f))
                            Mono(eur(summen[id] ?: 0.0), p.text, 12.sp, fett = true)
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Spacer(Modifier.width(16.dp))
                        Etikett("Gesamt netto", p.text, Modifier.weight(1f))
                        Mono(eur(summen.values.sum()), p.akzent, 13.sp, fett = true)
                    }
                }
            }
            if (liste.isEmpty()) item { Leer("Noch kein Auftrag in dieser Ansicht. Unten rechts auf + tippen.") }
            items(liste, key = { it.id }) { a -> AuftragKarte(d, a) { st.auftragOeffnen(a.id) } }
        }
        FloatingActionButton(
            onClick = { st.auftragMaske = AuftragMaskeStart() },
            containerColor = p.akzent, contentColor = textAuf(p.akzent), shape = CircleShape,
            modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(16.dp).size(52.dp),
        ) { Text("+", fontSize = 24.sp, fontFamily = Schrift.mono) }
    }
}

@Composable
fun AuftragKarte(d: AppDaten, a: Auftrag, onClick: () -> Unit) {
    val p = LocalPalette.current
    Zeile(Color(statusFarbe(a.status)), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Punkt(a.titel.ifBlank { "Ohne Bezeichnung" }.uppercase(), 15.sp, modifier = Modifier.weight(1f), zeilen = 2)
            Spacer(Modifier.width(8.dp))
            StatusMarke(a.status, a.statusText())
        }
        val kunde = d.kundeName(a.kundeId)
        if (kunde.isNotBlank()) Fliesstext(kunde, fett = true, groesse = 13.sp)
        Mono(zeitraumText(a), p.textDim, 11.sp)
        val ort = a.anschrift()
        if (ort.isNotBlank()) Fliesstext(ort, p.textDim, 12.sp, zeilen = 1)
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Mono(eur(a.netto()), p.akzent, 13.sp, fett = true)
            Spacer(Modifier.weight(1f))
            val mk = a.materialKosten()
            val teile = listOfNotNull(
                if (a.nummer.isNotBlank()) "Nr. ${a.nummer}" else null,
                if (mk != 0.0) "Mat. ${eur(mk)}" else null,
                if (a.dateien.isNotEmpty()) "${a.dateien.size} Dok." else null,
                if (a.fotos.isNotEmpty()) "${a.fotos.size} Fotos" else null,
                if (a.regieberichte.isNotEmpty()) "${a.regieberichte.size} RB" else null,
            )
            if (teile.isNotEmpty()) Mono(teile.joinToString(" · "), p.textFaint, 10.5.sp)
        }
    }
}
