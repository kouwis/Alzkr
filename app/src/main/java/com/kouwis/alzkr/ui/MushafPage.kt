package com.kouwis.alzkr.ui

import android.graphics.Typeface as AndroidTypeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.Typeface
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kouwis.alzkr.data.ArabicText.num
import com.kouwis.alzkr.data.PageFonts
import com.kouwis.alzkr.data.PageLine
import com.kouwis.alzkr.data.QuranData

private class PlacedWord(val key: String, val layout: TextLayoutResult, val topLeft: Offset, val hit: Rect)
private class PlacedLabel(val layout: TextLayoutResult, val topLeft: Offset, val frame: Rect?)
private class PageLayout(val words: List<PlacedWord>, val labels: List<PlacedLabel>, val lineH: Float)

private sealed interface FontState {
    data object Loading : FontState
    data object Failed : FontState
    data class Ready(val tf: AndroidTypeface) : FontState
}

/** One Madani mushaf page drawn line by line with its QCF V2 page font. */
@Composable
fun MushafPage(
    q: QuranData,
    page: Int,
    selected: String?,
    onTapVerse: (String) -> Unit,
    onTapEmpty: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalColors.current
    val ctx = LocalContext.current
    var retry by remember { mutableIntStateOf(0) }
    val font by produceState<FontState>(
        PageFonts.cached(page)?.let { FontState.Ready(it) } ?: FontState.Loading, page, retry,
    ) {
        if (value !is FontState.Ready) {
            value = FontState.Loading
            value = PageFonts.get(ctx, page)?.let { FontState.Ready(it) } ?: FontState.Failed
        }
    }
    val lines = q.pages[page - 1]
    val needsBasmala = lines.any { it is PageLine.Basmala }
    val basmalaFont by produceState(PageFonts.cached(1), needsBasmala) {
        if (needsBasmala && value == null) value = PageFonts.get(ctx, 1)
    }

    Column(
        modifier
            .background(c.pageOuter)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(c.paper, RoundedCornerShape(6.dp))
                .border(1.dp, c.frame, RoundedCornerShape(6.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("سورة ${q.surahName(q.surahOfPage(page))}", color = c.primary, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("الجزء ${num(q.juzOfPage(page))}", color = c.muted, fontSize = 12.sp)
            }
            HorizontalDivider(color = c.frame2)
            Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 6.dp)) {
                when (val f = font) {
                    is FontState.Ready -> PageCanvas(q, page, f.tf, basmalaFont, selected, onTapVerse, onTapEmpty)
                    FontState.Loading -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = c.primary)
                        Text("جارٍ تنزيل خط الصفحة…", color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
                    }
                    FontState.Failed -> Column(
                        Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text("محتاج إنترنت أول مرة عشان ننزّل خط الصفحة دي", color = c.muted, fontSize = 14.sp)
                        Text(
                            "حاول تاني",
                            color = c.onPrimary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .background(c.primary, RoundedCornerShape(20.dp))
                                .clickable { retry++ }
                                .padding(horizontal = 20.dp, vertical = 10.dp),
                        )
                    }
                }
            }
            HorizontalDivider(color = c.frame2)
            Text(
                num(page),
                color = c.muted,
                fontSize = 13.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun PageCanvas(
    q: QuranData,
    page: Int,
    tf: AndroidTypeface,
    basmalaTf: AndroidTypeface?,
    selected: String?,
    onTapVerse: (String) -> Unit,
    onTapEmpty: () -> Unit,
) {
    val c = LocalColors.current
    val measurer = rememberTextMeasurer(cacheSize = 0)
    val density = LocalDensity.current
    val tapVerse by rememberUpdatedState(onTapVerse)
    val tapEmpty by rememberUpdatedState(onTapEmpty)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val layout = remember(page, tf, basmalaTf, w, h, c.dark) {
            layoutPage(q, page, tf, basmalaTf, w, h, measurer, density)
        }
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(layout) {
                    detectTapGestures { pos ->
                        val hit = layout.words.firstOrNull { it.hit.contains(pos) }
                        if (hit != null) tapVerse(hit.key) else tapEmpty()
                    }
                },
        ) {
            if (selected != null) {
                layout.words.filter { it.key == selected }.forEach {
                    drawRoundRect(c.highlight, it.hit.topLeft, it.hit.size, CornerRadius(8f, 8f))
                }
            }
            layout.labels.forEach { l ->
                l.frame?.let { r ->
                    drawRoundRect(c.primary.copy(alpha = 0.08f), r.topLeft, r.size, CornerRadius(r.height / 2, r.height / 2))
                    drawRoundRect(c.primary, r.topLeft, r.size, CornerRadius(r.height / 2, r.height / 2), style = Stroke(width = 2f))
                    val inset = 5f
                    drawRoundRect(
                        c.gold, Offset(r.left + inset, r.top + inset), Size(r.width - 2 * inset, r.height - 2 * inset),
                        CornerRadius(r.height / 2, r.height / 2), style = Stroke(width = 1f),
                    )
                }
                drawText(l.layout, color = if (l.frame != null) c.primary else c.ink, topLeft = l.topLeft)
            }
            layout.words.forEach { drawText(it.layout, color = c.ink, topLeft = it.topLeft) }
        }
    }
}

private fun layoutPage(
    q: QuranData,
    page: Int,
    tf: AndroidTypeface,
    basmalaTf: AndroidTypeface?,
    w: Float,
    h: Float,
    measurer: TextMeasurer,
    density: Density,
): PageLayout {
    val family = FontFamily(Typeface(tf))
    val lines = q.pages[page - 1]
    val slots = if (page <= 2) lines.size + 2 else 15
    val lineH = h / slots
    val topPad = if (page <= 2) lineH else 0f
    val minGap = w * 0.008f

    fun style(px: Float, fam: FontFamily) = with(density) {
        TextStyle(fontFamily = fam, fontSize = px.toSp(), textDirection = TextDirection.Rtl)
    }

    // 1) find the font size: the fullest line must exactly fill the width.
    val basePx = 100f
    val baseStyle = style(basePx, family)
    var fullest = 0f
    var fullestCount = 1
    var glyphH = 0f
    lines.forEach { line ->
        if (line is PageLine.Text && line.words.isNotEmpty()) {
            var sum = 0f
            line.words.forEach { word ->
                val r = measurer.measure(word.glyph, baseStyle, softWrap = false, maxLines = 1)
                sum += r.size.width
                glyphH = maxOf(glyphH, r.size.height.toFloat())
            }
            if (sum > fullest) { fullest = sum; fullestCount = line.words.size }
        }
    }
    if (fullest == 0f) fullest = w
    val byWidth = basePx * (w - (fullestCount - 1) * minGap) / fullest
    val byHeight = if (glyphH > 0f) basePx * (lineH * 1.05f) / glyphH else byWidth
    val px = minOf(byWidth, byHeight)
    val st = style(px, family)

    val words = ArrayList<PlacedWord>()
    val labels = ArrayList<PlacedLabel>()

    lines.forEachIndexed { i, line ->
        val top = topPad + i * lineH
        val mid = top + lineH / 2
        when (line) {
            is PageLine.Header -> {
                val ls = with(density) {
                    TextStyle(fontSize = (lineH * 0.36f).toSp(), fontWeight = FontWeight.Bold, textDirection = TextDirection.Rtl)
                }
                val r = measurer.measure("سورة ${q.surahName(line.surah)}", ls)
                val frame = Rect(w * 0.06f, top + lineH * 0.12f, w * 0.94f, top + lineH * 0.88f)
                labels += PlacedLabel(r, Offset((w - r.size.width) / 2, mid - r.size.height / 2), frame)
            }
            PageLine.Basmala -> {
                val glyphs = q.meta.basmala
                val r = if (basmalaTf != null && glyphs != null) {
                    measurer.measure(glyphs, style(px * 0.9f, FontFamily(Typeface(basmalaTf))), softWrap = false)
                } else {
                    measurer.measure(
                        "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ",
                        with(density) { TextStyle(fontSize = (lineH * 0.38f).toSp(), textDirection = TextDirection.Rtl) },
                    )
                }
                labels += PlacedLabel(r, Offset((w - r.size.width) / 2, mid - r.size.height / 2), null)
            }
            is PageLine.Text -> {
                val measured = line.words.map { measurer.measure(it.glyph, st, softWrap = false, maxLines = 1) }
                val natural = measured.sumOf { it.size.width }.toFloat()
                val n = measured.size
                val justify = page > 2 && n > 1 && natural + (n - 1) * minGap > w * 0.5f
                val gap = if (justify) (w - natural) / (n - 1) else minGap * 2
                val total = natural + gap * (n - 1)
                var x = if (justify) w else (w + total) / 2 // right edge, reading right to left
                measured.forEachIndexed { j, r ->
                    val ww = r.size.width.toFloat()
                    val left = x - ww
                    val tl = Offset(left, mid - r.size.height / 2f)
                    val hit = Rect(left - gap / 2, top + lineH * 0.06f, x + gap / 2, top + lineH * 0.94f)
                    words += PlacedWord(line.words[j].key, r, tl, hit)
                    x = left - gap
                }
            }
        }
    }
    return PageLayout(words, labels, lineH)
}

/** A tall, scrollable version of the page for landscape. */
@Composable
fun MushafPageTall(
    q: QuranData,
    page: Int,
    selected: String?,
    onTapVerse: (String) -> Unit,
    onTapEmpty: () -> Unit,
    width: androidx.compose.ui.unit.Dp,
) {
    MushafPage(q, page, selected, onTapVerse, onTapEmpty, Modifier.fillMaxWidth().height(width * 1.55f))
}
