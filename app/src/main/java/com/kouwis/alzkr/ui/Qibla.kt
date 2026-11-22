package com.kouwis.alzkr.ui

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
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

/** The phone's heading from true north, smoothed, with the compass accuracy. */
@Composable
private fun rememberHeading(lat: Double, lng: Double): Pair<Float?, Int> {
    val context = LocalContext.current
    var heading by remember { mutableStateOf<Float?>(null) }
    var accuracy by remember { mutableIntStateOf(SensorManager.SENSOR_STATUS_ACCURACY_HIGH) }
    DisposableEffect(lat, lng) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) ?: sm.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)
        // magnetic north -> true north
        val declination = GeomagneticField(lat.toFloat(), lng.toFloat(), 0f, System.currentTimeMillis()).declination
        val rot = FloatArray(9)
        val orient = FloatArray(3)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rot, e.values)
                SensorManager.getOrientation(rot, orient)
                val h = ((Math.toDegrees(orient[0].toDouble()).toFloat() + declination) + 360f) % 360f
                val old = heading
                // a little smoothing so the needle doesn't shiver (and turns the short way round)
                heading = if (old == null) h else (old + diff(h, old) * 0.15f + 360f) % 360f
            }

            override fun onAccuracyChanged(s: Sensor?, a: Int) { accuracy = a }
        }
        if (sensor != null) sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { sm.unregisterListener(listener) }
    }
    return heading to accuracy
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun QiblaScreen(prefs: AppPrefs, nav: Nav) = AdhkarTheme {
    val c = LocalColors.current
    val view = LocalView.current
    val hasPlace = prefs.lat != null && prefs.lng != null
    val lat = prefs.lat ?: 30.0444
    val lng = prefs.lng ?: 31.2357
    val bearing = remember(lat, lng) { qiblaBearing(lat, lng).toFloat() }
    val km = remember(lat, lng) { Locator.km(lat, lng, KAABA_LAT, KAABA_LNG).roundToInt() }
    val (heading, accuracy) = rememberHeading(lat, lng)
    val off = heading?.let { diff(bearing, it) } // how far to turn: + = to the right
    val facing = off != null && abs(off) <= 3f
    LaunchedEffect(facing) { if (facing) view.haptic(Haptic.Confirm) }
    val ring by animateColorAsState(if (facing) c.primary else c.border, tween(250), label = "ring")

    Column(
        Modifier.fillMaxSize().background(c.bg)
            .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility)
            .windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RoundIconButton(Ic.back, "رجوع", { nav.back() })
            Column(Modifier.weight(1f)) {
                Text("القبلة", color = c.text, fontSize = 24.sp, fontFamily = AppFonts.naskhBold)
                Text(
                    (if (hasPlace) prefs.place.ifBlank { "موقعك" } else "القاهرة") + " · ${num(km)} كم من مكة",
                    color = c.muted, fontSize = 13.sp, maxLines = 1,
                )
            }
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
        ) {
            Text(
                when {
                    heading == null -> "الموبايل ده مفيهوش بوصلة"
                    facing -> "أنت متجه للقبلة"
                    off!! > 0 -> "لِف يمين ${num(abs(off).roundToInt())}°"
                    else -> "لِف شمال ${num(abs(off).roundToInt())}°"
                },
                color = if (facing) c.primary else c.text, fontSize = 22.sp, fontWeight = FontWeight.Bold,
            )
            Box(Modifier.fillMaxWidth().widthIn(max = 360.dp).aspectRatio(1f), contentAlignment = Alignment.Center) {
                val dial = c.card
                val tick = c.muted
                val north = c.heart
                val gold = c.gold
                val kaaba = Color(0xFF1B1B1B)
                val pointer = if (facing) c.primary else c.text
                Canvas(Modifier.fillMaxSize()) {
                    val r = size.minDimension / 2f
                    val center = Offset(size.width / 2f, size.height / 2f)
                    drawCircle(dial, r * 0.96f, center)
                    drawCircle(ring, r * 0.96f, center, style = Stroke(6.dp.toPx()))
                    // the dial turns so its north points to the real north
                    rotate(-(heading ?: 0f), center) {
                        for (d in 0 until 360 step 10) {
                            val long = d % 90 == 0
                            val a = Math.toRadians(d.toDouble())
                            val r1 = r * (if (long) 0.80f else 0.86f)
                            val r2 = r * 0.92f
                            drawLine(
                                if (d == 0) north else tick,
                                Offset(center.x + r1 * sin(a).toFloat(), center.y - r1 * cos(a).toFloat()),
                                Offset(center.x + r2 * sin(a).toFloat(), center.y - r2 * cos(a).toFloat()),
                                strokeWidth = (if (long) 3.dp else 1.5.dp).toPx(), cap = StrokeCap.Round,
                            )
                        }
                        // the Kaaba on the rim, at the qibla direction
                        val a = Math.toRadians(bearing.toDouble())
                        val kr = r * 0.66f
                        val kc = Offset(center.x + kr * sin(a).toFloat(), center.y - kr * cos(a).toFloat())
                        val s = r * 0.17f
                        rotate(bearing, kc) {
                            drawRoundRect(kaaba, Offset(kc.x - s / 2, kc.y - s / 2), Size(s, s), CornerRadius(s * 0.08f))
                            drawRect(gold, Offset(kc.x - s / 2, kc.y - s * 0.22f), Size(s, s * 0.12f))
                        }
                        // a line from the centre to the Kaaba
                        drawLine(
                            gold.copy(alpha = 0.55f), center,
                            Offset(center.x + r * 0.52f * sin(a).toFloat(), center.y - r * 0.52f * cos(a).toFloat()),
                            strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round,
                        )
                    }
                    // the phone's own direction: a fixed arrow pointing up
                    val p = Path().apply {
                        moveTo(center.x, center.y - r * 0.98f)
                        lineTo(center.x - r * 0.07f, center.y - r * 0.82f)
                        lineTo(center.x + r * 0.07f, center.y - r * 0.82f)
                        close()
                    }
                    drawPath(p, pointer)
                    drawCircle(pointer, r * 0.035f, center)
                }
            }
            Text(
                "اتجاه القبلة ${num(bearing.roundToInt())}° من الشمال" + if (!hasPlace) "\nحدد مكانك من كارت اليوم في الأذكار علشان الاتجاه يبقى أدق" else "",
                color = c.muted, fontSize = 13.sp, textAlign = TextAlign.Center, lineHeight = 22.sp,
            )
            if (heading != null && accuracy <= SensorManager.SENSOR_STATUS_ACCURACY_LOW) {
                Text(
                    "البوصلة محتاجة معايرة: حرّك الموبايل في الهوا على شكل رقم ٨ كذا مرة، وابعد عن الحديد والمغناطيس",
                    color = c.goldText, fontSize = 13.sp, textAlign = TextAlign.Center, lineHeight = 22.sp,
                    modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(c.goldSoft).padding(horizontal = 14.dp, vertical = 10.dp),
                )
            } else {
                Text("حط الموبايل مسطّح وبعيد عن أي حديد", color = c.muted, fontSize = 12.sp)
            }
        }
    }
}
