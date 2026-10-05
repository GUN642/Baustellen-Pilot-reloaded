package de.gun.baustellen.reloaded.ui.seiten

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Rect
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.gun.baustellen.reloaded.daten.AppDaten
import de.gun.baustellen.reloaded.daten.Auftrag
import de.gun.baustellen.reloaded.daten.Position
import de.gun.baustellen.reloaded.daten.Regiebericht
import de.gun.baustellen.reloaded.daten.Speicher
import de.gun.baustellen.reloaded.daten.dateiname
import de.gun.baustellen.reloaded.daten.neueId
import de.gun.baustellen.reloaded.logik.alsDE
import de.gun.baustellen.reloaded.logik.alsIso
import de.gun.baustellen.reloaded.logik.auftrag
import de.gun.baustellen.reloaded.logik.isoNachDE
import de.gun.baustellen.reloaded.logik.kundeName
import de.gun.baustellen.reloaded.logik.parseDE
import de.gun.baustellen.reloaded.logik.regieMinuten
import de.gun.baustellen.reloaded.logik.stundenText
import de.gun.baustellen.reloaded.logik.zahlFeld
import de.gun.baustellen.reloaded.logik.zahlLesen
import de.gun.baustellen.reloaded.logik.zeitNormieren
import de.gun.baustellen.reloaded.pdf.RegieberichtPdf
import de.gun.baustellen.reloaded.ui.Abstand
import de.gun.baustellen.reloaded.ui.DatumFeld
import de.gun.baustellen.reloaded.ui.Etikett
import de.gun.baustellen.reloaded.ui.Feld
import de.gun.baustellen.reloaded.ui.Hinweis
import de.gun.baustellen.reloaded.ui.Karte
import de.gun.baustellen.reloaded.ui.Knopf
import de.gun.baustellen.reloaded.ui.KnopfArt
import de.gun.baustellen.reloaded.ui.Knopfreihe
import de.gun.baustellen.reloaded.ui.LocalPalette
import de.gun.baustellen.reloaded.ui.LocalSteuerung
import de.gun.baustellen.reloaded.ui.Mono
import de.gun.baustellen.reloaded.ui.RUND_KLEIN
import de.gun.baustellen.reloaded.ui.RegieMaskeStart
import de.gun.baustellen.reloaded.ui.Steuerung
import de.gun.baustellen.reloaded.ui.VorschlagFeld
import de.gun.baustellen.reloaded.ui.ZeitFeld
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.util.Base64

private class PosZeile(beschreibung: String, menge: String, einheit: String) {
    var beschreibung by mutableStateOf(beschreibung)
    var menge by mutableStateOf(menge)
    var einheit by mutableStateOf(einheit)
}

/** Regiebericht als PDF erzeugen und über das Teilen-Menü (z. B. an die Mail-App) geben. */
fun regiePdfTeilen(ctx: Context, st: Steuerung, d: AppDaten, a: Auftrag, r: Regiebericht) {
    try {
        val name = "Regiebericht_" + dateiname(a.titel.ifBlank { "Auftrag" }) + "_" + r.datum + ".pdf"
        ausgabeTeilen(ctx, name, "application/pdf") { aus ->
            RegieberichtPdf.schreiben(d.einstellungen.firma, a, d.kundeName(a.kundeId), r, aus)
        }
    } catch (e: Exception) {
        st.melden("PDF", "Das PDF konnte nicht erstellt werden.\n\n" + (e.message ?: e.toString()))
    }
}

@Composable
fun RegieMaskeEbene(start: RegieMaskeStart) {
    val p = LocalPalette.current
    val st = LocalSteuerung.current
    val ctx = LocalContext.current
    val d = aktuelleDaten()
    val a = d.auftrag(start.auftragId)
    val alt = remember(start) { a?.regieberichte?.firstOrNull { it.id == start.berichtId } }

    var datum by remember(start) { mutableStateOf(alt?.let { isoNachDE(it.datum) } ?: LocalDate.now().alsDE()) }
    var mitarbeiter by remember(start) { mutableStateOf(alt?.mitarbeiter ?: "") }
    var beginn by remember(start) { mutableStateOf(alt?.beginn ?: "07:00") }
    var ende by remember(start) { mutableStateOf(alt?.ende ?: "16:00") }
    var pause by remember(start) { mutableStateOf(zahlFeld(alt?.pause ?: 30.0)) }
    var maschine by remember(start) { mutableStateOf(alt?.maschine ?: a?.maschine ?: "") }
    var mstd by remember(start) { mutableStateOf(alt?.let { if (it.maschinenstunden > 0) zahlFeld(it.maschinenstunden) else "" } ?: "") }
    var wetterBoden by remember(start) { mutableStateOf(alt?.wetterBoden ?: "") }
    var unterschriftName by remember(start) { mutableStateOf(alt?.unterschriftName ?: a?.let { d.kundeName(it.kundeId) } ?: "") }
    var notiz by remember(start) { mutableStateOf(alt?.notiz ?: "") }
    val positionen = remember(start) {
        mutableStateListOf<PosZeile>().apply {
            val l = alt?.positionen.orEmpty()
            if (l.isEmpty()) add(PosZeile("", "", ""))
            else l.forEach { add(PosZeile(it.beschreibung, zahlFeld(it.menge), it.einheit)) }
        }
    }
    // Unterschrift: vorhandene bleibt erhalten, solange das Feld nicht berührt wird
    var bestehend by remember(start) { mutableStateOf(alt?.unterschriftBild ?: "") }
    val striche = remember(start) { mutableStateListOf<List<Offset>>() }
    var aktuell by remember(start) { mutableStateOf<List<Offset>>(emptyList()) }
    var geaendert by remember(start) { mutableStateOf(false) }
    var flaeche by remember { mutableStateOf(IntSize.Zero) }
    val dichte = LocalDensity.current.density

    val bekannteMitarbeiter = remember(d) {
        d.auftraege.eintraege.flatMap { it.regieberichte }.map { it.mitarbeiter }.filter { it.isNotBlank() }.distinct().sorted()
    }
    val minuten = regieMinuten(zeitNormieren(beginn) ?: beginn, zeitNormieren(ende) ?: ende, zahlLesen(pause) ?: 0.0)

    fun unterschriftBild(): String {
        if (!geaendert) return bestehend
        if (striche.isEmpty() && bestehend.isBlank()) return ""
        val w = flaeche.width.coerceAtLeast(1)
        val h = flaeche.height.coerceAtLeast(1)
        val zielB = 600
        val faktor = zielB.toFloat() / w
        val zielH = (h * faktor).toInt().coerceAtLeast(1)
        val bild = Bitmap.createBitmap(zielB, zielH, Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(bild)
        c.drawColor(android.graphics.Color.WHITE)
        RegieberichtPdf.unterschrift(bestehend)?.let { alt0 -> c.drawBitmap(alt0, null, Rect(0, 0, zielB, zielH), Paint(Paint.FILTER_BITMAP_FLAG)) }
        val stift = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF14181F.toInt(); style = Paint.Style.STROKE; strokeWidth = 2.4f * dichte * faktor
            strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        }
        striche.forEach { s ->
            if (s.size == 1) c.drawPoint(s[0].x * faktor, s[0].y * faktor, stift)
            s.zipWithNext().forEach { (x, y) -> c.drawLine(x.x * faktor, x.y * faktor, y.x * faktor, y.y * faktor, stift) }
        }
        val aus = ByteArrayOutputStream()
        bild.compress(Bitmap.CompressFormat.PNG, 100, aus)
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(aus.toByteArray())
    }

    fun speichern(mitPdf: Boolean) {
        if (a == null) { st.regieMaske = null; return }
        val tag = parseDE(datum) ?: run { st.melden("Regiebericht", "Bitte ein gültiges Datum (TT.MM.JJJJ) eingeben."); return }
        val b = zeitNormieren(beginn) ?: "07:00"
        val e = zeitNormieren(ende) ?: "16:00"
        val pa = zahlLesen(pause) ?: 0.0
        val neu = (alt ?: Regiebericht(id = neueId())).copy(
            datum = tag.alsIso(), mitarbeiter = mitarbeiter.trim(), beginn = b, ende = e, pause = pa,
            nettoMinuten = regieMinuten(b, e, pa).toDouble(), maschine = maschine.trim(), maschinenstunden = zahlLesen(mstd) ?: 0.0,
            positionen = positionen.filter { it.beschreibung.isNotBlank() }.map { Position(it.beschreibung.trim(), zahlLesen(it.menge), it.einheit.trim()) },
            wetterBoden = wetterBoden.trim(), unterschriftName = unterschriftName.trim(), notiz = notiz.trim(),
            unterschriftBild = unterschriftBild(),
        )
        Speicher.auftrag(a.id) { x ->
            x.copy(regieberichte = if (alt != null) x.regieberichte.map { if (it.id == neu.id) neu else it } else x.regieberichte + neu)
        }
        st.regieMaske = null
        st.kurz("Regiebericht gespeichert")
        if (mitPdf) regiePdfTeilen(ctx, st, Speicher.aktuell, Speicher.aktuell.auftrag(a.id) ?: a, neu)
    }

    Column {
        EbenenKopf(if (alt != null) "Regiebericht bearbeiten" else "Neuer Regiebericht", { st.regieMaske = null })
        SeitenListe {
            item {
                Karte(a?.titel ?: "Auftrag", "01") {
                    Zweier(
                        { m -> DatumFeld(datum, { datum = it }, "Datum", m) },
                        { m -> Box(m) { VorschlagFeld(mitarbeiter, { mitarbeiter = it }, "Mitarbeiter", bekannteMitarbeiter.map { it to it }) } },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ZeitFeld(beginn, { beginn = it }, "Arbeitsbeginn", Modifier.weight(1f))
                        ZeitFeld(ende, { ende = it }, "Arbeitsende", Modifier.weight(1f))
                        Feld(pause, { pause = it }, "Pause (Min.)", Modifier.weight(1f), tastatur = KeyboardType.Number)
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp).clip(RUND_KLEIN).background(p.panelAlt).padding(10.dp)) {
                        Mono("Arbeitszeit " + stundenText(minuten.toDouble()), p.text, 13.sp, fett = true)
                    }
                    Zweier(
                        { m -> Feld(maschine, { maschine = it }, "Maschine/Gerät", m, platzhalter = "z. B. Kettenbagger CAT 320") },
                        { m -> Feld(mstd, { mstd = it }, "Betriebsstunden", m, platzhalter = "optional", tastatur = KeyboardType.Decimal) },
                    )
                }
            }
            item {
                Karte("Ausgeführte Leistungen", "02") {
                    positionen.forEachIndexed { i, pz ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Feld(pz.beschreibung, { pz.beschreibung = it }, "Beschreibung", Modifier.weight(1f), mehrzeilig = true)
                            Feld(pz.menge, { pz.menge = it }, "Menge", Modifier.weight(0.38f), tastatur = KeyboardType.Decimal)
                            Feld(pz.einheit, { pz.einheit = it }, "Einh.", Modifier.weight(0.32f), platzhalter = "m³")
                            LoeschKnopf { positionen.removeAt(i) }
                        }
                    }
                    Knopfreihe { Knopf("+ Position", klein = true) { positionen.add(PosZeile("", "", "")) } }
                }
            }
            item {
                Karte("Verhältnisse & Anmerkung", "03") {
                    Feld(wetterBoden, { wetterBoden = it }, "Wetter / Bodenverhältnisse", platzhalter = "z. B. trocken, fest")
                    Feld(notiz, { notiz = it }, "Anmerkung", platzhalter = "Besonderheiten, Erschwernisse, Absprachen …", zeilen = 3)
                }
            }
            item {
                Karte("Unterschrift Auftraggeber", "04") {
                    Feld(unterschriftName, { unterschriftName = it }, "Name (Unterschriftszeile)", platzhalter = "wer bestätigt vor Ort")
                    Abstand(8.dp)
                    Etikett("Mit Finger oder Stift unterschreiben (optional)")
                    val alteUnterschrift = remember(bestehend) { RegieberichtPdf.unterschrift(bestehend)?.asImageBitmap() }
                    Canvas(
                        Modifier.fillMaxWidth().padding(top = 6.dp).height(170.dp).clip(RUND_KLEIN).background(Color.White)
                            .border(1.dp, p.rand, RUND_KLEIN)
                            .onSizeChanged { flaeche = it }
                            .pointerInput(start) {
                                detectTapGestures { o -> striche.add(listOf(o)); geaendert = true }
                            }
                            .pointerInput(start) {
                                detectDragGestures(
                                    onDragStart = { o -> aktuell = listOf(o); geaendert = true },
                                    onDrag = { change, _ -> change.consume(); aktuell = aktuell + change.position },
                                    onDragEnd = { striche.add(aktuell); aktuell = emptyList() },
                                    onDragCancel = { striche.add(aktuell); aktuell = emptyList() },
                                )
                            }
                    ) {
                        alteUnterschrift?.let { drawImage(it, dstOffset = IntOffset.Zero, dstSize = IntSize(size.width.toInt(), size.height.toInt())) }
                        val farbe = Color(0xFF14181F)
                        (striche + listOf(aktuell)).forEach { s ->
                            if (s.isEmpty()) return@forEach
                            if (s.size == 1) drawCircle(farbe, 1.4f * density, s[0])
                            else {
                                val pfad = Path().apply { moveTo(s[0].x, s[0].y); s.drop(1).forEach { lineTo(it.x, it.y) } }
                                drawPath(pfad, farbe, style = Stroke(2.4f * density, cap = StrokeCap.Round, join = StrokeJoin.Round))
                            }
                        }
                    }
                    Knopfreihe {
                        Knopf("Unterschrift löschen", klein = true) { striche.clear(); aktuell = emptyList(); bestehend = ""; geaendert = true }
                    }
                    Hinweis("Ohne Berührung des Feldes bleibt eine vorhandene Unterschrift erhalten.")
                }
            }
            item {
                Column {
                    FormKnoepfe("Regiebericht speichern", { speichern(false) }) { st.regieMaske = null }
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                        Knopf("📄 Speichern & als PDF teilen", art = KnopfArt.NORMAL) { speichern(true) }
                    }
                }
            }
        }
    }
}
