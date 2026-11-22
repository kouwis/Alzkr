package com.kouwis.alzkr.ui

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
 * The direction the top of the phone points to, from true north. Works lying flat or held upright, in any
 * screen rotation. The value lives in a State read only while drawing, so turning the phone never recomposes.
 */
@Composable
private fun rememberCompass(lat: Double, lng: Double): Compass {
    val context = LocalContext.current
    val heading = remember { mutableStateOf<Float?>(null) }
    val accuracy = remember { mutableIntStateOf(SensorManager.SENSOR_STATUS_ACCURACY_HIGH) }
    val sm = remember { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    val sensor = remember { sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) ?: sm.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR) }
    DisposableEffect(lat, lng, sensor) {
        val declination = GeomagneticField(lat.toFloat(), lng.toFloat(), 0f, System.currentTimeMillis()).declination
        @Suppress("DEPRECATION")
        val display = (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay
        val rot = FloatArray(9)
        val screen = FloatArray(9)
        val upright = FloatArray(9)
        val orient = FloatArray(3)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rot, e.values)
                // the screen's own axes, whatever way the display is turned
                val (ax, ay) = when (display.rotation) {
                    Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
                    Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
                    Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
                    else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
                }
                SensorManager.remapCoordinateSystem(rot, ax, ay, screen)
                // held upright (tilted more than ~60°) the heading is where the back of the phone faces
                val m = if (abs(screen[8]) < 0.5f) {
                    SensorManager.remapCoordinateSystem(screen, SensorManager.AXIS_X, SensorManager.AXIS_Z, upright); upright
                } else screen
                SensorManager.getOrientation(m, orient)
                val h = ((Math.toDegrees(orient[0].toDouble()).toFloat() + declination) % 360f + 360f) % 360f
                val old = heading.value
                heading.value = if (old == null) h else ((old + diff(h, old) * 0.22f) % 360f + 360f) % 360f
            }

            override fun onAccuracyChanged(s: Sensor?, a: Int) { accuracy.intValue = a }
        }
        if (sensor != null) sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        onDispose { sm.unregisterListener(listener) }
    }
    return remember(sensor) { Compass(heading, accuracy, sensor != null) }
}

private val BgTop = Color(0xFF3D4858)
private val BgBottom = Color(0xFF222C39)
private val Rim = Color(0xFF2E3846)
private val Face = Color(0xFF5E6874)
private val Hub = Color(0xFFA9AFB7)
private val Ink = Color(0xFF5A6470)
private val Soft = Color(0xFFB8BEC6)
private val Gold = Color(0xFFD9B45A)

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun QiblaScreen(prefs: AppPrefs, nav: Nav) {
    val view = LocalView.current
    val hasPlace = prefs.lat != null && prefs.lng != null
    val lat = prefs.lat ?: 30.0444
    val lng = prefs.lng ?: 31.2357
    val place = if (hasPlace) prefs.place.ifBlank { "موقعك الحالي" } else "القاهرة"
    val bearing = remember(lat, lng) { qiblaBearing(lat, lng).toFloat() }
    val km = remember(lat, lng) { Locator.km(lat, lng, KAABA_LAT, KAABA_LNG).roundToInt() }
    val compass = rememberCompass(lat, lng)
    // recomposes only when you come onto (or leave) the qibla, not on every reading
    val facing by remember(bearing) { derivedStateOf { compass.heading.value?.let { abs(diff(bearing, it)) <= 3f } == true } }
    LaunchedEffect(facing) { if (facing) view.haptic(Haptic.Confirm) }
    val hubColor by animateColorAsState(if (facing) Gold else Hub, tween(250), label = "hub")
    var info by remember { mutableStateOf(false) }
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = Color.White, fontSize = 17.sp, fontFamily = AppFonts.naskhBold)

    Column(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(BgTop, BgBottom)))
            .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility)
            .windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier.align(Alignment.CenterStart).size(44.dp).clip(CircleShape)
                    .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                    .springClick(label = "رجوع") { nav.back() },
                contentAlignment = Alignment.Center,
            ) { Icon(Ic.back, "رجوع", tint = Color.White, modifier = Modifier.size(20.dp)) }
            Text("القبلة", color = Color.White, fontSize = 22.sp, fontFamily = AppFonts.naskhBold)
        }
        Spacer(Modifier.height(18.dp))
        Text("اتجاه القبلة في", color = Soft, fontSize = 17.sp)
        Text(
            place, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 1,
            modifier = Modifier.padding(top = 8.dp).widthIn(max = 300.dp).clip(RoundedCornerShape(24.dp))
                .background(Color.White.copy(alpha = 0.08f)).padding(horizontal = 32.dp, vertical = 9.dp),
        )

        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (!compass.available) {
                Text("هذا الهاتف لا يحتوي على بوصلة", color = Color.White, fontSize = 17.sp)
            } else {
                Canvas(Modifier.fillMaxWidth(0.86f).widthIn(max = 380.dp).aspectRatio(1f)) {
                    val h = compass.heading.value ?: 0f // read here only: a reading redraws, nothing more
                    val c = Offset(size.width / 2f, size.height / 2f)
                    val labelRoom = 26.dp.toPx()
                    val r = size.minDimension / 2f - labelRoom
                    drawCircle(Rim, r, c)
                    // ticks every 10°, the four directions longer and white; the dial turns with the phone
                    for (d in 0 until 360 step 10) {
                        val main = d % 90 == 0
                        val a = Math.toRadians((d - h).toDouble())
                        val r1 = r * (if (main) 0.86f else 0.89f)
                        val r2 = r * 0.95f
                        drawLine(
                            if (main) Color.White else Color.White.copy(alpha = 0.45f),
                            Offset(c.x + r1 * sin(a).toFloat(), c.y - r1 * cos(a).toFloat()),
                            Offset(c.x + r2 * sin(a).toFloat(), c.y - r2 * cos(a).toFloat()),
                            strokeWidth = (if (main) 2.5.dp else 1.5.dp).toPx(), cap = StrokeCap.Round,
                        )
                    }
                    // the four directions just outside the dial, always upright
                    listOf(0 to "شمال", 90 to "شرق", 180 to "جنوب", 270 to "غرب").forEach { (d, name) ->
                        val a = Math.toRadians((d - h).toDouble())
                        val rr = r + labelRoom * 0.62f
                        val layout = measurer.measure(name, labelStyle)
                        drawText(
                            layout,
                            topLeft = Offset(
                                c.x + rr * sin(a).toFloat() - layout.size.width / 2f,
                                c.y - rr * cos(a).toFloat() - layout.size.height / 2f,
                            ),
                        )
                    }
                    drawCircle(Face, r * 0.84f, c)
                    // the Kaaba on the face, toward the qibla
                    val q = bearing - h
                    val qa = Math.toRadians(q.toDouble())
                    val kr = r * 0.64f
                    kaaba(Offset(c.x + kr * sin(qa).toFloat(), c.y - kr * cos(qa).toFloat()), r * 0.11f, q, Soft)
                    // the hub with the arrow to the qibla
                    drawCircle(hubColor, r * 0.44f, c)
                    rotate(q, c) { arrow(c, r * 0.30f, Ink) }
                }
            }
        }

        Text(
            if (facing) "أنت في اتجاه القبلة" else "السهم يشير إلى اتجاه القبلة",
            color = if (facing) Gold else Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold,
        )
        Text(
            if (compass.accuracy.value <= SensorManager.SENSOR_STATUS_ACCURACY_LOW)
                "البوصلة تحتاج إلى معايرة: حرّك الهاتف في الهواء على شكل الرقم ٨ عدة مرات، بعيدًا عن المعادن والمغناطيس."
            else "هذا الاتجاه تقريبي ويعتمد على بوصلة الهاتف وموقعك، وقد يتأثر بالمعادن والمغناطيس وضعف المعايرة. تأكد من الاتجاه بوسيلة أخرى قبل الاعتماد عليه.",
            color = Soft, fontSize = 14.sp, lineHeight = 24.sp, textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 14.dp),
        )
        Box(
            Modifier.padding(bottom = 18.dp).size(56.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f))
                .springClick(label = "معلومات") { info = true },
            contentAlignment = Alignment.Center,
        ) { Icon(Ic.info, "معلومات", tint = Color.White, modifier = Modifier.size(24.dp)) }
    }

    if (info) {
        AlertDialog(
            onDismissRequest = { info = false },
            confirmButton = { TextButton(onClick = { info = false }) { Text("حسنًا") } },
            title = { Text("عن اتجاه القبلة", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "الموقع: $place\n" +
                        "اتجاه القبلة: ${num(bearing.roundToInt())}° من الشمال الحقيقي\n" +
                        "المسافة إلى مكة المكرمة: ${num(km)} كم\n\n" +
                        (if (!hasPlace) "لم يُحدَّد موقعك بعد، فالاتجاه محسوب للقاهرة. حدّد موقعك من بطاقة اليوم في قسم الأذكار.\n\n" else "") +
                        "للحصول على أدق نتيجة: ضع الهاتف مستويًا على يدك، وابتعد عن الأجهزة والمعادن. وإذا بدا الاتجاه غير مستقر، فحرّك الهاتف على شكل الرقم ٨ لمعايرة البوصلة.",
                    lineHeight = 24.sp,
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

/** A small Kaaba seen from a corner: two faces and the band of the kiswa. */
private fun DrawScope.kaaba(at: Offset, s: Float, turn: Float, color: Color) {
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
        drawPath(top, color.copy(alpha = 0.55f))
        drawPath(left, color)
        drawPath(right, color.copy(alpha = 0.8f))
        drawLine(Face, Offset(at.x - s, at.y - s * 0.1f), Offset(at.x, at.y + s * 0.35f), strokeWidth = s * 0.16f)
        drawLine(Face, Offset(at.x, at.y + s * 0.35f), Offset(at.x + s, at.y - s * 0.1f), strokeWidth = s * 0.16f)
    }
}
