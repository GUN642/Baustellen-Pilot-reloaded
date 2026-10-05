package de.gun.baustellen.reloaded.ui.seiten

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import de.gun.baustellen.reloaded.daten.AppDaten
import de.gun.baustellen.reloaded.daten.Speicher
import de.gun.baustellen.reloaded.logik.statusFarbe
import de.gun.baustellen.reloaded.ui.Knopf
import de.gun.baustellen.reloaded.ui.KnopfArt
import de.gun.baustellen.reloaded.ui.LocalPalette
import de.gun.baustellen.reloaded.ui.Punkt
import de.gun.baustellen.reloaded.ui.Schrift
import de.gun.baustellen.reloaded.ui.Symbol
import java.io.File

/** Aktueller Datenbestand als beobachteter Zustand. */
@Composable
fun aktuelleDaten(): AppDaten {
    val d by Speicher.daten.collectAsState()
    return d
}

/** Standard-Seite: senkrechte Liste mit Abständen. */
@Composable
fun SeitenListe(zustand: LazyListState = rememberLazyListState(), inhalt: LazyListScope.() -> Unit) {
    LazyColumn(
        state = zustand,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = inhalt,
    )
}

/** Kopfzeile einer Ebene: Schließen-Knopf, Titel, optionale Aktionen rechts. */
@Composable
fun EbenenKopf(titel: String, onZu: () -> Unit, aktionen: @Composable () -> Unit = {}) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Symbol("✕", p.text, onZu)
        Spacer(Modifier.width(6.dp))
        Punkt(titel.uppercase(), 20.sp, modifier = Modifier.weight(1f))
        aktionen()
    }
}

/** Farbige Marke für den Auftragsstatus. */
@Composable
fun StatusMarke(status: String, text: String) {
    val farbe = Color(statusFarbe(status))
    Box(Modifier.clip(RoundedCornerShape(50)).background(farbe.copy(alpha = 0.18f)).padding(horizontal = 8.dp, vertical = 3.dp)) {
        Text(text.uppercase(), color = farbe, style = TextStyle(fontFamily = Schrift.mono, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp))
    }
}

@Composable
fun Farbpunkt(farbe: Color, groesse: Int = 8) {
    Box(Modifier.size(groesse.dp).clip(CircleShape).background(farbe))
}

@Composable
fun LoeschKnopf(onClick: () -> Unit) = Symbol("✕", LocalPalette.current.rot, onClick)

@Composable
fun BearbeitenKnopf(onClick: () -> Unit) = Symbol("✎", LocalPalette.current.textDim, onClick)

@Composable
fun FormKnoepfe(speichernText: String, onSpeichern: () -> Unit, onAbbrechen: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
        Knopf("Abbrechen", art = KnopfArt.NORMAL, onClick = onAbbrechen)
        Knopf(speichernText, art = KnopfArt.PRIMAER, onClick = onSpeichern)
    }
}

/** Zwei Felder nebeneinander. */
@Composable
fun Zweier(links: @Composable (Modifier) -> Unit, rechts: @Composable (Modifier) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        links(Modifier.weight(1f))
        rechts(Modifier.weight(1f))
    }
}

// ---------------------------------------------------------------- Ausgabe

/** Datei im Ausgabeordner anlegen und über das Teilen-Menü anbieten. */
fun ausgabeTeilen(ctx: Context, name: String, typ: String, schreiben: (java.io.OutputStream) -> Unit) {
    val f = ausgabeDatei(ctx, name)
    f.outputStream().use { schreiben(it) }
    dateiTeilen(ctx, f, typ)
}

/** Datei im Ausgabeordner (cache/ausgabe), dessen Inhalte sich teilen lassen. */
fun ausgabeDatei(ctx: Context, name: String): File = File(File(ctx.cacheDir, "ausgabe").apply { mkdirs() }, name)

fun dateiTeilen(ctx: Context, f: File, typ: String) {
    val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".dateien", f)
    val i = Intent(Intent.ACTION_SEND).setType(typ).putExtra(Intent.EXTRA_STREAM, uri)
        .putExtra(Intent.EXTRA_SUBJECT, f.name).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    ctx.startActivity(Intent.createChooser(i, f.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

fun inZwischenablage(ctx: Context, text: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("Baustellen Pilot", text))
}

fun oeffneUrl(ctx: Context, url: String): Boolean = try {
    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
} catch (e: Exception) { false }

fun anrufen(ctx: Context, nummer: String) = oeffneUrl(ctx, "tel:" + nummer.replace(Regex("[^+\\d]"), ""))
fun mailen(ctx: Context, adresse: String) = oeffneUrl(ctx, "mailto:" + adresse.trim())

/** Navigation/Karte zur Anschrift. */
fun karte(ctx: Context, anschrift: String): Boolean =
    oeffneUrl(ctx, "geo:0,0?q=" + Uri.encode(anschrift)) ||
        oeffneUrl(ctx, "https://www.google.com/maps/search/?api=1&query=" + Uri.encode(anschrift))
