package de.gun.baustellen.reloaded.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.gun.baustellen.reloaded.logik.alsDE
import de.gun.baustellen.reloaded.logik.parseDE
import de.gun.baustellen.reloaded.logik.zeitNormieren
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

val RUND = RoundedCornerShape(20.dp)
val RUND_KLEIN = RoundedCornerShape(12.dp)

// ------------------------------------------------------------------ Texte

@Composable
fun Punkt(text: String, groesse: TextUnit = 22.sp, farbe: Color = LocalPalette.current.text, modifier: Modifier = Modifier, fett: Boolean = true, zeilen: Int = 1) {
    val p = LocalPalette.current
    Text(
        text, modifier = modifier, color = farbe,
        style = TextStyle(
            fontFamily = p.titelSchrift, fontSize = groesse,
            fontWeight = if (fett) FontWeight.ExtraBold else FontWeight.SemiBold,
            letterSpacing = if (p.punktSchrift) 1.sp else 0.sp,
        ),
        maxLines = zeilen, overflow = TextOverflow.Ellipsis,
    )
}

/** Kleine Beschriftung in Versalien, Monoschrift. */
@Composable
fun Etikett(text: String, farbe: Color = LocalPalette.current.textFaint, modifier: Modifier = Modifier, groesse: TextUnit = 10.5.sp, zeilen: Int = 1) {
    Text(
        text.uppercase(), modifier = modifier, color = farbe,
        style = TextStyle(fontFamily = Schrift.mono, fontSize = groesse, letterSpacing = 1.4.sp, fontWeight = FontWeight.Bold, lineHeight = groesse * 1.3f),
        maxLines = zeilen, overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun Fliesstext(text: String, farbe: Color = LocalPalette.current.text, groesse: TextUnit = 14.sp, modifier: Modifier = Modifier, fett: Boolean = false, zeilen: Int = Int.MAX_VALUE) {
    Text(
        text, modifier = modifier, color = farbe, maxLines = zeilen, overflow = TextOverflow.Ellipsis,
        style = TextStyle(fontFamily = Schrift.text, fontSize = groesse, fontWeight = if (fett) FontWeight.Bold else FontWeight.Normal, lineHeight = groesse * 1.35f),
    )
}

@Composable
fun Mono(text: String, farbe: Color = LocalPalette.current.textDim, groesse: TextUnit = 12.sp, modifier: Modifier = Modifier, fett: Boolean = false, zentriert: Boolean = false) {
    Text(text, modifier = modifier, color = farbe, maxLines = if (zentriert) 1 else Int.MAX_VALUE,
        style = TextStyle(fontFamily = Schrift.mono, fontSize = groesse, fontWeight = if (fett) FontWeight.Bold else FontWeight.Normal,
            textAlign = if (zentriert) TextAlign.Center else TextAlign.Unspecified))
}

@Composable
fun Hinweis(text: String, modifier: Modifier = Modifier, farbe: Color = LocalPalette.current.textFaint) {
    Text(text, modifier = modifier.padding(top = 4.dp), color = farbe, style = TextStyle(fontFamily = Schrift.text, fontSize = 12.sp, lineHeight = 16.sp))
}

// ------------------------------------------------------------------ Flächen

/** Punktraster wie auf der Rückseite der Nothing-Geräte. */
@Composable
fun PunktRaster(modifier: Modifier, farbe: Color, abstand: Dp = 9.dp, radius: Dp = 1.dp) {
    Canvas(modifier) {
        val a = abstand.toPx()
        val r = radius.toPx()
        var y = a / 2
        while (y < size.height) {
            var x = a / 2
            while (x < size.width) {
                drawCircle(farbe, r, Offset(x, y))
                x += a
            }
            y += a
        }
    }
}

@Composable
fun Karte(
    titel: String? = null,
    index: String? = null,
    modifier: Modifier = Modifier,
    aktion: (@Composable RowScope.() -> Unit)? = null,
    inhalt: @Composable ColumnScope.() -> Unit,
) {
    val p = LocalPalette.current
    Column(
        modifier
            .fillMaxWidth()
            .clip(RUND)
            .background(p.panel)
            .border(1.dp, p.rand, RUND)
            .padding(16.dp)
    ) {
        if (titel != null) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                if (index != null) {
                    Mono(index, p.akzent, 12.sp, fett = true)
                    Spacer(Modifier.width(8.dp))
                }
                Punkt(titel.uppercase(), 17.sp, modifier = Modifier.weight(1f), zeilen = 2)
                if (aktion != null) Row(verticalAlignment = Alignment.CenterVertically, content = aktion)
            }
        }
        inhalt()
    }
}

/** Listeneintrag mit farbigem Streifen links. */
@Composable
fun Zeile(
    streifen: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    aktionen: (@Composable RowScope.() -> Unit)? = null,
    inhalt: @Composable ColumnScope.() -> Unit,
) {
    val p = LocalPalette.current
    Row(
        modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RUND_KLEIN)
            .background(p.panelAlt)
            .border(1.dp, p.randLeise, RUND_KLEIN)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(streifen))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 10.dp), content = inhalt)
        if (aktionen != null) Row(Modifier.padding(end = 4.dp), verticalAlignment = Alignment.CenterVertically, content = aktionen)
    }
}

@Composable
fun Leer(text: String) {
    val p = LocalPalette.current
    Box(
        Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RUND_KLEIN)
            .border(BorderStroke(1.dp, p.randLeise), RUND_KLEIN).padding(16.dp),
        contentAlignment = Alignment.Center
    ) { Fliesstext(text, p.textFaint, 13.sp) }
}

@Composable
fun Trenner(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(vertical = 10.dp).height(1.dp).background(LocalPalette.current.randLeise))
}

@Composable
fun Abstand(h: Dp = 12.dp) = Spacer(Modifier.height(h))

// ------------------------------------------------------------------ Knöpfe

enum class KnopfArt { NORMAL, PRIMAER, GEFAHR, LEISE, BESTAETIGEN }

@Composable
fun Knopf(
    text: String,
    modifier: Modifier = Modifier,
    art: KnopfArt = KnopfArt.NORMAL,
    klein: Boolean = false,
    aktiv: Boolean = true,
    onClick: () -> Unit,
) {
    val p = LocalPalette.current
    val (hg, vg, rand) = when (art) {
        KnopfArt.PRIMAER -> Triple(p.akzent, if (p.akzent.luminanz() > 0.5f) Color.Black else Color.White, p.akzent)
        KnopfArt.GEFAHR -> Triple(Color.Transparent, p.rot, p.rot.copy(alpha = 0.6f))
        KnopfArt.BESTAETIGEN -> Triple(Color.Transparent, p.gruen, p.gruen.copy(alpha = 0.6f))
        KnopfArt.LEISE -> Triple(Color.Transparent, p.textDim, Color.Transparent)
        KnopfArt.NORMAL -> Triple(Color.Transparent, p.text, p.rand)
    }
    Box(
        modifier
            .heightIn(min = if (klein) 28.dp else 34.dp)
            .clip(RoundedCornerShape(50))
            .background(hg.copy(alpha = if (aktiv) hg.alpha else hg.alpha * 0.4f))
            .border(1.dp, rand, RoundedCornerShape(50))
            .clickable(enabled = aktiv, onClick = onClick)
            .padding(horizontal = if (klein) 10.dp else 14.dp, vertical = if (klein) 5.dp else 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text.uppercase(), color = vg.copy(alpha = if (aktiv) 1f else 0.4f), maxLines = 1,
            style = TextStyle(fontFamily = Schrift.mono, fontWeight = FontWeight.Bold, fontSize = if (klein) 10.sp else 11.sp, letterSpacing = 0.8.sp)
        )
    }
}

/** Runder Symbolknopf. */
@Composable
fun Symbol(text: String, farbe: Color = LocalPalette.current.textDim, onClick: () -> Unit) {
    Box(
        Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(text, color = farbe, fontSize = 16.sp, fontFamily = Schrift.mono) }
}

/** Schalter in Pillenform: ● an / ○ aus. */
@Composable
fun Pille(text: String, an: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val p = LocalPalette.current
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(if (an) p.akzentDim else Color.Transparent)
            .border(1.dp, if (an) p.akzent else p.rand, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            (if (an) "● " else "○ ") + text, color = if (an) p.text else p.textDim,
            style = TextStyle(fontFamily = Schrift.mono, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        )
    }
}

/** Segmentauswahl. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Segmente(optionen: List<Pair<String, String>>, gewaehlt: String, modifier: Modifier = Modifier, onWahl: (String) -> Unit) {
    val p = LocalPalette.current
    FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        optionen.forEach { (id, name) ->
            val an = id == gewaehlt
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (an) p.text else Color.Transparent)
                    .border(1.dp, if (an) p.text else p.rand, RoundedCornerShape(50))
                    .clickable { onWahl(id) }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(name.uppercase(), color = if (an) p.bg else p.textDim,
                    style = TextStyle(fontFamily = Schrift.mono, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Knopfreihe(modifier: Modifier = Modifier, inhalt: @Composable () -> Unit) {
    FlowRow(modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        inhalt()
    }
}

// ------------------------------------------------------------------ Kennzahlen

/** Große Kennzahl in Punktschrift. */
@Composable
fun Kennzahl(wert: String, label: String, farbe: Color = LocalPalette.current.text, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val p = LocalPalette.current
    Column(
        modifier.clip(RUND_KLEIN).background(p.panelAlt).border(1.dp, p.randLeise, RUND_KLEIN)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(12.dp)
    ) {
        Punkt(wert, if (wert.length > 5) 20.sp else 26.sp, farbe)
        Etikett(label, zeilen = 2)
    }
}

// ------------------------------------------------------------------ Eingaben

@Composable
fun feldFarben() = LocalPalette.current.let { p ->
    OutlinedTextFieldDefaults.colors(
        focusedBorderColor = p.akzent, unfocusedBorderColor = p.rand,
        focusedTextColor = p.text, unfocusedTextColor = p.text,
        cursorColor = p.akzent, focusedLabelColor = p.akzent, unfocusedLabelColor = p.textDim,
        focusedContainerColor = p.panelAlt, unfocusedContainerColor = p.panelAlt,
        focusedPlaceholderColor = p.textFaint, unfocusedPlaceholderColor = p.textFaint,
        disabledTextColor = p.text, disabledBorderColor = p.rand, disabledLabelColor = p.textDim,
        disabledContainerColor = p.panelAlt, disabledPlaceholderColor = p.textFaint,
    )
}

private val FELD_TEXT @Composable get() = TextStyle(fontFamily = Schrift.text, fontSize = 14.sp, color = LocalPalette.current.text)

/**
 * Kompakter Rahmen für alle Eingabefelder: deutlich weniger Innenabstand als das
 * Material-Standardfeld (56 dp). Ein Symbol rechts wird überlagert statt über den
 * Trailing-Slot eingesetzt, der sonst 48 dp Mindesthöhe erzwingt.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeldRahmen(
    text: String,
    inner: @Composable () -> Unit,
    label: String,
    platzhalter: String,
    einzeilig: Boolean,
    quelle: MutableInteractionSource,
    aktiv: Boolean = true,
    mitSymbol: Boolean = false,
) {
    val farben = feldFarben()
    OutlinedTextFieldDefaults.DecorationBox(
        value = text, innerTextField = inner, enabled = aktiv, singleLine = einzeilig,
        visualTransformation = VisualTransformation.None, interactionSource = quelle,
        label = { Text(label, fontFamily = Schrift.text, fontSize = 12.sp) },
        placeholder = if (platzhalter.isNotEmpty()) {
            { Text(platzhalter, fontFamily = Schrift.text, fontSize = 13.sp) }
        } else null,
        colors = farben,
        contentPadding = PaddingValues(start = 12.dp, end = if (mitSymbol) 40.dp else 12.dp, top = 9.dp, bottom = 9.dp),
        container = {
            OutlinedTextFieldDefaults.ContainerBox(aktiv, false, quelle, farben, RUND_KLEIN)
        },
    )
}

/** Kleines Symbol am rechten Rand eines Feldes. */
@Composable
private fun BoxScope.FeldSymbol(symbol: ImageVector, beschreibung: String?, onClick: (() -> Unit)?) {
    val p = LocalPalette.current
    Box(
        Modifier.align(Alignment.CenterEnd).padding(end = 4.dp).size(34.dp).clip(CircleShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) { Icon(symbol, beschreibung, tint = p.textDim, modifier = Modifier.size(20.dp)) }
}

@Composable
fun Feld(
    wert: String,
    onWert: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    platzhalter: String = "",
    zeilen: Int = 1,
    tastatur: KeyboardType = KeyboardType.Text,
    symbol: ImageVector? = null,
    onSymbol: (() -> Unit)? = null,
    beiVerlassen: (() -> Unit)? = null,
    mehrzeilig: Boolean = false,
) {
    var hatteFokus by remember { mutableStateOf(false) }
    val quelle = remember { MutableInteractionSource() }
    val p = LocalPalette.current
    Box(modifier.fillMaxWidth().padding(top = 7.dp, bottom = 2.dp)) {
        BasicTextField(
            value = wert, onValueChange = onWert,
            modifier = Modifier.fillMaxWidth()
                .then(if (beiVerlassen != null) Modifier.onFocusChanged {
                    if (it.isFocused) hatteFokus = true
                    else if (hatteFokus) { hatteFokus = false; beiVerlassen() }
                } else Modifier),
            singleLine = zeilen == 1 && !mehrzeilig, minLines = if (zeilen > 1) zeilen else 1,
            keyboardOptions = KeyboardOptions(keyboardType = tastatur),
            textStyle = FELD_TEXT, cursorBrush = SolidColor(p.akzent), interactionSource = quelle,
            decorationBox = { inner ->
                FeldRahmen(wert, inner, label, platzhalter, zeilen == 1 && !mehrzeilig, quelle, mitSymbol = symbol != null)
            },
        )
        if (symbol != null) FeldSymbol(symbol, null, onSymbol)
    }
}

/** Uhrzeit, wird beim Verlassen in HH:MM umgeschrieben (0930 -> 09:30). */
@Composable
fun ZeitFeld(wert: String, onWert: (String) -> Unit, label: String, modifier: Modifier = Modifier) {
    Feld(wert, onWert, label, modifier, platzhalter = "z. B. 0930", tastatur = KeyboardType.Number,
        beiVerlassen = { zeitNormieren(wert)?.let { if (it != wert) onWert(it) } })
}

/** Datum TT.MM.JJJJ mit automatischen Punkten und Kalenderauswahl. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatumFeld(wert: String, onWert: (String) -> Unit, label: String, modifier: Modifier = Modifier) {
    var zeigen by remember { mutableStateOf(false) }
    var hatteFokus by remember { mutableStateOf(false) }
    var feld by remember { mutableStateOf(TextFieldValue(wert, TextRange(wert.length))) }
    if (feld.text != wert) feld = TextFieldValue(wert, TextRange(wert.length))
    val quelle = remember { MutableInteractionSource() }
    val p0 = LocalPalette.current
    Box(modifier.fillMaxWidth().padding(top = 7.dp, bottom = 2.dp)) {
        BasicTextField(
            value = feld,
            onValueChange = { neu ->
                val z = neu.text.filter { it.isDigit() }.take(8)
                val t = when {
                    z.length > 4 -> z.substring(0, 2) + "." + z.substring(2, 4) + "." + z.substring(4)
                    z.length > 2 -> z.substring(0, 2) + "." + z.substring(2)
                    else -> z
                }
                val getippt = neu.text.length >= feld.text.length
                val ergebnis = if (getippt) t else neu.text.filter { it.isDigit() || it == '.' }
                feld = TextFieldValue(ergebnis, TextRange(ergebnis.length))
                onWert(ergebnis)
            },
            modifier = Modifier.fillMaxWidth().onFocusChanged {
                if (it.isFocused) hatteFokus = true
                else if (hatteFokus) {
                    hatteFokus = false
                    val z = wert.filter { c -> c.isDigit() }
                    if (z.length == 6) onWert(z.substring(0, 2) + "." + z.substring(2, 4) + ".20" + z.substring(4))
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = FELD_TEXT, cursorBrush = SolidColor(p0.akzent), interactionSource = quelle,
            decorationBox = { inner -> FeldRahmen(feld.text, inner, label, "TT.MM.JJJJ", true, quelle, mitSymbol = true) },
        )
        FeldSymbol(Icons.Outlined.CalendarMonth, "Kalender") { zeigen = true }
    }
    if (zeigen) {
        val start = parseDE(wert) ?: LocalDate.now()
        val zustand = rememberDatePickerState(initialSelectedDateMillis = start.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        val p = LocalPalette.current
        DatePickerDialog(
            onDismissRequest = { zeigen = false },
            confirmButton = {
                TextButton(onClick = {
                    zustand.selectedDateMillis?.let {
                        onWert(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().alsDE())
                    }
                    zeigen = false
                }) { Text("ÜBERNEHMEN", fontFamily = Schrift.mono, color = p.akzent) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { onWert(""); zeigen = false }) { Text("LEEREN", fontFamily = Schrift.mono, color = p.textDim) }
                    TextButton(onClick = { zeigen = false }) { Text("ABBRECHEN", fontFamily = Schrift.mono, color = p.textDim) }
                }
            },
            colors = DatePickerDefaults.colors(containerColor = p.panel),
        ) {
            DatePicker(
                state = zustand,
                colors = DatePickerDefaults.colors(
                    containerColor = p.panel, selectedDayContainerColor = p.akzent, todayDateBorderColor = p.akzent,
                    todayContentColor = p.akzent,
                ),
            )
        }
    }
}

/** Auswahlliste als aufklappbares Feld. */
@Composable
fun Auswahl(label: String, optionen: List<Pair<String, String>>, wert: String, modifier: Modifier = Modifier, onWert: (String) -> Unit) {
    var offen by remember { mutableStateOf(false) }
    val p = LocalPalette.current
    val quelle = remember { MutableInteractionSource() }
    val anzeige = optionen.firstOrNull { it.first == wert }?.second ?: wert
    Box(modifier.fillMaxWidth().padding(top = 7.dp, bottom = 2.dp)) {
        BasicTextField(
            value = anzeige, onValueChange = {}, readOnly = true, enabled = false, singleLine = true,
            modifier = Modifier.fillMaxWidth(), textStyle = FELD_TEXT, interactionSource = quelle,
            decorationBox = { inner -> FeldRahmen(anzeige, inner, label, "", true, quelle, aktiv = false, mitSymbol = true) },
        )
        Box(Modifier.matchParentSize().clip(RUND_KLEIN).clickable { offen = true })
        FeldSymbol(Icons.Outlined.KeyboardArrowDown, null, null)
        DropdownMenu(expanded = offen, onDismissRequest = { offen = false }) {
            optionen.forEach { (id, name) ->
                DropdownMenuItem(
                    text = { Text(name, fontFamily = Schrift.text, color = if (id == wert) p.akzent else p.text) },
                    onClick = { onWert(id); offen = false }
                )
            }
        }
    }
}

/** Freitextfeld mit Vorschlagsliste. */
@Composable
fun VorschlagFeld(wert: String, onWert: (String) -> Unit, label: String, vorschlaege: List<Pair<String, String>>, onVorschlag: ((String) -> Unit)? = null) {
    var offen by remember { mutableStateOf(false) }
    Box {
        Feld(wert, onWert, label, symbol = Icons.Outlined.KeyboardArrowDown, onSymbol = { offen = true })
        DropdownMenu(expanded = offen, onDismissRequest = { offen = false }) {
            vorschlaege.forEach { (id, name) ->
                DropdownMenuItem(text = { Text(name, fontFamily = Schrift.text) }, onClick = {
                    onWert(id); onVorschlag?.invoke(id); offen = false
                })
            }
        }
    }
}

@Composable
fun Regler(wert: Float, bereich: ClosedFloatingPointRange<Float>, schritte: Int, onWert: (Float) -> Unit, onFertig: () -> Unit = {}) {
    val p = LocalPalette.current
    Slider(
        value = wert, onValueChange = onWert, valueRange = bereich, steps = schritte, onValueChangeFinished = onFertig,
        colors = SliderDefaults.colors(thumbColor = p.akzent, activeTrackColor = p.akzent, inactiveTrackColor = p.rand,
            activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent)
    )
}

// ------------------------------------------------------------------ Dialoge

@Composable
fun Frage(titel: String, text: String, ja: String = "Löschen", gefahr: Boolean = true, onJa: () -> Unit, onNein: () -> Unit) {
    val p = LocalPalette.current
    AlertDialog(
        onDismissRequest = onNein,
        title = { Punkt(titel.uppercase(), 18.sp) },
        text = { Fliesstext(text, p.textDim) },
        confirmButton = { TextButton(onClick = onJa) { Text(ja.uppercase(), fontFamily = Schrift.mono, color = if (gefahr) p.rot else p.akzent, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onNein) { Text("ABBRECHEN", fontFamily = Schrift.mono, color = p.textDim) } },
        containerColor = p.panel, shape = RUND,
    )
}

@Composable
fun Meldung(titel: String, text: String, onOk: () -> Unit) {
    val p = LocalPalette.current
    AlertDialog(
        onDismissRequest = onOk,
        title = { Punkt(titel.uppercase(), 18.sp) },
        text = { Fliesstext(text, p.textDim) },
        confirmButton = { TextButton(onClick = onOk) { Text("OK", fontFamily = Schrift.mono, color = p.akzent, fontWeight = FontWeight.Bold) } },
        containerColor = p.panel, shape = RUND,
    )
}

/** Auswahl mit mehreren Knöpfen untereinander (z. B. Serientermin löschen). */
@Composable
fun Wahl(titel: String, text: String, knoepfe: List<Triple<String, KnopfArt, () -> Unit>>, onAbbruch: () -> Unit) {
    val p = LocalPalette.current
    AlertDialog(
        onDismissRequest = onAbbruch,
        title = { Punkt(titel.uppercase(), 18.sp) },
        text = {
            Column {
                Fliesstext(text, p.textDim)
                Abstand()
                knoepfe.forEach { (t, art, f) -> Knopf(t, Modifier.fillMaxWidth().padding(vertical = 4.dp), art) { f() } }
            }
        },
        confirmButton = { TextButton(onClick = onAbbruch) { Text("ABBRECHEN", fontFamily = Schrift.mono, color = p.textDim) } },
        containerColor = p.panel, shape = RUND,
    )
}

/** Klappbereich: "+ Eintrag hinzufügen" öffnet ein Formular. */
@Composable
fun Klappbereich(beschriftung: String, offen: Boolean, onUmschalten: (Boolean) -> Unit, inhalt: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Knopf((if (offen) "✕ " else "+ ") + beschriftung, Modifier, if (offen) KnopfArt.NORMAL else KnopfArt.PRIMAER, klein = true) {
            onUmschalten(!offen)
        }
        if (offen) {
            Abstand(8.dp)
            Column(content = inhalt)
        }
    }
}

/** Farbpunkte zur Auswahl, umbrechend, mit „+“ für eine frei gewählte Farbe. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FarbReihe(farben: List<String>, gewaehlt: String?, onWahl: (String) -> Unit) {
    val p = LocalPalette.current
    var eigeneOffen by remember { mutableStateOf(false) }
    val istEigene = gewaehlt != null && farben.none { it.equals(gewaehlt, true) }
    FlowRow(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        farben.forEach { hex ->
            val c = Color(de.gun.baustellen.reloaded.logik.farbeAusHex(hex))
            Box(
                Modifier.size(30.dp).clip(CircleShape).background(c)
                    .border(2.5.dp, if (hex.equals(gewaehlt, true)) p.text else Color.Transparent, CircleShape)
                    .clickable { onWahl(hex) }
            )
        }
        // Frei gewählte Farbe: zeigt die aktuelle eigene Farbe oder ein „+“
        Box(
            Modifier.size(30.dp).clip(CircleShape)
                .background(if (istEigene) Color(de.gun.baustellen.reloaded.logik.farbeAusHex(gewaehlt!!)) else Color.Transparent)
                .border(if (istEigene) 2.5.dp else 1.dp, if (istEigene) p.text else p.rand, CircleShape)
                .clickable { eigeneOffen = true },
            contentAlignment = Alignment.Center,
        ) { if (!istEigene) Text("+", color = p.textDim, fontSize = 16.sp, fontFamily = Schrift.mono) }
    }
    if (eigeneOffen) FarbWahlDialog(gewaehlt ?: farben.first(), { eigeneOffen = false }) { onWahl(it); eigeneOffen = false }
}

/** Freie Farbwahl über Farbton, Sättigung und Helligkeit oder Hex-Code. */
@Composable
fun FarbWahlDialog(start: String, onAbbruch: () -> Unit, onFertig: (String) -> Unit) {
    val p = LocalPalette.current
    val hsv = remember {
        FloatArray(3).also { android.graphics.Color.colorToHSV(de.gun.baustellen.reloaded.logik.farbeAusHex(start), it) }
    }
    var ton by remember { mutableStateOf(hsv[0]) }
    var satt by remember { mutableStateOf(hsv[1]) }
    var hell by remember { mutableStateOf(hsv[2]) }
    fun hexAktuell(): String = String.format("#%06x", android.graphics.Color.HSVToColor(floatArrayOf(ton, satt, hell)) and 0xFFFFFF)
    var hex by remember { mutableStateOf(hexAktuell()) }
    val farbe = Color(android.graphics.Color.HSVToColor(floatArrayOf(ton, satt, hell)))
    AlertDialog(
        onDismissRequest = onAbbruch,
        title = { Punkt("EIGENE FARBE", 18.sp) },
        text = {
            Column {
                Box(Modifier.fillMaxWidth().height(48.dp).clip(RUND_KLEIN).background(farbe))
                Abstand(8.dp)
                // Farbton-Verlauf als Orientierung über dem Regler
                Box(
                    Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(
                        androidx.compose.ui.graphics.Brush.horizontalGradient(
                            (0..12).map { Color(android.graphics.Color.HSVToColor(floatArrayOf(it * 30f, 1f, 1f))) }
                        )
                    )
                )
                Etikett("Farbton")
                Regler(ton, 0f..360f, 0, { ton = it; hex = hexAktuell() })
                Etikett("Sättigung")
                Regler(satt, 0f..1f, 0, { satt = it; hex = hexAktuell() })
                Etikett("Helligkeit")
                Regler(hell, 0f..1f, 0, { hell = it; hex = hexAktuell() })
                Feld(hex, { v ->
                    hex = v
                    val t = v.trim().removePrefix("#")
                    if (Regex("^[0-9a-fA-F]{6}$").matches(t)) {
                        val f = FloatArray(3)
                        android.graphics.Color.colorToHSV(("ff$t").toLong(16).toInt(), f)
                        ton = f[0]; satt = f[1]; hell = f[2]
                    }
                }, "Hex-Code", platzhalter = "#5fb4ff")
            }
        },
        confirmButton = { TextButton(onClick = { onFertig(hexAktuell()) }) { Text("ÜBERNEHMEN", fontFamily = Schrift.mono, color = p.akzent) } },
        dismissButton = { TextButton(onClick = onAbbruch) { Text("ABBRECHEN", fontFamily = Schrift.mono, color = p.textDim) } },
        containerColor = p.panel, shape = RUND,
    )
}

val PALETTE_KALENDER = listOf(
    "#5fb4ff", "#3f51b5", "#4dd0c4", "#35d488", "#0b8043", "#c0ca33",
    "#ffd54f", "#ffb020", "#f4511e", "#ff5c5c", "#d50000", "#ff8fc7",
    "#b98cff", "#8e24aa", "#795548", "#8b96a5", "#616161", "#e0e0e0",
)
