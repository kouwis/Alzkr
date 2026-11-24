package com.kouwis.alzkr.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.kouwis.alzkr.data.AppFonts
import com.kouwis.alzkr.data.ArabicText
import com.kouwis.alzkr.data.t

/*
 * Every widget is drawn by the app as one picture, with the app's own fonts: the same picture is
 * the widget on the home screen and its preview in the app, so what you see is what you get.
 * Numbers follow the app's language (Arabic digits in Arabic).
 */

enum class Align { Start, Center, End }

/** Drawing helpers in dp, laid out in the reading direction (start = right in Arabic). */
class Ink(val c: Canvas, val w: Float, val h: Float, val dp: Float, val k: Float, val rtl: Boolean, val s: WidgetStyle) {
    private val bold: Typeface = if (rtl) AppFonts.naskhBoldTf else Typeface.create("sans-serif", Typeface.BOLD)
    private val regular: Typeface = if (rtl) AppFonts.naskhTf else Typeface.create("sans-serif-medium", Typeface.NORMAL)
    val ink = s.ink.toArgb()
    val soft = s.soft.toArgb()
    val faint = s.faint.toArgb()
    val accent = s.accentColor.toArgb()
    fun px(v: Float) = v * dp * k

    /** x of a box [width] wide placed [fromStart] px from the reading start. */
    fun xAt(fromStart: Float, width: Float) = if (rtl) w - fromStart - width else fromStart

    /**
     * Text in a box ([left], [top], [width]); one line shrinks to fit, more lines end with «…».
     * Returns the height used.
     */
    fun text(
        s: String, left: Float, top: Float, width: Float, sizeSp: Float, color: Int,
        bold: Boolean = true, align: Align = Align.Start, lines: Int = 1, fit: Boolean = true,
    ): Float {
        if (s.isBlank() || width <= 0f) return 0f
        val p = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = if (bold) this@Ink.bold else regular
            this.color = color
            textSize = px(sizeSp)
        }
        if (lines == 1 && fit) while (p.measureText(s) > width && p.textSize > px(sizeSp) * 0.55f) p.textSize *= 0.94f
        val al = when (align) { Align.Start -> Layout.Alignment.ALIGN_NORMAL; Align.Center -> Layout.Alignment.ALIGN_CENTER; Align.End -> Layout.Alignment.ALIGN_OPPOSITE }
        val layout = StaticLayout.Builder.obtain(s, 0, s.length, p, width.toInt().coerceAtLeast(1))
            .setAlignment(al)
            .setTextDirection(if (rtl) TextDirectionHeuristics.RTL else TextDirectionHeuristics.LTR)
            .setMaxLines(lines).setEllipsize(TextUtils.TruncateAt.END)
            .setIncludePad(false).setLineSpacing(0f, if (lines > 1) 1.12f else 1f)
            .build()
        c.save(); c.translate(left, top); layout.draw(c); c.restore()
        return layout.height.toFloat()
    }

    /** The height one line of this size takes. */
    fun lineH(sizeSp: Float, bold: Boolean = true): Float {
        val p = TextPaint().apply { typeface = if (bold) this@Ink.bold else regular; textSize = px(sizeSp) }
        val f = p.fontMetrics
        return f.descent - f.ascent
    }

    fun round(left: Float, top: Float, width: Float, height: Float, r: Float, color: Int) {
        c.drawRoundRect(RectF(left, top, left + width, top + height), r, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
    }

    fun line(top: Float, left: Float, width: Float) = round(left, top, width, px(1f), 0f, faint)

    fun dg(s: String) = ArabicText.digitsOf(s)

    /**
     * Two texts on one line: one at the reading start, one at the end, each in its own share of
     * the width (so they never run into each other), both centred on the taller one.
     */
    fun pair(
        start: String, end: String, left: Float, top: Float, width: Float, startSize: Float, endSize: Float,
        startColor: Int = ink, endColor: Int = accent, bold: Boolean = true, ratio: Float = 0.6f,
    ) {
        val gap = px(8f)
        val sw = width * ratio - gap / 2; val ew = width * (1 - ratio) - gap / 2
        val lh = maxOf(lineH(startSize, bold), lineH(endSize, bold))
        text(start, if (rtl) left + width - sw else left, top + (lh - lineH(startSize, bold)) / 2f, sw, startSize, startColor, bold)
        text(end, if (rtl) left else left + width - ew, top + (lh - lineH(endSize, bold)) / 2f, ew, endSize, endColor, bold, align = Align.End)
    }
}

object WidgetRenderer {
    /** The kind's usual size in dp, for previews (a typical phone's home-screen cell). */
    fun defaultSize(kind: WidgetKind): Pair<Float, Float> = (kind.w * 86f - 10f) to (kind.h * 92f - 14f)

    /** How much bigger or smaller than usual the widget is shown (its type scales with it). */
    fun scaleOf(kind: WidgetKind, wDp: Float, hDp: Float): Float {
        val (bw, bh) = defaultSize(kind)
        return minOf(wDp / bw, hDp / bh).coerceIn(0.75f, 1.4f)
    }

    fun render(context: Context, kind: WidgetKind, s: WidgetStyle, d: WidgetSnapshot, wDp: Float, hDp: Float, density: Float): Bitmap {
        AppFonts.init(context)
        // keep the picture small enough to send to the home screen
        val scale = minOf(density, 1000f / maxOf(wDp, 1f), 1000f / maxOf(hDp, 1f))
        val w = (wDp * scale).toInt().coerceAtLeast(8); val h = (hDp * scale).toInt().coerceAtLeast(8)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        WidgetStyles.background(s, wDp, hDp, scale)?.let { c.drawBitmap(it, null, RectF(0f, 0f, w.toFloat(), h.toFloat()), Paint(Paint.FILTER_BITMAP_FLAG)) }
        val k = scaleOf(kind, wDp, hDp)
        val ink = Ink(c, w.toFloat(), h.toFloat(), scale, k, d.rtl, s)
        when (kind) {
            WidgetKind.Prayers -> prayers(ink, d)
            WidgetKind.Next -> next(ink, d)
            WidgetKind.Row -> row(ink, d)
            WidgetKind.DateTimes -> dateTimes(ink, d)
            WidgetKind.Table -> table(ink, d)
            WidgetKind.Progress -> progress(ink, d)
            WidgetKind.Banner -> banner(ink, d)
            WidgetKind.Date -> calendar(ink, d)
            WidgetKind.Clock -> clock(ink, d)
            WidgetKind.Tracker -> tracker(ink, d)
            WidgetKind.Ayah -> ayah(ink, d)
            WidgetKind.Dhikr -> dhikr(ink, d)
            WidgetKind.Tasbih -> tasbih(ink, d)
            WidgetKind.Occasions -> occasions(ink, d)
            WidgetKind.Custom -> custom(ink, d, s)
        }
        return bmp
    }

    /** Lays lines out from the top, centred as a block in the height. */
    private class Stack(val i: Ink, val left: Float, val width: Float) {
        val items = mutableListOf<Triple<Float, Float, (Float) -> Unit>>() // gap before, height, draw(top)
        fun add(gap: Float, height: Float, draw: (Float) -> Unit) { items += Triple(gap, height, draw) }
        fun drawCentered(top: Float, bottom: Float) {
            val total = items.sumOf { (it.first + it.second).toDouble() }.toFloat()
            var y = top + ((bottom - top) - total).coerceAtLeast(0f) / 2f
            items.forEach { (g, hh, f) -> y += g; f(y); y += hh }
        }
    }

    private fun Ink.textLine(st: Stack, gap: Float, s: String, size: Float, color: Int, bold: Boolean = true, align: Align = Align.Start) {
        val lh = lineH(size, bold)
        st.add(px(gap), lh) { top -> text(s, st.left, top, st.width, size, color, bold, align) }
    }

    /** One line of times: a name, a time, and whether it is the one coming. */
    private class Slot(val name: String, val time: String, val on: Boolean, val iqama: String? = null)

    /** The day's times to show: the five prayers, and sunrise and the last third if the style asks. */
    private fun Ink.slots(d: WidgetSnapshot): List<Slot> =
        d.rows.filter { s.sunrise || it.prayer.name != "Sunrise" }
            .map { Slot(it.name, it.time, it.prayer == d.next.prayer, if (it.prayer.name == "Sunrise") null else it.iqama ?: "—") } +
            listOfNotNull(d.lastThird.takeIf { s.lastThird && it.isNotBlank() }?.let { Slot(t("الثلث الأخير"), it, false) })

    // ---------------- prayer widgets ----------------

    private fun prayers(i: Ink, d: WidgetSnapshot) = with(i) {
        val pad = px(18f)
        val colW = (w - pad * 2 - px(14f)) / 2f
        // the next prayer, on the reading side
        val st = Stack(i, xAt(pad, colW), colW)
        textLine(st, 0f, d.place, 12f, soft, bold = false)
        textLine(st, 2f, d.headline, 25f, ink)
        textLine(st, 0f, dg(d.targetTime), 36f, ink)
        textLine(st, 2f, dg(d.remaining), 14f, accent)
        st.drawCentered(pad * 0.7f, h - pad * 0.7f)
        // all the times on the other side
        val lx = xAt(pad + colW + px(14f), colW)
        val rows = slots(d)
        val rh = (h - pad * 1.4f) / rows.size
        var sz = 14f
        while (lineH(sz) > rh * 0.98f && sz > 9f) sz -= 0.5f
        rows.forEachIndexed { n, r ->
            val on = r.on
            val top = pad * 0.7f + rh * n + (rh - lineH(sz)) / 2f
            if (on) round(lx - px(8f), pad * 0.7f + rh * n + px(1f), colW + px(16f), rh - px(2f), px(10f), faint)
            pair(r.name, dg(r.time), lx, top, colW, sz, sz, startColor = if (on) ink else soft, endColor = if (on) ink else soft, ratio = 0.62f)
        }
    }

    private fun next(i: Ink, d: WidgetSnapshot) = with(i) {
        val pad = px(16f)
        val st = Stack(i, pad, w - pad * 2)
        textLine(st, 0f, d.place, 11.5f, soft, bold = false, align = Align.Center)
        textLine(st, 2f, d.headline, 22f, ink, align = Align.Center)
        textLine(st, 0f, dg(d.targetTime), 34f, ink, align = Align.Center)
        textLine(st, 2f, dg(d.remaining), 14f, accent, align = Align.Center)
        st.drawCentered(pad * 0.6f, h - pad * 0.6f)
    }

    /** The day's times as columns, Fajr at the reading start. */
    private fun Ink.columns(d: WidgetSnapshot, left: Float, width: Float, top: Float, nameSize: Float, timeSize: Float): Float {
        val all = slots(d)
        val cw = width / all.size
        all.forEachIndexed { n, r ->
            val on = r.on
            val x = if (rtl) left + width - (n + 1) * cw else left + n * cw
            if (on) round(x + px(2f), top - px(4f), cw - px(4f), lineH(nameSize) + lineH(timeSize) + px(10f), px(12f), faint)
            text(r.name, x, top, cw, nameSize, if (on) accent else soft, bold = on, align = Align.Center)
            text(dg(r.time), x, top + lineH(nameSize) + px(1f), cw, timeSize, if (on) accent else ink, align = Align.Center)
        }
        return lineH(nameSize) + lineH(timeSize) + px(4f)
    }

    private fun row(i: Ink, d: WidgetSnapshot) = with(i) {
        val pad = px(12f)
        val block = lineH(12.5f) + lineH(17f)
        if (h > px(110f)) {
            pair(d.headline, dg(d.remaining), pad, pad, w - pad * 2, 16f, 14f)
            columns(d, pad, w - pad * 2, h - pad - block, 12.5f, 17f)
        } else columns(d, pad, w - pad * 2, (h - block) / 2f, 12.5f, 17f)
    }

    private fun dateTimes(i: Ink, d: WidgetSnapshot) = with(i) {
        val pad = px(16f)
        val inner = w - pad * 2
        val block = lineH(16f) + px(12f) + lineH(12.5f) + lineH(17f) + px(14f) + px(1f) + px(10f) + lineH(14f)
        var y = ((h - block) / 2f).coerceAtLeast(pad * 0.6f)
        pair("${d.weekday}  ${dg(d.hijriDay.toString())} ${d.hijriMonth}", d.place, pad, y, inner, 16f, 12f, endColor = soft, ratio = 0.68f)
        y += lineH(16f) + px(12f)
        y += columns(d, pad, inner, y, 12.5f, 17f) + px(10f)
        line(y, pad, inner); y += px(10f)
        pair("${d.headline}  ${dg(d.targetTime)}", dg(d.remaining), pad, y, inner, 14f, 14f, startColor = accent)
    }

    private fun table(i: Ink, d: WidgetSnapshot) = with(i) {
        val pad = px(14f)
        val inner = w - pad * 2
        val five = slots(d)
        val barH = lineH(15f) + px(14f)
        round(pad, pad, inner, barH, px(14f), faint)
        pair(d.headline, dg(d.remaining), pad + px(12f), pad + px(7f), inner - px(24f), 15f, 15f, startColor = accent)
        var y = pad + barH + px(8f)
        val cw = inner / 3f
        fun col(n: Int) = if (rtl) pad + inner - (n + 1) * cw else pad + n * cw
        listOf(t("الصلاة"), t("الأذان"), t("الإقامة")).forEachIndexed { n, s -> text(s, col(n), y, cw, 12f, soft, align = Align.Center) }
        y += lineH(12f) + px(4f)
        val rh = (h - pad - y) / five.size
        var sz = 15f
        while (lineH(sz) > rh * 0.98f && sz > 9f) sz -= 0.5f
        five.forEach { r ->
            val on = r.on
            val color = if (on) accent else ink
            if (on) round(pad, y, inner, rh - px(2f), px(10f), faint)
            val ty = y + (rh - lineH(sz)) / 2f
            text(r.name, col(0), ty, cw, sz, color, align = Align.Center)
            text(dg(r.time), col(1), ty, cw, sz, color, align = Align.Center)
            text(dg(r.iqama ?: ""), col(2), ty, cw, sz, color, align = Align.Center)
            y += rh
        }
    }

    private fun progress(i: Ink, d: WidgetSnapshot) = with(i) {
        val pad = px(16f)
        val inner = w - pad * 2
        val block = lineH(12f) + px(8f) + lineH(20f) + px(10f) + px(8f) + px(10f) + lineH(17f) + px(8f) + lineH(12f)
        var y = ((h - block) / 2f).coerceAtLeast(pad * 0.6f)
        pair(d.weekday, d.place, pad, y, inner, 12f, 12f, startColor = soft, endColor = soft, bold = false)
        y += lineH(12f) + px(8f)
        pair("✓ ${d.fromLabel}", d.headline, pad, y, inner, 20f, 20f, endColor = ink, ratio = 0.5f)
        y += lineH(20f) + px(10f)
        round(pad, y, inner, px(8f), px(4f), faint)
        val fw = inner * d.progress.coerceIn(0.03f, 1f)
        round(xAt(pad, fw), y, fw, px(8f), px(4f), accent)
        y += px(8f) + px(10f)
        text(dg(d.fromTime), pad, y, inner, 17f, ink)
        text(dg(d.remaining), pad, y + px(1f), inner, 14f, accent, align = Align.Center)
        text(dg(d.targetTime), pad, y, inner, 17f, ink, align = Align.End)
        y += lineH(17f) + px(8f)
        pair(dg(d.gregorian), dg(d.hijri), pad, y, inner, 12f, 12f, startColor = soft, endColor = soft, bold = false, ratio = 0.5f)
    }

    private fun banner(i: Ink, d: WidgetSnapshot) = with(i) {
        val pad = px(16f)
        val inner = w - pad * 2
        val big = 22f
        val block = (if (d.sub.isNotBlank()) lineH(12f) + px(2f) else 0f) + lineH(big)
        var y = (h - block) / 2f
        if (d.sub.isNotBlank()) { text(dg(d.sub), pad, y, inner, 12f, soft, bold = false); y += lineH(12f) + px(2f) }
        text("${d.headline}  ·  ${dg(d.remaining)}", pad, y, inner, big, ink)
    }

    // ---------------- dates ----------------

    private fun calendar(i: Ink, d: WidgetSnapshot) = with(i) {
        val pad = px(18f)
        val numSize = 64f
        val numW = maxOf(px(84f), lineH(numSize) * 0.95f)
        val numTop = (h - lineH(numSize)) / 2f
        text(dg(d.hijriDay.toString()), xAt(pad, numW), numTop, numW, numSize, ink, align = Align.Center)
        val restW = w - pad * 2 - numW - px(14f)
        val st = Stack(i, xAt(pad + numW + px(14f), restW), restW)
        textLine(st, 0f, d.hijriMonth, 24f, ink)
        textLine(st, 0f, d.weekday, 21f, accent)
        textLine(st, 2f, dg(d.gregorian), 14f, soft, bold = false)
        st.drawCentered(pad * 0.5f, h - pad * 0.5f)
    }

    private fun clock(i: Ink, d: WidgetSnapshot) = with(i) {
        val pad = px(16f)
        val now = java.time.LocalTime.now()
        val h12 = if (now.hour % 12 == 0) 12 else now.hour % 12
        val st = Stack(i, pad, w - pad * 2)
        textLine(st, 0f, dg("%d:%02d".format(java.util.Locale.US, h12, now.minute)), 50f, ink, align = Align.Center)
        textLine(st, 0f, "${d.weekday}، ${dg(d.gregorianShort)}", 15f, ink, align = Align.Center)
        textLine(st, 4f, "${d.headline}  ${dg(d.targetTime)}  ·  ${dg(d.remaining)}", 14f, accent, align = Align.Center)
        st.drawCentered(pad * 0.5f, h - pad * 0.5f)
    }

    // ---------------- worship ----------------

    /** The header's height of the tracker (its cells take the rest), shared with the tap areas. */
    const val TRACKER_HEAD_DP = 38f

    private fun tracker(i: Ink, d: WidgetSnapshot) = with(i) {
        val pad = px(12f)
        val inner = w - pad * 2
        val five = d.rows.filter { it.prayer.name != "Sunrise" }
        text(t("متابعة الصلوات"), pad + px(4f), pad, inner, 14f, accent)
        text(dg("${five.count { it.prayer.name in d.prayed }}/5"), pad, pad, inner - px(4f), 14f, soft, align = Align.End)
        val top = pad + px(TRACKER_HEAD_DP - 12f)
        val gap = px(6f)
        val cw = (inner - gap * 4) / 5f
        val ch = h - pad - top
        five.forEachIndexed { n, r ->
            val done = r.prayer.name in d.prayed
            val x = if (rtl) pad + inner - (n + 1) * cw - n * gap else pad + n * (cw + gap)
            round(x, top, cw, ch, px(16f), faint)
            val dot = minOf(px(28f), cw * 0.5f)
            val block = dot + px(6f) + lineH(13f) + lineH(12f)
            var y = top + (ch - block) / 2f
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (done) accent else faint }
            c.drawCircle(x + cw / 2f, y + dot / 2f, dot / 2f, p)
            if (done) text("✓", x, y + (dot - lineH(14f)) / 2f, cw, 14f, if (s.lightBg) Color.White.toArgb() else Color(0xFF14171C).toArgb(), align = Align.Center)
            y += dot + px(6f)
            text(r.name, x, y, cw, 13f, if (done) ink else soft, align = Align.Center)
            text(dg(r.time), x, y + lineH(13f), cw, 12f, soft, bold = false, align = Align.Center)
        }
    }

    private fun ayah(i: Ink, d: WidgetSnapshot) = with(i) {
        val pad = px(18f)
        val inner = w - pad * 2
        val lines = if (h > px(170f)) 6 else 3
        val head = lineH(12f) + px(4f)
        val foot = px(6f) + lineH(12f)
        val tmpH = run {
            val p = TextPaint().apply { typeface = AppFonts.naskhBoldTf; textSize = px(19f) }
            StaticLayout.Builder.obtain("\uFD3E ${d.ayah} \uFD3F", 0, d.ayah.length + 4, p, inner.toInt().coerceAtLeast(1)).setMaxLines(lines).build().height.toFloat()
        }
        var y = ((h - head - tmpH - foot) / 2f).coerceAtLeast(pad * 0.5f)
        text(t("آية اليوم"), pad, y, inner, 12f, accent, align = Align.Center); y += head
        y += text("\uFD3E ${d.ayah} \uFD3F", pad, y, inner, 19f, ink, align = Align.Center, lines = lines, fit = false) + px(6f)
        text(dg(d.ayahRef), pad, y, inner, 12f, soft, bold = false, align = Align.Center)
    }

    /** The height of the dhikr widget's «ذكر آخر» strip (also its tap area). */
    const val DHIKR_FOOT_DP = 44f

    private fun dhikr(i: Ink, d: WidgetSnapshot) = with(i) {
        val pad = px(18f)
        val inner = w - pad * 2
        val foot = px(DHIKR_FOOT_DP)
        val lines = if (h > px(170f)) 6 else 3
        val p = TextPaint().apply { typeface = AppFonts.naskhBoldTf; textSize = px(18f) }
        val th = StaticLayout.Builder.obtain(d.dhikr, 0, d.dhikr.length, p, inner.toInt().coerceAtLeast(1)).setMaxLines(lines).build().height.toFloat()
        text(d.dhikr, pad, ((h - foot - th) / 2f).coerceAtLeast(pad * 0.5f), inner, 18f, ink, align = Align.Center, lines = lines, fit = false)
        val label = t("ذكر آخر")
        val bw = px(96f); val bh = lineH(12f) + px(12f)
        round((w - bw) / 2f, h - foot + (foot - bh) / 2f - px(4f), bw, bh, bh / 2f, faint)
        text(label, (w - bw) / 2f, h - foot + (foot - bh) / 2f + px(2f), bw, 12f, ink, align = Align.Center)
    }

    const val TASBIH_FOOT_DP = 40f

    private fun tasbih(i: Ink, d: WidgetSnapshot) = with(i) {
        val pad = px(12f)
        val inner = w - pad * 2
        val foot = px(TASBIH_FOOT_DP)
        val st = Stack(i, pad, inner)
        textLine(st, 0f, d.tasbihText, 15f, ink, align = Align.Center)
        textLine(st, 0f, dg(d.tasbihCount.toString()), 44f, ink, align = Align.Center)
        textLine(st, 0f, t("من {0}", dg(WidgetData.TASBIH_TARGET.toString())), 12f, soft, bold = false, align = Align.Center)
        st.drawCentered(pad * 0.4f, h - foot)
        val bw = px(80f); val bh = lineH(12f) + px(10f)
        round((w - bw) / 2f, h - foot + (foot - bh) / 2f - px(4f), bw, bh, bh / 2f, faint)
        text(t("تصفير"), (w - bw) / 2f, h - foot + (foot - bh) / 2f + px(1f), bw, 12f, ink, align = Align.Center)
    }

    private fun occasions(i: Ink, d: WidgetSnapshot) = with(i) {
        val pad = px(16f)
        val inner = w - pad * 2
        val rowH = lineH(22f) + px(6f)
        val block = lineH(12f) + px(4f) + rowH * d.occasions.size
        var y = ((h - block) / 2f).coerceAtLeast(pad * 0.5f)
        text(t("الأيام"), pad, y, inner, 12f, accent); y += lineH(12f) + px(4f)
        d.occasions.forEachIndexed { n, (name, days) ->
            if (n > 0) line(y - px(3f), pad, inner)
            pair(name, if (days == 0L) t("الآن") else dg(days.toString()), pad, y, inner, 15f, 22f, endColor = ink, ratio = 0.62f)
            y += rowH
        }
    }

    private fun custom(i: Ink, d: WidgetSnapshot, s: WidgetStyle) = with(i) {
        val pad = px(16f)
        val inner = w - pad * 2
        val blocks = s.blocks.ifEmpty { listOf("next") }
        fun hOf(b: String) = when (b) {
            "next" -> lineH(21f)
            "times" -> lineH(12.5f) + lineH(17f) + px(4f)
            "date" -> lineH(15f)
            else -> lineH(15f) * 2 * 1.12f
        }
        val gap = px(10f)
        val total = blocks.sumOf { hOf(it).toDouble() }.toFloat() + gap * (blocks.size - 1)
        var y = ((h - total) / 2f).coerceAtLeast(pad * 0.5f)
        blocks.forEach { b ->
            when (b) {
                "next" -> {
                    pair("${d.headline}  ${dg(d.targetTime)}", dg(d.remaining), pad, y, inner, 21f, 15f)
                }
                "times" -> columns(d, pad, inner, y + px(4f), 12.5f, 17f)
                "date" -> {
                    pair("${d.weekday} · ${dg(d.hijriDay.toString())} ${d.hijriMonth}", dg(d.gregorianShort), pad, y, inner, 15f, 13f, endColor = soft)
                }
                "dhikr" -> text(d.dhikr, pad, y, inner, 15f, ink, align = Align.Center, lines = 2, fit = false)
                "ayah" -> text("\uFD3E ${d.ayah} \uFD3F", pad, y, inner, 15f, ink, align = Align.Center, lines = 2, fit = false)
            }
            y += hOf(b) + gap
        }
    }
}
