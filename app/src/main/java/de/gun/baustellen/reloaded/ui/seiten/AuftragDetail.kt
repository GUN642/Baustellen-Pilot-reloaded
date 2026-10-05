package de.gun.baustellen.reloaded.ui.seiten

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.gun.baustellen.reloaded.daten.AppDaten
import de.gun.baustellen.reloaded.daten.Auftrag
import de.gun.baustellen.reloaded.daten.Dateien
import de.gun.baustellen.reloaded.daten.STATUS
import de.gun.baustellen.reloaded.daten.Speicher
import de.gun.baustellen.reloaded.daten.dateiname
import de.gun.baustellen.reloaded.daten.groesseText
import de.gun.baustellen.reloaded.geraet.GeraeteKalender
import de.gun.baustellen.reloaded.geraet.KalenderAbgleich
import de.gun.baustellen.reloaded.logik.Ics
import de.gun.baustellen.reloaded.logik.anschrift
import de.gun.baustellen.reloaded.logik.auftrag
import de.gun.baustellen.reloaded.logik.betraege
import de.gun.baustellen.reloaded.logik.de
import de.gun.baustellen.reloaded.logik.einheitText
import de.gun.baustellen.reloaded.logik.eur
import de.gun.baustellen.reloaded.logik.heuteIso
import de.gun.baustellen.reloaded.logik.kunde
import de.gun.baustellen.reloaded.logik.kundeName
import de.gun.baustellen.reloaded.logik.materialSummen
import de.gun.baustellen.reloaded.logik.materialTextliste
import de.gun.baustellen.reloaded.logik.stundenText
import de.gun.baustellen.reloaded.logik.zahl
import de.gun.baustellen.reloaded.logik.zeitraumText
import de.gun.baustellen.reloaded.ui.Abstand
import de.gun.baustellen.reloaded.ui.Etikett
import de.gun.baustellen.reloaded.ui.Fliesstext
import de.gun.baustellen.reloaded.ui.Frage
import de.gun.baustellen.reloaded.ui.Hinweis
import de.gun.baustellen.reloaded.ui.Karte
import de.gun.baustellen.reloaded.ui.Kennzahl
import de.gun.baustellen.reloaded.ui.Knopf
import de.gun.baustellen.reloaded.ui.KnopfArt
import de.gun.baustellen.reloaded.ui.Knopfreihe
import de.gun.baustellen.reloaded.ui.KundeMaskeStart
import de.gun.baustellen.reloaded.ui.Leer
import de.gun.baustellen.reloaded.ui.LocalPalette
import de.gun.baustellen.reloaded.ui.LocalSteuerung
import de.gun.baustellen.reloaded.ui.MaterialMaskeStart
import de.gun.baustellen.reloaded.ui.Mono
import de.gun.baustellen.reloaded.ui.RUND_KLEIN
import de.gun.baustellen.reloaded.ui.RegieMaskeStart
import de.gun.baustellen.reloaded.ui.AuftragMaskeStart
import de.gun.baustellen.reloaded.ui.FotoStart
import de.gun.baustellen.reloaded.ui.Segmente
import de.gun.baustellen.reloaded.ui.Steuerung
import de.gun.baustellen.reloaded.ui.Wahl
import de.gun.baustellen.reloaded.ui.Zeile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Für Arbeiten, die die Ebene überdauern (z. B. Termin nach Löschen entfernen). */
val HINTERGRUND = CoroutineScope(SupervisorJob() + Dispatchers.Main)

private val KATEGORIEN = listOf("Angebot", "Rechnung", "Lieferschein", "Aufmaß", "Plan", "Sonstiges")

@Composable
fun AuftragDetailEbene(id: String) {
    val st = LocalSteuerung.current
    val d = aktuelleDaten()
    val a = d.auftrag(id)
    if (a == null) {
        Column {
            EbenenKopf("Auftrag", { st.detail = null })
            Leer("Dieser Auftrag existiert nicht mehr.")
        }
        return
    }
    Column {
        EbenenKopf(a.titel.ifBlank { "Auftrag" }, { st.detail = null }) {
            BearbeitenKnopf { st.auftragMaske = AuftragMaskeStart(a.id) }
        }
        SeitenListe {
            item { Kopfbereich(a) }
            item { Stammdaten(d, a) }
            item { MaterialBereich(d, a) }
            item { UnterlagenBereich(a) }
            item { FotoBereich(a) }
            item { RegieBereich(d, a) }
            item { Aktionen(d, a) }
        }
    }
}

@Composable
private fun Kopfbereich(a: Auftrag) {
    val p = LocalPalette.current
    Karte("Überblick", "01") {
        Mono(zeitraumText(a) + if (a.nummer.isNotBlank()) " · Nr. ${a.nummer}" else "", p.textDim, 12.sp)
        Abstand(8.dp)
        Segmente(STATUS.map { it.key to it.value }, a.status) { s -> Speicher.auftrag(a.id) { it.copy(status = s) } }
        Abstand(10.dp)
        val b = a.betraege()
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Kennzahl(zahl(b.netto, 0) + " €", "Netto", p.akzent, Modifier.weight(1f))
            Kennzahl(zahl(b.steuer, 0) + " €", "MwSt ${zahl(a.mwst, 1)} %", p.textDim, Modifier.weight(1f))
            Kennzahl(zahl(b.brutto, 0) + " €", "Brutto", p.text, Modifier.weight(1f))
        }
        if (a.eventId.isNotBlank()) Hinweis("Im Gerätekalender eingetragen. Änderungen am Zeitraum werden mitgeführt.")
    }
}

@Composable
private fun Stammdaten(d: AppDaten, a: Auftrag) {
    val p = LocalPalette.current
    val st = LocalSteuerung.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val k = d.kunde(a.kundeId)
    val ort = a.anschrift()
    Karte("Stammdaten", "02") {
        if (k != null) {
            Etikett("Kunde")
            Fliesstext(k.name, fett = true, modifier = Modifier.clickable { st.kundeMaske = KundeMaskeStart(k.id) })
            if (k.ansprech.isNotBlank()) Fliesstext(k.ansprech, p.textDim, 13.sp)
            Knopfreihe {
                if (k.telefon.isNotBlank()) Knopf("☎ " + k.telefon, klein = true) { anrufen(ctx, k.telefon) }
                if (k.email.isNotBlank()) Knopf("✉ " + k.email, klein = true) { mailen(ctx, k.email) }
            }
            Abstand(10.dp)
        } else Hinweis("Kein Kunde zugeordnet.")
        if (ort.isNotBlank()) {
            Etikett("Baustelle")
            Fliesstext(ort, fett = true)
            Knopfreihe {
                Knopf("⌖ Karte / Navigation", klein = true) { if (!karte(ctx, ort)) st.kurz("Keine Karten-App gefunden") }
                Knopf("🌦 Wetter für diesen Ort", klein = true) {
                    scope.launch {
                        val name = baustellenOrtSetzen(a.ort.ifBlank { a.plz }.ifBlank { ort })
                        st.kurz(if (name != null) "Wetterort auf $name gesetzt" else "Ort nicht gefunden")
                    }
                }
            }
            Abstand(10.dp)
        }
        if (a.maschine.isNotBlank()) {
            Etikett("Maschine/Gerät")
            Fliesstext(a.maschine)
            Abstand(10.dp)
        }
        if (a.notiz.isNotBlank()) {
            Etikett("Notiz")
            Fliesstext(a.notiz, p.textDim, 13.sp)
        }
    }
}

@Composable
private fun MaterialBereich(d: AppDaten, a: Auftrag) {
    val p = LocalPalette.current
    val st = LocalSteuerung.current
    val ctx = LocalContext.current
    val s = materialSummen(a.material)
    Karte("Material", "03", aktion = {
        Knopf("+ Material", klein = true, art = KnopfArt.PRIMAER) { st.materialMaske = MaterialMaskeStart(a.id) }
    }) {
        Column(Modifier.fillMaxWidth().clip(RUND_KLEIN).background(p.panelAlt).padding(10.dp)) {
            Mono("Bedarf     ${zahl(s.bedarfT)} t / ${zahl(s.bedarfM3)} m³ · ${eur(s.kostenBedarf)}", p.text, 11.5.sp)
            Mono("Verbrauch  ${zahl(s.verbrauchT)} t / ${zahl(s.verbrauchM3)} m³ · ${eur(s.kostenVerbrauch)}", p.text, 11.5.sp)
            Mono("Differenz  ${eur(s.differenzKosten)}", if (s.differenzKosten > 0) p.rot else p.gruen, 11.5.sp, fett = true)
        }
        if (a.material.isEmpty()) Leer("Noch kein Material erfasst.")
        a.material.sortedWith(compareBy({ it.art != "bedarf" }, { it.bez.lowercase() })).forEach { m ->
            val verbrauch = m.art == "verbrauch"
            Zeile(if (verbrauch) p.gruen else p.warn, onClick = { st.materialMaske = MaterialMaskeStart(a.id, m.id) }, aktionen = {
                LoeschKnopf {
                    val vorher = a.material
                    Speicher.auftrag(a.id) { it.copy(material = it.material.filter { x -> x.id != m.id }) }
                    st.rueckgaengig("Materialposten gelöscht", { Speicher.auftrag(a.id) { it.copy(material = vorher) } })
                }
            }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Fliesstext(m.bez, fett = true, groesse = 13.sp, modifier = Modifier.weight(1f), zeilen = 1)
                    Mono(if (verbrauch) "VERBRAUCH" else "BEDARF", if (verbrauch) p.gruen else p.warn, 9.5.sp, fett = true)
                }
                val e = einheitText(m.einheit)
                Mono("${zahl(m.menge)} $e × ${zahl(m.preis)} €/$e = ${eur(m.menge * m.preis)}", p.textDim, 11.sp)
                val zusatz = listOfNotNull(m.datum.takeIf { it.isNotBlank() }?.let { de(it) }, m.lieferant.takeIf { it.isNotBlank() }).joinToString(" · ")
                if (zusatz.isNotBlank()) Mono(zusatz, p.textFaint, 10.5.sp)
            }
        }
        if (a.material.isNotEmpty()) Knopfreihe {
            Knopf("⧉ Liste kopieren", klein = true) {
                inZwischenablage(ctx, materialTextliste(a, d.kundeName(a.kundeId)))
                st.kurz("In die Zwischenablage kopiert")
            }
        }
    }
}

@Composable
private fun UnterlagenBereich(a: Auftrag) {
    val p = LocalPalette.current
    val st = LocalSteuerung.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var kategorieWahl by remember { mutableStateOf(false) }
    var kategorie by remember { mutableStateOf("Angebot") }
    val waehlen = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            try {
                val info = Dateien.unterlageUebernehmen(ctx, uri, kategorie, heuteIso())
                Speicher.auftrag(a.id) { it.copy(dateien = it.dateien + info) }
                st.kurz("Datei hinterlegt")
            } catch (e: Exception) {
                st.melden("Datei", e.message ?: "Die Datei konnte nicht übernommen werden.")
            }
        }
    }
    Karte("Angebote, Rechnungen, Unterlagen", "04", aktion = {
        Knopf("+ Datei", klein = true, art = KnopfArt.PRIMAER) { kategorieWahl = true }
    }) {
        if (a.dateien.isEmpty()) Leer("Noch keine Datei hinterlegt.")
        a.dateien.forEach { f ->
            val vorhanden = remember(f.id) { Dateien.vorhanden(ctx, f.id) }
            Zeile(if (f.typ.contains("pdf")) p.rot else p.neutral, onClick = {
                if (!Dateien.oeffnen(ctx, f.id, f.name, f.typ)) st.melden("Datei", "Die Datei ist nicht mehr im Speicher vorhanden oder es gibt keine App zum Öffnen.")
            }, aktionen = {
                de.gun.baustellen.reloaded.ui.Symbol("⇪") { if (!Dateien.teilen(ctx, f.id, f.name, f.typ)) st.kurz("Datei nicht vorhanden") }
                LoeschKnopf {
                    val vorher = a.dateien
                    Speicher.auftrag(a.id) { it.copy(dateien = it.dateien.filter { x -> x.id != f.id }) }
                    st.rueckgaengig("Datei gelöscht", { Speicher.auftrag(a.id) { it.copy(dateien = vorher) } }) {
                        Dateien.loeschen(ctx, listOf(f.id))
                    }
                }
            }) {
                Fliesstext(f.name, fett = true, groesse = 13.sp, zeilen = 2)
                Mono(
                    f.kategorie + " · " + groesseText(f.groesse) + " · " + de(f.datum) + if (!vorhanden) " · fehlt" else "",
                    if (vorhanden) p.textDim else p.rot, 11.sp,
                )
            }
        }
    }
    if (kategorieWahl) Wahl(
        "Art der Datei", "Die Datei wird dem Auftrag als … hinterlegt.",
        KATEGORIEN.map { k -> Triple(k, KnopfArt.NORMAL) { kategorie = k; kategorieWahl = false; waehlen.launch(arrayOf("*/*")) } },
    ) { kategorieWahl = false }
}

@Composable
private fun FotoBereich(a: Auftrag) {
    val p = LocalPalette.current
    val st = LocalSteuerung.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var kameraUri by remember { mutableStateOf<Uri?>(null) }

    fun hinzu(uris: List<Uri>) = scope.launch {
        var n = 0
        uris.forEach { u ->
            try {
                val f = Dateien.fotoUebernehmen(ctx, u, heuteIso())
                Speicher.auftrag(a.id) { it.copy(fotos = it.fotos + f) }
                n++
            } catch (e: Exception) {
                st.melden("Foto", e.message ?: "Das Foto konnte nicht gespeichert werden.")
            }
        }
        if (n > 0) st.kurz(if (n == 1) "Foto hinterlegt" else "$n Fotos hinterlegt")
    }

    val kamera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val u = kameraUri
        if (ok && u != null) hinzu(listOf(u))
    }
    val galerie = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) hinzu(uris)
    }
    Karte("Baustellenfotos", "05", aktion = { Mono("${a.fotos.size}", p.textDim) }) {
        Knopfreihe {
            Knopf("📷 Kamera", klein = true, art = KnopfArt.PRIMAER) {
                try {
                    val u = Dateien.kameraZiel(ctx)
                    kameraUri = u
                    kamera.launch(u)
                } catch (e: Exception) { st.melden("Kamera", "Keine Kamera-App gefunden.") }
            }
            Knopf("+ Aus Galerie", klein = true) { galerie.launch("image/*") }
        }
        Abstand(8.dp)
        if (a.fotos.isEmpty()) Leer("Noch kein Foto hinterlegt.")
        a.fotos.sortedByDescending { it.datum }.chunked(3).forEach { reihe ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                reihe.forEach { f ->
                    Box(
                        Modifier.weight(1f).aspectRatio(1f).clip(RUND_KLEIN).background(p.panelAlt).border(1.dp, p.randLeise, RUND_KLEIN)
                            .clickable { st.foto = FotoStart(a.id, f.id) }
                    ) {
                        val bild by produceState<ImageBitmap?>(null, f.id) {
                            value = withContext(Dispatchers.IO) { Dateien.vorschau(ctx, f.id, 360)?.asImageBitmap() }
                        }
                        val b = bild
                        if (b != null) Image(b, f.notiz.ifBlank { "Baustellenfoto" }, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        else Mono("FOTO", p.textFaint, 10.sp, Modifier.align(Alignment.Center))
                        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 6.dp, vertical = 3.dp)) {
                            Mono(de(f.datum) + if (f.notiz.isNotBlank()) " · " + f.notiz else "", Color.White, 9.sp)
                        }
                    }
                }
                repeat(3 - reihe.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun RegieBereich(d: AppDaten, a: Auftrag) {
    val p = LocalPalette.current
    val st = LocalSteuerung.current
    val ctx = LocalContext.current
    Karte("Regieberichte", "06", aktion = {
        Knopf("+ Regiebericht", klein = true, art = KnopfArt.PRIMAER) { st.regieMaske = RegieMaskeStart(a.id) }
    }) {
        if (a.regieberichte.isEmpty()) Leer("Noch kein Regiebericht erfasst. Für Arbeiten, die nach Aufwand abgerechnet werden.")
        a.regieberichte.sortedByDescending { it.datum }.forEach { r ->
            Zeile(p.akzent, onClick = { st.regieMaske = RegieMaskeStart(a.id, r.id) }, aktionen = {
                de.gun.baustellen.reloaded.ui.Symbol("PDF", p.akzent) { regiePdfTeilen(ctx, st, d, a, r) }
                LoeschKnopf {
                    val vorher = a.regieberichte
                    Speicher.auftrag(a.id) { it.copy(regieberichte = it.regieberichte.filter { x -> x.id != r.id }) }
                    st.rueckgaengig("Regiebericht gelöscht", { Speicher.auftrag(a.id) { it.copy(regieberichte = vorher) } })
                }
            }) {
                Fliesstext(de(r.datum), fett = true, groesse = 13.sp)
                Mono(
                    listOfNotNull(r.mitarbeiter.ifBlank { "—" }, stundenText(r.nettoMinuten), r.maschine.takeIf { it.isNotBlank() },
                        if (r.unterschriftBild.isNotBlank()) "✓ unterschrieben" else null).joinToString(" · "),
                    p.textDim, 11.sp,
                )
            }
        }
    }
}

@Composable
private fun Aktionen(d: AppDaten, a: Auftrag) {
    val st = LocalSteuerung.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var loeschFrage by remember { mutableStateOf(false) }
    val rechte = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { erg ->
        if (erg.values.all { it }) scope.launch {
            try { st.kurz(KalenderAbgleich.abgleichen(ctx, a)) } catch (e: Exception) { st.melden("Kalender", e.message ?: "Fehler") }
        } else st.melden("Kalender", "Ohne Kalenderberechtigung kann nichts eingetragen werden.")
    }
    Karte("Aktionen", "07") {
        Knopfreihe {
            Knopf("✎ Bearbeiten") { st.auftragMaske = AuftragMaskeStart(a.id) }
            Knopf("📅 In Gerätekalender", art = KnopfArt.PRIMAER) {
                if (!GeraeteKalender.darfSchreiben(ctx)) rechte.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
                else scope.launch {
                    try { st.kurz(KalenderAbgleich.abgleichen(ctx, a)) } catch (e: Exception) { st.melden("Kalender", e.message ?: "Fehler") }
                }
            }
            Knopf("↓ .ics-Datei") {
                ausgabeTeilen(ctx, dateiname(a.titel.ifBlank { "Auftrag" }) + ".ics", "text/calendar") {
                    it.write(Ics.dokument(listOf(a to d.kundeName(a.kundeId))).toByteArray(Charsets.UTF_8))
                }
            }
            Knopf("↗ Google Kalender") {
                Ics.googleLink(a, d.kundeName(a.kundeId))?.let { oeffneUrl(ctx, it) }
            }
            Knopf("✕ Auftrag löschen", art = KnopfArt.GEFAHR) { loeschFrage = true }
        }
    }
    if (loeschFrage) Frage(
        "Auftrag löschen",
        "„${a.titel}“ mit allen Unterlagen, Fotos und Regieberichten löschen?" + if (a.eventId.isNotBlank()) " Der Termin im Gerätekalender wird ebenfalls entfernt." else "",
        onJa = {
            loeschFrage = false
            auftragLoeschen(st, a, ctx)
        },
        onNein = { loeschFrage = false },
    )
}

/** Löschen mit Rückgängig; Dateien und Kalendertermin erst nach Ablauf der Frist. */
fun auftragLoeschen(st: Steuerung, a: Auftrag, ctx: android.content.Context) {
    val app = ctx.applicationContext
    Speicher.aendern { dd -> dd.copy(auftraege = dd.auftraege.copy(eintraege = dd.auftraege.eintraege.filter { it.id != a.id })) }
    st.detail = null
    st.rueckgaengig("Auftrag gelöscht", {
        Speicher.aendern { dd -> dd.copy(auftraege = dd.auftraege.copy(eintraege = dd.auftraege.eintraege + a)) }
    }) {
        Dateien.loeschen(app, a.dateien.map { it.id } + a.fotos.map { it.id })
        HINTERGRUND.launch { KalenderAbgleich.entfernen(app, a) }
    }
}
