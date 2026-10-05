package de.gun.baustellen.reloaded.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import de.gun.baustellen.reloaded.Aktualisierung
import de.gun.baustellen.reloaded.BuildConfig
import de.gun.baustellen.reloaded.MainActivity
import de.gun.baustellen.reloaded.daten.Speicher
import de.gun.baustellen.reloaded.logik.parseIso
import de.gun.baustellen.reloaded.netz.UpdateDienst
import de.gun.baustellen.reloaded.netz.UpdateInfo
import de.gun.baustellen.reloaded.netz.UpdateLader
import de.gun.baustellen.reloaded.netz.UpdateZustand
import de.gun.baustellen.reloaded.ui.seiten.AuftragDetailEbene
import de.gun.baustellen.reloaded.ui.seiten.AuftragMaskeEbene
import de.gun.baustellen.reloaded.ui.seiten.AuftraegeSeite
import de.gun.baustellen.reloaded.ui.seiten.ChangelogEbene
import de.gun.baustellen.reloaded.ui.seiten.FotoEbene
import de.gun.baustellen.reloaded.ui.seiten.KalenderSeite
import de.gun.baustellen.reloaded.ui.seiten.KundeMaskeEbene
import de.gun.baustellen.reloaded.ui.seiten.KundenSeite
import de.gun.baustellen.reloaded.ui.seiten.MaterialMaskeEbene
import de.gun.baustellen.reloaded.ui.seiten.MaterialSeite
import de.gun.baustellen.reloaded.ui.seiten.MenueEbene
import de.gun.baustellen.reloaded.ui.seiten.RegieMaskeEbene
import de.gun.baustellen.reloaded.ui.seiten.StatistikSeite
import de.gun.baustellen.reloaded.ui.seiten.SucheEbene
import de.gun.baustellen.reloaded.ui.seiten.WocheSeite
import kotlinx.coroutines.delay
import java.time.LocalDate

@Composable
fun Oberflaeche(aktivitaet: MainActivity) {
    val daten by Speicher.daten.collectAsState()
    BaustellenTheme(daten.design) {
        val p = LocalPalette.current
        val view = LocalView.current
        SideEffect {
            WindowCompat.getInsetsController(aktivitaet.window, view).apply {
                isAppearanceLightStatusBars = p.hell
                isAppearanceLightNavigationBars = p.hell
            }
        }
        val scope = rememberCoroutineScope()
        val snackbar = remember { SnackbarHostState() }
        val st = remember { Steuerung(scope, snackbar) }
        CompositionLocalProvider(LocalSteuerung provides st) {
            Gesamt(aktivitaet, st)
        }
    }
}

/** Verhindert eine erneute Update-Prüfung, wenn die Activity z. B. beim Drehen neu aufgebaut wird. */
private var updateGeprueft = false

@Composable
private fun Gesamt(aktivitaet: MainActivity, st: Steuerung) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val daten by Speicher.daten.collectAsState()

    // Kalenderzugriff einmal beim ersten Start erfragen, danach nur auf Wunsch
    val rechte = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        Aktualisierung.geraetLaden(ctx)
    }
    LaunchedEffect(Unit) {
        val prefs = ctx.getSharedPreferences("start", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("rechteGefragt", false)) {
            prefs.edit().putBoolean("rechteGefragt", true).apply()
            rechte.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
        }
    }

    // Update-Prüfung bei jedem App-Start (einmal je Prozess), Sicherungs-Erinnerung höchstens alle sieben Tage
    var update by remember { mutableStateOf<UpdateInfo?>(null) }
    var sicherungFrage by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(2500)
        val u = Speicher.aktuell.update
        if (!updateGeprueft) {
            updateGeprueft = true
            try {
                val info = UpdateDienst.pruefen(u.repo)
                if (info != null && info.version != u.uebersprungen) update = info
            } catch (e: Exception) { }
        }
        delay(1500)
        val s = Speicher.aktuell.sicherung
        val jetzt = System.currentTimeMillis()
        val hatDaten = Speicher.aktuell.auftraege.eintraege.isNotEmpty() || Speicher.aktuell.kunden.eintraege.isNotEmpty()
        if (hatDaten && jetzt - s.popupZuletzt > 7L * 24 * 3600 * 1000) {
            Speicher.aendern { it.copy(sicherung = it.sicherung.copy(popupZuletzt = jetzt)) }
            val letzte = parseIso(s.letzte)
            if (letzte == null || letzte.isBefore(LocalDate.now().minusDays(7))) sicherungFrage = true
        }
    }

    BackHandler(enabled = true) {
        if (!st.zurueck()) aktivitaet.moveTaskToBack(true)
    }

    Scaffold(
        containerColor = p.bg,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = {
            SnackbarHost(st.snackbar, Modifier.navigationBarsPadding().imePadding()) { data ->
                Snackbar(data, containerColor = p.text, contentColor = p.bg, actionColor = p.akzent, shape = RUND_KLEIN)
            }
        },
    ) { innen ->
        Box(Modifier.fillMaxSize().padding(innen).background(p.bg)) {
            val vollbild = st.reiter == Reiter.KALENDER && st.kalenderGross && !st.kalenderEinstellungen
            LaunchedEffect(vollbild) {
                val fenster = aktivitaet.window
                WindowCompat.getInsetsController(fenster, fenster.decorView).apply {
                    if (vollbild) {
                        systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                        hide(WindowInsetsCompat.Type.systemBars())
                    } else show(WindowInsetsCompat.Type.systemBars())
                }
            }
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                val leisteUnten = daten.design.reiterUnten
                AnimatedVisibility(!vollbild, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    Column {
                        Kopf(st)
                        if (!leisteUnten) ReiterLeiste(st)
                    }
                }
                Box(
                    Modifier.weight(1f).fillMaxWidth()
                        .then(if (leisteUnten && !vollbild) Modifier.consumeWindowInsets(WindowInsets.navigationBars) else Modifier)
                        .imePadding()
                ) {
                    when (st.reiter) {
                        Reiter.WOCHE -> WocheSeite()
                        Reiter.AUFTRAEGE -> AuftraegeSeite()
                        Reiter.KUNDEN -> KundenSeite()
                        Reiter.MATERIAL -> MaterialSeite()
                        Reiter.KALENDER -> KalenderSeite()
                        Reiter.STATISTIK -> StatistikSeite()
                    }
                }
                AnimatedVisibility(leisteUnten && !vollbild, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    ReiterLeiste(st, unten = true)
                }
            }

            Ebene(st.menueOffen) { MenueEbene() }
            Ebene(st.sucheOffen) { SucheEbene() }
            Ebene(st.changelogOffen) { ChangelogEbene() }
            Ebene(st.detail != null) { st.detail?.let { AuftragDetailEbene(it) } }
            Ebene(st.auftragMaske != null) { st.auftragMaske?.let { AuftragMaskeEbene(it) } }
            Ebene(st.kundeMaske != null) { st.kundeMaske?.let { KundeMaskeEbene(it) } }
            Ebene(st.materialMaske != null) { st.materialMaske?.let { MaterialMaskeEbene(it) } }
            Ebene(st.regieMaske != null) { st.regieMaske?.let { RegieMaskeEbene(it) } }
            Ebene(st.foto != null) { st.foto?.let { FotoEbene(it) } }
        }
    }

    st.meldung?.let { (t, x) -> Meldung(t, x) { st.meldung = null } }
    UpdateFortschritt()
    update?.let { u ->
        Wahl(
            "Update verfügbar",
            "Installiert: v${BuildConfig.VERSION_NAME}\nVerfügbar: v${u.version}" +
                (if (u.groesse > 0) " · " + String.format("%.1f MB", u.groesse / 1048576.0) else "") +
                (if (u.notizen.isNotBlank()) "\n\n" + u.notizen else ""),
            listOf(
                Triple("Jetzt aktualisieren", KnopfArt.PRIMAER) {
                    UpdateLader.starten(ctx, u)
                    update = null
                },
                Triple("Diese Version überspringen", KnopfArt.NORMAL) {
                    Speicher.aendern { it.copy(update = it.update.copy(uebersprungen = u.version)) }
                    update = null
                },
            ),
        ) { update = null }
    }
    if (sicherungFrage) {
        val letzte = daten.sicherung.letzte
        Wahl(
            "Datensicherung",
            (if (letzte.isBlank()) "Es wurde noch keine eigene Sicherung erstellt." else "Die letzte eigene Sicherung ist vom ${de.gun.baustellen.reloaded.logik.de(letzte)}.") +
                " Aufträge, Kunden, Unterlagen und Fotos liegen ausschließlich auf diesem Gerät.",
            listOf(
                Triple("Jetzt sichern", KnopfArt.PRIMAER) { sicherungFrage = false; st.menueOffen = true },
                Triple("Später erinnern", KnopfArt.NORMAL) { sicherungFrage = false },
            ),
        ) { sicherungFrage = false }
    }
}

/** Fortschritt und Ergebnis des Update-Downloads als Dialog. */
@Composable
private fun UpdateFortschritt() {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val z by UpdateLader.zustand.collectAsState()
    when (val zu = z) {
        is UpdateZustand.Leer -> {}
        is UpdateZustand.Laedt -> AlertDialog(
            onDismissRequest = {},
            title = { Punkt("UPDATE ${zu.version}", 18.sp) },
            text = {
                Column {
                    val anteil = if (zu.gesamt > 0) (zu.fertig.toFloat() / zu.gesamt).coerceIn(0f, 1f) else 0f
                    Fliesstext("Wird heruntergeladen …", p.textDim)
                    Abstand(10.dp)
                    Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(p.randLeise)) {
                        Box(Modifier.fillMaxWidth(anteil).fillMaxHeight().background(p.akzent))
                    }
                    Abstand(6.dp)
                    Mono(String.format("%.1f / %.1f MB", zu.fertig / 1048576.0, zu.gesamt / 1048576.0), p.textFaint, 11.sp)
                }
            },
            confirmButton = { TextButton(onClick = { UpdateLader.abbrechen() }) { Text("ABBRECHEN", fontFamily = Schrift.mono, color = p.textDim) } },
            containerColor = p.panel, shape = RUND,
        )
        is UpdateZustand.Bereit -> Wahl(
            "Update ${zu.version}",
            "Die neue Version ist heruntergeladen. Android fragt jetzt nach der Installation – deine Daten bleiben erhalten.\n\n" +
                "Beim ersten Mal muss „Unbekannte Apps installieren“ für den Baustellen Pilot erlaubt werden; danach hier erneut auf „Installieren“ tippen.",
            listOf(Triple("Installieren", KnopfArt.PRIMAER) { UpdateLader.installieren(ctx, zu.datei) }),
        ) { UpdateLader.schliessen() }
        is UpdateZustand.Fehler -> Wahl(
            "Update",
            zu.text,
            listOf(Triple("Zur Download-Seite", KnopfArt.NORMAL) {
                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/" + Speicher.aktuell.update.repo + "/releases")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                UpdateLader.schliessen()
            }),
        ) { UpdateLader.schliessen() }
    }
}

/** Vollflächige Ebene über dem Inhalt. */
@Composable
private fun Ebene(sichtbar: Boolean, inhalt: @Composable () -> Unit) {
    val p = LocalPalette.current
    AnimatedVisibility(
        sichtbar,
        enter = fadeIn() + slideInVertically { it / 12 },
        exit = fadeOut() + slideOutVertically { it / 12 },
    ) {
        Box(
            Modifier.fillMaxSize().background(p.bg).clickable(enabled = true, onClick = {}, indication = null,
                interactionSource = remember { MutableInteractionSource() })
                .statusBarsPadding().imePadding()
        ) { inhalt() }
    }
}

@Composable
internal fun Kopf(st: Steuerung) {
    val p = LocalPalette.current
    Box(Modifier.fillMaxWidth().height(64.dp)) {
        if (p.punktRaster) PunktRaster(Modifier.matchParentSize(), p.randLeise)
        Row(Modifier.fillMaxSize().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(p.akzent))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Etikett("Baustellen Pilot", p.textFaint, groesse = 9.5.sp)
                Punkt("RELOADED", 22.sp)
            }
            Box(
                Modifier.clip(CircleShape).clickable { st.changelogOffen = true }
                    .background(p.panel).padding(horizontal = 10.dp, vertical = 4.dp)
            ) { Mono("v" + BuildConfig.VERSION_NAME, p.textDim, 11.sp) }
            IconButton(onClick = { st.sucheOffen = true }) { Icon(Icons.Outlined.Search, "Suchen", tint = p.text) }
            IconButton(onClick = { st.menueOffen = true }) { Icon(Icons.Outlined.Menu, "Menü", tint = p.text) }
        }
    }
}

@Composable
internal fun ReiterLeiste(st: Steuerung, unten: Boolean = false) {
    val p = LocalPalette.current
    val liste = rememberLazyListState()
    LaunchedEffect(st.reiter) { liste.animateScrollToItem(maxOf(0, st.reiter.ordinal - 1)) }
    Column(if (unten) Modifier.background(p.bg).navigationBarsPadding() else Modifier) {
        if (unten) Box(Modifier.fillMaxWidth().height(1.dp).background(p.randLeise))
        LazyRow(
            state = liste,
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            itemsIndexed(Reiter.entries) { _, r ->
                val aktiv = r == st.reiter
                Column(
                    Modifier.clip(RUND_KLEIN).clickable { st.reiter = r }.padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Punkt(r.titel.uppercase(), 15.sp, if (aktiv) p.text else p.textFaint, fett = aktiv)
                    Spacer(Modifier.height(4.dp))
                    Box(Modifier.size(5.dp).clip(CircleShape).background(if (aktiv) p.akzent else Color.Transparent))
                }
            }
        }
        if (!unten) Box(Modifier.fillMaxWidth().height(1.dp).background(p.randLeise))
    }
}
