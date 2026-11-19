package com.kouwis.alzkr.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kouwis.alzkr.data.AppFonts
import com.kouwis.alzkr.data.ArabicText.num
import com.kouwis.alzkr.data.PageFonts
import com.kouwis.alzkr.data.PageLine
import com.kouwis.alzkr.data.QuranData

private class PlacedWord(val key: String, val text: String, val x: Float, val baseline: Float, val hit: Rect, val scaleX: Float = 1f)
private class PlacedGlyph(val text: String, val x: Float, val baseline: Float, val size: Float, val bold: Boolean = true)
private class PageLayout(
    val textSize: Float,
    val words: List<PlacedWord>,
    val ornaments: List<PlacedGlyph>,
    val labelSize: Float,
    val headerBaseline: Float,
    val footerBaseline: Float,
)

/**
 * One page of the Madinah mushaf, drawn like the printed page: 15 lines, each
 * line set with the page's own KFGQPC QCF font, real surah frames and basmala.
 * Long-press an ayah to select it; a short tap is reported through [onTap].
 */
@Composable
fun MushafPage(
    q: QuranData,
    page: Int,
    selected: String?,
    onLongPressVerse: (String) -> Unit,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    focus: String? = null,
    tajweed: Boolean = false,
) {
    val c = LocalColors.current
    Column(
        modifier
            .background(c.paper)
            .padding(horizontal = 22.dp, vertical = 8.dp),
    ) {
        PageCanvas(q, page, selected, focus, tajweed, onLongPressVerse, onTap, Modifier.weight(1f).fillMaxWidth())
    }
}

@Composable
private fun PageCanvas(
    q: QuranData,
    page: Int,
    selected: String?,
    focus: String?,
    tajweed: Boolean,
    onLongPressVerse: (String) -> Unit,
    onTap: () -> Unit,
    modifier: Modifier,
) {
    val c = LocalColors.current
    val haptics = LocalHapticFeedback.current
    val longPress by rememberUpdatedState(onLongPressVerse)
    val tap by rememberUpdatedState(onTap)
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val dark = c.dark
    // tajweed: the colour font for this page (downloaded once); until it is here, the normal page
    val tjTf by androidx.compose.runtime.produceState(
        if (tajweed) com.kouwis.alzkr.data.TajweedFonts.cached(page, dark) else null, page, tajweed, dark,
    ) {
        value = if (tajweed) com.kouwis.alzkr.data.TajweedFonts.get(ctx, page, dark) else null
    }
    val useTajweed = tajweed && tjTf != null && q.pagesTajweed.size == 604
    // the page font is read from the APK and the page is laid out on a background thread,
    // so opening the mushaf and turning pages never stalls the animation
    val plainTf by androidx.compose.runtime.produceState(PageFonts.cached(page), page) {
        if (value == null) value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { PageFonts.get(page) }
    }
    val tf = if (useTajweed) tjTf else plainTf
    // the ayah you came to (from favourites / bookmarks / search) lights up: two soft pulses,
    // then it stays gently lit until you tap the page
    val glowAlpha = remember { Animatable(0f) }
    LaunchedEffect(focus) {
        glowAlpha.snapTo(0f)
        if (focus != null) {
            repeat(2) {
                glowAlpha.animateTo(1f, tween(500))
                glowAlpha.animateTo(0.35f, tween(500))
            }
            glowAlpha.animateTo(0.85f, tween(500))
        }
    }
    BoxWithConstraints(modifier) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        if (tf == null || w <= 0f || h <= 0f) return@BoxWithConstraints
        val laid by androidx.compose.runtime.produceState<PageLayout?>(null, page, w, h, useTajweed, tf) {
            value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { layoutPage(q, page, tf, w, h, useTajweed) }
        }
        val layout = laid ?: return@BoxWithConstraints
        // the page fonts exactly as the complex drew them: no added boldness
        val wordPaint = remember(tf) {
            // subpixel positioning + linear (unhinted, uncached-bitmap) glyphs: every word is
            // rasterised from its outline the same way, wherever it lands on the pixel grid
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG or Paint.LINEAR_TEXT_FLAG).apply {
                typeface = tf; style = Paint.Style.FILL
                if (android.os.Build.VERSION.SDK_INT >= 29) hinting = Paint.HINTING_OFF
            }
        }
        val ornPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = AppFonts.bsml; strokeJoin = Paint.Join.ROUND } }
        val labelPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = AppFonts.naskhTf } }
        val surahLabel = q.surahName(q.surahOfPage(page))
        val juzLabel = "الجزء ${num(q.juzOfPage(page))}"
        val pageLabel = num(page)
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(layout) {
                    detectTapGestures(
                        onLongPress = { pos ->
                            layout.words.firstOrNull { it.hit.contains(pos) }?.let {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                longPress(it.key)
                            }
                        },
                        onTap = { tap() },
                    )
                },
        ) {
            if (focus != null && focus != selected) {
                val glow = c.gold.copy(alpha = 0.30f * glowAlpha.value)
                layout.words.filter { it.key == focus }.forEach {
                    drawRoundRect(glow, it.hit.topLeft, it.hit.size, CornerRadius(10f, 10f))
                }
            }
            if (selected != null) {
                layout.words.filter { it.key == selected }.forEach {
                    drawRoundRect(c.highlight, it.hit.topLeft, it.hit.size, CornerRadius(10f, 10f))
                }
            }
            wordPaint.textSize = layout.textSize
            wordPaint.color = c.ink.toArgb()
            ornPaint.color = c.ink.toArgb()
            labelPaint.color = c.muted.toArgb()
            labelPaint.textSize = layout.labelSize
            drawIntoCanvas { canvas ->
                val nc = canvas.nativeCanvas
                labelPaint.textAlign = Paint.Align.RIGHT
                nc.drawText(surahLabel, size.width, layout.headerBaseline, labelPaint)
                labelPaint.textAlign = Paint.Align.LEFT
                nc.drawText(juzLabel, 0f, layout.headerBaseline, labelPaint)
                labelPaint.textAlign = Paint.Align.CENTER
                nc.drawText(pageLabel, size.width / 2f, layout.footerBaseline, labelPaint)
                layout.ornaments.forEach { o ->
                    ornPaint.textSize = o.size
                    ornPaint.style = Paint.Style.FILL
                    nc.drawText(o.text, o.x, o.baseline, ornPaint)
                }
                layout.words.forEach { wd ->
                    wordPaint.textScaleX = wd.scaleX
                    nc.drawText(wd.text, wd.x, wd.baseline, wordPaint)
                }
            }
        }
    }
}

private fun layoutPage(q: QuranData, page: Int, tf: Typeface, w: Float, h: Float, tajweed: Boolean = false): PageLayout {
    val lines = if (tajweed) q.pagesTajweed[page - 1] else q.pages[page - 1]
    val lineEm = if (tajweed) q.meta.lineEmTajweed else q.meta.lineEm
    // Keep the printed page's proportions: never taller than the real text block,
    // with room just above it for surah/juz and just below it for the page number.
    val labelRoom = w * 1.62f / 15f
    val blockH = minOf(h - 2f * labelRoom, w * 1.62f)
    val blockTop = (h - blockH) / 2f
    val lineH = blockH / 15f
    val opening = page <= 2 // al-Fatiha and the start of al-Baqara: short centred lines, set tight
    val labelSize = labelRoom * 0.42f

    // Every word and every ayah-number is its own unit, so spacing is the same everywhere.
    class Glyph(val key: String, val glyph: String)
    val lineUnits = lines.map { line ->
        if (line is PageLine.Text) line.words.flatMap { wd -> wd.glyph.split(' ').filter { it.isNotEmpty() }.map { Glyph(wd.key, it) } }
        else emptyList()
    }

    // Font size: the fullest line exactly fills the width (QCF V1 lines are pre-justified).
    val probe = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = tf; textSize = 100f }
    var fullest = 0f
    var fullestN = 1
    lineUnits.forEach { units ->
        if (units.isNotEmpty()) {
            var sum = 0f
            units.forEach { sum += probe.measureText(it.glyph) }
            if (sum > fullest) { fullest = sum; fullestN = units.size }
        }
    }
    if (fullest <= 0f) fullest = w
    val minGap = w * 0.004f // a hair of space between units even on the fullest line
    // One text size for the whole mushaf (from the typical full-line width of all pages);
    // falls back to this page's fullest line if that number is missing.
    var size = if (lineEm > 0f) w / lineEm * 0.985f else 100f * (w - minGap * (fullestN - 1)) / fullest
    probe.textSize = size
    var fm = probe.fontMetrics
    val boxH = fm.descent - fm.ascent
    if (boxH > lineH * 1.3f) size *= lineH * 1.3f / boxH
    if (opening) size *= 1.12f
    probe.textSize = size
    fm = probe.fontMetrics

    // Line positions: 15 equal lines on normal pages; on the two opening pages the lines sit
    // close together (like the print) and the whole group is centred on the page.
    val lineHeights = lines.map { line ->
        when {
            !opening -> lineH
            line is PageLine.Header -> lineH * 1.35f
            else -> (fm.descent - fm.ascent) * 0.95f
        }
    }
    val lineTops = FloatArray(lines.size)
    run {
        var y = if (opening) blockTop + (blockH - lineHeights.sum()) / 2f else blockTop
        lineHeights.forEachIndexed { i, lh -> lineTops[i] = y; y += lh }
    }

    val words = ArrayList<PlacedWord>()
    val ornaments = ArrayList<PlacedGlyph>()
    val orn = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = AppFonts.bsml }
    val bounds = android.graphics.Rect()

    fun centered(text: String, targetW: Float, maxH: Float, mid: Float): PlacedGlyph {
        orn.textSize = 100f
        orn.getTextBounds(text, 0, text.length, bounds)
        var s = 100f * targetW / bounds.width().coerceAtLeast(1)
        if (bounds.height() * s / 100f > maxH) s = 100f * maxH / bounds.height().coerceAtLeast(1)
        orn.textSize = s
        orn.getTextBounds(text, 0, text.length, bounds)
        val x = (w - bounds.width()) / 2f - bounds.left
        val baseline = mid - bounds.exactCenterY()
        return PlacedGlyph(text, x, baseline, s)
    }

    lines.forEachIndexed { i, line ->
        val top = lineTops[i]
        val lh = lineHeights[i]
        val mid = top + lh / 2f
        when (line) {
            is PageLine.Header -> {
                ornaments += centered(AppFonts.FRAME.toString(), w, lineH * 1.3f, mid).let {
                    PlacedGlyph(it.text, it.x, it.baseline, it.size, bold = false)
                }
                val frameH = minOf(lineH * 1.3f, w / 8.2f)
                ornaments += centered(AppFonts.surahNameChar(line.surah).toString(), w * 0.22f, frameH * 0.46f, mid).let {
                    PlacedGlyph(it.text, it.x, it.baseline, it.size, bold = false)
                }
            }
            PageLine.Basmala -> ornaments += centered(AppFonts.BASMALA, w * 0.52f, lineH * 0.8f, mid).let {
                PlacedGlyph(it.text, it.x, it.baseline, it.size, bold = false)
            }
            is PageLine.Text -> {
                val units = lineUnits[i]
                var widths = units.map { probe.measureText(it.glyph) }
                val n = widths.size
                // a line a hair longer than the others is condensed slightly instead of shrinking the page
                val room = w - minGap * (n - 1).coerceAtLeast(0)
                val scaleX = if (widths.sum() > room) room / widths.sum() else 1f
                if (scaleX < 1f) widths = widths.map { it * scaleX }
                val natural = widths.sum()
                // justified edge to edge (like the print); only the short opening pages are centred
                val justify = page > 2 && n > 1 && natural >= w * 0.6f
                val gap = if (justify) (w - natural) / (n - 1) else size * 0.07f
                val total = natural + gap * (n - 1).coerceAtLeast(0)
                var x = if (justify) w else (w + total) / 2f
                val baseline = mid - (fm.ascent + fm.descent) / 2f
                units.forEachIndexed { j, u ->
                    val ww = widths[j]
                    val left = x - ww
                    val hit = Rect(left - gap / 2f, top + lh * 0.04f, x + gap / 2f, top + lh * 0.96f)
                    words += PlacedWord(u.key, u.glyph, left, baseline, hit, scaleX)
                    x = left - gap
                }
            }
        }
    }
    return PageLayout(
        size, words, ornaments, labelSize,
        // labels sit in the middle of the margin: away from the text and away from the edge
        headerBaseline = blockTop - minOf(blockTop * 0.5f, labelRoom * 1.1f) + labelSize * 0.35f,
        footerBaseline = blockTop + blockH + minOf((h - blockTop - blockH) * 0.5f, labelRoom * 1.1f) + labelSize * 0.35f,
    )
}

/**
 * Thin stacked page edges on the outer side of the page, over its full height (from the very
 * top of the screen to the bottom): odd pages are right-hand pages, even pages left-hand.
 */
fun Modifier.pageEdges(page: Int, dark: Boolean, bookmarked: Boolean = false, ribbon: Color = Color(0xFFB8893B)): Modifier = drawWithContent {
    // drawn on top of the page (it sits in the outer margin, clear of the text)
    drawContent()
    val rightHand = page % 2 == 1
    if (bookmarked) {
        // a ribbon hanging from the top of the page, near its outer edge
        val rw = 16.dp.toPx()
        val rh = 44.dp.toPx()
        val left = if (rightHand) size.width - 36.dp.toPx() - rw else 36.dp.toPx()
        val path = Path().apply {
            moveTo(left, 0f)
            lineTo(left + rw, 0f)
            lineTo(left + rw, rh)
            lineTo(left + rw / 2f, rh - rw * 0.55f)
            lineTo(left, rh)
            close()
        }
        drawPath(path, ribbon)
    }
    val edge = if (dark) Color.White.copy(alpha = 0.18f) else Color.Black.copy(alpha = 0.18f)
    val step = 2.5.dp.toPx()
    for (k in 0 until 4) {
        val x = if (rightHand) size.width - step * (k + 0.5f) else step * (k + 0.5f)
        drawLine(edge.copy(alpha = edge.alpha * (1f - k * 0.2f)), Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.dp.toPx())
    }
}

/** The page at full width and taller than the screen, for reading in landscape. */
@Composable
fun MushafPageTall(
    q: QuranData,
    page: Int,
    selected: String?,
    onLongPressVerse: (String) -> Unit,
    onTap: () -> Unit,
    width: Dp,
    focus: String? = null,
    tajweed: Boolean = false,
) {
    MushafPage(q, page, selected, onLongPressVerse, onTap, Modifier.fillMaxWidth().height(width * 1.6f), focus, tajweed)
}

/** Real mushaf surah frame (QCF_BSML) with the surah name, for the verse-by-verse list. */
@Composable
fun SurahFrame(surah: Int, modifier: Modifier = Modifier) {
    val c = LocalColors.current
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = AppFonts.bsml } }
    Canvas(modifier) {
        paint.color = c.ink.toArgb()
        val b = android.graphics.Rect()
        val cw = size.width
        val ch = size.height
        fun draw(text: String, targetW: Float, maxH: Float) {
            paint.textSize = 100f
            paint.getTextBounds(text, 0, text.length, b)
            var s = 100f * targetW / b.width().coerceAtLeast(1)
            if (b.height() * s / 100f > maxH) s = 100f * maxH / b.height().coerceAtLeast(1)
            paint.textSize = s
            paint.getTextBounds(text, 0, text.length, b)
            drawIntoCanvas {
                it.nativeCanvas.drawText(text, (cw - b.width()) / 2f - b.left, ch / 2f - b.exactCenterY(), paint)
            }
        }
        draw(AppFonts.FRAME.toString(), cw, ch)
        draw(AppFonts.surahNameChar(surah).toString(), cw * 0.22f, minOf(ch, cw / 8.2f) * 0.46f)
    }
}
