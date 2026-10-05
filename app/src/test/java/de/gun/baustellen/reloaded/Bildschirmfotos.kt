package de.gun.baustellen.reloaded

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import de.gun.baustellen.reloaded.daten.*
import de.gun.baustellen.reloaded.ui.*
import de.gun.baustellen.reloaded.ui.seiten.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/** Erzeugt Bildschirmfotos aller Reiter (zur Sichtprüfung des Designs). */
class Bildschirmfotos {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5, maxPercentDifference = 100.0)

    private fun t(tage: Long) = LocalDate.now().plusDays(tage).toString()

    private fun beispiel(thema: String) = AppDaten(
        design = Design(thema = thema),
        kunden = Kunden(listOf(
            Kunde("k1", "Bauunternehmen Muster GmbH", "Herr Muster", "Hauptstraße 4", "89186", "Illerrieden", "0170 1234567", "info@muster.de"),
            Kunde("k2", "Familie Schmid", "", "Lindenweg 3", "89081", "Ulm", "0731 55555"),
        )),
        auftraege = Auftraege(listOf(
            Auftrag("a1", "Kellerausschachtung Lindenweg", "2026-014", "k2", "laufend", "Lindenweg 3", "89081", "Ulm", t(-1), t(2),
                betrag = 8400.0, maschine = "Kettenbagger CAT 320",
                material = listOf(
                    Material("m1", "Schotter 0/32", "bedarf", 40.0, "t", 18.5, t(-2), "Kieswerk Ulm"),
                    Material("m2", "Schotter 0/32", "verbrauch", 46.0, "t", 18.5, t(0), "Kieswerk Ulm"),
                    Material("m3", "Aushub Abfuhr", "verbrauch", 120.0, "m3", 9.0, t(0)),
                ),
                dateien = listOf(DateiInfo("d1", "Angebot_2026-014.pdf", "application/pdf", 182000.0, "Angebot", t(-20))),
                regieberichte = listOf(Regiebericht("r1", t(-1), "Max Müller", "07:00", "16:30", 30.0, 540.0, "CAT 320", 8.5,
                    listOf(Position("Baugrube ausheben", 120.0, "m³")), "trocken", "Herr Schmid")),
            ),
            Auftrag("a2", "Pflasterarbeiten Hof", "2026-015", "k1", "ausstehend", "Hauptstraße 4", "89186", "Illerrieden", t(4), t(8), betrag = 12500.0),
            Auftrag("a3", "Drainage Gartenseite", "", "k1", "abgeschlossen", "", "", "Dietenheim", t(-12), t(-10), betrag = 3200.0, basis = "brutto"),
            Auftrag("a4", "Besichtigung Neubau", "", "", "ausstehend", "", "", "Vöhringen", t(1), t(1), false, "10:00", "11:00"),
        )),
        tagesnotizen = mapOf(t(0) to "Bagger um 6:30 laden"),
        einstellungen = Einstellungen(firma = Firma("Erdbau Müller", "Industriestraße 1", "89186", "Illerrieden")),
    )

    @Composable
    private fun Rahmen(thema: String, reiter: Reiter, inhalt: @Composable () -> Unit) {
        Speicher.vorschau(beispiel(thema))
        BaustellenTheme(Design(thema = thema)) {
            val scope = rememberCoroutineScope()
            val st = remember { Steuerung(scope, SnackbarHostState()).also { it.reiter = reiter } }
            val registerOwner = remember {
                object : androidx.activity.result.ActivityResultRegistryOwner {
                    override val activityResultRegistry = object : androidx.activity.result.ActivityResultRegistry() {
                        override fun <I, O> onLaunch(
                            requestCode: Int,
                            contract: androidx.activity.result.contract.ActivityResultContract<I, O>,
                            input: I,
                            options: androidx.core.app.ActivityOptionsCompat?,
                        ) {}
                    }
                }
            }
            CompositionLocalProvider(
                LocalSteuerung provides st,
                androidx.activity.compose.LocalActivityResultRegistryOwner provides registerOwner,
            ) {
                Column(Modifier.fillMaxSize().background(LocalPalette.current.bg)) {
                    Kopf(st)
                    ReiterLeiste(st)
                    Box(Modifier.weight(1f)) { inhalt() }
                }
            }
        }
    }

    private fun foto(name: String, thema: String, reiter: Reiter, inhalt: @Composable () -> Unit) {
        paparazzi.snapshot(name = "${thema}_$name") { Rahmen(thema, reiter, inhalt) }
    }

    @Test fun woche() { foto("woche", "nothing", Reiter.WOCHE) { WocheSeite() }; foto("woche", "nothing-light", Reiter.WOCHE) { WocheSeite() } }
    @Test fun auftraege() { foto("auftraege", "nothing", Reiter.AUFTRAEGE) { AuftraegeSeite() }; foto("auftraege", "graphit", Reiter.AUFTRAEGE) { AuftraegeSeite() } }
    @Test fun detail() { foto("detail", "nothing", Reiter.AUFTRAEGE) { AuftragDetailEbene("a1") } }
    @Test fun kunden() { foto("kunden", "nothing", Reiter.KUNDEN) { KundenSeite() } }
    @Test fun material() { foto("material", "aulumu", Reiter.MATERIAL) { MaterialSeite() } }
    @Test fun kalender() { foto("kalender", "nothing", Reiter.KALENDER) { KalenderSeite() } }
    @Test fun statistik() { foto("statistik", "nothing", Reiter.STATISTIK) { StatistikSeite() } }
    @Test fun menue() { foto("menue", "nothing", Reiter.WOCHE) { MenueEbene() } }
    @Test fun maske() { foto("maske", "nothing", Reiter.AUFTRAEGE) { AuftragMaskeEbene(AuftragMaskeStart("a1")) } }
    @Test fun regie() { foto("regie", "nothing", Reiter.AUFTRAEGE) { RegieMaskeEbene(RegieMaskeStart("a1", "r1")) } }
}
