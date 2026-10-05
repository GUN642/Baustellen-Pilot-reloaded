package de.gun.baustellen.reloaded.ui.seiten

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.gun.baustellen.reloaded.BuildConfig
import de.gun.baustellen.reloaded.daten.AppDaten
import de.gun.baustellen.reloaded.daten.Auftraege
import de.gun.baustellen.reloaded.daten.Dateien
import de.gun.baustellen.reloaded.daten.Firma
import de.gun.baustellen.reloaded.daten.Kunden
import de.gun.baustellen.reloaded.daten.Sicherung
import de.gun.baustellen.reloaded.daten.SicherungsFormat
import de.gun.baustellen.reloaded.daten.Speicher
import de.gun.baustellen.reloaded.daten.groesseText
import de.gun.baustellen.reloaded.logik.de
import de.gun.baustellen.reloaded.logik.heuteIso
import de.gun.baustellen.reloaded.logik.parseIso
import de.gun.baustellen.reloaded.logik.zahlFeld
import de.gun.baustellen.reloaded.logik.zahlLesen
import de.gun.baustellen.reloaded.netz.UpdateDienst
import de.gun.baustellen.reloaded.netz.UpdateLader
import de.gun.baustellen.reloaded.ui.AKZENTE
import de.gun.baustellen.reloaded.ui.Abstand
import de.gun.baustellen.reloaded.ui.Etikett
import de.gun.baustellen.reloaded.ui.Feld
import de.gun.baustellen.reloaded.ui.Fliesstext
import de.gun.baustellen.reloaded.ui.Frage
import de.gun.baustellen.reloaded.ui.Hinweis
import de.gun.baustellen.reloaded.ui.Karte
import de.gun.baustellen.reloaded.ui.Knopf
import de.gun.baustellen.reloaded.ui.KnopfArt
import de.gun.baustellen.reloaded.ui.Knopfreihe
import de.gun.baustellen.reloaded.ui.LocalPalette
import de.gun.baustellen.reloaded.ui.LocalSteuerung
import de.gun.baustellen.reloaded.ui.Mono
import de.gun.baustellen.reloaded.ui.Pille
import de.gun.baustellen.reloaded.ui.PunktRaster
import de.gun.baustellen.reloaded.ui.RUND_KLEIN
import de.gun.baustellen.reloaded.ui.Reiter
import de.gun.baustellen.reloaded.ui.THEMEN
import de.gun.baustellen.reloaded.ui.palette
import de.gun.baustellen.reloaded.ui.titelSchrift
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MenueEbene() {
    val p = LocalPalette.current
    val st = LocalSteuerung.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val d = aktuelleDaten()
    var importFrage by remember { mutableStateOf<SicherungsFormat.Gelesen?>(null) }
    var loeschFrage by remember { mutableStateOf(0) }
    var updateStatus by remember { mutableStateOf("") }
    var laeuft by remember { mutableStateOf(false) }

    fun dateiname() = "BaustellenPilot_" + (if (d.sicherung.mitDateien) "mitDateien_" else "") + "v${BuildConfig.VERSION_NAME}_${LocalDate.now()}.json"
    fun gesichert() = Speicher.aendern { it.copy(sicherung = it.sicherung.copy(letzte = heuteIso())) }

    val speichernUnter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri != null) scope.launch {
            laeuft = true
            try {
                withContext(Dispatchers.IO) {
                    ctx.contentResolver.openOutputStream(uri)?.use { Sicherung.exportieren(ctx, Speicher.aktuell, Speicher.aktuell.sicherung.mitDateien, it) }
                        ?: throw IllegalStateException("Ziel nicht beschreibbar.")
                }
                gesichert(); st.kurz("Sicherung gespeichert")
            } catch (e: Exception) { st.melden("Sicherung", "Die Sicherung konnte nicht erstellt werden.\n\n" + e.message) }
            laeuft = false
        }
    }
    val oeffnen = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            laeuft = true
            try {
                importFrage = withContext(Dispatchers.IO) {
                    ctx.contentResolver.openInputStream(uri)?.use { Sicherung.lesen(ctx, it, Speicher.aktuell) }
                        ?: throw IllegalArgumentException("Die Datei ließ sich nicht öffnen.")
                }
            } catch (e: OutOfMemoryError) {
                st.melden("Einlesen fehlgeschlagen", "Die Datei ist zu groß für den Arbeitsspeicher.")
            } catch (e: Exception) {
                st.melden("Einlesen fehlgeschlagen", e.message ?: "Die Datei scheint keine gültige Sicherung zu sein.")
            }
            laeuft = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        EbenenKopf("Menü", { st.menueOffen = false })
        SeitenListe {
            // ------------------------------------------------ Design
            item {
                Karte("Design", "01") {
                    Etikett("Thema")
                    Column(Modifier.fillMaxWidth()) {
                        THEMEN.chunked(2).forEach { reihe ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                                reihe.forEach { t ->
                                    val vorschau = palette(d.design.copy(thema = t.id), true)
                                    val aktiv = d.design.thema == t.id
                                    Column(
                                        Modifier.weight(1f).clip(RUND_KLEIN).background(vorschau.bg)
                                            .border(if (aktiv) 2.dp else 1.dp, if (aktiv) p.akzent else p.rand, RUND_KLEIN)
                                            .clickable { Speicher.aendern { it.copy(design = it.design.copy(thema = t.id)) } }
                                    ) {
                                        Box(Modifier.fillMaxWidth().height(34.dp)) {
                                            PunktRaster(Modifier.fillMaxSize(), vorschau.randLeise, 7.dp)
                                            Box(Modifier.padding(10.dp).size(10.dp).clip(CircleShape).background(vorschau.akzent))
                                        }
                                        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                                            Text(t.name.uppercase(), color = vorschau.text,
                                                style = TextStyle(fontFamily = p.titelSchrift, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold))
                                            Text(t.beschreibung, color = vorschau.textDim, fontSize = 11.sp)
                                        }
                                    }
                                }
                                if (reihe.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                    Abstand()
                    Etikett("Akzentfarbe")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                        AKZENTE.forEach { a ->
                            val c = if (p.hell) a.hell else a.dunkel
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable {
                                Speicher.aendern { it.copy(design = it.design.copy(akzent = a.id)) }
                            }) {
                                Box(Modifier.size(34.dp).clip(CircleShape).background(c)
                                    .border(2.dp, if (d.design.akzent == a.id) p.text else Color.Transparent, CircleShape))
                                Mono(a.name, if (d.design.akzent == a.id) p.text else p.textFaint, 9.sp)
                            }
                        }
                    }
                    Abstand()
                    Knopfreihe {
                        Pille("Punktschrift", d.design.punktSchrift) { Speicher.aendern { it.copy(design = it.design.copy(punktSchrift = !it.design.punktSchrift)) } }
                        Pille("Punktraster", d.design.punktRaster) { Speicher.aendern { it.copy(design = it.design.copy(punktRaster = !it.design.punktRaster)) } }
                        Pille("Reiterleiste unten", d.design.reiterUnten) { Speicher.aendern { it.copy(design = it.design.copy(reiterUnten = !it.design.reiterUnten)) } }
                    }
                }
            }
            // ------------------------------------------------ Sicherung
            item {
                Karte("Sicherung", "02") {
                    val letzte = parseIso(d.sicherung.letzte)
                    val tage = letzte?.let { ChronoUnit.DAYS.between(it, LocalDate.now()) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Farbpunkt(when {
                            tage == null || tage >= 30 -> p.rot
                            tage >= 7 -> p.warn
                            else -> p.gruen
                        }, 10)
                        Spacer(Modifier.width(8.dp))
                        Fliesstext(
                            if (tage == null) "Noch keine eigene Sicherung erstellt."
                            else "Letzte eigene Sicherung: " + (if (tage <= 0) "heute" else if (tage == 1L) "gestern" else "vor $tage Tagen") + " (${de(d.sicherung.letzte)})",
                            p.textDim, 13.sp,
                        )
                    }
                    Knopfreihe {
                        Pille("Unterlagen und Fotos einschließen", d.sicherung.mitDateien) {
                            Speicher.aendern { it.copy(sicherung = it.sicherung.copy(mitDateien = !it.sicherung.mitDateien)) }
                        }
                    }
                    Knopfreihe {
                        Knopf("↓ Exportieren (Teilen)", art = KnopfArt.PRIMAER, aktiv = !laeuft) {
                            scope.launch {
                                laeuft = true
                                try {
                                    val f = ausgabeDatei(ctx, dateiname())
                                    withContext(Dispatchers.IO) {
                                        f.outputStream().use { Sicherung.exportieren(ctx, Speicher.aktuell, Speicher.aktuell.sicherung.mitDateien, it) }
                                    }
                                    dateiTeilen(ctx, f, "application/json")
                                    gesichert()
                                } catch (e: Exception) { st.melden("Sicherung", "Die Sicherung konnte nicht erstellt werden.\n\n" + e.message) }
                                laeuft = false
                            }
                        }
                        Knopf("↓ Speichern unter …", aktiv = !laeuft) { speichernUnter.launch(dateiname()) }
                        Knopf("↑ Importieren", aktiv = !laeuft) { oeffnen.launch(arrayOf("application/json", "text/*", "application/octet-stream", "*/*")) }
                    }
                    if (laeuft) Hinweis("Bitte warten …")
                    Hinweis("Das Format ist dasselbe wie beim alten Baustellen Pilot: Eine dort mit „Export .json“ oder „Export mit Dateien“ erstellte Datei lässt sich hier einlesen – und umgekehrt.")
                    Hinweis("Beim Teilen lässt sich z. B. Google Drive als Ziel wählen. Zusätzlich sichert Android die Daten selbsttätig ins Google-Konto.")
                }
            }
            // ------------------------------------------------ Firma
            item {
                Karte("Firma", "03") {
                    val f = d.einstellungen.firma
                    fun setzen(neu: (Firma) -> Firma) = Speicher.aendern { it.copy(einstellungen = it.einstellungen.copy(firma = neu(it.einstellungen.firma))) }
                    var name by remember { mutableStateOf(f.name) }
                    var strasse by remember { mutableStateOf(f.strasse) }
                    var plz by remember { mutableStateOf(f.plz) }
                    var ort by remember { mutableStateOf(f.ort) }
                    var telefon by remember { mutableStateOf(f.telefon) }
                    var email by remember { mutableStateOf(f.email) }
                    Feld(name, { name = it; setzen { x -> x.copy(name = it.trim()) } }, "Firmenname")
                    Feld(strasse, { strasse = it; setzen { x -> x.copy(strasse = it.trim()) } }, "Straße + Nr.")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Feld(plz, { plz = it; setzen { x -> x.copy(plz = it.trim()) } }, "PLZ", Modifier.weight(0.35f), tastatur = KeyboardType.Number)
                        Feld(ort, { ort = it; setzen { x -> x.copy(ort = it.trim()) } }, "Ort", Modifier.weight(0.65f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Feld(telefon, { telefon = it; setzen { x -> x.copy(telefon = it.trim()) } }, "Telefon", Modifier.weight(1f), tastatur = KeyboardType.Phone)
                        Feld(email, { email = it; setzen { x -> x.copy(email = it.trim()) } }, "E-Mail", Modifier.weight(1f), tastatur = KeyboardType.Email)
                    }
                    Hinweis("Erscheint im Kopf jedes Regieberichts.")
                    Abstand()
                    var mwst by remember { mutableStateOf(zahlFeld(d.einstellungen.mwst)) }
                    Feld(mwst, { v ->
                        mwst = v
                        zahlLesen(v)?.takeIf { it in 0.0..100.0 }?.let { n -> Speicher.aendern { it.copy(einstellungen = it.einstellungen.copy(mwst = n)) } }
                    }, "Standard-Mehrwertsteuer (%)", tastatur = KeyboardType.Decimal)
                    Hinweis("Vorbelegung für neue Aufträge.")
                }
            }
            // ------------------------------------------------ Kalender
            item {
                Karte("Kalender", "04") {
                    Fliesstext("Zielkalender, automatische Übertragung und anzuzeigende Gerätekalender liegen im Kalender-Reiter unter ⚙.", p.textDim, 13.sp)
                    Knopfreihe {
                        Knopf("Kalender-Einstellungen", klein = true) {
                            st.menueOffen = false; st.reiter = Reiter.KALENDER; st.kalenderEinstellungen = true
                        }
                        Knopf("App-Einstellungen", klein = true, art = KnopfArt.LEISE) {
                            ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    }
                }
            }
            // ------------------------------------------------ Updates & Info
            item {
                Karte("Updates & Info", "05") {
                    Knopfreihe {
                        Knopf("🔄 Nach Update suchen", klein = true) {
                            updateStatus = "Prüfe …"
                            scope.launch {
                                updateStatus = try {
                                    val u = UpdateDienst.pruefen(d.update.repo)
                                    if (u == null) "Diese Version (${BuildConfig.VERSION_NAME}) ist aktuell."
                                    else {
                                        UpdateLader.starten(ctx, u)
                                        "Version ${u.version} wird heruntergeladen und anschließend installiert."
                                    }
                                } catch (e: Exception) { "Die Update-Prüfung ist fehlgeschlagen: " + (e.message ?: "") }
                            }
                        }
                        Knopf("📜 Changelog", klein = true) { st.changelogOffen = true }
                    }
                    if (updateStatus.isNotBlank()) Hinweis(updateStatus)
                    Abstand()
                    Hinweis("Baustellen Pilot Reloaded · Version ${BuildConfig.VERSION_NAME} · Alle Daten liegen nur auf diesem Gerät. Es gibt keinen Server.")
                }
            }
            // ------------------------------------------------ Speicher
            item {
                Karte("Speicher", "06") {
                    var belegung by remember { mutableStateOf<Pair<Int, Long>?>(null) }
                    LaunchedEffect(d) { belegung = withContext(Dispatchers.IO) { Dateien.belegung(ctx) } }
                    Mono(
                        "Aufträge: ${d.auftraege.eintraege.size} · Kunden: ${d.kunden.eintraege.size}" +
                            (belegung?.let { " · Dateien: ${it.first} (${groesseText(it.second.toDouble())})" } ?: ""),
                        p.textDim, 12.sp,
                    )
                    Knopfreihe { Knopf("Alle Daten löschen", klein = true, art = KnopfArt.GEFAHR) { loeschFrage = 1 } }
                }
            }
        }
    }

    importFrage?.let { erg ->
        Frage(
            "Sicherung einlesen",
            "Gefunden: " + erg.bereiche.joinToString(", ") + "\n\nDiese Bereiche werden durch den Inhalt der Datei ersetzt." +
                (if (erg.probleme.isNotEmpty()) "\n\nNicht lesbar: " + erg.probleme.joinToString("; ") else ""),
            ja = "Einlesen", gefahr = false,
            onJa = {
                importFrage = null
                scope.launch {
                    laeuft = true
                    val n = withContext(Dispatchers.IO) { Sicherung.anwenden(ctx, erg) }
                    laeuft = false
                    st.melden("Sicherung eingelesen", "Die Daten wurden übernommen." + if (n > 0) " $n Dateien/Fotos abgelegt." else "")
                }
            },
            onNein = { importFrage = null; Sicherung.verwerfen(ctx) },
        )
    }
    when (loeschFrage) {
        1 -> Frage("Alle Daten löschen", "Wirklich alle Aufträge, Kunden, Unterlagen und Fotos löschen? Das lässt sich nicht rückgängig machen.",
            onJa = { loeschFrage = 2 }, onNein = { loeschFrage = 0 })
        2 -> Frage("Sicher?", "Ohne vorherigen Export sind die Daten endgültig weg.", ja = "Endgültig löschen",
            onJa = {
                loeschFrage = 0
                Speicher.aendern { it.copy(kunden = Kunden(), auftraege = Auftraege(), tagesnotizen = emptyMap()) }
                Dateien.verwaisteAufraeumen(ctx, AppDaten())
                st.menueOffen = false
                st.kurz("Alle Daten gelöscht")
            }, onNein = { loeschFrage = 0 })
    }
}
