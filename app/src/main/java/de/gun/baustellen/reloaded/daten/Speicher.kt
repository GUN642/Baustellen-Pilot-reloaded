package de.gun.baustellen.reloaded.daten

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

/**
 * Hält den gesamten Datenbestand im Speicher und schreibt ihn bei jeder
 * Änderung (kurz gebündelt) atomar in files/daten.json. Die Android-
 * Systemsicherung erfasst diese Datei mit.
 */
object Speicher {
    private lateinit var datei: AtomicFile
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var schreibJob: Job? = null
    private val sperre = Any()

    private val _daten = MutableStateFlow(AppDaten())
    val daten: StateFlow<AppDaten> = _daten.asStateFlow()

    val aktuell: AppDaten get() = _daten.value

    @Volatile private var geladen = false

    fun init(context: Context) {
        synchronized(sperre) {
            if (geladen) return
            datei = AtomicFile(File(context.applicationContext.filesDir, "daten.json"))
            val gelesen = try {
                if (datei.baseFile.exists()) {
                    JsonFormat.decodeFromString(AppDaten.serializer(), String(datei.readFully(), Charsets.UTF_8))
                } else null
            } catch (e: Exception) {
                null
            }
            _daten.value = migrieren(gelesen ?: AppDaten())
            geladen = true
        }
    }

    /** Nur für Vorschauen/Tests: Bestand setzen, ohne Datei. */
    fun vorschau(d: AppDaten) {
        _daten.value = d
    }

    fun aendern(block: (AppDaten) -> AppDaten) {
        _daten.update(block)
        schreibenPlanen()
    }

    /** Einen Auftrag ändern (per Kennung). */
    fun auftrag(id: String, block: (Auftrag) -> Auftrag) = aendern { d ->
        d.copy(auftraege = d.auftraege.copy(eintraege = d.auftraege.eintraege.map { if (it.id == id) block(it) else it }))
    }

    /** Ersetzt den gesamten Bestand (Import). */
    fun ersetzen(neu: AppDaten) {
        _daten.value = migrieren(neu)
        sofortSchreiben()
    }

    private fun schreibenPlanen() {
        schreibJob?.cancel()
        schreibJob = scope.launch {
            delay(250)
            schreiben(_daten.value)
        }
    }

    /** Sofort schreiben, etwa wenn die App in den Hintergrund geht. */
    fun sofortSchreiben() {
        schreibJob?.cancel()
        schreiben(_daten.value)
    }

    private fun schreiben(stand: AppDaten) {
        synchronized(sperre) {
            if (!geladen) return
            val text = JsonFormat.encodeToString(AppDaten.serializer(), stand)
            val aus = try { datei.startWrite() } catch (e: Exception) { return }
            try {
                aus.write(text.toByteArray(Charsets.UTF_8))
                datei.finishWrite(aus)
            } catch (e: Exception) {
                datei.failWrite(aus)
            }
        }
    }

    /** Fehlende Kennungen und Altlasten bereinigen (auch nach einem Import). */
    fun migrieren(d: AppDaten): AppDaten {
        fun id(s: String) = s.ifBlank { neueId() }
        val auftraege = d.auftraege.eintraege.map { a ->
            a.copy(
                id = id(a.id),
                status = if (a.status in STATUS) a.status else "ausstehend",
                basis = if (a.basis == "brutto") "brutto" else "netto",
                bis = a.bis.ifBlank { a.von },
                material = a.material.map { it.copy(id = id(it.id), einheit = if (it.einheit == "m3") "m3" else "t") },
                regieberichte = a.regieberichte.map { it.copy(id = id(it.id)) },
            )
        }
        val kunden = d.kunden.eintraege.map { it.copy(id = id(it.id)) }
        return d.copy(auftraege = Auftraege(auftraege), kunden = Kunden(kunden))
    }
}

fun neueId(): String = UUID.randomUUID().toString()
