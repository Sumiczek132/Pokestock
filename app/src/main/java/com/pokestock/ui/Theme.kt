package com.pokestock.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

data class Palette(
    val bg: Color, val surface: Color, val surface2: Color, val line: Color,
    val fg: Color, val muted: Color, val faint: Color,
    val accent: Color, val onAccent: Color, val accentInk: Color,
    val up: Color, val down: Color, val want: Color,
)

val DarkPalette = Palette(
    bg = Color(0xFF0E131D), surface = Color(0xFF161D2B), surface2 = Color(0xFF1E2738), line = Color(0xFF2A3447),
    fg = Color(0xFFEDF1F8), muted = Color(0xFF9BA6BB), faint = Color(0xFF6E7A90),
    accent = Color(0xFFF5C518), onAccent = Color(0xFF1C1600), accentInk = Color(0xFFF5C518),
    up = Color(0xFF45CF86), down = Color(0xFFFF7466), want = Color(0xFF7FB2FF),
)
val LightPalette = Palette(
    bg = Color(0xFFEEF1F6), surface = Color(0xFFFFFFFF), surface2 = Color(0xFFE6EBF3), line = Color(0xFFD3DAE6),
    fg = Color(0xFF111826), muted = Color(0xFF4F5A70), faint = Color(0xFF6A7489),
    accent = Color(0xFFF2B705), onAccent = Color(0xFF1C1600), accentInk = Color(0xFF7A5800),
    up = Color(0xFF11784A), down = Color(0xFFBE3326), want = Color(0xFF1F5FC4),
)

val LocalPalette = staticCompositionLocalOf { DarkPalette }
val P: Palette @Composable get() = LocalPalette.current

object Type {
    val display = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, letterSpacing = (-0.4).sp, lineHeight = 32.sp)
    val value = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.ExtraBold, fontSize = 42.sp, letterSpacing = (-0.8).sp, lineHeight = 44.sp)
    val price = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.ExtraBold, fontSize = 32.sp, lineHeight = 34.sp)
    val title = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp)
    val body = TextStyle(fontSize = 15.sp)
    val strong = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    val small = TextStyle(fontSize = 12.sp)
    val label = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
    val mono = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp)
}

@Composable
fun PokestockTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val p = if (dark) DarkPalette else LightPalette
    val scheme = if (dark) darkColorScheme(primary = p.accent, onPrimary = p.onAccent, background = p.bg, surface = p.surface, onSurface = p.fg, onBackground = p.fg)
    else lightColorScheme(primary = p.accent, onPrimary = p.onAccent, background = p.bg, surface = p.surface, onSurface = p.fg, onBackground = p.fg)
    CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
