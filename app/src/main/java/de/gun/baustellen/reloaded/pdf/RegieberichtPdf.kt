package de.gun.baustellen.reloaded.pdf

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import de.gun.baustellen.reloaded.daten.Auftrag
import de.gun.baustellen.reloaded.daten.Firma
import de.gun.baustellen.reloaded.daten.Regiebericht
import de.gun.baustellen.reloaded.logik.anschrift
import de.gun.baustellen.reloaded.logik.de
import de.gun.baustellen.reloaded.logik.zahl
import java.io.OutputStream
import java.util.Base64

/**
 * Regiebericht als A4-PDF – vollständig auf dem Gerät erzeugt, ohne
 * Internetverbindung. Ein Regiebericht entsteht oft am Ende eines
 * Arbeitstags direkt auf der Baustelle.
 */
object RegieberichtPdf {
    private const val BREITE = 595
    private const val HOEHE = 842
    private const val RAND_L = 42f
    private const val RAND_R = 42f
    private const val OBEN = 50f
    private const val UNTEN = 790f

    private class Schreiber(val dok: PdfDocument, val fortsetzung: String) {
        var nr = 0
        lateinit var seite: PdfDocument.Page
        lateinit var c: Canvas
        var y = OBEN

        fun neueSeite() {
            if (nr > 0) dok.finishPage(seite)
            nr++
            seite = dok.startPage(PdfDocument.PageInfo.Builder(BREITE, HOEHE, nr).create())
            c = seite.canvas
            y = OBEN
            if (nr > 1) {
                text(fortsetzung, RAND_L, 9f, fett = true)
                y += 22f
            }
        }

        fun platz(noetig: Float) {
            if (y + noetig > UNTEN) neueSeite()
        }

        fun stift(groesse: Float, fett: Boolean = false, grau: Int = 0x14181F) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = groesse
            color = Color.rgb(grau shr 16 and 0xFF, grau shr 8 and 0xFF, grau and 0xFF)
            typeface = Typeface.create(Typeface.SANS_SERIF, if (fett) Typeface.BOLD else Typeface.NORMAL)
        }

        fun text(t: String, x: Float, groesse: Float, fett: Boolean = false, grau: Int = 0x14181F) {
            c.drawText(t, x, y, stift(groesse, fett, grau))
        }

        fun linie(dicke: Float = 0.7f, grau: Int = 0xBFBFBF, x1: Float = RAND_L, x2: Float = BREITE - RAND_R) {
            val p = Paint().apply { strokeWidth = dicke; color = Color.rgb(grau shr 16 and 0xFF, grau shr 8 and 0xFF, grau and 0xFF) }
            c.drawLine(x1, y, x2, y, p)
        }

        /** Text in Zeilen umbrechen, die in [breite] passen. */
        fun umbrechen(t: String, breite: Float, groesse: Float, fett: Boolean = false): List<String> {
            val p = stift(groesse, fett)
            val aus = mutableListOf<String>()
            t.split("\n").forEach { absatz ->
                var zeile = ""
                absatz.split(" ").forEach { wort ->
                    val probe = if (zeile.isEmpty()) wort else "$zeile $wort"
                    if (p.measureText(probe) <= breite || zeile.isEmpty()) zeile = probe
                    else { aus += zeile; zeile = wort }
                }
                aus += zeile
            }
            return aus
        }
    }

    /** Unterschrift aus der data:-URL lesen. */
    fun unterschrift(dataUrl: String): Bitmap? = try {
        if (dataUrl.isBlank()) null else {
            val b = Base64.getMimeDecoder().decode(dataUrl.substringAfter("base64,", dataUrl))
            BitmapFactory.decodeByteArray(b, 0, b.size)
        }
    } catch (e: Exception) {
        null
    }

    fun schreiben(firma: Firma, a: Auftrag, kunde: String, r: Regiebericht, aus: OutputStream) {
        val dok = PdfDocument()
        val s = Schreiber(dok, (a.titel.ifBlank { "Regiebericht" }) + " — Fortsetzung")
        s.neueSeite()
        val rechts = BREITE - RAND_R

        // Kopf
        s.text(firma.name.ifBlank { "Firma" }, RAND_L, 15f, fett = true)
        val titel = "REGIEBERICHT"
        s.c.drawText(titel, rechts - s.stift(18f, true).measureText(titel), s.y, s.stift(18f, true))
        s.y += 15f
        listOf(firma.strasse, listOf(firma.plz, firma.ort).filter { it.isNotBlank() }.joinToString(" "), firma.telefon, firma.email)
            .filter { it.isNotBlank() }.forEach { s.text(it, RAND_L, 9f); s.y += 11f }
        s.y += 4f
        s.linie(1f, 0x262626)
        s.y += 22f

        // Auftrag
        s.text("Baustelle / Auftrag", RAND_L, 10f, fett = true)
        s.text("Datum", 340f, 10f, fett = true)
        s.y += 14f
        s.text(a.titel.ifBlank { "—" }, RAND_L, 10f)
        s.text(de(r.datum), 340f, 10f)
        s.y += 14f
        val anschrift = a.anschrift()
        if (anschrift.isNotBlank()) s.text(anschrift, RAND_L, 9f)
        if (a.nummer.isNotBlank()) s.text("Auftrag Nr. " + a.nummer, 340f, 9f)
        if (anschrift.isNotBlank() || a.nummer.isNotBlank()) s.y += 14f
        if (kunde.isNotBlank()) { s.text("Kunde: $kunde", RAND_L, 9f); s.y += 14f }
        s.y += 2f
        s.linie()
        s.y += 22f

        // Arbeitszeit
        s.text("Arbeitszeit", RAND_L, 10f, fett = true)
        s.y += 15f
        val spalten = listOf(
            "BEGINN" to r.beginn.ifBlank { "—" }, "ENDE" to r.ende.ifBlank { "—" },
            "PAUSE" to "${r.pause.toInt()} Min.", "ARBEITSZEIT" to zahl(r.nettoMinuten / 60.0, 1) + " Std.",
        )
        var x = RAND_L
        spalten.forEach { (label, wert) ->
            s.text(label, x, 8.5f, grau = 0x5A5A5A)
            s.y += 13f
            s.text(wert, x, 11f, fett = true)
            s.y -= 13f
            x += 128f
        }
        s.y += 26f
        s.linie()
        s.y += 22f

        // Personal / Maschine
        s.text("Personal / Maschine", RAND_L, 10f, fett = true)
        s.y += 15f
        s.text("Mitarbeiter: " + r.mitarbeiter.ifBlank { "—" }, RAND_L, 9f)
        s.y += 13f
        s.text("Maschine/Gerät: " + r.maschine.ifBlank { "—" } +
            (if (r.maschinenstunden > 0) "   ·   Betriebsstunden: " + zahl(r.maschinenstunden, 1) else ""), RAND_L, 9f)
        s.y += 18f
        s.linie()
        s.y += 22f

        // Leistungen
        s.platz(60f)
        s.text("Ausgeführte Leistungen", RAND_L, 10f, fett = true)
        s.y += 16f
        val posX = listOf(RAND_L, 400f, 470f)
        s.text("BESCHREIBUNG", posX[0], 8.5f, fett = true)
        s.text("MENGE", posX[1], 8.5f, fett = true)
        s.text("EINHEIT", posX[2], 8.5f, fett = true)
        s.y += 6f
        s.linie(0.6f)
        s.y += 13f
        val positionen = r.positionen.ifEmpty { listOf(de.gun.baustellen.reloaded.daten.Position("—")) }
        positionen.forEach { p ->
            val zeilen = s.umbrechen(p.beschreibung.ifBlank { "—" }, posX[1] - posX[0] - 12f, 9f)
            s.platz(14f * zeilen.size + 4f)
            p.menge?.let { s.text(zahl(it, 2), posX[1], 9f) }
            if (p.einheit.isNotBlank()) s.text(p.einheit, posX[2], 9f)
            zeilen.forEach { z -> s.text(z, posX[0], 9f); s.y += 13f }
            s.y += 1f
        }
        s.y += 4f
        s.linie()
        s.y += 20f

        // Wetter, Anmerkung
        if (r.wetterBoden.isNotBlank()) {
            s.platz(18f)
            s.text("Wetter/Boden: " + r.wetterBoden, RAND_L, 9f)
            s.y += 16f
        }
        if (r.notiz.isNotBlank()) {
            s.platz(30f)
            s.text("Anmerkung:", RAND_L, 9f, fett = true)
            s.y += 13f
            s.umbrechen(r.notiz, rechts - RAND_L, 9f).forEach { z ->
                s.platz(13f)
                s.text(z, RAND_L, 9f)
                s.y += 12f
            }
        }
        s.y += 12f

        // Bestätigung und Unterschrift
        s.platz(130f)
        s.linie()
        s.y += 18f
        s.text("Die oben genannten Leistungen wurden wie beschrieben ausgeführt und werden hiermit bestätigt.", RAND_L, 9f)
        s.y += 70f
        unterschrift(r.unterschriftBild)?.let { bild ->
            val h = 56f
            val b = minOf(220f, bild.width * h / maxOf(1, bild.height))
            s.c.drawBitmap(bild, null, RectF(RAND_L, s.y - h - 2f, RAND_L + b, s.y - 2f), Paint(Paint.FILTER_BITMAP_FLAG))
        }
        s.y += 2f
        s.linie(0.7f, 0x4D4D4D, RAND_L, RAND_L + 220f)
        s.linie(0.7f, 0x4D4D4D, 340f, 340f + 180f)
        s.y += 12f
        s.text("Unterschrift Auftraggeber", RAND_L, 8.5f, grau = 0x5A5A5A)
        s.text("Ort, Datum", 340f, 8.5f, grau = 0x5A5A5A)
        if (r.unterschriftName.isNotBlank()) {
            s.y += 12f
            s.text(r.unterschriftName, RAND_L, 9f)
        }

        dok.finishPage(s.seite)
        dok.writeTo(aus)
        dok.close()
    }
}
