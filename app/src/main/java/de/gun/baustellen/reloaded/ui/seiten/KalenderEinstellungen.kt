package de.gun.baustellen.reloaded.ui.seiten

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.gun.baustellen.reloaded.Aktualisierung
import de.gun.baustellen.reloaded.daten.Speicher
import de.gun.baustellen.reloaded.geraet.GeraeteKalender
import de.gun.baustellen.reloaded.geraet.KalenderAbgleich
import de.gun.baustellen.reloaded.logik.Ics
import de.gun.baustellen.reloaded.logik.hexAusFarbe
import de.gun.baustellen.reloaded.logik.kalenderFarbe
import de.gun.baustellen.reloaded.logik.kalenderSichtbar
import de.gun.baustellen.reloaded.logik.kundeName
import de.gun.baustellen.reloaded.ui.Abstand
import de.gun.baustellen.reloaded.ui.Auswahl
import de.gun.baustellen.reloaded.ui.FarbReihe
import de.gun.baustellen.reloaded.ui.Fliesstext
import de.gun.baustellen.reloaded.ui.Hinweis
import de.gun.baustellen.reloaded.ui.Karte
import de.gun.baustellen.reloaded.ui.Knopf
import de.gun.baustellen.reloaded.ui.KnopfArt
import de.gun.baustellen.reloaded.ui.Knopfreihe
import de.gun.baustellen.reloaded.ui.Leer
import de.gun.baustellen.reloaded.ui.LocalPalette
import de.gun.baustellen.reloaded.ui.LocalSteuerung
import de.gun.baustellen.reloaded.ui.Mono
import de.gun.baustellen.reloaded.ui.PALETTE_KALENDER
import de.gun.baustellen.reloaded.ui.Pille
import de.gun.baustellen.reloaded.ui.Zeile
import kotlinx.coroutines.launch

@Composable
fun KalenderEinstellungen() {
    val p = LocalPalette.current
    val st = LocalSteuerung.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val d = aktuelleDaten()
    val kalender by GeraeteKalender.kalender.collectAsState()
    val stand by GeraeteKalender.stand.collectAsState()
    var status by remember { mutableStateOf("") }
    var farbeOffen by remember { mutableStateOf<String?>(null) }
    val rechte = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        scope.launch {
            val ok = Aktualisierung.geraetLadenJetzt(ctx)
            status = if (ok) "${GeraeteKalender.kalender.value.size} Kalender gefunden." else "Kalenderzugriff nicht erteilt."
        }
    }
    val darf = GeraeteKalender.darfLesen(ctx)

    Column {
        EbenenKopf("Kalender-Einstellungen", { st.kalenderEinstellungen = false })
        SeitenListe {
            item {
                Karte("Gerätekalender", "01") {
                    Fliesstext(
                        when {
                            !darf -> "Nicht verbunden – Kalenderzugriff erlauben."
                            stand == 0L -> "Wird eingelesen …"
                            else -> "${kalender.size} Kalender gefunden."
                        }, p.textDim, 13.sp,
                    )
                    if (status.isNotBlank()) Hinweis(status)
                    Knopfreihe {
                        Knopf(if (darf) "↻ Kalender einlesen" else "Kalenderzugriff erlauben", art = if (darf) KnopfArt.NORMAL else KnopfArt.PRIMAER, klein = true) {
                            rechte.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
                        }
                        Knopf("↓ Alle Aufträge als .ics", klein = true) {
                            val liste = d.auftraege.eintraege.filter { it.von.isNotBlank() }
                            if (liste.isEmpty()) st.kurz("Kein Auftrag vorhanden")
                            else ausgabeTeilen(ctx, "Baustellen_Auftraege.ics", "text/calendar") { aus ->
                                aus.write(Ics.dokument(liste.map { it to d.kundeName(it.kundeId) }).toByteArray(Charsets.UTF_8))
                            }
                        }
                    }
                    Hinweis("Das gewünschte Konto (Google, Outlook …) muss in den Android-Einstellungen unter „Konten“ eingerichtet sein – dann überträgt Android die Termine selbst in die Cloud.")
                }
            }
            item {
                Karte("Auftragszeiträume", "02") {
                    val schreibbar = kalender.filter { it.schreibbar }.sortedBy { it.titel.lowercase() }
                    Auswahl(
                        "Zielkalender für Auftragszeiträume",
                        listOf("" to "— kein Gerätekalender —") + schreibbar.map { it.id to (it.titel + if (it.kontoName.isNotBlank()) " · " + it.kontoName else "") },
                        d.einstellungen.zielKalenderId,
                    ) { id ->
                        Speicher.aendern { it.copy(einstellungen = it.einstellungen.copy(zielKalenderId = id)) }
                        st.kurz(if (id.isBlank()) "Zielkalender entfernt" else "Zielkalender gesetzt")
                    }
                    Hinweis("Beim Speichern eines Auftrags wird sein Zeitraum hier eingetragen – mit Kunde, Auftragsnummer, Summe und Notiz in der Beschreibung und der Baustelle als Ort.")
                    Knopfreihe {
                        Pille("Automatisch übertragen", d.einstellungen.autoKalender) {
                            Speicher.aendern { it.copy(einstellungen = it.einstellungen.copy(autoKalender = !it.einstellungen.autoKalender)) }
                        }
                        Knopf("Alle Aufträge jetzt übertragen", klein = true) {
                            scope.launch {
                                var n = 0
                                var fehler: String? = null
                                Speicher.aktuell.auftraege.eintraege.filter { it.von.isNotBlank() }.forEach { a ->
                                    try { KalenderAbgleich.abgleichen(ctx, a); n++ } catch (e: Exception) { if (fehler == null) fehler = e.message }
                                }
                                if (fehler != null && n == 0) st.melden("Kalender", fehler ?: "Fehler") else st.kurz("$n Aufträge übertragen")
                            }
                        }
                    }
                    Hinweis("Änderungen am Zeitraum werden im Zielkalender nachgeführt, Löschen eines Auftrags entfernt den Termin dort.")
                }
            }
            item {
                Karte("Anzuzeigende Kalender", "03") {
                    if (kalender.isEmpty()) Leer("Noch nicht eingelesen – oben auf „Kalenderzugriff erlauben“ tippen.")
                    kalender.sortedWith(compareBy({ it.dienst }, { it.titel.lowercase() })).forEach { k ->
                        val farbe = kalenderFarbe(d, k)
                        val sichtbar = kalenderSichtbar(d, k.id)
                        val eigen = d.einstellungen.kalenderFarben.containsKey(k.id)
                        Zeile(Color(farbe), onClick = { farbeOffen = if (farbeOffen == k.id) null else k.id }, aktionen = {
                            Pille(if (sichtbar) "an" else "aus", sichtbar) {
                                Speicher.aendern {
                                    val v = it.einstellungen.versteckteKalender
                                    it.copy(einstellungen = it.einstellungen.copy(versteckteKalender = if (k.id in v) v - k.id else v + k.id))
                                }
                            }
                            Spacer(Modifier.width(6.dp))
                        }) {
                            Fliesstext(k.titel, fett = true, groesse = 13.sp, zeilen = 1)
                            Mono(listOf(k.dienst, k.kontoName.ifBlank { "Konto unbekannt" }).joinToString(" · ") +
                                (if (eigen) " · eigene Farbe" else "") + (if (k.id == d.einstellungen.zielKalenderId) " · Ziel" else ""), p.textDim, 10.5.sp)
                            if (farbeOffen == k.id) {
                                FarbReihe(PALETTE_KALENDER, hexAusFarbe(farbe)) { hex ->
                                    Speicher.aendern { it.copy(einstellungen = it.einstellungen.copy(kalenderFarben = it.einstellungen.kalenderFarben + (k.id to hex))) }
                                }
                                if (eigen) Row(verticalAlignment = Alignment.CenterVertically) {
                                    Knopf("↺ Gerätefarbe", klein = true, art = KnopfArt.LEISE) {
                                        Speicher.aendern { it.copy(einstellungen = it.einstellungen.copy(kalenderFarben = it.einstellungen.kalenderFarben - k.id)) }
                                    }
                                }
                                Abstand(4.dp)
                            }
                        }
                    }
                    Hinweis("Antippen zeigt die Farbwahl. Termine aus abgeschalteten Kalendern erscheinen nicht im Raster. Aufträge erscheinen in ihrer Statusfarbe.")
                }
            }
        }
    }
}
