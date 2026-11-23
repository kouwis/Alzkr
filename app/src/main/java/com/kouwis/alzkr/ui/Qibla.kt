package com.kouwis.alzkr.ui

import com.kouwis.alzkr.data.t
import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import android.view.WindowManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kouwis.alzkr.data.AppFonts
import com.kouwis.alzkr.data.AppPrefs
import com.kouwis.alzkr.data.ArabicText.num
import com.kouwis.alzkr.data.Locator
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private const val KAABA_LAT = 21.422487
private const val KAABA_LNG = 39.826206

/** The direction of the Kaaba from a place, degrees clockwise from true north (great-circle bearing). */
fun qiblaBearing(lat: Double, lng: Double): Double {
    val p1 = Math.toRadians(lat)
    val p2 = Math.toRadians(KAABA_LAT)
    val dl = Math.toRadians(KAABA_LNG - lng)
    val y = sin(dl) * cos(p2)
    val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
    return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
}

/** Signed smallest difference a - b in degrees, in -180..180. */
private fun diff(a: Float, b: Float): Float = ((a - b + 540f) % 360f) - 180f

private class Compass(val heading: State<Float?>, val accuracy: State<Int>, val available: Boolean)

/**
 * The direction the top of the phone points to, from true north; works lying flat or held upright, in any
 * screen rotation.
 *
 * Read straight from the accelerometer and the magnetometer (each lightly filtered), not from the fused
 * rotation vector: the fused one corrects its heading slowly, so it could point the wrong way for a second
 * and then jump. The needle is then eased toward the reading once per frame, so it follows the phone
 * smoothly; the value is a State read only while drawing, so turning the phone never recomposes the screen.
 */
@Composable
private fun rememberCompass(lat: Double, lng: Double): Compass {
    val context = LocalContext.current
    val shown = remember { mutableStateOf<Float?>(null) }
    val accuracy = remember { mutableIntStateOf(SensorManager.SENSOR_STATUS_ACCURACY_HIGH) }
    val sm = remember { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    val accel = remember { sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) }
    val magnet = remember { sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) }
    val available = accel != null && magnet != null
    // the latest reading, written by the sensors and read by the frame loop
    val target = remember { floatArrayOf(Float.NaN) }
    DisposableEffect(lat, lng, available) {
        val declination = GeomagneticField(lat.toFloat(), lng.toFloat(), 0f, System.currentTimeMillis()).declination
        @Suppress("DEPRECATION")
        val display = (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay
        val g = FloatArray(3)
        val m = FloatArray(3)
        var haveG = false
        var haveM = false
        val rot = FloatArray(9)
        val screen = FloatArray(9)
        val upright = FloatArray(9)
        val orient = FloatArray(3)
        fun update() {
            if (!haveG || !haveM || !SensorManager.getRotationMatrix(rot, null, g, m)) return
            val (ax, ay) = when (display.rotation) {
                Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
                Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
                Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
                else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
            }
            SensorManager.remapCoordinateSystem(rot, ax, ay, screen)
            // held upright, the direction is where the back of the phone faces
            val mat = if (abs(screen[8]) < 0.45f) {
                SensorManager.remapCoordinateSystem(screen, SensorManager.AXIS_X, SensorManager.AXIS_Z, upright); upright
            } else screen
            SensorManager.getOrientation(mat, orient)
            target[0] = ((Math.toDegrees(orient[0].toDouble()).toFloat() + declination) % 360f + 360f) % 360f
        }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val (v, first) = if (e.sensor.type == Sensor.TYPE_ACCELEROMETER) g to !haveG else m to !haveM
                // a light low-pass on each sensor: removes the jitter, keeps the response quick
                for (k in 0..2) v[k] = if (first) e.values[k] else v[k] + 0.25f * (e.values[k] - v[k])
                if (e.sensor.type == Sensor.TYPE_ACCELEROMETER) haveG = true else haveM = true
                update()
            }

            override fun onAccuracyChanged(s: Sensor?, a: Int) {
                if (s?.type == Sensor.TYPE_MAGNETIC_FIELD) accuracy.intValue = a
            }
        }
        if (available) {
            sm.registerListener(listener, accel, SensorManager.SENSOR_DELAY_GAME)
            sm.registerListener(listener, magnet, SensorManager.SENSOR_DELAY_GAME)
        }
        onDispose { sm.unregisterListener(listener) }
    }
    // once per frame: ease the needle toward the reading (about a tenth of a second to catch up)
    LaunchedEffect(available) {
        var last = 0L
        while (true) {
            androidx.compose.runtime.withFrameNanos { now ->
                val t = target[0]
                if (!t.isNaN()) {
                    val old = shown.value
                    val dt = if (last == 0L) 0.016f else ((now - last) / 1e9f).coerceIn(0f, 0.1f)
                    val k = 1f - kotlin.math.exp(-dt / 0.09f)
                    shown.value = if (old == null) t else ((old + diff(t, old) * k) % 360f + 360f) % 360f
                }
                last = now
            }
        }
    }
    return remember(available) { Compass(shown, accuracy, available) }
}

private val KaabaBlack = Color(0xFF1C1C1C)
private val KaabaGold = Color(0xFFD9B45A)

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun QiblaScreen(prefs: AppPrefs, nav: Nav) = AdhkarTheme {
    val c = LocalColors.current
    val view = LocalView.current
    val hasPlace = prefs.lat != null && prefs.lng != null
    val lat = prefs.lat ?: 30.0444
    val lng = prefs.lng ?: 31.2357
    val place = if (hasPlace) prefs.place.ifBlank { t("موقعك الحالي") } else t("القاهرة")
    val bearing = remember(lat, lng) { qiblaBearing(lat, lng).toFloat() }
    val km = remember(lat, lng) { Locator.km(lat, lng, KAABA_LAT, KAABA_LNG).roundToInt() }
    val compass = rememberCompass(lat, lng)
    // recomposes only when you come onto (or leave) the qibla, not on every reading
    val facing by remember(bearing) { derivedStateOf { compass.heading.value?.let { abs(diff(bearing, it)) <= 3f } == true } }
    LaunchedEffect(facing) { if (facing) view.haptic(Haptic.Confirm) }
    val hubColor by animateColorAsState(if (facing) c.primary else c.card, tween(250), label = "hub")
    val arrowColor by animateColorAsState(if (facing) c.onPrimary else c.primary, tween(250), label = "arrow")
    var info by remember { mutableStateOf(false) }
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = c.text, fontSize = 16.sp, fontFamily = AppFonts.naskhBold)
    val northStyle = labelStyle.copy(color = c.heart)

    Column(
        Modifier.fillMaxSize().background(c.bg)
            .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility)
            .windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.align(Alignment.CenterStart)) { RoundIconButton(Ic.back, t("رجوع"), { nav.back() }) }
            Text(t("القبلة"), color = c.text, fontSize = 24.sp, fontFamily = AppFonts.naskhBold)
            Box(Modifier.align(Alignment.CenterEnd)) { RoundIconButton(Ic.info, t("معلومات"), { info = true }, tint = c.primary) }
        }
        Spacer(Modifier.height(10.dp))
        Text(t("اتجاه القبلة في"), color = c.muted, fontSize = 15.sp)
        Text(
            place, color = c.text, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1,
            modifier = Modifier.padding(top = 8.dp).widthIn(max = 300.dp).clip(RoundedCornerShape(22.dp))
                .background(c.card).border(1.dp, c.border, RoundedCornerShape(22.dp))
                .padding(horizontal = 28.dp, vertical = 8.dp),
        )

        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (!compass.available) {
                Text(t("هذا الهاتف لا يحتوي على بوصلة"), color = c.text, fontSize = 17.sp)
            } else {
                val rim = c.card
                val rimLine = c.border
                val face = c.primarySoft
                val tickMain = c.text
                val tick = c.muted
                val ring = c.primary
                Canvas(Modifier.fillMaxWidth(0.88f).widthIn(max = 380.dp).aspectRatio(1f)) {
                    val h = compass.heading.value ?: 0f // read here only: a new reading redraws, nothing more
                    val ctr = Offset(size.width / 2f, size.height / 2f)
                    val labelRoom = 28.dp.toPx()
                    val r = size.minDimension / 2f - labelRoom
                    // a soft shadow, then the rim
                    drawCircle(Color.Black.copy(alpha = if (face.luminance() > 0.5f) 0.06f else 0.25f), r * 1.02f, ctr + Offset(0f, 6.dp.toPx()))
                    drawCircle(rim, r, ctr)
                    drawCircle(rimLine, r, ctr, style = Stroke(1.dp.toPx()))
                    // ticks every 5°, the four directions longer; the dial turns with the phone
                    for (d in 0 until 360 step 5) {
                        val main = d % 90 == 0
                        val mid = d % 30 == 0
                        val a = Math.toRadians((d - h).toDouble())
                        val r1 = r * (if (main) 0.83f else if (mid) 0.86f else 0.89f)
                        val r2 = r * 0.94f
                        drawLine(
                            if (d == 0) c.heart else if (main || mid) tickMain else tick.copy(alpha = 0.55f),
                            Offset(ctr.x + r1 * sin(a).toFloat(), ctr.y - r1 * cos(a).toFloat()),
                            Offset(ctr.x + r2 * sin(a).toFloat(), ctr.y - r2 * cos(a).toFloat()),
                            strokeWidth = (if (main) 2.5.dp else if (mid) 1.6.dp else 1.dp).toPx(), cap = StrokeCap.Round,
                        )
                    }
                    // the four directions just outside the dial, always upright
                    listOf(0 to t("شمال"), 90 to t("شرق"), 180 to t("جنوب"), 270 to t("غرب")).forEach { (d, name) ->
                        val a = Math.toRadians((d - h).toDouble())
                        val rr = r + labelRoom * 0.6f
                        val layout = measurer.measure(name, if (d == 0) northStyle else labelStyle)
                        drawText(
                            layout,
                            topLeft = Offset(
                                ctr.x + rr * sin(a).toFloat() - layout.size.width / 2f,
                                ctr.y - rr * cos(a).toFloat() - layout.size.height / 2f,
                            ),
                        )
                    }
                    drawCircle(face, r * 0.78f, ctr)
                    // where the qibla is on the dial: a thin line from the hub to the Kaaba
                    val q = bearing - h
                    val qa = Math.toRadians(q.toDouble())
                    drawLine(
                        ring.copy(alpha = 0.35f), ctr,
                        Offset(ctr.x + r * 0.56f * sin(qa).toFloat(), ctr.y - r * 0.56f * cos(qa).toFloat()),
                        strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round,
                    )
                    val kr = r * 0.62f
                    kaaba(Offset(ctr.x + kr * sin(qa).toFloat(), ctr.y - kr * cos(qa).toFloat()), r * 0.10f, q)
                    // the hub with the arrow to the qibla
                    drawCircle(Color.Black.copy(alpha = 0.10f), r * 0.40f, ctr + Offset(0f, 3.dp.toPx()))
                    drawCircle(hubColor, r * 0.38f, ctr)
                    drawCircle(ring.copy(alpha = 0.25f), r * 0.38f, ctr, style = Stroke(1.5.dp.toPx()))
                    rotate(q, ctr) { arrow(ctr, r * 0.27f, arrowColor) }
                    // the phone's own direction: a small mark at the top of the rim
                    val top = Path().apply {
                        moveTo(ctr.x, ctr.y - r * 1.0f + 2.dp.toPx())
                        lineTo(ctr.x - 7.dp.toPx(), ctr.y - r * 1.0f - 9.dp.toPx())
                        lineTo(ctr.x + 7.dp.toPx(), ctr.y - r * 1.0f - 9.dp.toPx())
                        close()
                    }
                    drawPath(top, ring)
                }
            }
        }

        Text(
            if (facing) t("أنت في اتجاه القبلة") else t("السهم يشير إلى اتجاه القبلة"),
            color = if (facing) c.primary else c.text, fontSize = 20.sp, fontWeight = FontWeight.Bold,
        )
        Text(
            if (compass.accuracy.value <= SensorManager.SENSOR_STATUS_ACCURACY_LOW)
                t("البوصلة تحتاج إلى معايرة: حرّك الهاتف في الهواء على شكل الرقم ٨ عدة مرات، بعيدًا عن المعادن والمغناطيس.")
            else t("الاتجاه تقريبي ويعتمد على بوصلة الهاتف وموقعك، وقد يتأثر بالمعادن والمغناطيس. ضع الهاتف مستويًا للحصول على أدق نتيجة."),
            color = if (compass.accuracy.value <= SensorManager.SENSOR_STATUS_ACCURACY_LOW) c.goldText else c.muted,
            fontSize = 13.sp, lineHeight = 22.sp, textAlign = TextAlign.Center,
            modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 10.dp, bottom = 24.dp),
        )
    }

    if (info) {
        AlertDialog(
            onDismissRequest = { info = false },
            containerColor = c.card,
            confirmButton = { TextButton(onClick = { info = false }) { Text(t("حسنًا"), color = c.primary) } },
            title = { Text(t("عن اتجاه القبلة"), fontWeight = FontWeight.Bold, color = c.text) },
            text = {
                Text(
                    t("الموقع: {0}\n", place) +
                        t("اتجاه القبلة: {0}° من الشمال الحقيقي\n", num(bearing.roundToInt())) +
                        t("المسافة إلى مكة المكرمة: {0} كم\n\n", num(km)) +
                        (if (!hasPlace) t("لم يُحدَّد موقعك بعد، فالاتجاه محسوب للقاهرة. حدّد موقعك من بطاقة اليوم في قسم الأذكار.\n\n") else "") +
                        t("للحصول على أدق نتيجة: ضع الهاتف مستويًا على يدك، وابتعد عن الأجهزة والمعادن. وإذا بدا الاتجاه غير مستقر، فحرّك الهاتف على شكل الرقم ٨ لمعايرة البوصلة."),
                    color = c.text, lineHeight = 24.sp,
                )
            },
        )
    }
}

/** A plain arrow pointing up from the centre. */
private fun DrawScope.arrow(c: Offset, len: Float, color: Color) {
    val w = len * 0.30f
    val stroke = Stroke(width = len * 0.17f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    drawLine(color, Offset(c.x, c.y + len * 0.75f), Offset(c.x, c.y - len * 0.85f), strokeWidth = stroke.width, cap = StrokeCap.Round)
    val head = Path().apply {
        moveTo(c.x - w * 1.6f, c.y - len * 0.85f + w * 1.6f)
        lineTo(c.x, c.y - len * 0.85f)
        lineTo(c.x + w * 1.6f, c.y - len * 0.85f + w * 1.6f)
    }
    drawPath(head, color, style = stroke)
}

/** A small Kaaba seen from a corner: black, with the gold band of the kiswa. */
private fun DrawScope.kaaba(at: Offset, s: Float, turn: Float) {
    rotate(turn, at) {
        val top = Path().apply {
            moveTo(at.x, at.y - s * 0.9f); lineTo(at.x + s, at.y - s * 0.45f); lineTo(at.x, at.y); lineTo(at.x - s, at.y - s * 0.45f); close()
        }
        val left = Path().apply {
            moveTo(at.x - s, at.y - s * 0.45f); lineTo(at.x, at.y); lineTo(at.x, at.y + s); lineTo(at.x - s, at.y + s * 0.5f); close()
        }
        val right = Path().apply {
            moveTo(at.x + s, at.y - s * 0.45f); lineTo(at.x, at.y); lineTo(at.x, at.y + s); lineTo(at.x + s, at.y + s * 0.5f); close()
        }
        drawPath(top, Color(0xFF3A3A3A))
        drawPath(left, KaabaBlack)
        drawPath(right, Color(0xFF2A2A2A))
        drawLine(KaabaGold, Offset(at.x - s, at.y - s * 0.12f), Offset(at.x, at.y + s * 0.33f), strokeWidth = s * 0.17f)
        drawLine(KaabaGold, Offset(at.x, at.y + s * 0.33f), Offset(at.x + s, at.y - s * 0.12f), strokeWidth = s * 0.17f)
    }
}
