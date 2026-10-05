package de.gun.baustellen.reloaded.netz

import kotlinx.coroutines.ensureActive
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import de.gun.baustellen.reloaded.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

sealed class UpdateZustand {
    data object Leer : UpdateZustand()
    data class Laedt(val version: String, val fertig: Long, val gesamt: Long) : UpdateZustand()
    data class Bereit(val version: String, val datei: File) : UpdateZustand()
    data class Fehler(val text: String) : UpdateZustand()
}

/**
 * Lädt die APK einer neuen Version direkt aus dem GitHub-Release und startet die Installation
 *. Der Download läuft unabhängig von der Oberfläche weiter.
 */
object UpdateLader {
    val zustand = MutableStateFlow<UpdateZustand>(UpdateZustand.Leer)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    fun starten(ctx: Context, info: UpdateInfo) {
        if (job?.isActive == true) return
        val app = ctx.applicationContext
        if (info.apkUrl.isBlank()) { zustand.value = UpdateZustand.Fehler("Für diese Version gibt es keine APK-Datei."); return }
        val ziel = File(app.cacheDir, "updates/BaustellenPilotReloaded-${info.version}.apk")
        job = scope.launch {
            zustand.value = UpdateZustand.Laedt(info.version, 0, info.groesse)
            try {
                ziel.parentFile?.listFiles()?.forEach { if (it != ziel) it.delete() }
                laden(info.apkUrl, ziel) { f, g -> zustand.value = UpdateZustand.Laedt(info.version, f, if (g > 0) g else info.groesse) }
                pruefen(app, ziel)
                zustand.value = UpdateZustand.Bereit(info.version, ziel)
                installieren(app, ziel)
            } catch (e: CancellationException) {
                ziel.delete(); zustand.value = UpdateZustand.Leer
            } catch (e: Exception) {
                ziel.delete(); zustand.value = UpdateZustand.Fehler("Download fehlgeschlagen: " + (e.message ?: "keine Verbindung"))
            }
        }
    }

    fun abbrechen() { job?.cancel(); zustand.value = UpdateZustand.Leer }

    fun schliessen() { if (job?.isActive != true) zustand.value = UpdateZustand.Leer }

    /** Nur eine APK derselben App mit höherer Versionsnummer wird zur Installation angeboten. */
    private fun pruefen(ctx: Context, datei: File) {
        val info = ctx.packageManager.getPackageArchiveInfo(datei.path, 0)
            ?: throw IOException("Die heruntergeladene Datei ist keine gültige APK.")
        if (info.packageName != ctx.packageName) throw IOException("Die APK gehört zu einer anderen App.")
        val code = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
        if (code <= BuildConfig.VERSION_CODE) throw IOException("Die APK ist nicht neuer als die installierte Version.")
    }

    /** Öffnet den Android-Installer; fehlt die Erlaubnis „Unbekannte Apps installieren“, zuerst deren Einstellung. */
    fun installieren(ctx: Context, datei: File): Boolean {
        if (Build.VERSION.SDK_INT >= 26 && !ctx.packageManager.canRequestPackageInstalls()) {
            ctx.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + ctx.packageName))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return false
        }
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".dateien", datei)
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return true
    }

    private suspend fun laden(url: String, ziel: File, fortschritt: (Long, Long) -> Unit) {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000; c.readTimeout = 30_000; c.instanceFollowRedirects = true
        c.setRequestProperty("Accept", "application/octet-stream")
        c.setRequestProperty("User-Agent", "BaustellenPilotReloaded/" + BuildConfig.VERSION_NAME)
        try {
            if (c.responseCode !in 200..299) throw IOException("Server antwortet mit ${c.responseCode}")
            val gesamt = c.contentLengthLong
            ziel.parentFile?.mkdirs()
            c.inputStream.use { ein ->
                FileOutputStream(ziel).use { aus ->
                    val puffer = ByteArray(64 * 1024)
                    var fertig = 0L
                    var zuletzt = 0L
                    while (kotlinx.coroutines.currentCoroutineContext().isActive) {
                        val n = ein.read(puffer)
                        if (n < 0) break
                        aus.write(puffer, 0, n)
                        fertig += n
                        if (fertig - zuletzt > 128 * 1024) { fortschritt(fertig, gesamt); zuletzt = fertig }
                    }
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    fortschritt(fertig, gesamt)
                }
            }
        } finally {
            c.disconnect()
        }
    }
}
