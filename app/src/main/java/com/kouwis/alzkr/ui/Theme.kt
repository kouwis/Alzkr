package com.kouwis.alzkr.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

data class AlzkrColors(
    val bg: Color,
    val card: Color,
    val border: Color,
    val chip: Color,
    val text: Color,
    val muted: Color,
    val primary: Color,
    val onPrimary: Color,
    val primarySoft: Color,
    val primaryLine: Color,
    val gold: Color,
    val goldSoft: Color,
    val goldText: Color,
    val heart: Color,
    val paper: Color,
    val pageOuter: Color,
    val frame: Color,
    val frame2: Color,
    val ink: Color,
    val highlight: Color,
    val dark: Boolean,
)

val LightColors = AlzkrColors(
    bg = Color(0xFFF6F2EA), card = Color.White, border = Color(0xFFE9E2D4), chip = Color(0xFFECE6D9),
    text = Color(0xFF1E2A28), muted = Color(0xFF5E6B68), primary = Color(0xFF1F5E55), onPrimary = Color.White,
    primarySoft = Color(0xFFE3EEEA), primaryLine = Color(0xFF3A7A70), gold = Color(0xFFB8893B),
    goldSoft = Color(0xFFF4EAD7), goldText = Color(0xFF8A5F1A), heart = Color(0xFFB03A48),
    paper = Color(0xFFFCFAF4), pageOuter = Color(0xFFEFE9DC), frame = Color(0xFFD9CBAE), frame2 = Color(0xFFE6DAC0),
    ink = Color(0xFF1B1B1B), highlight = Color(0xFFF3DFC1), dark = false,
)

val DarkColors = AlzkrColors(
    bg = Color(0xFF0F1514), card = Color(0xFF18201F), border = Color(0xFF2A3533), chip = Color(0xFF222B29),
    text = Color(0xFFE8E2D4), muted = Color(0xFF9AA6A3), primary = Color(0xFF2C7166), onPrimary = Color.White,
    primarySoft = Color(0xFF1E3A35), primaryLine = Color(0xFF3E8579), gold = Color(0xFFC9A05A),
    goldSoft = Color(0xFF3A3020), goldText = Color(0xFFE2C287), heart = Color(0xFFE07A86),
    paper = Color(0xFF161D1C), pageOuter = Color(0xFF0F1514), frame = Color(0xFF33403D), frame2 = Color(0xFF26302E),
    ink = Color(0xFFE8E2D4), highlight = Color(0xFF3A4A3F), dark = true,
)

val LocalColors = staticCompositionLocalOf { LightColors }

@Composable
fun AlzkrTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val c = if (dark) DarkColors else LightColors
    val scheme = if (dark) {
        darkColorScheme(primary = c.primary, background = c.bg, surface = c.card, onSurface = c.text, onBackground = c.text)
    } else {
        lightColorScheme(primary = c.primary, background = c.bg, surface = c.card, onSurface = c.text, onBackground = c.text)
    }
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(
            LocalColors provides c,
            LocalLayoutDirection provides LayoutDirection.Rtl,
            content = content,
        )
    }
}

/** Small stroke icons drawn from SVG path data (24x24 viewport). */
object Ic {
    private fun make(name: String, d: String, fill: Boolean = false): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).addPath(
            pathData = PathParser().parsePathString(d).toNodes(),
            fill = if (fill) SolidColor(Color.Black) else null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ).build()

    val search = make("search", "M11 4a7 7 0 1 0 0 14 7 7 0 0 0 0-14zM20 20l-4-4")
    val settings = make("settings", "M12 9a3 3 0 1 0 0 6 3 3 0 0 0 0-6zM12 2v3M12 19v3M4.9 4.9l2.1 2.1M17 17l2.1 2.1M2 12h3M19 12h3M4.9 19.1L7 17M17 7l2.1-2.1")
    val heart = make("heart", "M12 20s-7-4.4-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.6-7 10-7 10z", fill = true)
    val heartOutline = make("heartOutline", "M12 20s-7-4.4-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.6-7 10-7 10z")
    val back = make("back", "M9 6l6 6-6 6")
    val forward = make("forward", "M15 6l-6 6 6 6")
    val book = make("book", "M4 5a2 2 0 0 1 2-2h12v16H6a2 2 0 0 0-2 2V5zM4 19a2 2 0 0 1 2-2h12M9 8h6M9 11h4")
    val translate = make("translate", "M4 5h8M8 3v2M10 5c-1 4-3.5 7-6 8.5M6 9c1.2 2 3 3.6 5 4.5M13 21l4-10 4 10M14.5 17.5h5")
    val copy = make("copy", "M9 9h10v12H9zM5 15V3h10")
    val share = make("share", "M12 3v12M7 8l5-5 5 5M5 14v6h14v-6")
    val lines = make("lines", "M4 6h16M4 12h16M4 18h10")
    val page = make("page", "M6 3h9l4 4v14H6zM14 3v5h5")
    val up = make("up", "M6 15l6-6 6 6")
    val close = make("close", "M6 6l12 12M18 6L6 18")
}
