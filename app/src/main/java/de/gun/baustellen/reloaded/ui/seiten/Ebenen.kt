package de.gun.baustellen.reloaded.ui.seiten

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.gun.baustellen.reloaded.BuildConfig
import de.gun.baustellen.reloaded.daten.Dateien
import de.gun.baustellen.reloaded.daten.Speicher
import de.gun.baustellen.reloaded.logik.anschrift
import de.gun.baustellen.reloaded.logik.auftrag
import de.gun.baustellen.reloaded.logik.de
import de.gun.baustellen.reloaded.ui.Feld
import de.gun.baustellen.reloaded.ui.Fliesstext
import de.gun.baustellen.reloaded.ui.FotoStart
import de.gun.baustellen.reloaded.ui.Hinweis
import de.gun.baustellen.reloaded.ui.Karte
import de.gun.baustellen.reloaded.ui.Knopf
import de.gun.baustellen.reloaded.ui.KnopfArt
import de.gun.baustellen.reloaded.ui.Knopfreihe
import de.gun.baustellen.reloaded.ui.KundeMaskeStart
import de.gun.baustellen.reloaded.ui.Leer
import de.gun.baustellen.reloaded.ui.LocalPalette
import de.gun.baustellen.reloaded.ui.LocalSteuerung
import de.gun.baustellen.reloaded.ui.Mono
import de.gun.baustellen.reloaded.ui.Zeile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

val CHANGELOG = listOf(
    "2.0.0" to listOf(
        "Baustellen Pilot komplett neu als native Android-App (Kotlin, Jetpack Compose) im Stil des Soldaten Dashboard Reloaded",
        "Designs Nothing, Nothing hell, Graphit, Aulumu und System, sieben Akzentfarben, Punktschrift und Punktraster",
        "Woche mit Auftragslage, Wetter je Arbeitstag (7–16 Uhr) und Tagesnotizen",
        "Aufträge mit Netto/Brutto, Material (Bedarf/Verbrauch in t und m³), Unterlagen, Baustellenfotos (Kamera und Galerie) und Regieberichten",
        "Regiebericht mit Unterschrift auf dem Gerät und PDF-Ausgabe – komplett ohne Internet",
        "Kalender mit durchgehenden Balken, Wischen zwischen Monaten, Vollbild, Gerätekalender lesen und Auftragszeiträume direkt eintragen",
        "Wetter: ICON-D2 für die ersten zwei Tage, ECMWF für die Woche, Baustellen-Hinweise bei Frost, Starkregen und Sturm",
        "Statistik nach Jahr und Monat, Jahresvergleich, Kunden im Jahr",
        "Suche über Aufträge und Kunden, Rückgängig beim Löschen",
        "Sicherung im Format des alten Baustellen Pilot – inklusive Unterlagen und Fotos, in beide Richtungen kompatibel",
        "Update-Prüfung mit Download und Installation direkt aus der App",
    ),
)

@Composable
fun ChangelogEbene() {
    val p = LocalPalette.current
    val st = LocalSteuerung.current
    Column(Modifier.fillMaxSize()) {
        EbenenKopf("Changelog", { st.changelogOffen = false })
        SeitenListe {
            CHANGELOG.forEach { (v, punkte) ->
                item {
                    Karte("Version $v", if (v == BuildConfig.VERSION_NAME) "●" else null) {
                        punkte.forEach { Fliesstext("· $it", p.textDim, 14.sp, Modifier.padding(vertical = 3.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
fun SucheEbene() {
    val p = LocalPalette.current
    val st = LocalSteuerung.current
    val d = aktuelleDaten()
    var begriff by remember { mutableStateOf("") }
    val b = begriff.trim()
    val auftraege = if (b.length < 2) emptyList() else d.auftraege.eintraege.filter { a ->
        listOf(a.titel, a.nummer, a.anschrift(), a.notiz, a.maschine, d.kunden.eintraege.firstOrNull { it.id == a.kundeId }?.name ?: "").any { it.contains(b, true) } ||
            a.material.any { it.bez.contains(b, true) || it.lieferant.contains(b, true) } ||
            a.regieberichte.any { r -> r.mitarbeiter.contains(b, true) || r.positionen.any { it.beschreibung.contains(b, true) } } ||
            a.dateien.any { it.name.contains(b, true) } || a.fotos.any { it.notiz.contains(b, true) }
    }
    val kunden = if (b.length < 2) emptyList() else d.kunden.eintraege.filter { k ->
        listOf(k.name, k.ansprech, k.ort, k.strasse, k.telefon, k.email, k.notiz).any { it.contains(b, true) }
    }
    val notizen = if (b.length < 2) emptyList() else d.tagesnotizen.filter { it.value.contains(b, true) }.toList().sortedByDescending { it.first }
    Column(Modifier.fillMaxSize()) {
        EbenenKopf("Suche", { st.sucheOffen = false })
        Box(Modifier.padding(horizontal = 14.dp)) { Feld(begriff, { begriff = it }, "Suchbegriff", platzhalter = "Auftrag, Kunde, Material, Ort …") }
        SeitenListe {
            if (b.length < 2) item { Hinweis("Mindestens zwei Zeichen eingeben.") }
            else if (auftraege.isEmpty() && kunden.isEmpty() && notizen.isEmpty()) item { Leer("Nichts gefunden.") }
            if (auftraege.isNotEmpty()) item { Mono("AUFTRÄGE · ${auftraege.size}", p.textFaint, 11.sp, fett = true) }
            items(auftraege, key = { "a" + it.id }) { a -> AuftragKarte(d, a) { st.sucheOffen = false; st.auftragOeffnen(a.id) } }
            if (kunden.isNotEmpty()) item { Mono("KUNDEN · ${kunden.size}", p.textFaint, 11.sp, fett = true) }
            items(kunden, key = { "k" + it.id }) { k ->
                Zeile(p.akzent, onClick = { st.sucheOffen = false; st.kundeMaske = KundeMaskeStart(k.id) }) {
                    Fliesstext(k.name, fett = true)
                    Mono(listOf(k.ansprech, k.ort, k.telefon).filter { it.isNotBlank() }.joinToString(" · "), p.textDim, 11.sp)
                }
            }
            if (notizen.isNotEmpty()) item { Mono("TAGESNOTIZEN · ${notizen.size}", p.textFaint, 11.sp, fett = true) }
            items(notizen, key = { "n" + it.first }) { (tag, text) ->
                Zeile(p.neutral, onClick = {
                    st.sucheOffen = false; st.reiter = de.gun.baustellen.reloaded.ui.Reiter.WOCHE
                    de.gun.baustellen.reloaded.logik.parseIso(tag)?.let { st.wochenAnker = it }
                }) {
                    Mono(de(tag), p.textDim, 11.sp)
                    Fliesstext(text, zeilen = 3)
                }
            }
        }
    }
}

/** Foto groß anzeigen (mit zwei Fingern zoomen), Notiz bearbeiten, teilen, löschen. */
@Composable
fun FotoEbene(start: FotoStart) {
    val p = LocalPalette.current
    val st = LocalSteuerung.current
    val ctx = LocalContext.current
    val d = aktuelleDaten()
    val a = d.auftrag(start.auftragId)
    val f = a?.fotos?.firstOrNull { it.id == start.fotoId }
    if (a == null || f == null) {
        Column { EbenenKopf("Foto", { st.foto = null }); Leer("Dieses Foto existiert nicht mehr.") }
        return
    }
    val bild by produceState<ImageBitmap?>(null, f.id) {
        value = withContext(Dispatchers.IO) { Dateien.vorschau(ctx, f.id, 2000)?.asImageBitmap() }
    }
    var notiz by remember(f.id) { mutableStateOf(f.notiz) }
    var zoom by remember(f.id) { mutableFloatStateOf(1f) }
    var versatz by remember(f.id) { mutableStateOf(Offset.Zero) }
    val transform = rememberTransformableState { z, v, _ ->
        zoom = (zoom * z).coerceIn(1f, 6f)
        versatz = if (zoom <= 1f) Offset.Zero else versatz + v
    }
    Column(Modifier.fillMaxSize()) {
        EbenenKopf(de(f.datum), { st.foto = null })
        Box(Modifier.weight(1f).fillMaxWidth().background(Color.Black).transformable(transform), contentAlignment = Alignment.Center) {
            val b = bild
            if (b != null) Image(
                b, f.notiz.ifBlank { "Baustellenfoto" },
                Modifier.fillMaxSize().graphicsLayer(scaleX = zoom, scaleY = zoom, translationX = versatz.x, translationY = versatz.y),
                contentScale = ContentScale.Fit,
            ) else Mono(if (Dateien.vorhanden(ctx, f.id)) "Wird geladen …" else "Die Bilddatei fehlt.", Color.White, 12.sp)
        }
        Column(Modifier.padding(14.dp)) {
            Feld(notiz, { v -> notiz = v; Speicher.auftrag(a.id) { x -> x.copy(fotos = x.fotos.map { if (it.id == f.id) it.copy(notiz = v.trim()) else it }) } },
                "Notiz zum Foto")
            Knopfreihe {
                Knopf("⇪ Teilen", klein = true) { if (!Dateien.teilen(ctx, f.id, f.name.ifBlank { "Foto.jpg" }, "image/jpeg")) st.kurz("Bilddatei fehlt") }
                Knopf("✕ Löschen", klein = true, art = KnopfArt.GEFAHR) {
                    val vorher = a.fotos
                    Speicher.auftrag(a.id) { x -> x.copy(fotos = x.fotos.filter { it.id != f.id }) }
                    st.foto = null
                    st.rueckgaengig("Foto gelöscht", { Speicher.auftrag(a.id) { x -> x.copy(fotos = vorher) } }) {
                        Dateien.loeschen(ctx, listOf(f.id))
                    }
                }
            }
        }
    }
}
