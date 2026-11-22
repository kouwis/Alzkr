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
    paper = Color.White, pageOuter = Color.White, frame = Color(0xFFD9CBAE), frame2 = Color(0xFFE6DAC0),
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
    // gear (Feather "settings", MIT)
    val settings = make(
        "settings",
        "M12 9a3 3 0 1 0 0 6 3 3 0 0 0 0-6z" +
            "M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z",
    )
    val palette = make("palette", "M12 3a9 9 0 0 0 0 18c1.1 0 1.6-.9 1.2-1.8-.5-1 .1-2.2 1.3-2.2H17a4 4 0 0 0 4-4c0-5.5-4-10-9-10zM7.5 11.5h.01M10 7.5h.01M14.5 7.5h.01M17 11h.01")
    val check = make("check", "M5 12.5l4.5 4.5L19 7.5")
    val tajweed = make("tajweed", "M12 3c-3 3.5-5 6.2-5 9a5 5 0 0 0 10 0c0-2.8-2-5.5-5-9zM9.5 13.5a2.5 2.5 0 0 0 2.5 2.5")
    val trash = make("trash", "M4 7h16M9 7V4h6v3M6 7l1 13h10l1-13M10 11v6M14 11v6")
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
    val bookmark = make("bookmark", "M6 3h12v18l-6-4.5L6 21z")
    val bookmarkFilled = make("bookmarkFilled", "M6 3h12v18l-6-4.5L6 21z", fill = true)
    val close = make("close", "M6 6l12 12M18 6L6 18")
    val info = make("info", "M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18zM12 11v5.5M12 7.6v.01")
    val pen = make("pen", "M4 20h4L19 9l-4-4L4 16v4zM13.5 6.5l4 4")
    val refresh = make("refresh", "M4 12a8 8 0 0 1 13.7-5.6L20 8.5M20 4v4.5h-4.5M20 12a8 8 0 0 1-13.7 5.6L4 15.5M4 20v-4.5h4.5")
}

/** The Adhkar section: the same look as the Quran section, blue instead of green. */
@Composable
fun AdhkarTheme(content: @Composable () -> Unit) {
    val base = LocalColors.current
    val c = if (base.dark) base.copy(
        primary = Color(0xFF2C6496), primarySoft = Color(0xFF1D2B39), primaryLine = Color(0xFF4F8BC4),
    ) else base.copy(
        primary = Color(0xFF1E4E79), primarySoft = Color(0xFFE3ECF5), primaryLine = Color(0xFF3C6E9A),
    )
    CompositionLocalProvider(LocalColors provides c, content = content)
}

object AdhkarIc {
    private fun make(name: String, d: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).addPath(
            pathData = PathParser().parsePathString(d).toNodes(),
            stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
        ).build()

    val beads = make("beads", "M12 3a2 2 0 1 0 0 4 2 2 0 0 0 0-4zM17.5 7a2 2 0 1 0 0 4 2 2 0 0 0 0-4zM16 13.5a2 2 0 1 0 0 4 2 2 0 0 0 0-4zM8 13.5a2 2 0 1 0 0 4 2 2 0 0 0 0-4zM6.5 7a2 2 0 1 0 0 4 2 2 0 0 0 0-4zM12 17.5V22")
    val qibla = make("qibla", "M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18zM15.5 8.5l-2 5-5 2 2-5z")
    val sun = make("sun", "M12 3v2M12 19v2M4.6 4.6l1.4 1.4M18 18l1.4 1.4M3 12h2M19 12h2M4.6 19.4L6 18M18 6l1.4-1.4M12 8a4 4 0 1 0 0 8 4 4 0 0 0 0-8z")
    val rise = make("rise", "M12 3v5M9 6l3-3 3 3M4 18h16M7 18a5 5 0 0 1 10 0M3 22h18")
    val set = make("set", "M12 8V3M9 5l3 3 3-3M4 18h16M7 18a5 5 0 0 1 10 0M3 22h18")
    val cloud = make("cloud", "M7 18h10a4 4 0 0 0 0-8 5 5 0 0 0-9.6 1.4A3.3 3.3 0 0 0 7 18z")
    val moon = make("moon", "M20 14.5A8 8 0 0 1 9.5 4a8 8 0 1 0 10.5 10.5z")
    val bed = make("bed", "M3 18v-6a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2v6M3 15h18M7 10V8a2 2 0 0 1 2-2h2")
    val alarm = make("alarm", "M12 8v4l2 2M5 3L2 6M22 6l-3-3M12 5a8 8 0 1 0 0 16 8 8 0 0 0 0-16z")
    val afterPrayer = make("afterPrayer", "M6 15l6-6 6 6M6 20l6-6 6 6")
    val mosque = make("mosque", "M4 21V11a8 8 0 0 1 16 0v10M2 21h20M12 3v2M9 21v-5a3 3 0 0 1 6 0v5")
    val star = make("star", "M12 3l2.4 4.9 5.4.8-3.9 3.8.9 5.4L12 15.4 7.2 17.9l.9-5.4L4.2 8.7l5.4-.8z")
    val book = make("book", "M4 5a2 2 0 0 1 2-2h12v16H6a2 2 0 0 0-2 2V5zM4 19a2 2 0 0 1 2-2h12")
    val clock = make("clock", "M12 7v5l3 2M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18z")
    val list = make("list", "M9 6h11M9 12h11M9 18h11M4 6h.01M4 12h.01M4 18h.01")
    val hands = make("hands", "M8 13V6a1.5 1.5 0 0 1 3 0v6M11 12V4.5a1.5 1.5 0 0 1 3 0V12M14 12V6a1.5 1.5 0 0 1 3 0v7a6 6 0 0 1-6 6h-1a5 5 0 0 1-4.2-2.3L4 13.5a1.5 1.5 0 0 1 2.5-1.6L8 14")
    val sorrow = make("sorrow", "M12 21s-7-4.4-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 11c0 5.6-7 10-7 10zM9 12h6")
    val shield = make("shield", "M12 3l7 3v6c0 4.5-3 7.5-7 9-4-1.5-7-4.5-7-9V6z")
    val istikhara = make("istikhara", "M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18zM12 7v5M12 16h.01")
    val sick = make("sick", "M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18zM12 8v8M8 12h8")
    val pin = make("pin", "M12 21s-7-6.2-7-11a7 7 0 0 1 14 0c0 4.8-7 11-7 11zM12 7.5a2.5 2.5 0 1 0 0 5 2.5 2.5 0 0 0 0-5z")
}
