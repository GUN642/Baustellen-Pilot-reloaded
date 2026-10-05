package de.gun.baustellen.reloaded.ui.seiten

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.gun.baustellen.reloaded.daten.Speicher
import de.gun.baustellen.reloaded.daten.WetterOrt
import de.gun.baustellen.reloaded.geraet.Standort
import de.gun.baustellen.reloaded.logik.WOCHENTAGE
import de.gun.baustellen.reloaded.logik.auftraegeAnTag
import de.gun.baustellen.reloaded.logik.zwei
import de.gun.baustellen.reloaded.netz.Wetter
import de.gun.baustellen.reloaded.netz.WetterDienst
import de.gun.baustellen.reloaded.netz.arbeitsStunden
import de.gun.baustellen.reloaded.netz.baustellenWarnungen
import de.gun.baustellen.reloaded.ui.Abstand
import de.gun.baustellen.reloaded.ui.Feld
import de.gun.baustellen.reloaded.ui.Fliesstext
import de.gun.baustellen.reloaded.ui.Hinweis
import de.gun.baustellen.reloaded.ui.Karte
import de.gun.baustellen.reloaded.ui.Knopf
import de.gun.baustellen.reloaded.ui.Knopfreihe
import de.gun.baustellen.reloaded.ui.LocalPalette
import de.gun.baustellen.reloaded.ui.Mono
import de.gun.baustellen.reloaded.ui.Punkt
import de.gun.baustellen.reloaded.ui.RUND_KLEIN
import de.gun.baustellen.reloaded.ui.Symbol
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

@Composable
fun WetterKarte(wetter: Wetter?, laedt: Boolean, fehler: String?, neuLaden: () -> Unit) {
    var einstellen by remember { mutableStateOf(false) }
    Karte("Wetter auf der Baustelle", "03", aktion = {
        Symbol("↻") { neuLaden() }
        Symbol("⚙") { einstellen = !einstellen }
    }) {
        if (einstellen) {
            WetterEinstellungen { einstellen = false }
            Abstand()
        }
        when {
            laedt && wetter == null -> Hinweis("Wetter wird geladen …")
            fehler != null && wetter == null -> {
                Hinweis("Das Wetter konnte nicht geladen werden: $fehler")
                Knopf("Erneut versuchen", klein = true) { neuLaden() }
            }
            wetter != null -> WetterAnzeige(wetter)
        }
    }
}

@Composable
private fun WetterAnzeige(w: Wetter) {
    val p = LocalPalette.current
    val st = de.gun.baustellen.reloaded.ui.LocalSteuerung.current
    val d = aktuelleDaten()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Fliesstext(WetterDienst.zeichen(w.code), groesse = 40.sp)
        Spacer(Modifier.width(10.dp))
        Punkt((w.grad?.let { Math.round(it).toString() } ?: "—") + "°", 46.sp)
        Spacer(Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Fliesstext(w.ort, fett = true, zeilen = 1)
            Fliesstext(WetterDienst.text(w.code), p.textDim, 13.sp)
            Mono(listOfNotNull(
                w.gefuehlt?.let { "gefühlt ${Math.round(it)}°" }, w.wind?.let { "Wind ${Math.round(it)} km/h" }, w.feuchte?.let { "${Math.round(it)} %" }
            ).joinToString(" · "), p.textFaint, 11.sp)
        }
    }
    Abstand(10.dp)
    // Nächste zwölf Stunden
    val jetzt = LocalDateTime.now().withMinute(0).withSecond(0).withNano(0)
    val start = w.stunden.indexOfFirst { !it.zeit.isBefore(jetzt) }.coerceAtLeast(0)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        w.stunden.drop(start).take(12).forEachIndexed { i, s ->
            Column(
                Modifier.clip(RUND_KLEIN).background(if (i == 0) p.akzentDim else p.panelAlt).padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Mono(zwei(s.zeit.hour), p.textFaint, 10.sp)
                Fliesstext(WetterDienst.zeichen(s.code), groesse = 18.sp)
                Mono((s.grad?.let { Math.round(it).toString() } ?: "—") + "°", p.text, 12.sp, fett = true)
                Mono(if ((s.regenWkt ?: 0) > 0) "${s.regenWkt}%" else if ((s.regenMm ?: 0.0) > 0) String.format("%.1f", s.regenMm) else " ", p.neutral, 9.sp)
            }
        }
    }
    Abstand(12.dp)
    // Sieben Tage, je Tag drei Zeitpunkte der Arbeitszeit
    val heute = LocalDate.now()
    w.tage.forEach { t ->
        val anzahl = auftraegeAnTag(d, t.datum).size
        Column(
            Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RUND_KLEIN).background(p.panelAlt)
                .clickable { st.wochenAnker = t.datum }
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Mono(
                    (if (t.datum == heute) "Heute" else WOCHENTAGE[t.datum.dayOfWeek.value - 1] + " " + zwei(t.datum.dayOfMonth) + ".") +
                        if (anzahl > 0) " · $anzahl" else "",
                    p.text, 12.sp, Modifier.width(84.dp), fett = t.datum == heute,
                )
                Fliesstext(WetterDienst.zeichen(t.code), groesse = 17.sp)
                Spacer(Modifier.width(6.dp))
                Mono(
                    when {
                        (t.regenWkt ?: 0) > 0 -> "${t.regenWkt} %"
                        (t.regenMm ?: 0.0) > 0.0 -> String.format("%.1f mm", t.regenMm)
                        else -> ""
                    }, p.neutral, 11.sp, Modifier.weight(1f)
                )
                Mono((t.max?.let { Math.round(it).toString() } ?: "—") + "° / " + (t.min?.let { Math.round(it).toString() } ?: "—") + "°", p.text, 12.sp, fett = true)
            }
            val stunden = arbeitsStunden(w.stunden, t.datum)
            if (stunden.isNotEmpty()) Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                stunden.forEach { s ->
                    Mono(
                        "${zwei(s.stunde)} Uhr " + WetterDienst.zeichen(s.code) + " " + (s.grad?.let { "$it°" } ?: "—") +
                            if ((s.regenWkt ?: 0) > 0) " · ${s.regenWkt}%" else "",
                        p.textDim, 10.5.sp,
                    )
                }
            }
        }
    }
    baustellenWarnungen(w.tage).forEach { warnung ->
        Row(
            Modifier.fillMaxWidth().padding(top = 6.dp).clip(RUND_KLEIN).background(p.warnDim).padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) { Fliesstext("⚠ $warnung", p.warn, 13.sp) }
    }
    w.tage.firstOrNull()?.let { t ->
        val auf = t.aufgang?.let { zwei(it.hour) + ":" + zwei(it.minute) }
        val unter = t.untergang?.let { zwei(it.hour) + ":" + zwei(it.minute) }
        if (auf != null && unter != null) Mono("↑ $auf   ↓ $unter", p.textDim, 12.sp, Modifier.padding(top = 8.dp))
    }
    Hinweis("Modell ${w.modelle} · Daten: Open-Meteo")
}

@Composable
private fun WetterEinstellungen(fertig: () -> Unit) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val ort = aktuelleDaten().wetter.ort
    var suche by remember { mutableStateOf("") }
    var treffer by remember { mutableStateOf<List<Pair<String, WetterOrt>>>(emptyList()) }
    var status by remember { mutableStateOf("") }

    fun ortSetzen(o: WetterOrt) {
        Speicher.aendern { it.copy(wetter = it.wetter.copy(ort = o)) }
        treffer = emptyList(); suche = ""; fertig()
    }

    val standortRecht = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { erg ->
        if (erg.values.any { it }) scope.launch {
            status = "Standort wird bestimmt …"
            val pos = Standort.holen(ctx)
            if (pos == null) { status = "Standort nicht verfügbar."; return@launch }
            val name = WetterDienst.ortsname(pos.first, pos.second)
                ?: ("Standort " + String.format("%.3f, %.3f", pos.first, pos.second))
            ortSetzen(WetterOrt(name, pos.first, pos.second))
        } else status = "Die Standortberechtigung wurde nicht erteilt."
    }

    Column(Modifier.fillMaxWidth().clip(RUND_KLEIN).background(p.panelAlt).padding(12.dp)) {
        Hinweis("Aktuell: ${ort.name} (${String.format("%.3f", ort.breite)}, ${String.format("%.3f", ort.laenge)})")
        Feld(suche, { suche = it }, "Ort suchen", platzhalter = "z. B. Ulm")
        Knopfreihe {
            Knopf("Suchen", klein = true) {
                if (suche.isBlank()) return@Knopf
                status = "Suche läuft …"
                scope.launch {
                    treffer = try { WetterDienst.ortSuchen(suche.trim()) } catch (e: Exception) { status = "Die Suche ist nicht erreichbar."; emptyList() }
                    if (treffer.isEmpty()) { if (status == "Suche läuft …") status = "Kein Ort gefunden." } else status = ""
                }
            }
            Knopf("⌖ Aktueller Standort", klein = true) {
                standortRecht.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
            }
        }
        if (status.isNotEmpty()) Hinweis(status)
        treffer.forEach { (name, o) ->
            Fliesstext(name, modifier = Modifier.fillMaxWidth().clickable { ortSetzen(o) }.padding(vertical = 8.dp))
        }
        Hinweis("In der Detailansicht eines Auftrags setzt „Wetter für diesen Ort“ den Wetterort auf die Baustelle.")
    }
}

/** Wetterort auf die Baustellenanschrift setzen (Ort bzw. PLZ). */
suspend fun baustellenOrtSetzen(begriff: String): String? {
    val t = try { WetterDienst.ortSuchen(begriff).firstOrNull() } catch (e: Exception) { null } ?: return null
    Speicher.aendern { it.copy(wetter = it.wetter.copy(ort = t.second)) }
    return t.second.name
}
