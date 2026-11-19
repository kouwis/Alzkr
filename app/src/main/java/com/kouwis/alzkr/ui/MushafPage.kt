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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
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

private class PlacedWord(val key: String, val text: String, val x: Float, val baseline: Float, val hit: Rect)
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
) {
    val c = LocalColors.current
    Column(modifier.background(c.paper).padding(horizontal = 22.dp, vertical = 8.dp)) {
        PageCanvas(q, page, selected, onLongPressVerse, onTap, Modifier.weight(1f).fillMaxWidth())
    }
}

@Composable
private fun PageCanvas(
    q: QuranData,
    page: Int,
    selected: String?,
    onLongPressVerse: (String) -> Unit,
    onTap: () -> Unit,
    modifier: Modifier,
) {
    val c = LocalColors.current
    val haptics = LocalHapticFeedback.current
    val longPress by rememberUpdatedState(onLongPressVerse)
    val tap by rememberUpdatedState(onTap)
    val tf = remember(page) { PageFonts.get(page) }
    BoxWithConstraints(modifier) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        if (tf == null || w <= 0f || h <= 0f) return@BoxWithConstraints
        val layout = remember(page, w, h) { layoutPage(q, page, tf, w, h) }
        // every page drawn equally bold (the QCF page fonts differ slightly in weight)
        val wordPaint = remember(tf) {
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = tf; style = Paint.Style.FILL_AND_STROKE; strokeJoin = Paint.Join.ROUND
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
            if (selected != null) {
                layout.words.filter { it.key == selected }.forEach {
                    drawRoundRect(c.highlight, it.hit.topLeft, it.hit.size, CornerRadius(10f, 10f))
                }
            }
            wordPaint.textSize = layout.textSize
            wordPaint.strokeWidth = layout.textSize * 0.006f
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
                    ornPaint.style = if (o.bold) Paint.Style.FILL_AND_STROKE else Paint.Style.FILL
                    ornPaint.strokeWidth = o.size * 0.012f
                    nc.drawText(o.text, o.x, o.baseline, ornPaint)
                }
                layout.words.forEach { wd -> nc.drawText(wd.text, wd.x, wd.baseline, wordPaint) }
            }
        }
    }
}

private fun layoutPage(q: QuranData, page: Int, tf: Typeface, w: Float, h: Float): PageLayout {
    val lines = q.pages[page - 1]
    // Keep the printed page's proportions: never taller than the real text block,
    // with room just above it for surah/juz and just below it for the page number.
    val labelRoom = w * 1.62f / 15f
    val blockH = minOf(h - 2f * labelRoom, w * 1.62f)
    val blockTop = (h - blockH) / 2f
    val slots = if (page <= 2) lines.size + 3 else 15
    val lineH = blockH / slots
    val topPad = blockTop + if (page <= 2) lineH * 1.5f else 0f
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
    var size = 100f * (w - minGap * (fullestN - 1)) / fullest
    probe.textSize = size
    var fm = probe.fontMetrics
    val boxH = fm.descent - fm.ascent
    if (boxH > lineH * 1.3f) size *= lineH * 1.3f / boxH
    if (page <= 2) size *= 0.92f
    probe.textSize = size
    fm = probe.fontMetrics

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
        val top = topPad + i * lineH
        val mid = top + lineH / 2f
        when (line) {
            is PageLine.Header -> {
                ornaments += centered(AppFonts.FRAME.toString(), w, lineH * 1.3f, mid).let {
                    PlacedGlyph(it.text, it.x, it.baseline, it.size, bold = false)
                }
                val frameH = minOf(lineH * 1.3f, w / 8.2f)
                ornaments += centered(AppFonts.surahNameChar(line.surah).toString(), w * 0.30f, frameH * 0.62f, mid)
            }
            PageLine.Basmala -> ornaments += centered(AppFonts.BASMALA, w * 0.52f, lineH * 0.8f, mid)
            is PageLine.Text -> {
                val units = lineUnits[i]
                val widths = units.map { probe.measureText(it.glyph) }
                val natural = widths.sum()
                val n = widths.size
                // justified edge to edge (like the print); only the short opening pages are centred
                val justify = page > 2 && n > 1 && natural >= w * 0.6f
                val gap = if (justify) (w - natural) / (n - 1) else size * 0.18f
                val total = natural + gap * (n - 1).coerceAtLeast(0)
                var x = if (justify) w else (w + total) / 2f
                val baseline = mid - (fm.ascent + fm.descent) / 2f
                units.forEachIndexed { j, u ->
                    val ww = widths[j]
                    val left = x - ww
                    val hit = Rect(left - gap / 2f, top + lineH * 0.04f, x + gap / 2f, top + lineH * 0.96f)
                    words += PlacedWord(u.key, u.glyph, left, baseline, hit)
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

/** The page at full width and taller than the screen, for reading in landscape. */
@Composable
fun MushafPageTall(
    q: QuranData,
    page: Int,
    selected: String?,
    onLongPressVerse: (String) -> Unit,
    onTap: () -> Unit,
    width: Dp,
) {
    MushafPage(q, page, selected, onLongPressVerse, onTap, Modifier.fillMaxWidth().height(width * 1.6f))
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
        draw(AppFonts.surahNameChar(surah).toString(), cw * 0.30f, minOf(ch, cw / 8.2f) * 0.62f)
    }
}
