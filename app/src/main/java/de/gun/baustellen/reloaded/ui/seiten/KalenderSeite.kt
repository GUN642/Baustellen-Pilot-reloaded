package de.gun.baustellen.reloaded.ui.seiten

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.gun.baustellen.reloaded.Aktualisierung
import de.gun.baustellen.reloaded.daten.AppDaten
import de.gun.baustellen.reloaded.geraet.GeraeteKalender
import de.gun.baustellen.reloaded.geraet.GeraetKalender
import de.gun.baustellen.reloaded.geraet.GeraetTermin
import de.gun.baustellen.reloaded.logik.KalEintrag
import de.gun.baustellen.reloaded.logik.MONATE
import de.gun.baustellen.reloaded.logik.WOCHENTAGE
import de.gun.baustellen.reloaded.logik.eintraegeAm
import de.gun.baustellen.reloaded.logik.kalenderEintraege
import de.gun.baustellen.reloaded.logik.kalenderwoche
import de.gun.baustellen.reloaded.logik.lang
import de.gun.baustellen.reloaded.logik.uhrzeitText
import de.gun.baustellen.reloaded.logik.wochenStart
import de.gun.baustellen.reloaded.ui.AuftragMaskeStart
import de.gun.baustellen.reloaded.ui.Fliesstext
import de.gun.baustellen.reloaded.ui.Karte
import de.gun.baustellen.reloaded.ui.Leer
import de.gun.baustellen.reloaded.ui.LocalPalette
import de.gun.baustellen.reloaded.ui.LocalSteuerung
import de.gun.baustellen.reloaded.ui.Mono
import de.gun.baustellen.reloaded.ui.Punkt
import de.gun.baustellen.reloaded.ui.RUND
import de.gun.baustellen.reloaded.ui.Schrift
import de.gun.baustellen.reloaded.ui.Symbol
import de.gun.baustellen.reloaded.ui.Zeile
import de.gun.baustellen.reloaded.ui.textAuf
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit

private const val SEITEN = 2400
private const val MITTE = SEITEN / 2

private fun monatZuSeite(anker: LocalDate, seite: Int): LocalDate = anker.plusMonths((seite - MITTE).toLong())
private fun seiteZuMonat(anker: LocalDate, monat: LocalDate): Int =
    (MITTE + ChronoUnit.MONTHS.between(anker, monat.withDayOfMonth(1))).toInt().coerceIn(0, SEITEN - 1)

private data class Balken(val e: KalEintrag, val von: Int, val bis: Int, val spur: Int, val offenLinks: Boolean, val offenRechts: Boolean)

/** Verteilt die Einträge einer Woche auf Spuren (mehrtägige zuerst). */
private fun wochenBalken(alle: List<KalEintrag>, ws: LocalDate): List<Balken> {
    val we = ws.plusDays(6)
    val inWoche = alle.filter { !it.ersterTag.isAfter(we) && !it.letzterTag.isBefore(ws) }
        .sortedWith(compareByDescending<KalEintrag> { ChronoUnit.DAYS.between(it.ersterTag, it.letzterTag) }
            .thenBy { it.auftragId == null }.thenBy { it.ersterTag })
    val spuren = mutableListOf<MutableList<IntRange>>()
    return inWoche.map { t ->
        val von = maxOf(0, ChronoUnit.DAYS.between(ws, t.ersterTag).toInt())
        val bis = minOf(6, ChronoUnit.DAYS.between(ws, t.letzterTag).toInt())
        var s = 0
        while (true) {
            if (s >= spuren.size) spuren.add(mutableListOf())
            if (spuren[s].none { !(bis < it.first || von > it.last) }) { spuren[s].add(von..bis); break }
            s++
        }
        Balken(t, von, bis, s, t.ersterTag.isBefore(ws), t.letzterTag.isAfter(we))
    }
}

/** Kalender- und Termindaten als beobachteter Zustand. */
@Composable
fun rememberGeraet(): Pair<List<GeraetKalender>, List<GeraetTermin>> {
    val k by GeraeteKalender.kalender.collectAsState()
    val t by GeraeteKalender.termine.collectAsState()
    return k to t
}

@Composable
fun KalenderSeite() {
    val st = LocalSteuerung.current
    if (st.kalenderEinstellungen) {
        KalenderEinstellungen()
        return
    }
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val d = aktuelleDaten()
    val (kalender, termine) = rememberGeraet()
    val monat = st.kalenderMonat

    val anker = remember { st.kalenderMonat.withDayOfMonth(1) }
    val pager = rememberPagerState(initialPage = seiteZuMonat(anker, monat)) { SEITEN }
    LaunchedEffect(pager) {
        snapshotFlow { pager.currentPage }.collect { st.kalenderMonat = monatZuSeite(anker, it) }
    }
    LaunchedEffect(monat) {
        val ziel = seiteZuMonat(anker, monat)
        if (ziel != pager.currentPage) {
            if (kotlin.math.abs(ziel - pager.currentPage) > 1) pager.scrollToPage(ziel)
            else pager.animateScrollToPage(ziel, animationSpec = tween(240, easing = FastOutSlowInEasing))
        }
    }
    fun wechseln(delta: Long) { st.kalenderMonat = st.kalenderMonat.withDayOfMonth(1).plusMonths(delta) }
    val oeffnen: (KalEintrag) -> Unit = { e -> if (e.auftragId != null) st.auftragOeffnen(e.auftragId) else st.melden(e.titel, geraetText(e)) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val rasterMin = if (constraints.hasBoundedHeight) (maxHeight - (if (st.kalenderGross) 84 else 92).dp).coerceAtLeast(0.dp) else 0.dp
        Column(Modifier.fillMaxSize().then(if (st.kalenderGross) Modifier else Modifier.verticalScroll(rememberScrollState()))) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Symbol("‹", p.text) { wechseln(-1) }
                Column(Modifier.weight(1f).clickable { st.kalenderGross = !st.kalenderGross }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Punkt(MONATE[monat.monthValue - 1].uppercase() + " " + monat.year, 20.sp)
                }
                Symbol("›", p.text) { wechseln(1) }
                Symbol("◎", p.akzent) { st.kalenderMonat = LocalDate.now().withDayOfMonth(1); st.kalenderTag = LocalDate.now() }
                Symbol("↻") { scope.launch { Aktualisierung.geraetLadenJetzt(ctx); st.kurz("Gerätekalender neu eingelesen") } }
                if (st.kalenderGross) Symbol("✕") { st.kalenderGross = false } else Symbol("⚙") { st.kalenderEinstellungen = true }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp)) {
                WOCHENTAGE.forEachIndexed { i, w ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Mono(w.uppercase(), if (i >= 5) p.akzent else p.textFaint, 11.sp, fett = true)
                    }
                }
            }
            HorizontalPager(
                state = pager,
                modifier = if (st.kalenderGross) Modifier.weight(1f) else Modifier,
                beyondViewportPageCount = 1,
                flingBehavior = PagerDefaults.flingBehavior(state = pager, snapPositionalThreshold = 0.2f, snapAnimationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
                verticalAlignment = Alignment.Top,
                key = { it },
            ) { seite ->
                MonatsRaster(monatZuSeite(anker, seite), d, kalender, termine, st.kalenderGross, rasterMin, oeffnen)
            }
        }
        val gewaehlterTag = st.kalenderTag
        var zuletztTag by remember { mutableStateOf(gewaehlterTag) }
        if (gewaehlterTag != null) zuletztTag = gewaehlterTag
        val panelOffen = gewaehlterTag != null && !st.kalenderGross
        AnimatedVisibility(
            visible = panelOffen,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(tween(200)) { it } + fadeIn(tween(200)),
            exit = slideOutVertically(tween(160)) { it } + fadeOut(tween(160)),
        ) {
            zuletztTag?.let { tag ->
                Box(Modifier.fillMaxWidth().heightIn(max = maxHeight * 0.55f).navigationBarsPadding().verticalScroll(rememberScrollState())) {
                    TagesDetail(tag, d, kalender, termine, oeffnen)
                }
            }
        }
        if (!panelOffen) FloatingActionButton(
            onClick = { st.auftragMaske = AuftragMaskeStart(datum = st.kalenderTag ?: LocalDate.now()) },
            containerColor = p.akzent, contentColor = textAuf(p.akzent), shape = CircleShape,
            modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(16.dp).size(48.dp),
        ) { Text("+", fontSize = 22.sp, fontFamily = Schrift.mono) }
    }
}

/** Angaben zu einem Termin aus dem Gerätekalender. */
private fun geraetText(e: KalEintrag): String {
    val t = e.geraet
    return listOfNotNull(
        (if (e.ersterTag == e.letzterTag) e.ersterTag.lang() else e.ersterTag.lang() + " – " + e.letzterTag.lang()) + " · " + uhrzeitText(e),
        t?.ort?.takeIf { it.isNotBlank() }?.let { "Ort: $it" },
        "Kalender: " + e.quelle,
        t?.notiz?.takeIf { it.isNotBlank() }?.take(400),
    ).joinToString("\n\n")
}

@Composable
private fun MonatsRaster(
    monat: LocalDate, d: AppDaten, kalender: List<GeraetKalender>, termine: List<GeraetTermin>,
    gross: Boolean, rasterMin: Dp, oeffnen: (KalEintrag) -> Unit,
) {
    val p = LocalPalette.current
    val st = LocalSteuerung.current
    val haptik = LocalHapticFeedback.current
    val erster = monat.withDayOfMonth(1)
    val start = wochenStart(erster)
    val wochen = ((ChronoUnit.DAYS.between(start, erster) + erster.lengthOfMonth() + 6) / 7).toInt()
    val ende = start.plusDays(wochen * 7L - 1)
    val alle = remember(d, kalender, termine, start) { kalenderEintraege(d, kalender, termine, start, ende) }
    val heute = LocalDate.now()
    val spurH = 15.dp

    BoxWithConstraints(Modifier.fillMaxWidth().then(if (gross) Modifier.fillMaxHeight() else Modifier).padding(horizontal = 6.dp)) {
        val zeilenH: Dp = if (gross) (maxHeight / wochen).coerceAtLeast(60.dp) else maxOf(86.dp, rasterMin / wochen)
        val zellenB = maxWidth / 7
        Column {
            for (w in 0 until wochen) {
                val ws = start.plusDays(w * 7L)
                val balken = remember(alle, ws) { wochenBalken(alle, ws) }
                val kopfH = 20.dp
                val maxSpuren = maxOf(1, ((zeilenH - kopfH - 2.dp) / spurH).toInt())
                Box(Modifier.fillMaxWidth().height(zeilenH)) {
                    Row(Modifier.fillMaxSize()) {
                        for (i in 0..6) {
                            val tag = ws.plusDays(i.toLong())
                            val fremd = tag.monthValue != monat.monthValue
                            val gewaehlt = tag == st.kalenderTag
                            Box(
                                Modifier.weight(1f).fillMaxHeight().padding(1.dp).clip(RoundedCornerShape(6.dp))
                                    .background(if (gewaehlt) p.akzentDim else if (i >= 5) p.panelAlt else Color.Transparent)
                                    .border(if (gewaehlt) 1.dp else 0.5.dp, if (gewaehlt) p.akzent else p.randLeise, RoundedCornerShape(6.dp))
                                    .pointerInput(tag) {
                                        detectTapGestures(
                                            onTap = { st.kalenderTag = if (st.kalenderTag == tag && !gross) null else tag; if (gross) st.kalenderGross = false },
                                            onLongPress = {
                                                haptik.performHapticFeedback(HapticFeedbackType.LongPress)
                                                st.auftragMaske = AuftragMaskeStart(datum = tag)
                                            },
                                        )
                                    }
                            ) {
                                Row(Modifier.padding(horizontal = 3.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                    val istHeute = tag == heute
                                    Box(Modifier.size(18.dp).clip(CircleShape).background(if (istHeute) p.akzent else Color.Transparent), contentAlignment = Alignment.Center) {
                                        Text(
                                            tag.dayOfMonth.toString(),
                                            color = when { istHeute -> textAuf(p.akzent); fremd -> p.textFaint.copy(alpha = 0.6f); else -> p.text },
                                            style = TextStyle(fontFamily = Schrift.mono, fontSize = 11.sp, fontWeight = FontWeight.Bold),
                                        )
                                    }
                                    if (i == 0) {
                                        Spacer(Modifier.weight(1f))
                                        Text(kalenderwoche(tag).toString(), color = p.textFaint.copy(alpha = 0.7f), style = TextStyle(fontFamily = Schrift.mono, fontSize = 8.sp))
                                    }
                                }
                            }
                        }
                    }
                    balken.filter { it.spur < maxSpuren }.forEach { bk ->
                        val farbe = Color(bk.e.farbe)
                        val voll = bk.e.ganztags || bk.bis > bk.von
                        Box(
                            Modifier
                                .offset(x = zellenB * bk.von + 2.dp, y = kopfH + spurH * bk.spur)
                                .width(zellenB * (bk.bis - bk.von + 1) - 4.dp)
                                .height(spurH - 2.dp)
                                .clip(
                                    RoundedCornerShape(
                                        topStart = if (bk.offenLinks) 0.dp else 4.dp, bottomStart = if (bk.offenLinks) 0.dp else 4.dp,
                                        topEnd = if (bk.offenRechts) 0.dp else 4.dp, bottomEnd = if (bk.offenRechts) 0.dp else 4.dp,
                                    )
                                )
                                .background(if (voll) farbe else farbe.copy(alpha = 0.18f))
                                .clickable { oeffnen(bk.e) }
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxSize()) {
                                if (!voll) Box(Modifier.width(3.dp).fillMaxHeight().background(farbe))
                                Text(
                                    bk.e.titel, color = if (voll) textAuf(farbe) else p.text, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip,
                                    modifier = Modifier.padding(horizontal = 3.dp),
                                    style = TextStyle(fontFamily = Schrift.text, fontSize = 9.5.sp, fontWeight = FontWeight.Medium, lineHeight = 11.sp),
                                )
                            }
                        }
                    }
                    for (c in 0..6) {
                        val mehr = balken.count { it.spur >= maxSpuren && c in it.von..it.bis }
                        if (mehr > 0) Text(
                            "+$mehr", color = p.textDim,
                            modifier = Modifier.offset(x = zellenB * c + zellenB - 20.dp, y = 3.dp),
                            style = TextStyle(fontFamily = Schrift.mono, fontSize = 9.sp, fontWeight = FontWeight.Bold),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TagesDetail(tag: LocalDate, d: AppDaten, kalender: List<GeraetKalender>, termine: List<GeraetTermin>, oeffnen: (KalEintrag) -> Unit) {
    val p = LocalPalette.current
    val st = LocalSteuerung.current
    val liste = remember(d, kalender, termine, tag) { eintraegeAm(kalenderEintraege(d, kalender, termine, tag, tag), tag) }
    Box(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
        Karte(titel = null, modifier = Modifier.shadow(16.dp, RUND)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Punkt(tag.lang().uppercase(), 15.sp, zeilen = 2)
                    Mono("KW " + kalenderwoche(tag), p.textDim, 11.sp)
                }
                Symbol("+", p.akzent) { st.auftragMaske = AuftragMaskeStart(datum = tag) }
                Symbol("✕") { st.kalenderTag = null }
            }
            Spacer(Modifier.height(8.dp))
            if (liste.isEmpty()) Leer("Kein Auftrag an diesem Tag. Zum Anlegen einen Tag lange drücken oder auf + tippen.")
            liste.forEach { e ->
                Zeile(Color(e.farbe), onClick = { oeffnen(e) }) {
                    Fliesstext(e.titel, fett = true, groesse = 13.sp, zeilen = 2)
                    Mono(uhrzeitText(e) + " · " + e.quelle, p.textDim, 11.sp)
                }
            }
        }
    }
}
