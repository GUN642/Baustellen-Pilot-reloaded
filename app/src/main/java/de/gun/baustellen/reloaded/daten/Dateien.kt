package de.gun.baustellen.reloaded.daten

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Unterlagen und Fotos der Aufträge liegen als Dateien unter files/dateien,
 * benannt nach ihrer Kennung – wie im alten Baustellen Pilot die Einträge der
 * Gerätedatenbank. Fotos werden vor dem Ablegen auf 1600 px längste Kante
 * verkleinert (JPEG).
 */
object Dateien {
    private const val ORDNER = "dateien"
    private const val KANTE = 1600
    const val MAX_BYTES = 25L * 1024 * 1024

    private fun ordner(ctx: Context) = File(ctx.filesDir, ORDNER).apply { mkdirs() }

    fun datei(ctx: Context, id: String): File = File(ordner(ctx), id.replace(Regex("[^\\w\\-]"), "_"))

    fun vorhanden(ctx: Context, id: String) = datei(ctx, id).exists()

    private fun anzeigeName(ctx: Context, uri: Uri): Pair<String, Long> {
        var name = "Datei"
        var groesse = -1L
        try {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    c.getString(0)?.let { name = it }
                    if (!c.isNull(1)) groesse = c.getLong(1)
                }
            }
        } catch (e: Exception) { }
        return name to groesse
    }

    /** Übernimmt eine gewählte Unterlage (PDF, Bild, …) unverändert. */
    suspend fun unterlageUebernehmen(ctx: Context, uri: Uri, kategorie: String, datum: String): DateiInfo = withContext(Dispatchers.IO) {
        val (name, groesse) = anzeigeName(ctx, uri)
        if (groesse > MAX_BYTES) throw IllegalStateException("Die Datei ist zu groß (höchstens 25 MB).")
        val typ = ctx.contentResolver.getType(uri) ?: "application/octet-stream"
        val id = neueId()
        val ziel = datei(ctx, id)
        ctx.contentResolver.openInputStream(uri)?.use { ein -> ziel.outputStream().use { ein.copyTo(it) } }
            ?: throw IllegalStateException("Die Datei ist nicht lesbar.")
        DateiInfo(id, name, typ, ziel.length().toDouble(), kategorie.ifBlank { "Unterlage" }, datum)
    }

    /** Übernimmt ein Foto aus der Galerie oder der Kamera, verkleinert. */
    suspend fun fotoUebernehmen(ctx: Context, uri: Uri, datum: String): Foto = withContext(Dispatchers.IO) {
        val (name, _) = anzeigeName(ctx, uri)
        val roh = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("Das Foto ist nicht lesbar.")
        val bytes = bildVerkleinern(roh) ?: roh
        val id = neueId()
        datei(ctx, id).writeBytes(bytes)
        Foto(id, if (name == "Datei") "Foto_$datum.jpg" else name, bytes.size.toDouble(), datum, "")
    }

    /** Ziel für ein Kamerafoto (wird danach mit [fotoUebernehmen] übernommen). */
    fun kameraZiel(ctx: Context): Uri {
        val f = File(File(ctx.cacheDir, "kamera").apply { mkdirs() }, "aufnahme.jpg")
        f.delete()
        return FileProvider.getUriForFile(ctx, ctx.packageName + ".dateien", f)
    }

    private fun bildVerkleinern(roh: ByteArray): ByteArray? = try {
        val grenzen = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(roh, 0, roh.size, grenzen)
        var probe = 1
        while (maxOf(grenzen.outWidth, grenzen.outHeight) / (probe * 2) >= KANTE) probe *= 2
        var bild = BitmapFactory.decodeByteArray(roh, 0, roh.size, BitmapFactory.Options().apply { inSampleSize = probe })
        // Kamerafotos tragen die Drehung nur als EXIF-Angabe
        val drehung = try {
            when (ExifInterface(ByteArrayInputStream(roh)).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } catch (e: Exception) { 0f }
        if (drehung != 0f) bild = Bitmap.createBitmap(bild, 0, 0, bild.width, bild.height, Matrix().apply { postRotate(drehung) }, true)
        val faktor = minOf(1.0, KANTE.toDouble() / maxOf(bild.width, bild.height))
        val b = maxOf(1, (bild.width * faktor).toInt())
        val h = maxOf(1, (bild.height * faktor).toInt())
        val ziel = Bitmap.createBitmap(b, h, Bitmap.Config.ARGB_8888)
        Canvas(ziel).apply {
            drawColor(Color.WHITE)
            drawBitmap(Bitmap.createScaledBitmap(bild, b, h, true), 0f, 0f, null)
        }
        ByteArrayOutputStream().also { ziel.compress(Bitmap.CompressFormat.JPEG, 82, it) }.toByteArray()
    } catch (e: Throwable) {
        null
    }

    /** Vorschaubild (verkleinert geladen). */
    fun vorschau(ctx: Context, id: String, kante: Int = 400): Bitmap? = try {
        val f = datei(ctx, id)
        if (!f.exists()) null else {
            val g = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.path, g)
            var probe = 1
            while (maxOf(g.outWidth, g.outHeight) / (probe * 2) >= kante) probe *= 2
            BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = probe })
        }
    } catch (e: Throwable) {
        null
    }

    fun loeschen(ctx: Context, ids: List<String>) {
        ids.forEach { datei(ctx, it).delete() }
    }

    /** Kopie im Ausgabeordner mit sprechendem Dateinamen (für Öffnen/Teilen). */
    private fun ausgabeKopie(ctx: Context, id: String, name: String): File? {
        val f = datei(ctx, id)
        if (!f.exists()) return null
        val aus = File(ctx.cacheDir, "ausgabe").apply { mkdirs() }
        val ziel = File(aus, dateiname(name.ifBlank { id }))
        f.copyTo(ziel, overwrite = true)
        return ziel
    }

    fun oeffnen(ctx: Context, id: String, name: String, typ: String): Boolean {
        val f = ausgabeKopie(ctx, id, name) ?: return false
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".dateien", f)
        val i = Intent(Intent.ACTION_VIEW).setDataAndType(uri, typ.ifBlank { "*/*" })
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            ctx.startActivity(Intent.createChooser(i, name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
        } catch (e: Exception) { false }
    }

    fun teilen(ctx: Context, id: String, name: String, typ: String): Boolean {
        val f = ausgabeKopie(ctx, id, name) ?: return false
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".dateien", f)
        val i = Intent(Intent.ACTION_SEND).setType(typ.ifBlank { "*/*" }).putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return try {
            ctx.startActivity(Intent.createChooser(i, name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
        } catch (e: Exception) { false }
    }

    /** Alle Kennungen, auf die der Bestand verweist. */
    fun benutzt(d: AppDaten): Set<String> =
        d.auftraege.eintraege.flatMap { a -> a.dateien.map { it.id } + a.fotos.map { it.id } }.toSet()

    /** Dateien ohne Verweis entfernen (etwa nach gelöschtem Auftrag). */
    fun verwaisteAufraeumen(ctx: Context, d: AppDaten) {
        val namen = benutzt(d).map { datei(ctx, it).name }.toSet()
        ordner(ctx).listFiles()?.forEach { if (it.name !in namen) it.delete() }
    }

    /** Anzahl und Gesamtgröße der abgelegten Dateien. */
    fun belegung(ctx: Context): Pair<Int, Long> {
        val l = ordner(ctx).listFiles() ?: return 0 to 0L
        return l.size to l.sumOf { it.length() }
    }
}

/** Dateiname ohne problematische Zeichen. */
fun dateiname(text: String): String =
    text.replace(Regex("[^\\wäöüÄÖÜß.\\- ]"), "_").trim().replace(Regex("\\s+"), "_").take(80).ifBlank { "Datei" }

fun groesseText(bytes: Double): String = when {
    bytes < 1024 -> "${bytes.toLong()} B"
    bytes < 1024 * 1024 -> "${(bytes / 1024).toLong()} KB"
    else -> String.format(java.util.Locale.GERMANY, "%.1f MB", bytes / 1024 / 1024)
}
