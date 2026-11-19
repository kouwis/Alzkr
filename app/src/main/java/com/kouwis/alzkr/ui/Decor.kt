package com.kouwis.alzkr.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp

/**
 * Quran-section card backdrop: a faint field of eight-pointed stars and a quiet mosque
 * silhouette on the left, so the green card isn't a flat colour. Purely decorative and very
 * low contrast, so the text and buttons on top stay crisp.
 */
fun Modifier.mosqueBackdrop(tint: Color = Color.White): Modifier = drawBehind {
    // 1) eight-pointed stars (two overlapping squares), offset every other row
    val step = 44.dp.toPx()
    val r = 9.dp.toPx()
    val starStroke = Stroke(width = 1.dp.toPx())
    val starColor = tint.copy(alpha = 0.07f)
    var row = 0
    var y = step / 2
    while (y < size.height + step) {
        var x = if (row % 2 == 0) step / 2 else step
        while (x < size.width + step) {
            star(Offset(x, y), r, starColor, starStroke)
            x += step
        }
        y += step * 0.8f
        row++
    }

    // 2) mosque silhouette, left side (the reading side of the card is on the right)
    val u = size.height * 0.62f // overall height unit
    val base = size.height * 0.60f // where the building stands
    val cx = 20.dp.toPx() + u * 0.62f
    val fill = tint.copy(alpha = 0.11f)
    val p = Path()
    // hall
    p.addRect(Rect(cx - u * 0.42f, base - u * 0.26f, cx + u * 0.42f, base))
    // central dome on a drum
    p.addRect(Rect(cx - u * 0.24f, base - u * 0.34f, cx + u * 0.24f, base - u * 0.26f))
    p.addArcPath(Rect(cx - u * 0.26f, base - u * 0.60f, cx + u * 0.26f, base - u * 0.08f))
    // finial + crescent
    p.addRect(Rect(cx - u * 0.008f, base - u * 0.72f, cx + u * 0.008f, base - u * 0.60f))
    // side domes
    for (sx in listOf(-0.30f, 0.30f)) {
        p.addArcPath(Rect(cx + u * (sx - 0.11f), base - u * 0.38f, cx + u * (sx + 0.11f), base - u * 0.16f))
    }
    // minarets with pointed caps and a balcony
    for (mx in listOf(-0.56f, 0.56f)) {
        val l = cx + u * (mx - 0.035f)
        val rr = cx + u * (mx + 0.035f)
        p.addRect(Rect(l, base - u * 0.80f, rr, base))
        p.addRect(Rect(l - u * 0.02f, base - u * 0.62f, rr + u * 0.02f, base - u * 0.59f))
        p.moveTo(l - u * 0.005f, base - u * 0.80f)
        p.lineTo(cx + u * mx, base - u * 0.95f)
        p.lineTo(rr + u * 0.005f, base - u * 0.80f)
        p.close()
    }
    drawPath(p, fill)
    // crescent: a circle with a smaller circle "cut" by drawing the card colour is not possible
    // on an unknown background, so draw it as a thin arc stroke instead
    drawArc(
        color = fill,
        startAngle = 40f, sweepAngle = 280f, useCenter = false,
        topLeft = Offset(cx - u * 0.045f, base - u * 0.83f), size = Size(u * 0.09f, u * 0.09f),
        style = Stroke(width = u * 0.018f),
    )
}

private fun Path.addArcPath(oval: Rect) {
    // upper half of the oval, closed along its middle — a dome
    arcTo(oval, 180f, 180f, forceMoveTo = true)
    close()
}

private fun DrawScope.star(c: Offset, r: Float, color: Color, stroke: Stroke) {
    val topLeft = Offset(c.x - r, c.y - r)
    drawRect(color, topLeft, Size(r * 2, r * 2), style = stroke)
    rotate(45f, pivot = c) { drawRect(color, topLeft, Size(r * 2, r * 2), style = stroke) }
}
