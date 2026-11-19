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
private class PlacedGlyph(val text: String, val x: Float, val baseline: Float, val size: Float)
private class PageLayout(val textSize: Float, val words: List<PlacedWord>, val ornaments: List<PlacedGlyph>)

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
    Column(modifier.background(c.paper).padding(horizontal = 22.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                q.surahName(q.surahOfPage(page)), color = c.muted, fontSize = 16.sp,
                fontFamily = AppFonts.naskhBold, modifier = Modifier.weight(1f),
            )
            Text("الجزء ${num(q.juzOfPage(page))}", color = c.muted, fontSize = 16.sp, fontFamily = AppFonts.naskhBold)
        }
        PageCanvas(q, page, selected, onLongPressVerse, onTap, Modifier.weight(1f).fillMaxWidth().padding(vertical = 6.dp))
        Text(
            num(page), color = c.muted, fontSize = 15.sp, fontFamily = AppFonts.naskh,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 10.dp),
        )
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
        val wordPaint = remember(tf) { Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = tf } }
        val ornPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = AppFonts.bsml } }
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
            wordPaint.color = c.ink.toArgb()
            ornPaint.color = c.ink.toArgb()
            drawIntoCanvas { canvas ->
                val nc = canvas.nativeCanvas
                layout.ornaments.forEach { o ->
                    ornPaint.textSize = o.size
                    nc.drawText(o.text, o.x, o.baseline, ornPaint)
                }
                layout.words.forEach { wd -> nc.drawText(wd.text, wd.x, wd.baseline, wordPaint) }
            }
        }
    }
}

private fun layoutPage(q: QuranData, page: Int, tf: Typeface, w: Float, h: Float): PageLayout {
    val lines = q.pages[page - 1]
    val slots = if (page <= 2) lines.size + 3 else 15
    val lineH = h / slots
    val topPad = if (page <= 2) lineH * 1.5f else 0f

    // Font size: the fullest line exactly fills the width (QCF V1 lines are pre-justified).
    val probe = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = tf; textSize = 100f }
    var fullest = 0f
    lines.forEach { line ->
        if (line is PageLine.Text) {
            var sum = 0f
            line.words.forEach { sum += probe.measureText(it.glyph) }
            fullest = maxOf(fullest, sum)
        }
    }
    if (fullest <= 0f) fullest = w
    var size = 100f * w / fullest
    probe.textSize = size
    var fm = probe.fontMetrics
    val boxH = fm.descent - fm.ascent
    if (boxH > lineH * 1.35f) size *= lineH * 1.35f / boxH
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
                ornaments += centered(AppFonts.FRAME.toString(), w, lineH * 0.92f, mid)
                val frameH = minOf(lineH * 0.92f, w / 8.2f)
                ornaments += centered(AppFonts.surahNameChar(line.surah).toString(), w * 0.30f, frameH * 0.62f, mid)
            }
            PageLine.Basmala -> ornaments += centered(AppFonts.BASMALA, w * 0.52f, lineH * 0.8f, mid)
            is PageLine.Text -> {
                val widths = line.words.map { probe.measureText(it.glyph) }
                val natural = widths.sum()
                val n = widths.size
                val justify = page > 2 && n > 1 && natural >= w * 0.85f
                val gap = if (justify) (w - natural) / (n - 1) else size * 0.12f
                val total = natural + gap * (n - 1).coerceAtLeast(0)
                var x = if (justify) w else (w + total) / 2f
                val baseline = mid - (fm.ascent + fm.descent) / 2f
                line.words.forEachIndexed { j, word ->
                    val ww = widths[j]
                    val left = x - ww
                    val hit = Rect(left - gap / 2f, top + lineH * 0.04f, x + gap / 2f, top + lineH * 0.96f)
                    words += PlacedWord(word.key, word.glyph, left, baseline, hit)
                    x = left - gap
                }
            }
        }
    }
    return PageLayout(size, words, ornaments)
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
