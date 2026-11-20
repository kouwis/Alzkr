package com.kouwis.alzkr.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.kouwis.alzkr.data.AppFonts
import com.kouwis.alzkr.data.AppPrefs
import com.kouwis.alzkr.data.Hijri
import com.kouwis.alzkr.data.Locator
import com.kouwis.alzkr.data.Prayer
import com.kouwis.alzkr.data.PrayerTimes
import com.kouwis.alzkr.data.Prefs
import com.kouwis.alzkr.data.QuranData
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDateTime
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos

private const val CAIRO_LAT = 30.0444
private const val CAIRO_LNG = 31.2357

private fun ar(s: String) = s.map { if (it in '0'..'9') '٠' + (it - '0') else it }.joinToString("")

/** "٤:٥٥" — 12-hour clock with Arabic digits. */
private fun clock(min: Int): String {
    val h = (min / 60) % 24
    val h12 = if (h % 12 == 0) 12 else h % 12
    return ar("%d:%02d".format(h12, min % 60))
}

/** "بعد ١:٥١" or "بعد ٢٥ دقيقة". */
private fun after(mins: Int): String = if (mins < 60) "بعد ${ar(mins.toString())} دقيقة" else "بعد ${ar("%d:%02d".format(mins / 60, mins % 60))}"

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun AdhkarHomeScreen(q: QuranData, prefs: AppPrefs, store: Prefs, nav: Nav) = AdhkarTheme {
    val c = LocalColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val soon = soon()
    val view = LocalView.current

    // the clock moves the card on by itself
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) { while (true) { delay(20_000); now = LocalDateTime.now() } }

    val hasPlace = prefs.lat != null && prefs.lng != null
    val lat = prefs.lat ?: CAIRO_LAT
    val lng = prefs.lng ?: CAIRO_LNG
    val place = when {
        !hasPlace -> "القاهرة"
        prefs.place.isNotBlank() -> prefs.place
        else -> "موقعك"
    }
    val today = now.toLocalDate()
    val times = remember(today, lat, lng) { PrayerTimes.forDay(today, lat, lng) }
    val (current, next) = PrayerTimes.current(times, now)
    val minuteNow = now.hour * 60 + now.minute
    val untilNext = (times.getValue(next) - minuteNow).let { if (it < 0) it + 1440 else it }
    val hijri = remember(today) { Hijri.of(today) }

    var locating by remember { mutableStateOf(false) }
    fun locateNow() {
        locating = true
        scope.launch {
            val fix = Locator.locate(context)
            if (fix != null) store.setLocation(fix.lat, fix.lng, fix.place)
            else android.widget.Toast.makeText(context, "معرفناش نحدد مكانك — شغّل الموقع وجرّب تاني", android.widget.Toast.LENGTH_LONG).show()
            locating = false
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) locateNow()
    }
    val askLocation = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) locateNow()
        else permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    // swipe the top part to change section: right -> left goes back to القرآن
    var sectionDrag by remember { mutableFloatStateOf(0f) }
    val toQuran = {
        scope.launch { store.setSection("quran") }
        nav.setRoot(Screen.Home)
    }
    val swipeSection = Modifier.pointerInput(Unit) {
        detectHorizontalDragGestures(
            onDragEnd = {
                val limit = 70.dp.toPx()
                when {
                    sectionDrag < -limit -> { view.haptic(Haptic.Tick); toQuran() }
                    sectionDrag > limit -> { view.haptic(Haptic.Tick); soon("الفقه") }
                }
                sectionDrag = 0f
            },
            onDragCancel = { sectionDrag = 0f },
        ) { change, dx -> change.consume(); sectionDrag += dx }
    }

    // Friday's sunnah runs from Thursday's Maghrib to Friday's Maghrib
    val dow = today.dayOfWeek
    val fridayTime = (dow == DayOfWeek.FRIDAY && minuteNow < times.getValue(Prayer.Maghrib)) ||
        (dow == DayOfWeek.THURSDAY && minuteNow >= times.getValue(Prayer.Maghrib))
    val kahfPage = remember { q.pageOf(18, 1) }

    data class Suggestion(val title: String, val sub: String, val icon: ImageVector, val onClick: () -> Unit)
    val beforeFajr = minuteNow < times.getValue(Prayer.Fajr)
    val suggestions = buildList {
        when {
            current == Prayer.Isha || beforeFajr -> {
                add(Suggestion("أذكار النوم", "قبل ما تنام", AdhkarIc.bed) { soon("أذكار النوم") })
                add(Suggestion("أذكار المساء", "لو لسه مقرأتهاش", AdhkarIc.moon) { soon("أذكار المساء") })
            }
            current == Prayer.Asr || current == Prayer.Maghrib ->
                add(Suggestion("أذكار المساء", "من العصر للمغرب", AdhkarIc.moon) { soon("أذكار المساء") })
            else -> add(Suggestion("أذكار الصباح", "وقتها لحد قبل العصر", AdhkarIc.sun) { soon("أذكار الصباح") })
        }
        if (fridayTime) {
            add(Suggestion("الصلاة على النبي ﷺ", "يوم الجمعة · بتفتح في العداد", AdhkarIc.star) { soon("العداد") })
            add(Suggestion("سورة الكهف", "يوم الجمعة · بتفتح في المصحف", AdhkarIc.book) {
                nav.go(Screen.Mushaf(kahfPage, "18:1"))
            })
        }
    }

    data class Item(val name: String, val icon: ImageVector)
    val groups = listOf(
        Triple("الأذكار", "اليوم والليلة", listOf(
            Item("أذكار الصباح", AdhkarIc.sun), Item("أذكار المساء", AdhkarIc.moon),
            Item("أذكار النوم", AdhkarIc.bed), Item("أذكار الاستيقاظ", AdhkarIc.alarm),
            Item("بعد الصلاة", AdhkarIc.afterPrayer), Item("المسجد والأذان", AdhkarIc.mosque),
        )),
        Triple("الجمعة", "", listOf(
            Item("الصلاة على النبي ﷺ", AdhkarIc.star), Item("سورة الكهف", AdhkarIc.book),
            Item("ساعة الإجابة", AdhkarIc.clock), Item("سنن الجمعة", AdhkarIc.list),
        )),
        Triple("الأدعية", "", listOf(
            Item("أدعية من القرآن", AdhkarIc.book), Item("أدعية نبوية", AdhkarIc.hands),
            Item("الهم والكرب", AdhkarIc.sorrow), Item("الرقية الشرعية", AdhkarIc.shield),
            Item("الاستخارة", AdhkarIc.istikhara), Item("المريض", AdhkarIc.sick),
        )),
    )

    var preview by remember { mutableStateOf<Prayer?>(null) }
    // a preview goes back to the real sky after a little while
    LaunchedEffect(preview) { if (preview != null) { delay(8_000); preview = null } }
    val scene = preview ?: current

    Column(
        Modifier.fillMaxSize().background(c.bg)
            .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility)
            .windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility),
    ) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            item {
                Column(Modifier.then(swipeSection).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("الأذكار", color = c.text, fontSize = 30.sp, fontFamily = AppFonts.naskhBold, modifier = Modifier.weight(1f))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            RoundIconButton(AdhkarIc.beads, "العداد", { soon("العداد") }, tint = c.primary)
                            RoundIconButton(AdhkarIc.qibla, "القبلة", { soon("القبلة") }, tint = c.primary)
                            RoundIconButton(Ic.search, "بحث", { soon("البحث في الأذكار") })
                            RoundIconButton(Ic.heart, "المفضلة", { soon("مفضلة الأذكار") }, tint = c.heart)
                            RoundIconButton(Ic.settings, "الإعدادات", { soon("الإعدادات") })
                        }
                    }
                    DayCard(
                        weekday = hijri.weekday,
                        hijriLine = "${ar(hijri.day.toString())} ${hijri.month} ${ar(hijri.year.toString())}",
                        gregorian = ar(hijri.gregorian),
                        place = place,
                        placeIsDefault = !hasPlace,
                        locating = locating,
                        onLocate = askLocation,
                        current = current,
                        next = next,
                        times = times,
                        untilNext = untilNext,
                        scene = scene,
                        hijriDay = hijri.day,
                        onPick = { p -> view.haptic(Haptic.Tick); preview = if (p == current) null else p },
                    )
                    Text("مقترح لك الآن", color = c.text, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp))
                }
            }
            items(suggestions.size) { i ->
                val s = suggestions[i]
                Row(
                    Modifier.padding(start = 20.dp, end = 20.dp, top = if (i == 0) 10.dp else 10.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(c.primarySoft)
                        .springClick(role = Role.Button, label = s.title, pressedScale = 0.98f, onClick = s.onClick)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(Modifier.size(44.dp).clip(CircleShape).background(c.card), contentAlignment = Alignment.Center) {
                        Icon(s.icon, null, tint = c.primary, modifier = Modifier.size(21.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(s.title, color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(s.sub, color = c.muted, fontSize = 12.sp)
                    }
                    Icon(Ic.forward, null, tint = c.muted, modifier = Modifier.size(18.dp))
                }
            }
            groups.forEach { (title, note, items) ->
                item {
                    Row(Modifier.padding(start = 24.dp, end = 24.dp, top = 22.dp, bottom = 10.dp), verticalAlignment = Alignment.Bottom) {
                        Text(title, color = c.text, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        if (note.isNotEmpty()) Text(note, color = c.muted, fontSize = 12.sp)
                    }
                }
                items((items.size + 1) / 2) { r ->
                    Row(Modifier.padding(horizontal = 20.dp, vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        for (k in 0..1) {
                            val it = items.getOrNull(r * 2 + k)
                            if (it == null) { Spacer(Modifier.weight(1f)); continue }
                            Row(
                                Modifier.weight(1f).height(54.dp)
                                    .clip(RoundedCornerShape(27.dp))
                                    .background(c.card)
                                    .border(1.dp, c.border, RoundedCornerShape(27.dp))
                                    .springClick(role = Role.Button, label = it.name, pressedScale = 0.97f) { soon(it.name) }
                                    .padding(start = 6.dp, end = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Box(Modifier.size(42.dp).clip(CircleShape).background(c.primarySoft), contentAlignment = Alignment.Center) {
                                    Icon(it.icon, null, tint = c.primary, modifier = Modifier.size(19.dp))
                                }
                                Text(it.name, color = c.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(14.dp)) }
        }

        // Section switcher
        Row(
            Modifier
                .align(Alignment.CenterHorizontally)
                .padding(vertical = 8.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(c.card)
                .border(1.dp, c.border, RoundedCornerShape(24.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SectionPill("القرآن", false) { toQuran() }
            SectionPill("الأذكار", true) {}
            SectionPill("الفقه", false) { soon("الفقه") }
        }
    }
}

@Composable
fun SectionPill(label: String, on: Boolean, onClick: () -> Unit) {
    val c = LocalColors.current
    Box(
        Modifier
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (on) c.primary else Color.Transparent)
            .springClick(role = Role.Tab, haptic = Haptic.Tick, ripple = false, onClick = onClick)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (on) c.onPrimary else c.muted, fontSize = 14.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium)
    }
}

// ---------------------------------------------------------------- the day card and its sky

/** What the sky looks like at each time of day. x/y are fractions of the card. */
private data class Sky(
    val top: Color, val mid: Color, val bottom: Color,
    val bodyX: Float, val bodyY: Float, val sun: Float, val moon: Float, val glowR: Float,
    val glow: Color, val sunMid: Color, val sunEdge: Color,
    val land: Color, val land2: Color, val stars: Float, val soft: Color,
)

private fun skyOf(p: Prayer): Sky = when (p) {
    Prayer.Fajr -> Sky(Color(0xFF101D38), Color(0xFF2E4877), Color(0xFFC2907A), 0.28f, 0.22f, 0f, 0.75f, 70f,
        Color(0xFFF3B98A), Color(0xFFFFE2B0), Color(0xFFF6A86A), Color(0xFF1B2640), Color(0xFF131C31), 0.6f, Color(0xFFD7E1EE))
    Prayer.Sunrise -> Sky(Color(0xFF3A6AA3), Color(0xFF7FA6CF), Color(0xFFF0C08A), 0.22f, 0.83f, 1f, 0f, 95f,
        Color(0xFFFFC983), Color(0xFFFFE7B8), Color(0xFFFFAE5C), Color(0xFF3A5070), Color(0xFF2B3E5B), 0f, Color(0xFFEAF1F8))
    Prayer.Dhuhr -> Sky(Color(0xFF1F5A98), Color(0xFF3E7CC0), Color(0xFF8DBBE4), 0.30f, 0.17f, 1f, 0f, 85f,
        Color(0xFFFFF6D6), Color(0xFFFFFBEA), Color(0xFFFFE69A), Color(0xFF4C7FB0), Color(0xFF3B6A99), 0f, Color(0xFFE3EDF8))
    Prayer.Asr -> Sky(Color(0xFF295B92), Color(0xFF5D8CBE), Color(0xFFE2BE88), 0.20f, 0.42f, 1f, 0f, 90f,
        Color(0xFFFFE2A6), Color(0xFFFFF0C8), Color(0xFFFFCB72), Color(0xFF41628A), Color(0xFF334F74), 0f, Color(0xFFE6EEF7))
    Prayer.Maghrib -> Sky(Color(0xFF202C57), Color(0xFF74496A), Color(0xFFE5865A), 0.18f, 0.86f, 1f, 0f, 110f,
        Color(0xFFFF9A62), Color(0xFFFFD0A0), Color(0xFFFF7A45), Color(0xFF3A2B45), Color(0xFF2A2036), 0.2f, Color(0xFFF1DDE0))
    Prayer.Isha -> Sky(Color(0xFF08112A), Color(0xFF122244), Color(0xFF1C3160), 0.21f, 0.24f, 0f, 1f, 40f,
        Color(0xFFC9D8F2), Color.White, Color.White, Color(0xFF0E1830), Color(0xFF0A1226), 0.85f, Color(0xFFC7D3E6))
}

private val prayerIcons = mapOf(
    Prayer.Fajr to AdhkarIc.rise, Prayer.Sunrise to AdhkarIc.rise, Prayer.Dhuhr to AdhkarIc.sun,
    Prayer.Asr to AdhkarIc.cloud, Prayer.Maghrib to AdhkarIc.set, Prayer.Isha to AdhkarIc.moon,
)

private val starSpots = listOf(
    0.11f to 0.10f, 0.26f to 0.24f, 0.43f to 0.08f, 0.60f to 0.21f, 0.75f to 0.07f, 0.34f to 0.42f,
    0.09f to 0.39f, 0.86f to 0.31f, 0.53f to 0.36f, 0.67f to 0.46f, 0.92f to 0.14f, 0.48f to 0.55f,
)

@Composable
private fun DayCard(
    weekday: String, hijriLine: String, gregorian: String,
    place: String, placeIsDefault: Boolean, locating: Boolean, onLocate: () -> Unit,
    current: Prayer, next: Prayer, times: Map<Prayer, Int>, untilNext: Int,
    scene: Prayer, hijriDay: Int, onPick: (Prayer) -> Unit,
) {
    val target = skyOf(scene)
    val spec = tween<Color>(900)
    val fspec = tween<Float>(900)
    val top by animateColorAsState(target.top, spec, label = "top")
    val mid by animateColorAsState(target.mid, spec, label = "mid")
    val bottom by animateColorAsState(target.bottom, spec, label = "bottom")
    val glow by animateColorAsState(target.glow, spec, label = "glow")
    val sunMid by animateColorAsState(target.sunMid, spec, label = "sunMid")
    val sunEdge by animateColorAsState(target.sunEdge, spec, label = "sunEdge")
    val land by animateColorAsState(target.land, spec, label = "land")
    val land2 by animateColorAsState(target.land2, spec, label = "land2")
    val soft by animateColorAsState(target.soft, spec, label = "soft")
    val bx by animateFloatAsState(target.bodyX, fspec, label = "bx")
    val bodyY by animateFloatAsState(target.bodyY, fspec, label = "by")
    val sunA by animateFloatAsState(target.sun, fspec, label = "sun")
    val moonA by animateFloatAsState(target.moon, fspec, label = "moon")
    val glowR by animateFloatAsState(target.glowR, fspec, label = "glowR")
    val stars by animateFloatAsState(target.stars, fspec, label = "stars")

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .drawBehind {
                drawRect(Brush.verticalGradient(0f to top, 0.58f to mid, 1f to bottom))
                starSpots.forEachIndexed { i, (sx, sy) ->
                    if (stars > 0f) drawCircle(Color.White.copy(alpha = stars * (0.55f + 0.45f * ((i * 37) % 10) / 10f)), radius = (0.7f + (i % 3) * 0.35f) * density, center = Offset(sx * size.width, sy * size.height))
                }
                val center = Offset(bx * size.width, bodyY * size.height)
                if (sunA > 0.01f) drawSun(center, glowR.dp.toPx(), glow, sunMid, sunEdge, sunA)
                if (moonA > 0.01f) drawMoon(center, 23.dp.toPx(), hijriDay, moonA, top)
                drawLand(land, land2)
            },
    ) {
        Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 18.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(AdhkarIc.pin, null, tint = soft, modifier = Modifier.size(14.dp))
                Text(place, color = soft, fontSize = 13.sp)
                if (placeIsDefault) {
                    Text(
                        if (locating) "بنحدد مكانك…" else "استخدم موقعي",
                        color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.18f))
                            .springClick(role = Role.Button, label = "استخدم موقعي") { if (!locating) onLocate() }
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
            Text(weekday, color = Color.White, fontSize = 44.sp, fontFamily = AppFonts.naskhBold, lineHeight = 56.sp)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(hijriLine, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(gregorian, color = soft, fontSize = 13.sp)
            }
        }

        Row(
            Modifier
                .padding(start = 14.dp, end = 14.dp, top = 14.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0x470A1628))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(if (current == Prayer.Sunrise) "وقت الشروق" else "الصلاة الحالية", color = soft, fontSize = 12.sp)
                Text("${current.ar} · ${clock(times.getValue(current))}", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(next.ar, color = soft, fontSize = 12.sp)
                Text(after(untilNext), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        Row(Modifier.padding(horizontal = 10.dp, vertical = 12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Prayer.entries.forEach { p ->
                val on = p == scene
                val isNow = p == current
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (on) Color.White else Color.White.copy(alpha = 0.12f))
                        .springClick(role = Role.Tab, label = p.ar, haptic = Haptic.Tick, ripple = false) { onPick(p) }
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Icon(prayerIcons.getValue(p), null, tint = if (on) Color(0xFF1E4E79) else Color.White, modifier = Modifier.size(18.dp))
                    Text(p.ar, color = if (on) Color(0xFF1E4E79) else Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 1)
                    Text(clock(times.getValue(p)), color = if (on) Color(0xFF1E4E79) else Color.White, fontSize = 11.sp, maxLines = 1)
                    Box(Modifier.size(4.dp).clip(CircleShape).background(if (isNow) (if (on) Color(0xFF1E4E79) else Color.White) else Color.Transparent))
                }
            }
        }
    }
}

private fun DrawScope.drawSun(c: Offset, glowR: Float, glow: Color, mid: Color, edge: Color, alpha: Float) {
    val r = 20.dp.toPx()
    // wide soft halo, then a tighter glow, then the disc: white-hot centre warming to the rim
    drawCircle(Brush.radialGradient(0f to glow.copy(alpha = 0.75f * alpha), 0.35f to glow.copy(alpha = 0.28f * alpha), 1f to Color.Transparent, center = c, radius = glowR), glowR, c)
    drawCircle(Brush.radialGradient(0f to glow.copy(alpha = 0.55f * alpha), 1f to Color.Transparent, center = c, radius = r * 1.9f), r * 1.9f, c)
    drawCircle(Brush.radialGradient(0f to Color.White.copy(alpha = alpha), 0.45f to mid.copy(alpha = alpha), 0.92f to edge.copy(alpha = alpha), 1f to edge.copy(alpha = 0f), center = c, radius = r * 1.08f), r * 1.08f, c)
}

/**
 * The moon as it is on this Hijri day: a lit textured disc with the dark part drawn over it.
 * Waxing (first half of the month) is lit on the right, waning on the left.
 */
private fun DrawScope.drawMoon(c: Offset, r: Float, hijriDay: Int, alpha: Float, sky: Color) {
    drawCircle(Brush.radialGradient(0f to Color(0xFFDCE6F7).copy(alpha = 0.45f * alpha), 0.4f to Color(0xFFDCE6F7).copy(alpha = 0.12f * alpha), 1f to Color.Transparent, center = c, radius = r * 3f), r * 3f, c)
    val disc = Path().apply { addOval(Rect(c, r)) }
    clipPath(disc) {
        drawCircle(Brush.radialGradient(0f to Color(0xFFFBF9F1), 0.7f to Color(0xFFE4E0D3), 1f to Color(0xFFC9C4B4), center = c + Offset(-0.08f * r, -0.1f * r), radius = r * 1.25f), r, c, alpha = alpha)
        // the darker "seas"
        listOf(
            Triple(-0.26f, -0.35f, 0.34f), Triple(0.22f, -0.13f, 0.30f), Triple(-0.09f, 0.30f, 0.28f),
            Triple(0.39f, 0.43f, 0.17f), Triple(-0.52f, 0.13f, 0.16f),
        ).forEach { (dx, dy, rr) ->
            val o = c + Offset(dx * r, dy * r)
            drawCircle(Brush.radialGradient(0f to Color(0xFFA9A495).copy(alpha = 0.55f * alpha), 1f to Color.Transparent, center = o, radius = rr * r * 1.3f), rr * r * 1.3f, o)
        }
        // the shadowed part, from the age of the moon
        val phase = ((hijriDay - 0.5f) / 29.53f).coerceIn(0f, 1f)
        val waxing = phase <= 0.5f
        val p = if (waxing) phase else 1f - phase // 0 new … 0.5 full
        val k = r * cos(2f * PI.toFloat() * p) // terminator half-width; > 0 crescent, < 0 gibbous
        val lit = Path().apply {
            moveTo(c.x, c.y - r)
            arcTo(Rect(c, r), -90f, 180f, false) // right half
            arcTo(Rect(c.x - abs(k), c.y - r, c.x + abs(k), c.y + r), 90f, if (k > 0) -180f else 180f, false)
            close()
        }
        val shadow = Path().apply { op(disc, lit, PathOperation.Difference) }
        scale(if (waxing) 1f else -1f, 1f, pivot = c) {
            drawPath(shadow, sky.copy(alpha = 0.86f * alpha))
        }
    }
}

private fun DrawScope.drawLand(land: Color, land2: Color) {
    val w = size.width; val h = size.height
    val far = Path().apply {
        moveTo(0f, h * 0.86f)
        cubicTo(w * 0.14f, h * 0.79f, w * 0.27f, h * 0.84f, w * 0.43f, h * 0.81f)
        cubicTo(w * 0.58f, h * 0.78f, w * 0.71f, h * 0.75f, w * 0.86f, h * 0.79f)
        cubicTo(w * 0.92f, h * 0.81f, w * 0.97f, h * 0.82f, w, h * 0.82f)
        lineTo(w, h); lineTo(0f, h); close()
    }
    val near = Path().apply {
        moveTo(0f, h * 0.92f)
        cubicTo(w * 0.2f, h * 0.88f, w * 0.4f, h * 0.93f, w * 0.6f, h * 0.9f)
        cubicTo(w * 0.74f, h * 0.87f, w * 0.89f, h * 0.9f, w, h * 0.89f)
        lineTo(w, h); lineTo(0f, h); close()
    }
    drawPath(far, land)
    drawPath(near, land2)
}
