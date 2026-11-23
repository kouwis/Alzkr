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

/** "١:٥١:٠٧" — hours, minutes and seconds to go, ticking. */
/** Minutes from the adhan to the iqama (the usual times in Egypt's mosques; to be set in the settings later). */
val IqamaDelay = mapOf(Prayer.Fajr to 25, Prayer.Dhuhr to 20, Prayer.Asr to 20, Prayer.Maghrib to 10, Prayer.Isha to 20)

private fun countdown(secs: Int): String = ar("%d:%02d:%02d".format(secs / 3600, secs / 60 % 60, secs % 60))

/** The day's name written out in full, with its vowels and stretched (kashida) like a heading. */
private fun longDay(name: String): String = when (name) {
    "السبت" -> "السَّـبْـت"
    "الأحد" -> "الأَحَـــد"
    "الاثنين" -> "الاثْـنَـيْـن"
    "الثلاثاء" -> "الثُّـلَاثَـاء"
    "الأربعاء" -> "الأَرْبِـعَـاء"
    "الخميس" -> "الخَـمِـيـس"
    "الجُمُعة" -> "الجُـمُـعَـة"
    else -> name
}

/** When the place was last quietly checked, for this run of the app. */
private object LocationCheck { var last = 0L }

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun AdhkarHomeScreen(q: QuranData, prefs: AppPrefs, store: Prefs, nav: Nav, onSectionSwipe: (Int) -> Unit) = AdhkarTheme {
    val c = LocalColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val soon = soon()
    val view = LocalView.current

    // the clock moves the card on by itself
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) { while (true) { delay(1_000 - System.currentTimeMillis() % 1_000); now = LocalDateTime.now() } }

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
    val secondNow = minuteNow * 60 + now.second
    val untilNext = (times.getValue(next) * 60 - secondNow).let { if (it < 0) it + 86_400 else it }
    // the Islamic day begins at Maghrib: from then on the Hijri date is the next day's
    val afterMaghrib = minuteNow >= times.getValue(Prayer.Maghrib)
    val hijri = remember(today, afterMaghrib) { Hijri.of(today, afterMaghrib) }
    // after the adhan, the time left to the iqama (then the strip moves on to the next prayer)
    val iqamaAt = IqamaDelay[current]?.let { times.getValue(current) + it }
    val untilIqama = iqamaAt?.let { at -> (at * 60 - secondNow).takeIf { it > 0 && secondNow >= times.getValue(current) * 60 } }

    var locating by remember { mutableStateOf(false) }
    fun toast(t: String) = android.widget.Toast.makeText(context, t, android.widget.Toast.LENGTH_LONG).show()
    fun locateNow(quiet: Boolean = false) {
        // the quiet check runs behind the scenes: the card keeps showing the place it has
        if (!quiet) locating = true
        scope.launch {
            when (val r = Locator.locate(context)) {
                is Locator.Result.Found -> {
                    val f = r.fix
                    val moved = prefs.lat == null || prefs.lng == null ||
                        Locator.km(prefs.lat, prefs.lng, f.lat, f.lng) > 2.0
                    // keep a known name if the lookup failed (no network) and we haven't moved
                    val name = f.place.ifBlank { if (moved) "" else prefs.place }
                    if (moved || name != prefs.place) store.setLocation(f.lat, f.lng, name)
                    if (!quiet) toast(if (name.isNotBlank()) "المواقيت الآن حسب $name" else "تم تحديد موقعك")
                }
                Locator.Result.Off -> if (!quiet) toast("خدمة الموقع مغلقة في الهاتف — فعّلها ثم حاول مرة أخرى")
                Locator.Result.NotFound -> if (!quiet) toast("تعذّر تحديد موقعك الآن — حاول مرة أخرى بعد قليل")
            }
            if (!quiet) locating = false
        }
    }
    fun granted() = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    // each time the Adhkar home opens, quietly check we're still where the times were worked out
    // (at most every 30 minutes, not on every return to this screen)
    LaunchedEffect(hasPlace) {
        val t = System.currentTimeMillis()
        if (hasPlace && granted() && t - LocationCheck.last > 30 * 60_000L) { LocationCheck.last = t; locateNow(quiet = true) }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) locateNow()
    }
    val askLocation = {
        if (granted()) locateNow() else permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    // swipe the top part to change section: right -> left goes back to القرآن, left -> right to الفقه
    var sectionDrag by remember { mutableFloatStateOf(0f) }

    val swipeSection = Modifier.pointerInput(Unit) {
        detectHorizontalDragGestures(
            onDragEnd = {
                val limit = 70.dp.toPx()
                when {
                    sectionDrag < -limit -> { view.haptic(Haptic.Tick); onSectionSwipe(-1) }
                    sectionDrag > limit -> { view.haptic(Haptic.Tick); onSectionSwipe(1) }
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
    // adhkar already finished in their time are taken off the suggestions
    fun at(day: java.time.LocalDate, minute: Int) =
        day.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() + minute * 60_000L
    val night = if (beforeFajr) today.minusDays(1) else today // the evening/night the reader is in
    fun doneSince(id: String, from: Long) = (prefs.adhkarDone[id] ?: 0L) >= from
    val morningDone = doneSince("morning", at(today, times.getValue(Prayer.Fajr)))
    val eveningDone = doneSince("evening", at(night, times.getValue(Prayer.Asr)))
    val sleepDone = doneSince("sleep", at(night, times.getValue(Prayer.Isha)))
    val suggestions = buildList {
        when {
            current == Prayer.Isha || beforeFajr -> {
                if (!sleepDone) add(Suggestion("أذكار النوم", "تُقال عند الاضطجاع للنوم", AdhkarIc.bed) { nav.go(Screen.Adhkar("sleep")) })
                if (!eveningDone) add(Suggestion("أذكار المساء", "وقتها من العصر إلى المغرب، إن لم تقرأها بعد", AdhkarIc.moon) { nav.go(Screen.Adhkar("evening")) })
            }
            current == Prayer.Asr || current == Prayer.Maghrib ->
                if (!eveningDone) add(Suggestion("أذكار المساء", "تُقال من العصر إلى المغرب", AdhkarIc.moon) { nav.go(Screen.Adhkar("evening")) })
            else -> if (!morningDone) add(Suggestion("أذكار الصباح", "تُقال من الفجر إلى ما قبل العصر", AdhkarIc.sun) { nav.go(Screen.Adhkar("morning")) })
        }
        if (fridayTime) {
            add(Suggestion("الصلاة على النبي ﷺ", "يُكثَر منها ليلة الجمعة ويومها، من مغرب الخميس إلى مغرب الجمعة", AdhkarIc.star) { nav.go(Screen.Adhkar("salawat")) })
            add(Suggestion("سورة الكهف", "تُقرأ يوم الجمعة، من مغرب الخميس إلى مغرب الجمعة", AdhkarIc.book) {
                nav.go(Screen.Mushaf(kahfPage, "18:1"))
            })
        }
        // the hour of answer: the last hour of Friday before Maghrib
        val maghrib = times.getValue(Prayer.Maghrib)
        if (dow == DayOfWeek.FRIDAY && minuteNow in (maghrib - 60) until maghrib) {
            add(0, Suggestion("ساعة الإجابة", "آخر ساعة من يوم الجمعة، حتى أذان المغرب", AdhkarIc.clock) {
                nav.go(Screen.Adhkar("friday_hour"))
            })
        }
        // the occasions of the Hijri year, while they are on
        val hd = java.time.chrono.HijrahDate.from(if (afterMaghrib) today.plusDays(1) else today)
        val hMonth = hd.get(java.time.temporal.ChronoField.MONTH_OF_YEAR)
        val hDay = hd.get(java.time.temporal.ChronoField.DAY_OF_MONTH)
        when {
            hMonth == 9 -> add(Suggestion("رمضان", "الصيام والقيام وليلة القدر", AdhkarIc.moon) { nav.go(Screen.Adhkar("ramadan")) })
            hMonth == 10 && hDay <= 3 -> add(Suggestion("عيد الفطر", "زكاة الفطر وسنن العيد وصيام ست من شوال", AdhkarIc.star) { nav.go(Screen.Adhkar("eid_fitr")) })
            hMonth == 12 && hDay == 9 -> add(Suggestion("يوم عرفة", "خير الدعاء دعاء يوم عرفة", AdhkarIc.sun) { nav.go(Screen.Adhkar("arafa")) })
            hMonth == 12 && hDay <= 13 -> add(Suggestion("العشر وعيد الأضحى", "أفضل أيام الدنيا، والأضحية وأيام التشريق", AdhkarIc.sheep) { nav.go(Screen.Adhkar("dhul_hijja")) })
            hMonth == 1 && hDay <= 10 -> add(Suggestion("عاشوراء وتاسوعاء", "صيام التاسع والعاشر من المحرم", AdhkarIc.moon) { nav.go(Screen.Adhkar("ashura")) })
        }
    }

    data class Item(val name: String, val icon: ImageVector, val id: String? = null)
    val groups = listOf(
        Triple("الأذكار", "اليوم والليلة", listOf(
            Item("أذكار الصباح", AdhkarIc.sun, "morning"), Item("أذكار المساء", AdhkarIc.moon, "evening"),
            Item("أذكار النوم", AdhkarIc.bed, "sleep"), Item("أذكار الاستيقاظ", AdhkarIc.alarm, "waking"),
            Item("بعد الصلاة", AdhkarIc.afterPrayer, "after_prayer"), Item("المسجد والأذان", AdhkarIc.mosque, "mosque"),
        )),
        Triple("الجمعة", "", listOf(
            Item("الصلاة على النبي ﷺ", AdhkarIc.star, "salawat"), Item("سورة الكهف", AdhkarIc.book, "kahf"),
            Item("ساعة الإجابة", AdhkarIc.clock, "friday_hour"), Item("سنن الجمعة", AdhkarIc.list, "friday_sunan"),
        )),
        Triple("المناسبات", "", listOf(
            Item("رمضان", AdhkarIc.moon, "ramadan"), Item("عيد الفطر", AdhkarIc.star, "eid_fitr"),
            Item("العشر والأضحى", AdhkarIc.sheep, "dhul_hijja"), Item("يوم عرفة", AdhkarIc.sun, "arafa"),
            Item("الحج والعمرة", AdhkarIc.kaaba, "hajj_umrah"), Item("عاشوراء", AdhkarIc.calendar, "ashura"),
        )),
        Triple("الأدعية", "", listOf(
            Item("أدعية من القرآن", AdhkarIc.book, "quran_duas"), Item("أدعية نبوية", AdhkarIc.hands, "prophetic"),
            Item("الهم والكرب", AdhkarIc.sorrow, "worry"), Item("الرقية الشرعية", AdhkarIc.shield, "ruqya"),
            Item("الاستخارة", AdhkarIc.istikhara, "istikhara"), Item("المريض", AdhkarIc.sick, "sick"),
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
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = SectionBarSpace)) {
            item {
                Column(Modifier.then(swipeSection).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp).height(HomeHeaderHeight), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("الأذكار", color = c.text, fontSize = 30.sp, fontFamily = AppFonts.naskhBold)
                            Text("حصن المسلم · مواقيت الصلاة", color = c.muted, fontSize = 13.sp)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            RoundIconButton(AdhkarIc.beads, "السبحة", { nav.go(Screen.Tasbih) }, tint = c.primary)
                            RoundIconButton(AdhkarIc.qibla, "القبلة", { nav.go(Screen.Qibla) }, tint = c.primary)
                            RoundIconButton(Ic.search, "بحث", { nav.go(Screen.AdhkarSearch) })
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
                        iqama = if (untilIqama != null && iqamaAt != null) Triple(current, iqamaAt, untilIqama) else null,
                        scene = scene,
                        hijriDay = hijri.day,
                        onPick = { p -> view.haptic(Haptic.Tick); preview = if (p == current) null else p },
                    )
                    Text("مقترح لك الآن", color = c.text, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp))
                    if (suggestions.isEmpty()) {
                        Text("أتممت أذكار هذا الوقت، والحمد لله", color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 4.dp))
                    }
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
            item { TrackerEntry(prefs, { nav.go(Screen.Tracker) }, Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp)) }
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
                                    .springClick(role = Role.Button, label = it.name, pressedScale = 0.97f) {
                                        when (it.id) {
                                            null -> soon(it.name)
                                            "kahf" -> nav.go(Screen.Mushaf(kahfPage, "18:1"))
                                            else -> nav.go(Screen.Adhkar(it.id))
                                        }
                                    }
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
            // every other chapter of the book
            item {
                Row(
                    Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp).fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp)).background(c.card).border(1.dp, c.border, RoundedCornerShape(20.dp))
                        .springClick(role = Role.Button, label = "كل أبواب حصن المسلم", pressedScale = 0.98f) { nav.go(Screen.HisnIndex) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(Modifier.size(44.dp).clip(CircleShape).background(c.primarySoft), contentAlignment = Alignment.Center) {
                        Icon(AdhkarIc.books, null, tint = c.primary, modifier = Modifier.size(21.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text("كل أبواب حصن المسلم", color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("السفر والطعام والمطر والسوق والمرض وغيرها", color = c.muted, fontSize = 12.sp)
                    }
                    Icon(Ic.forward, null, tint = c.muted, modifier = Modifier.size(18.dp))
                }
            }
            item { Spacer(Modifier.height(14.dp)) }
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
    iqama: Triple<Prayer, Int, Int>? = null, // (prayer, iqama minute, seconds left)
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
            .height(HomeCardHeight)
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
                Text(
                    if (locating && !placeIsDefault) "جارٍ تحديث الموقع…" else place,
                    color = soft, fontSize = 13.sp,
                    // tap the place to locate again (after travelling, say)
                    modifier = if (placeIsDefault) Modifier else Modifier.springClick(role = Role.Button, label = "تحديث الموقع", ripple = false) { if (!locating) onLocate() },
                )
                if (placeIsDefault) {
                    Text(
                        if (locating) "جارٍ تحديد الموقع…" else "استخدم موقعي",
                        color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.18f))
                            .springClick(role = Role.Button, label = "استخدم موقعي") { if (!locating) onLocate() }
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
            Text(longDay(weekday), color = Color.White, fontSize = 50.sp, fontFamily = AppFonts.naskhBold, lineHeight = 74.sp)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(hijriLine, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(gregorian, color = soft, fontSize = 13.sp)
            }
        }

        Spacer(Modifier.weight(1f))
        Row(
            Modifier
                .padding(start = 14.dp, end = 14.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0x470A1628))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // the prayer that has come is done with: the strip is about the next one, live
            Column(Modifier.weight(1f)) {
                Text(if (iqama != null) "الإقامة" else if (next == Prayer.Sunrise) "الشروق" else "الصلاة القادمة", color = soft, fontSize = 12.sp)
                Text(
                    if (iqama != null) "إقامة ${iqama.first.ar} · ${clock(iqama.second)}" else "${next.ar} · ${clock(times.getValue(next))}",
                    color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("باقي", color = soft, fontSize = 12.sp)
                Text(countdown(iqama?.third ?: untilNext), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                    style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"))
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
