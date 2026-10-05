package de.gun.baustellen.reloaded.ui

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class Reiter(val titel: String) {
    WOCHE("Woche"), AUFTRAEGE("Aufträge"), KUNDEN("Kunden"), MATERIAL("Material"), KALENDER("Kalender"), STATISTIK("Statistik")
}

/** Aufruf der Auftragsmaske: bestehender Auftrag oder neuer, ggf. mit Startdatum. */
data class AuftragMaskeStart(val id: String? = null, val datum: LocalDate? = null, val kundeId: String? = null)
data class KundeMaskeStart(val id: String? = null)
data class MaterialMaskeStart(val auftragId: String, val materialId: String? = null)
data class RegieMaskeStart(val auftragId: String, val berichtId: String? = null)
data class FotoStart(val auftragId: String, val fotoId: String)

/** Globaler Zustand der Oberfläche: Reiter, Ebenen, Meldungen. */
class Steuerung(private val scope: CoroutineScope, val snackbar: SnackbarHostState) {
    var reiter by mutableStateOf(Reiter.WOCHE)
    var menueOffen by mutableStateOf(false)
    var sucheOffen by mutableStateOf(false)
    var changelogOffen by mutableStateOf(false)
    var meldung by mutableStateOf<Pair<String, String>?>(null)

    var detail by mutableStateOf<String?>(null)
    var auftragMaske by mutableStateOf<AuftragMaskeStart?>(null)
    var kundeMaske by mutableStateOf<KundeMaskeStart?>(null)
    var materialMaske by mutableStateOf<MaterialMaskeStart?>(null)
    var regieMaske by mutableStateOf<RegieMaskeStart?>(null)
    var foto by mutableStateOf<FotoStart?>(null)

    /** Woche: angezeigte Woche (beliebiger Tag darin). */
    var wochenAnker by mutableStateOf(LocalDate.now())
    var auftragFilter by mutableStateOf("alle")

    /** Kalender: gewählter Tag und angezeigter Monat. */
    var kalenderTag by mutableStateOf<LocalDate?>(null)
    var kalenderMonat by mutableStateOf(LocalDate.now().withDayOfMonth(1))
    var kalenderEinstellungen by mutableStateOf(false)
    var kalenderGross by mutableStateOf(false)

    private var rueckgaengigJob: Job? = null
    private var ausstehend: (() -> Unit)? = null

    fun melden(titel: String, text: String) { meldung = titel to text }

    fun kurz(text: String) {
        scope.launch { snackbar.showSnackbar(text, duration = SnackbarDuration.Short) }
    }

    /**
     * Löschen mit kurzer Rückgängig-Chance. Der Aufrufer hat bereits gelöscht;
     * [endgueltig] läuft erst, wenn die Frist ohne Rückgängig abläuft (z. B.
     * Dateien entfernen).
     */
    fun rueckgaengig(text: String, wiederherstellen: () -> Unit, endgueltig: (() -> Unit)? = null) {
        ausstehend?.invoke()
        ausstehend = endgueltig
        rueckgaengigJob?.cancel()
        snackbar.currentSnackbarData?.dismiss()
        rueckgaengigJob = scope.launch {
            val r = snackbar.showSnackbar(text, actionLabel = "Rückgängig", duration = SnackbarDuration.Long)
            if (r == SnackbarResult.ActionPerformed) {
                ausstehend = null
                wiederherstellen()
            } else {
                ausstehend?.invoke()
                ausstehend = null
            }
        }
    }

    fun auftragOeffnen(id: String) {
        detail = id
    }

    /** Zurück-Taste: oberste Ebene schließen. true = behandelt. */
    fun zurueck(): Boolean {
        when {
            meldung != null -> meldung = null
            foto != null -> foto = null
            regieMaske != null -> regieMaske = null
            materialMaske != null -> materialMaske = null
            kundeMaske != null -> kundeMaske = null
            auftragMaske != null -> auftragMaske = null
            detail != null -> detail = null
            changelogOffen -> changelogOffen = false
            sucheOffen -> sucheOffen = false
            menueOffen -> menueOffen = false
            reiter == Reiter.KALENDER && kalenderEinstellungen -> kalenderEinstellungen = false
            reiter == Reiter.KALENDER && kalenderGross -> kalenderGross = false
            reiter == Reiter.KALENDER && kalenderTag != null -> kalenderTag = null
            reiter != Reiter.WOCHE -> reiter = Reiter.WOCHE
            else -> return false
        }
        return true
    }
}

val LocalSteuerung = staticCompositionLocalOf<Steuerung> { error("Keine Steuerung") }
