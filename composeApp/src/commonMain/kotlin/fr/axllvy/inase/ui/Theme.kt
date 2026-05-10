package fr.axllvy.inase.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import fr.axllvy.inase.data.StageKey

@Immutable
data class InaseColorPalette(
    val bg: Color,
    val bgMid: Color,
    val bgTop: Color,
    val onBg: Color,
    val onBgEmphasis: Color,
    val onBgTimeChip: Color,
    val onBgSubtle: Color,
    val onBgDim: Color,
    val onBgFaint: Color,
    val border: Color,
    val gridLine: Color,
    val columnBg: Color,
    val tabInactiveBg: Color,
    val headerBg: Color,
    val dialogScrim: Color,
    val accent: Color,
    val star: Color,
    val warn: Color,
    val isDark: Boolean,
)

private val DarkPalette = InaseColorPalette(
    bg = Color(0xFF050309),
    bgMid = Color(0xFF0A0814),
    bgTop = Color(0xFF1A1530),
    onBg = Color.White,
    onBgEmphasis = Color(0xD9FFFFFF),
    onBgTimeChip = Color(0x8CFFFFFF),
    onBgSubtle = Color(0x66FFFFFF),
    onBgDim = Color(0x80FFFFFF),
    onBgFaint = Color(0x4DFFFFFF),
    border = Color(0x18FFFFFF),
    gridLine = Color(0x0AFFFFFF),
    columnBg = Color(0x04FFFFFF),
    tabInactiveBg = Color(0x08FFFFFF),
    headerBg = Color(0xD90A0814),
    dialogScrim = Color(0x8C000000),
    accent = Color(0xFFA78BFA),
    star = Color(0xFFFBBF24),
    warn = Color(0xFFF59E0B),
    isDark = true,
)

private val LightPalette = InaseColorPalette(
    bg = Color(0xFFFAFAFC),
    bgMid = Color(0xFFF0EEF5),
    bgTop = Color(0xFFE8E2F2),
    onBg = Color(0xFF1A1530),
    onBgEmphasis = Color(0xD9000000),
    onBgTimeChip = Color(0x99000000),
    onBgSubtle = Color(0x80000000),
    onBgDim = Color(0x80000000),
    onBgFaint = Color(0x59000000),
    border = Color(0x1F000000),
    gridLine = Color(0x14000000),
    columnBg = Color(0x08000000),
    tabInactiveBg = Color(0x0A000000),
    headerBg = Color(0xD9F0EEF5),
    dialogScrim = Color(0x80000000),
    accent = Color(0xFF6D4FCC),
    star = Color(0xFFCA8A04),
    warn = Color(0xFFB45309),
    isDark = false,
)

val LocalInaseColors = staticCompositionLocalOf { DarkPalette }

object InaseColors {
    val Bg: Color @Composable get() = LocalInaseColors.current.bg
    val BgMid: Color @Composable get() = LocalInaseColors.current.bgMid
    val BgTop: Color @Composable get() = LocalInaseColors.current.bgTop
    val OnBg: Color @Composable get() = LocalInaseColors.current.onBg
    val OnBgEmphasis: Color @Composable get() = LocalInaseColors.current.onBgEmphasis
    val OnBgTimeChip: Color @Composable get() = LocalInaseColors.current.onBgTimeChip
    val OnBgSubtle: Color @Composable get() = LocalInaseColors.current.onBgSubtle
    val OnBgDim: Color @Composable get() = LocalInaseColors.current.onBgDim
    val OnBgFaint: Color @Composable get() = LocalInaseColors.current.onBgFaint
    val Border: Color @Composable get() = LocalInaseColors.current.border
    val GridLine: Color @Composable get() = LocalInaseColors.current.gridLine
    val ColumnBg: Color @Composable get() = LocalInaseColors.current.columnBg
    val TabInactiveBg: Color @Composable get() = LocalInaseColors.current.tabInactiveBg
    val HeaderBg: Color @Composable get() = LocalInaseColors.current.headerBg
    val DialogScrim: Color @Composable get() = LocalInaseColors.current.dialogScrim
    val Accent: Color @Composable get() = LocalInaseColors.current.accent
    val Star: Color @Composable get() = LocalInaseColors.current.star
    val Warn: Color @Composable get() = LocalInaseColors.current.warn
}

data class StageMeta(val color: Color, val label: String)

val stageMeta: Map<StageKey, StageMeta> = mapOf(
    StageKey.MIRAGE to StageMeta(Color(0xFFA78BFA), "Mirage"),
    StageKey.CLOUD to StageMeta(Color(0xFF60A5FA), "Cloud"),
    StageKey.ALTF4 to StageMeta(Color(0xFFF472B6), "AltF4"),
    StageKey.TECHNOBUS to StageMeta(Color(0xFF34D399), "Technobus"),
)

@Composable
fun InaseTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val palette = if (dark) DarkPalette else LightPalette
    val scheme = if (dark) {
        darkColorScheme(
            primary = palette.accent,
            background = palette.bg,
            surface = palette.bgMid,
            onBackground = palette.onBg,
            onSurface = palette.onBg,
        )
    } else {
        lightColorScheme(
            primary = palette.accent,
            background = palette.bg,
            surface = palette.bgMid,
            onBackground = palette.onBg,
            onSurface = palette.onBg,
        )
    }
    CompositionLocalProvider(LocalInaseColors provides palette) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
