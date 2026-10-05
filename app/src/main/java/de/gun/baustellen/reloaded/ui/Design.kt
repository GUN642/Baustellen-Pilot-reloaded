package de.gun.baustellen.reloaded.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import de.gun.baustellen.reloaded.R
import de.gun.baustellen.reloaded.daten.Design

/** Farben eines Themas. */
data class Palette(
    val hell: Boolean,
    val bg: Color,
    val panel: Color,
    val panelAlt: Color,
    val rand: Color,
    val randLeise: Color,
    val text: Color,
    val textDim: Color,
    val textFaint: Color,
    val akzent: Color,
    val akzentDim: Color,
    val gruen: Color,
    val gruenDim: Color,
    val rot: Color,
    val rotDim: Color,
    val warn: Color,
    val warnDim: Color,
    val neutral: Color,
    val punktSchrift: Boolean,
    val punktRaster: Boolean,
)

data class Thema(val id: String, val name: String, val beschreibung: String)

val THEMEN = listOf(
    Thema("nothing", "Nothing", "Tiefschwarz (AMOLED), Punktschrift"),
    Thema("nothing-light", "Nothing hell", "Weiß, klare Linien"),
    Thema("graphit", "Graphit", "Gedämpftes Dunkelgrau"),
    Thema("aulumu", "Aulumu", "Warmes Schwarz"),
    Thema("system", "System", "Folgt Hell/Dunkel des Geräts"),
)

data class Akzent(val id: String, val name: String, val dunkel: Color, val hell: Color)

val AKZENTE = listOf(
    Akzent("rot", "Nothing-Rot", Color(0xFFD71921), Color(0xFFD71921)),
    Akzent("orange", "Orange", Color(0xFFFF5A1F), Color(0xFFE04A10)),
    Akzent("amber", "Amber", Color(0xFFFFB020), Color(0xFFB7791F)),
    Akzent("gruen", "Grün", Color(0xFF35D488), Color(0xFF0F8A56)),
    Akzent("blau", "Blau", Color(0xFF5FB4FF), Color(0xFF1F6FD1)),
    Akzent("violett", "Violett", Color(0xFFB98CFF), Color(0xFF7446C9)),
    Akzent("mono", "Mono", Color(0xFFFFFFFF), Color(0xFF000000)),
)

fun palette(design: Design, systemDunkel: Boolean): Palette {
    val thema = if (design.thema == "system") (if (systemDunkel) "nothing" else "nothing-light") else design.thema
    val akz = AKZENTE.firstOrNull { it.id == design.akzent } ?: AKZENTE[0]
    val ps = design.punktSchrift
    val pr = design.punktRaster
    return when (thema) {
        "nothing-light" -> Palette(
            true, Color(0xFFFFFFFF), Color(0xFFF6F6F6), Color(0xFFEFEFEF), Color(0xFFD0D0D0), Color(0xFFE4E4E4),
            Color(0xFF000000), Color(0xFF545454), Color(0xFF8A8A8A), akz.hell, akz.hell.copy(alpha = 0.12f),
            Color(0xFF0F8A56), Color(0xFFD7EFE3), Color(0xFFC2141B), Color(0xFFF8DCDD), Color(0xFFB7791F), Color(0xFFFFF1D6),
            Color(0xFF6B6B6B), ps, pr
        )
        "graphit" -> Palette(
            false, Color(0xFF16181C), Color(0xFF1E2126), Color(0xFF191C21), Color(0xFF2C3038), Color(0xFF23262C),
            Color(0xFFEEF0F3), Color(0xFFA7ADB8), Color(0xFF767C87), akz.dunkel, akz.dunkel.copy(alpha = 0.16f),
            Color(0xFF35D488), Color(0xFF15271F), Color(0xFFFF5C52), Color(0xFF3A1615), Color(0xFFFFB020), Color(0xFF3A2A0A),
            Color(0xFF93A0B4), ps, pr
        )
        "aulumu" -> Palette(
            false, Color(0xFF0E0F11), Color(0xFF17181B), Color(0xFF121316), Color(0xFF2A2B2F), Color(0xFF1F2023),
            Color(0xFFF2F1EE), Color(0xFFA6A5A1), Color(0xFF7D7C78),
            if (design.akzent == "rot") Color(0xFFFF5A1F) else akz.dunkel,
            (if (design.akzent == "rot") Color(0xFFFF5A1F) else akz.dunkel).copy(alpha = 0.16f),
            Color(0xFF35D488), Color(0xFF15271F), Color(0xFFFF4D4D), Color(0xFF3A1414), Color(0xFFFFB020), Color(0xFF3A2A0A),
            Color(0xFF7C8A94), ps, pr
        )
        else -> Palette(
            false, Color(0xFF000000), Color(0xFF0D0D0D), Color(0xFF070707), Color(0xFF2B2B2B), Color(0xFF1A1A1A),
            Color(0xFFFFFFFF), Color(0xFF9B9B9B), Color(0xFF6A6A6A), akz.dunkel, akz.dunkel.copy(alpha = 0.18f),
            Color(0xFF35D488), Color(0xFF12301F), Color(0xFFFF453A), Color(0xFF3A0F0D), Color(0xFFFFB020), Color(0xFF3A2A0A),
            Color(0xFFB4B4B4), ps, pr
        )
    }
}

val LocalPalette = staticCompositionLocalOf { palette(Design(), true) }

object Schrift {
    val punkt = FontFamily(
        Font(R.font.doto_semibold, FontWeight.SemiBold),
        Font(R.font.doto_extrabold, FontWeight.ExtraBold),
    )
    val mono = FontFamily(
        Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
        Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
    )
    val text = FontFamily(
        Font(R.font.space_grotesk_regular, FontWeight.Normal),
        Font(R.font.space_grotesk_medium, FontWeight.Medium),
        Font(R.font.space_grotesk_bold, FontWeight.Bold),
    )
}

/** Überschriften in Punktschrift – oder, falls abgeschaltet, in der Monoschrift. */
val Palette.titelSchrift: FontFamily get() = if (punktSchrift) Schrift.punkt else Schrift.mono

@Composable
fun BaustellenTheme(design: Design, inhalt: @Composable () -> Unit) {
    val p = palette(design, isSystemInDarkTheme())
    val basis = if (p.hell) lightColorScheme() else darkColorScheme()
    val farben = basis.copy(
        primary = p.akzent,
        onPrimary = if (p.akzent.luminanz() > 0.5f) Color.Black else Color.White,
        primaryContainer = p.akzentDim,
        onPrimaryContainer = p.text,
        secondary = p.textDim,
        background = p.bg,
        onBackground = p.text,
        surface = p.panel,
        onSurface = p.text,
        surfaceVariant = p.panelAlt,
        onSurfaceVariant = p.textDim,
        surfaceContainer = p.panel,
        surfaceContainerHigh = p.panel,
        surfaceContainerHighest = p.panelAlt,
        surfaceContainerLow = p.panel,
        surfaceContainerLowest = p.bg,
        outline = p.rand,
        outlineVariant = p.randLeise,
        error = p.rot,
        onError = Color.White,
        inverseSurface = p.text,
        inverseOnSurface = p.bg,
        scrim = Color.Black,
    )
    val typo = MaterialTheme.typography.let { t ->
        t.copy(
            bodyLarge = t.bodyLarge.copy(fontFamily = Schrift.text),
            bodyMedium = t.bodyMedium.copy(fontFamily = Schrift.text),
            bodySmall = t.bodySmall.copy(fontFamily = Schrift.text),
            labelLarge = t.labelLarge.copy(fontFamily = Schrift.mono),
            labelMedium = t.labelMedium.copy(fontFamily = Schrift.mono),
            labelSmall = t.labelSmall.copy(fontFamily = Schrift.mono),
            titleLarge = t.titleLarge.copy(fontFamily = p.titelSchrift, fontWeight = FontWeight.ExtraBold),
            titleMedium = t.titleMedium.copy(fontFamily = Schrift.text, fontWeight = FontWeight.Bold),
            titleSmall = t.titleSmall.copy(fontFamily = Schrift.text, fontWeight = FontWeight.Medium),
            headlineSmall = t.headlineSmall.copy(fontFamily = p.titelSchrift, fontWeight = FontWeight.ExtraBold),
        )
    }
    CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(colorScheme = farben, typography = typo, content = inhalt)
    }
}

fun Color.luminanz(): Float = 0.299f * red + 0.587f * green + 0.114f * blue

/** Lesbare Schrift auf einer Kalenderfarbe. */
fun textAuf(farbe: Color): Color = if (farbe.luminanz() > 0.53f) Color(0xFF0D1116) else Color.White
