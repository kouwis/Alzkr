package com.kouwis.alzkr.ui

import com.kouwis.alzkr.data.t
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kouwis.alzkr.data.AppFonts
import com.kouwis.alzkr.data.AppPrefs
import com.kouwis.alzkr.data.ArabicText.num
import com.kouwis.alzkr.data.Hijri
import com.kouwis.alzkr.data.Prefs
import com.kouwis.alzkr.data.Prayer
import com.kouwis.alzkr.data.PrayerTimes
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/*
 * «متابعة العبادات»: the deeds change with the day. Each prayer opens when its time comes, the
 * sunnahs with their prayers, the fast only on the days it is sunnah (or Ramadan), Friday's own
 * deeds on Friday. The day's achievement weighs each group (the prayers half of it).
 */

enum class DeedGroup(val arTitle: String, val weight: Int) {
    Prayers("الصلوات", 50), Adhkar("الأذكار", 10), Quran("القرآن الكريم", 10), Daily("عبادات يومية", 10),
    Rawatib("سنن رواتب", 0), Night("قيام الليل والوتر", 0), Fasting("الصيام", 10), Other("أخرى", 0),
    ;
    val title: String get() = t(arTitle)
    /** the group whose weight it shares (the sunnahs and the night count with the nawafil) */
    val scoreGroup: String get() = when (this) { Rawatib, Night -> "nawafil"; Other -> "daily"; Daily -> "daily"; else -> name }
}

/** One deed: it opens [after] minutes past [anchor]'s time on the day itself (null: open all day). */
data class Deed(
    val id: String, val arTitle: String, val icon: ImageVector, val group: DeedGroup,
    val anchor: Prayer? = null, val after: Int = 0, val sub: String = "", val plainTitle: String? = null,
) {
    val title: String get() = plainTitle ?: t(arTitle)
    fun opensAt(times: Map<Prayer, Int>): Int? = anchor?.let { (times[it] ?: 0) + after }
}

private val ScoreWeights = mapOf("Prayers" to 50, "Adhkar" to 10, "Quran" to 10, "nawafil" to 10, "daily" to 10, "Fasting" to 10)

/** Why the day is one to fast, if it is: (the short label, the line under «صمت اليوم»). */
fun fastOf(day: LocalDate): Pair<String, String>? {
    val h = Hijri.hijrah(day)
    val m = h.get(ChronoField.MONTH_OF_YEAR); val d = h.get(ChronoField.DAY_OF_MONTH)
    if ((m == 10 && d == 1) || (m == 12 && d in 10..13)) return null // the Eids and the days of tashreeq
    val dow = day.dayOfWeek.value
    val ordinal = listOf("الأول", "الثاني", "الثالث")
    return when {
        m == 9 -> t("رمضان") to t("صيام رمضان")
        m == 12 && d == 9 -> t("يوم عرفة") to t("صيام يوم عرفة")
        m == 12 && d < 9 -> t("العشر من ذي الحجة") to t("صيام العشر من ذي الحجة")
        m == 1 && d == 9 -> t("تاسوعاء") to t("صيام تاسوعاء")
        m == 1 && d == 10 -> t("عاشوراء") to t("صيام عاشوراء")
        d in 13..15 -> t("الأيام البيض") to t("صيام اليوم {0} من الأيام البيض", t(ordinal[d - 13]))
        m == 10 -> t("ست من شوال") to t("صيام الست من شوال")
        dow == 1 -> t("الاثنين") to t("صيام يوم الاثنين")
        dow == 4 -> t("الخميس") to t("صيام يوم الخميس")
        else -> null
    }
}

/** The deeds of a day. */
fun deedsOf(day: LocalDate, custom: List<String>): List<Deed> = buildList {
    val friday = day.dayOfWeek.value == 5
    add(Deed("prayer_Fajr", "الفجر", AdhkarIc.rise, DeedGroup.Prayers, Prayer.Fajr))
    add(Deed("prayer_Dhuhr", if (friday) "الجمعة" else "الظهر", AdhkarIc.sun, DeedGroup.Prayers, Prayer.Dhuhr))
    add(Deed("prayer_Asr", "العصر", AdhkarIc.cloud, DeedGroup.Prayers, Prayer.Asr))
    add(Deed("prayer_Maghrib", "المغرب", AdhkarIc.set, DeedGroup.Prayers, Prayer.Maghrib))
    add(Deed("prayer_Isha", "العشاء", AdhkarIc.moon, DeedGroup.Prayers, Prayer.Isha))

    add(Deed("morning", "أذكار الصباح", AdhkarIc.rise, DeedGroup.Adhkar, Prayer.Fajr))
    add(Deed("evening", "أذكار المساء", AdhkarIc.cloud, DeedGroup.Adhkar, Prayer.Asr))
    add(Deed("after_prayer", "أذكار الصلاة", AdhkarIc.afterPrayer, DeedGroup.Adhkar, Prayer.Fajr))
    add(Deed("sleep", "أذكار النوم", AdhkarIc.bed, DeedGroup.Adhkar, Prayer.Isha))

    add(Deed("ward", "ورد القرآن", AdhkarIc.book, DeedGroup.Quran, sub = t("ورد يومي من القرآن الكريم")))
    if (friday) add(Deed("kahf", "سورة الكهف", AdhkarIc.book, DeedGroup.Quran, sub = t("قراءتها يوم الجمعة")))

    add(Deed("duha", "صلاة الضحى", AdhkarIc.sun, DeedGroup.Daily, Prayer.Sunrise, 15, t("من ركعتين إلى ثماني ركعات بعد شروق الشمس")))
    if (friday) {
        add(Deed("salawat", "الصلاة على النبي ﷺ", AdhkarIc.hands, DeedGroup.Daily, sub = t("الإكثار منها يوم الجمعة")))
        add(Deed("jumua_hour", "دعاء ساعة الإجابة", AdhkarIc.hands, DeedGroup.Daily, Prayer.Asr, sub = t("آخر ساعة من يوم الجمعة")))
    }

    add(Deed("sunnah_fajr", "سنة الفجر", AdhkarIc.rise, DeedGroup.Rawatib, Prayer.Fajr, sub = t("ركعتان قبل صلاة الفجر")))
    if (!friday) add(Deed("sunnah_dhuhr_before", "سنة الظهر القبلية", AdhkarIc.sun, DeedGroup.Rawatib, Prayer.Dhuhr, sub = t("أربع ركعات قبل صلاة الظهر")))
    add(Deed("sunnah_dhuhr_after", if (friday) "سنة الجمعة" else "سنة الظهر البعدية", AdhkarIc.sun, DeedGroup.Rawatib, Prayer.Dhuhr,
        sub = if (friday) t("ركعتان أو أربع بعد صلاة الجمعة") else t("ركعتان بعد صلاة الظهر")))
    add(Deed("sunnah_maghrib", "سنة المغرب", AdhkarIc.set, DeedGroup.Rawatib, Prayer.Maghrib, sub = t("ركعتان بعد صلاة المغرب")))
    add(Deed("sunnah_isha", "سنة العشاء", AdhkarIc.moon, DeedGroup.Rawatib, Prayer.Isha, sub = t("ركعتان بعد صلاة العشاء")))

    add(Deed("qiyam", "قيام الليل", AdhkarIc.star, DeedGroup.Night, Prayer.Isha, sub = t("وأفضله في الثلث الأخير من الليل")))
    add(Deed("witr", "صلاة الوتر", AdhkarIc.moon, DeedGroup.Night, Prayer.Isha, sub = t("ركعة أو ثلاث أو خمس أو سبع أو إحدى عشرة")))

    fastOf(day)?.let { (_, line) -> add(Deed("fast", "صمت اليوم", AdhkarIc.dates, DeedGroup.Fasting, sub = line)) }

    custom.forEach { add(Deed("custom:$it", it, Ic.check, DeedGroup.Other, plainTitle = it)) }
}

/** A day's achievement, 0..1: each group's share done, weighed (an excused day leaves the prayers out). */
fun dayScore(prefs: AppPrefs, day: LocalDate): Float {
    val done = prefs.track[day.toString()].orEmpty()
    val excused = "excused" in done
    val groups = deedsOf(day, prefs.customDeeds).filter { !(excused && it.group == DeedGroup.Prayers) }.groupBy { it.group.scoreGroup }
    var sum = 0f; var weights = 0
    groups.forEach { (g, list) ->
        val w = ScoreWeights[g] ?: 0
        if (w == 0 || list.isEmpty()) return@forEach
        sum += w * list.count { it.id in done } / list.size.toFloat(); weights += w
    }
    return if (weights == 0) 0f else sum / weights
}

/** An occasion of the Hijri year: its day ([day] of [month]), how many days it lasts, and its section. */
data class Occasion(val arTitle: String, val month: Int, val day: Int, val days: Int, val section: String?) { val title: String get() = t(arTitle) }

val Occasions = listOf(
    Occasion("رمضان", 9, 1, 30, "ramadan"),
    Occasion("عيد الفطر", 10, 1, 3, "eid_fitr"),
    Occasion("العشر من ذي الحجة", 12, 1, 9, "dhul_hijja"),
    Occasion("يوم عرفة", 12, 9, 1, "arafa"),
    Occasion("عيد الأضحى", 12, 10, 4, "dhul_hijja"),
    Occasion("رأس السنة الهجرية", 1, 1, 1, null),
    Occasion("عاشوراء", 1, 10, 1, "ashura"),
)

/** When an occasion next starts (today or later), or the start of the one going on now; and whether it is on now. */
fun nextOf(o: Occasion, today: LocalDate): Pair<LocalDate, Boolean> {
    val hy = Hijri.hijrah(today).get(ChronoField.YEAR)
    for (y in listOf(hy - 1, hy, hy + 1)) {
        val start = runCatching { Hijri.gregorianOf(HijrahDate.of(y, o.month, o.day)) }.getOrNull() ?: continue
        val end = start.plusDays(o.days.toLong() - 1)
        if (!today.isAfter(end)) return start to !today.isBefore(start)
    }
    return today to false
}

private fun gDate(d: LocalDate) = "${num(d.dayOfMonth)} ${Hijri.gMonths[d.monthValue - 1]}"
private fun hDate(d: LocalDate): String {
    val h = Hijri.hijrah(d)
    return "${num(h.get(ChronoField.DAY_OF_MONTH))} ${Hijri.months[h.get(ChronoField.MONTH_OF_YEAR) - 1]}"
}

/** «متابعة العبادات»: the day's deeds (as the time, day and date bring them), and the statistics. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun TrackerScreen(prefs: AppPrefs, store: Prefs, nav: Nav) = AdhkarTheme {
    val c = LocalColors.current
    val scope = rememberCoroutineScope()
    val today = remember { LocalDate.now() }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var day by remember { mutableStateOf(today) }
    // the minute now, so deeds open as their time comes
    var nowMin by remember { mutableIntStateOf(java.time.LocalTime.now().let { it.hour * 60 + it.minute }) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(60_000L - System.currentTimeMillis() % 60_000L + 200L)
            nowMin = java.time.LocalTime.now().let { it.hour * 60 + it.minute }
        }
    }
    // the ward of the Quran ticks itself once it is read
    LaunchedEffect(prefs.wardDone, prefs.wardTarget) {
        if (prefs.wardTarget > 0 && prefs.wardDone >= prefs.wardTarget && "ward" !in prefs.track[today.toString()].orEmpty()) {
            store.setTracked(today.toString(), "ward", true)
        }
    }
    Column(
        Modifier.fillMaxSize().background(c.bg)
            .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility)
            .windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility),
    ) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.align(Alignment.CenterStart)) { RoundIconButton(Ic.back, t("رجوع"), { nav.back() }) }
            Text(t("متابعة العبادات"), color = c.text, fontSize = 22.sp, fontFamily = AppFonts.naskhBold)
        }
        Segmented(listOf(t("المتابعة"), t("الإحصائيات")), tab, { tab = it }, Modifier.fillMaxWidth().padding(horizontal = 20.dp))
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (tab == 0) {
                item(key = "week") { WeekCard(prefs, today, day) { day = it } }
                deedSections(prefs, day, today, nowMin, store) { id, on -> scope.launch { store.setTracked(day.toString(), id, on) } }
            } else {
                item { ProgressCard(prefs, today) }
                item { CalendarCard(today) }
                item { OccasionsCard(today) { id -> nav.go(Screen.Adhkar(id)) } }
            }
        }
    }
}

@Composable
private fun Panel(title: String, trailing: @Composable () -> Unit = {}, content: @Composable () -> Unit) {
    val c = LocalColors.current
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(c.card)
            .border(1.dp, c.border, RoundedCornerShape(26.dp)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = c.text, fontSize = 18.sp, fontFamily = AppFonts.naskhBold, modifier = Modifier.weight(1f))
            trailing()
        }
        content()
    }
}

/** The day picked, its achievement, and the week to pick another day (not one to come). */
@Composable
private fun WeekCard(prefs: AppPrefs, today: LocalDate, day: LocalDate, onPick: (LocalDate) -> Unit) {
    val c = LocalColors.current
    val score = dayScore(prefs, day)
    val ring by animateFloatAsState(score, tween(500), label = "ring")
    val sat = day.minusDays(((day.dayOfWeek.value + 1) % 7).toLong())
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(c.card).border(1.dp, c.border, RoundedCornerShape(26.dp)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                val h = Hijri.hijrah(day)
                Text(if (day == today) t("اليوم") else com.kouwis.alzkr.data.Hijri.of(day).weekday, color = c.text, fontSize = 22.sp, fontFamily = AppFonts.naskhBold)
                Text("${num(h.get(ChronoField.DAY_OF_MONTH))} ${Hijri.months[h.get(ChronoField.MONTH_OF_YEAR) - 1]} · ${gDate(day)}", color = c.muted, fontSize = 14.sp)
                fastOf(day)?.let { (label, _) ->
                    Text(label, color = c.goldText, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 6.dp).clip(RoundedCornerShape(12.dp)).background(c.goldSoft).padding(horizontal = 10.dp, vertical = 3.dp))
                }
            }
            Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                val track = c.chip; val fill = c.primary
                Canvas(Modifier.fillMaxSize()) {
                    val w = 6.dp.toPx()
                    val sz = androidx.compose.ui.geometry.Size(size.width - w, size.height - w)
                    drawArc(track, 0f, 360f, false, Offset(w / 2, w / 2), sz, style = Stroke(w))
                    if (ring > 0f) drawArc(fill, -90f, 360f * ring, false, Offset(w / 2, w / 2), sz, style = Stroke(w, cap = StrokeCap.Round))
                }
                Text(t("{0}٪", num((score * 100).roundToInt())), color = c.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
        Row(Modifier.fillMaxWidth()) {
            (0..6).forEach { i ->
                val d = sat.plusDays(i.toLong())
                val future = d.isAfter(today)
                val on = d == day
                val sc = if (future) 0f else dayScore(prefs, d)
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                        .then(if (future) Modifier else Modifier.springClick(label = d.toString(), ripple = false) { onPick(d) })
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(listOf(t("سبت"), t("أحد"), t("اثنين"), t("ثلاثاء"), t("أربعاء"), t("خميس"), t("جمعة"))[i],
                        color = if (on) c.goldText else if (future) c.muted.copy(alpha = 0.5f) else c.muted, fontSize = 11.sp, maxLines = 1)
                    Box(
                        Modifier.size(28.dp).clip(CircleShape)
                            .background(if (sc > 0f) c.primary.copy(alpha = 0.25f + 0.75f * sc) else c.chip.copy(alpha = if (future) 0.4f else 1f))
                            .border(if (on) 2.dp else 0.dp, if (on) c.gold else Color.Transparent, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { if (sc >= 1f) Icon(Ic.check, null, tint = c.onPrimary, modifier = Modifier.size(14.dp)) }
                    Text(num(d.dayOfMonth), color = if (on) c.goldText else if (future) c.muted.copy(alpha = 0.5f) else c.text,
                        fontSize = 13.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
    }
}

/** The day's deeds, group by group. A deed whose time has not come today is locked. */
private fun androidx.compose.foundation.lazy.LazyListScope.deedSections(
    prefs: AppPrefs, day: LocalDate, today: LocalDate, nowMin: Int, store: Prefs, onToggle: (String, Boolean) -> Unit,
) {
    val done = prefs.track[day.toString()].orEmpty()
    val excused = "excused" in done
    val times = PrayerTimes.forDay(day, prefs.lat ?: 30.0444, prefs.lng ?: 31.2357, prefs.prayer)
    fun locked(d: Deed) = day == today && (d.opensAt(times)?.let { nowMin < it } ?: false)
    val deeds = deedsOf(day, prefs.customDeeds)
    DeedGroup.entries.forEach { g ->
        val list = deeds.filter { it.group == g }
        if (list.isEmpty() && g != DeedGroup.Other) return@forEach
        item(key = "h:${g.name}") { GroupHeader(g, list, done, excused, day, prefs.customDeeds, store) }
        if (g == DeedGroup.Prayers) {
            item(key = "prayers") {
                val c = LocalColors.current
                if (excused) Text(t("الصلوات موقوفة اليوم للعذر الشرعي"), color = c.muted, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 6.dp))
                else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    list.forEach { d -> PrayerChip(d, d.id in done, locked(d), Modifier.weight(1f)) { onToggle(d.id, it) } }
                }
            }
        } else list.forEach { d ->
            item(key = "d:${d.id}") { DeedRow(d, d.id in done, locked(d), d.opensAt(times)) { onToggle(d.id, it) } }
        }
    }
    item(key = "excused") {
        val c = LocalColors.current
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(if (excused) c.goldSoft else c.card)
                .springClick(role = Role.Switch, label = t("العذر الشرعي")) { onToggle("excused", !excused) }
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(t("العذر الشرعي"), color = c.text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(t("إيقاف تتبع الصلوات لهذا اليوم"), color = c.muted, fontSize = 13.sp)
            }
            AnimatedToggle(excused, { onToggle("excused", it) })
        }
    }
    item(key = "weights") {
        val c = LocalColors.current
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).border(1.dp, c.border, RoundedCornerShape(22.dp)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(t("كيف يُحسب الإنجاز؟"), color = c.muted, fontSize = 14.sp)
            Text(
                t("الصلوات {0}٪ · الأذكار {1}٪ · القرآن {1}٪ · النوافل {1}٪ · عبادات يومية {1}٪ · الصيام {1}٪ في أيامه", num(50), num(10)),
                color = c.text, fontSize = 13.sp, lineHeight = 20.sp,
            )
        }
    }
}

@Composable
private fun GroupHeader(g: DeedGroup, list: List<Deed>, done: Set<String>, excused: Boolean, day: LocalDate, custom: List<String>, store: Prefs) {
    val c = LocalColors.current
    val scope = rememberCoroutineScope()
    var adding by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(top = 6.dp, start = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(g.title, color = c.text, fontSize = 20.sp, fontFamily = AppFonts.naskhBold, modifier = Modifier.weight(1f))
        when {
            g == DeedGroup.Fasting -> fastOf(day)?.let { (label, _) ->
                Text(label, color = c.goldText, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(c.goldSoft).padding(horizontal = 12.dp, vertical = 5.dp))
            }
            g == DeedGroup.Other -> Box(
                Modifier.size(34.dp).clip(CircleShape).background(c.gold).springClick(label = t("إضافة")) { adding = true },
                contentAlignment = Alignment.Center,
            ) { Icon(Ic.plus, t("إضافة"), tint = Color(0xFF1B1B1B), modifier = Modifier.size(18.dp)) }
            !(g == DeedGroup.Prayers && excused) && list.isNotEmpty() ->
                Text(t("{0}/{1}", num(list.count { it.id in done }), num(list.size)), color = c.muted, fontSize = 14.sp)
        }
    }
    if (adding) CustomDeedsDialog(custom, onSave = { scope.launch { store.setCustomDeeds(it) } }) { adding = false }
}

@Composable
private fun PrayerChip(d: Deed, on: Boolean, locked: Boolean, modifier: Modifier, onToggle: (Boolean) -> Unit) {
    val c = LocalColors.current
    val bg by animateColorAsState(if (on) c.primary else c.card, tween(200), label = "chip")
    val fg = if (on) c.onPrimary else if (locked) c.muted.copy(alpha = 0.6f) else c.text
    Column(
        modifier.height(92.dp).clip(RoundedCornerShape(22.dp)).background(bg)
            .border(1.dp, if (on) c.primary else c.border, RoundedCornerShape(22.dp))
            .then(if (locked) Modifier else Modifier.springClick(role = Role.Checkbox, label = d.title, haptic = Haptic.Toggle, pressedScale = 0.95f) { onToggle(!on) }),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Text(d.title, color = fg, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Icon(if (on) Ic.check else if (locked) Ic.lock else d.icon, null, tint = fg, modifier = Modifier.padding(top = 8.dp).size(22.dp))
    }
}

@Composable
private fun DeedRow(d: Deed, on: Boolean, locked: Boolean, opensAt: Int?, onToggle: (Boolean) -> Unit) {
    val c = LocalColors.current
    val bg by animateColorAsState(if (on) c.primarySoft else c.card, tween(200), label = "deed")
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(bg)
            .border(1.dp, if (on) c.primary.copy(alpha = 0.5f) else c.border, RoundedCornerShape(22.dp))
            .then(if (locked) Modifier else Modifier.springClick(role = Role.Checkbox, label = d.title, haptic = Haptic.Toggle, pressedScale = 0.98f) { onToggle(!on) })
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(d.icon, null, tint = if (locked) c.muted.copy(alpha = 0.5f) else c.goldText, modifier = Modifier.size(26.dp))
        Column(Modifier.weight(1f)) {
            Text(d.title, color = if (locked) c.muted else c.text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            val sub = if (locked && opensAt != null) t("يبدأ {0}", clock(opensAt)) else d.sub
            if (sub.isNotBlank()) Text(sub, color = c.muted, fontSize = 13.sp, lineHeight = 18.sp)
        }
        Box(
            Modifier.size(28.dp).clip(CircleShape).background(if (on) c.primary else Color.Transparent)
                .border(1.5.dp, if (on) c.primary else if (locked) Color.Transparent else c.muted, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (on) Icon(Ic.check, null, tint = c.onPrimary, modifier = Modifier.size(16.dp))
            else if (locked) Icon(Ic.lock, null, tint = c.muted, modifier = Modifier.size(18.dp))
        }
    }
}

/** «أخرى»: add the user's own deeds, or remove them. */
@Composable
private fun CustomDeedsDialog(list: List<String>, onSave: (List<String>) -> Unit, onDismiss: () -> Unit) {
    val c = LocalColors.current
    var items by remember { mutableStateOf(list) }
    var text by remember { mutableStateOf("") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.card,
        title = { Text(t("عبادات أخرى"), color = c.text, fontFamily = AppFonts.naskhBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.OutlinedTextField(
                        text, { text = it.take(40) }, singleLine = true, modifier = Modifier.weight(1f),
                        placeholder = { Text(t("مثلًا: صدقة، صلة رحم"), color = c.muted) },
                        colors = fieldColors(),
                    )
                    Box(
                        Modifier.size(44.dp).clip(CircleShape).background(c.gold).springClick(label = t("إضافة")) {
                            val v = text.trim()
                            if (v.isNotEmpty() && v !in items) { items = items + v; onSave(items) }
                            text = ""
                        },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Ic.plus, t("إضافة"), tint = Color(0xFF1B1B1B), modifier = Modifier.size(18.dp)) }
                }
                items.forEach { name ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(name, color = c.text, fontSize = 16.sp, modifier = Modifier.weight(1f))
                        Box(
                            Modifier.size(36.dp).clip(CircleShape).springClick(label = t("حذف")) { items = items - name; onSave(items) },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Ic.trash, t("حذف"), tint = c.heart, modifier = Modifier.size(18.dp)) }
                    }
                }
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text(t("تم"), color = c.primary, fontWeight = FontWeight.Bold) } },
    )
}

@Composable
private fun ProgressCard(prefs: AppPrefs, today: LocalDate) {
    val c = LocalColors.current
    // the week runs Saturday to Friday
    val sat = today.minusDays(((today.dayOfWeek.value + 1) % 7).toLong())
    val upto = ChronoUnit.DAYS.between(sat, today).toInt() // today's place in the week, 0..6
    val week = (0..6).map { dayScore(prefs, sat.plusDays(it.toLong())) }
    val lastWeek = (0..6).map { dayScore(prefs, sat.minusDays(7L - it)) }
    val avg = week.take(upto + 1).average().toFloat()
    val up = avg >= lastWeek.average().toFloat()
    val todayPct = (week[upto] * 100).roundToInt()
    Panel(t("تقدم هذا الأسبوع")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(t("إنجاز اليوم"), color = c.muted, fontSize = 13.sp)
                Text(t("{0}٪", num(todayPct)), color = c.text, fontSize = 34.sp, fontWeight = FontWeight.Bold)
            }
            Box(Modifier.width(1.dp).height(44.dp).background(c.border))
            Column(Modifier.weight(1f).padding(start = 16.dp)) {
                Text(t("معدل الأسبوع"), color = c.muted, fontSize = 13.sp)
                Text(t("{0}٪", num((avg * 100).roundToInt())), color = c.text, fontSize = 34.sp, fontWeight = FontWeight.Bold)
            }
            Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(if (up) c.primarySoft else c.goldSoft), contentAlignment = Alignment.Center) {
                Text(if (up) "↑" else "↓", color = if (up) c.primary else c.goldText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
        }
        // Saturday on the right, Friday on the left (Arabic reads from the right); the line stops at today
        val line = c.gold
        val grid = c.border
        Canvas(Modifier.fillMaxWidth().height(110.dp)) {
            val pad = 8.dp.toPx()
            val w = size.width - pad * 2
            val h = size.height - pad * 2
            val col = w / 7
            val rtl = com.kouwis.alzkr.data.I18n.lang.rtl // the week runs the way the text reads
            fun pt(i: Int) = Offset(if (rtl) pad + w - col * (i + 0.5f) else pad + col * (i + 0.5f), pad + h - h * week[i])
            for (k in 0..2) drawLine(grid, Offset(pad, pad + h * k / 2), Offset(pad + w, pad + h * k / 2), strokeWidth = 1f)
            val path = Path().apply {
                moveTo(pt(0).x, pt(0).y)
                for (i in 1..upto) {
                    val a = pt(i - 1); val b = pt(i)
                    val mx = (a.x + b.x) / 2
                    cubicTo(mx, a.y, mx, b.y, b.x, b.y)
                }
            }
            if (upto > 0) {
                val fill = Path().apply {
                    addPath(path)
                    lineTo(pt(upto).x, pad + h); lineTo(pt(0).x, pad + h); close()
                }
                drawPath(fill, Brush.verticalGradient(listOf(line.copy(alpha = 0.28f), Color.Transparent)))
                drawPath(path, line, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
            }
            // the days still to come: faint dots
            for (i in upto + 1..6) drawCircle(grid, 3.dp.toPx(), Offset(pt(i).x, pad + h))
            drawCircle(line, 5.dp.toPx(), pt(upto))
            drawCircle(Color.White, 2.dp.toPx(), pt(upto))
        }
        Row(Modifier.fillMaxWidth()) {
            listOf(t("سبت"), t("أحد"), t("اثنين"), t("ثلاثاء"), t("أربعاء"), t("خميس"), t("جمعة")).forEachIndexed { i, name ->
                Text(
                    if (i == upto) t("اليوم") else name,
                    color = if (i == upto) c.text else if (i > upto) c.muted.copy(alpha = 0.5f) else c.muted,
                    fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
                )
            }
        }
        if (week[upto] >= 1f) Text(t("أتممت عبادات اليوم، تقبّل الله منك"), color = c.primary, fontSize = 13.sp)
    }
}

@Composable
private fun CalendarCard(today: LocalDate) {
    val c = LocalColors.current
    var hijri by rememberSaveable { mutableStateOf(true) }
    var offset by rememberSaveable { mutableIntStateOf(0) }
    var picked by remember { mutableStateOf<LocalDate?>(null) }
    // the month shown: its first day and its length
    val (first, length, title) = remember(hijri, offset) {
        if (hijri) {
            val h = Hijri.hijrah(today).plus(offset.toLong(), ChronoUnit.MONTHS)
            val f = h.with(ChronoField.DAY_OF_MONTH, 1)
            Triple(Hijri.gregorianOf(f), f.lengthOfMonth(), "${Hijri.months[f.get(ChronoField.MONTH_OF_YEAR) - 1]} ${num(f.get(ChronoField.YEAR))}")
        } else {
            val f = today.plusMonths(offset.toLong()).withDayOfMonth(1)
            Triple(f, f.lengthOfMonth(), "${Hijri.gMonths[f.monthValue - 1]} ${num(f.year)}")
        }
    }
    // occasion days in this month
    val marks = remember(first, length) {
        (0 until length).associate { k ->
            val d = first.plusDays(k.toLong())
            val h = Hijri.hijrah(d)
            val m = h.get(ChronoField.MONTH_OF_YEAR); val dd = h.get(ChronoField.DAY_OF_MONTH)
            // a long occasion (Ramadan) is marked on its first day; the shortest one wins (عرفة inside the ten days)
            d to Occasions.filter { o -> o.month == m && dd >= o.day && dd < o.day + (if (o.days > 10) 1 else o.days) }.minByOrNull { it.days }?.title
        }
    }
    Panel(t("التقويم"), trailing = {
        Segmented(listOf(t("هجري"), t("ميلادي")), if (hijri) 0 else 1, { hijri = it == 0; offset = 0; picked = null }, Modifier.width(150.dp), height = 34.dp)
    }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RoundIconButton(Ic.back, t("الشهر السابق"), { offset -= 1; picked = null }, size = 36.dp)
            Text(title, color = c.text, fontSize = 17.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            RoundIconButton(Ic.forward, t("الشهر التالي"), { offset += 1; picked = null }, size = 36.dp)
        }
        Row(Modifier.fillMaxWidth()) {
            listOf(t("سبت"), t("أحد"), t("اثنين"), t("ثلاثاء"), t("أربعاء"), t("خميس"), t("جمعة")).forEach {
                Text(it, color = c.muted, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
        val lead = (first.dayOfWeek.value + 1) % 7 // Saturday first
        val cells = lead + length
        (0 until (cells + 6) / 7).forEach { r ->
            Row(Modifier.fillMaxWidth()) {
                (0..6).forEach { col ->
                    val k = r * 7 + col - lead
                    Box(Modifier.weight(1f).aspectRatio(0.92f).padding(2.dp), contentAlignment = Alignment.Center) {
                        if (k in 0 until length) {
                            val d = first.plusDays(k.toLong())
                            val isToday = d == today
                            val mark = marks[d]
                            val main = if (hijri) Hijri.hijrah(d).get(ChronoField.DAY_OF_MONTH) else d.dayOfMonth
                            val sub = if (hijri) d.dayOfMonth else Hijri.hijrah(d).get(ChronoField.DAY_OF_MONTH)
                            Column(
                                Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp))
                                    .background(if (isToday) c.primary else if (d == picked) c.primarySoft else Color.Transparent)
                                    .springClick(role = Role.Button, label = num(main), haptic = Haptic.Tick, ripple = false) { picked = d },
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Text(num(main), color = if (isToday) c.onPrimary else if (col == 6) c.primary else c.text, fontSize = 16.sp, fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal)
                                Text(num(sub), color = if (isToday) c.onPrimary.copy(alpha = 0.75f) else c.muted, fontSize = 9.sp)
                                Box(Modifier.padding(top = 2.dp).size(5.dp).clip(CircleShape).background(if (mark != null) c.gold else Color.Transparent))
                            }
                        }
                    }
                }
            }
        }
        val p = picked ?: today
        Text(
            t("{0} {1} هـ · {2} {3} م", hDate(p), num(Hijri.hijrah(p).get(ChronoField.YEAR)), gDate(p), num(p.year)) + (marks[p]?.let { " · $it" } ?: ""),
            color = c.muted, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun OccasionsCard(today: LocalDate, onOpen: (String) -> Unit) {
    val c = LocalColors.current
    val list = remember(today) { Occasions.map { it to nextOf(it, today) }.sortedBy { it.second.first } }
    Panel(t("المناسبات")) {
        list.forEach { (o, at) ->
            val (date, now) = at
            val left = ChronoUnit.DAYS.between(today, date).toInt()
            val months = left / 30
            val days = left % 30
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.bg)
                    .then(if (o.section != null) Modifier.springClick(label = o.title, pressedScale = 0.98f) { onOpen(o.section) } else Modifier)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1.3f)) {
                        Text(o.title, color = c.text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text("${hDate(date)} · ${gDate(date)} ${num(date.year)}", color = c.muted, fontSize = 12.sp)
                    }
                    if (now || left == 0) {
                        Text(
                            if (left < 0) t("جارٍ الآن") else t("اليوم"), color = c.onPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(c.primary).padding(horizontal = 14.dp, vertical = 6.dp),
                        )
                    } else {
                        Cell(num(months), when (months) { 2 -> t("شهران"); in 3..10 -> t("أشهر"); 0, 1 -> t("شهر"); else -> t("شهرًا") }, Modifier.weight(0.7f), highlight = months == 0)
                        Box(Modifier.width(1.dp).height(36.dp).background(c.border))
                        Cell(num(days), when (days) { 2 -> t("يومان"); in 3..10 -> t("أيام"); 0, 1 -> t("يوم"); else -> t("يومًا") }, Modifier.weight(0.7f), highlight = months == 0)
                    }
                }
                // how near it is: a year of ticks, the passed ones lit
                val frac = if (now) 1f else (1f - left / 354f).coerceIn(0f, 1f)
                val lit = c.gold
                val dim = c.border
                Canvas(Modifier.fillMaxWidth().height(16.dp)) {
                    val n = 36
                    val step = size.width / n
                    for (i in 0 until n) {
                        // ticks fill from the right, like the text
                        val on = (if (com.kouwis.alzkr.data.I18n.lang.rtl) n - 1 - i else i) < (frac * n).roundToInt()
                        val x = step * i + step / 2
                        drawLine(if (on) lit else dim, Offset(x, 2f), Offset(x, size.height - 2f), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
                    }
                }
            }
        }
    }
}

@Composable
private fun Cell(value: String, unit: String, modifier: Modifier, highlight: Boolean) {
    val c = LocalColors.current
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = if (highlight) c.gold else c.text, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        Text(unit, color = c.muted, fontSize = 12.sp)
    }
}

/** The card on the adhkar home that opens «متابعتي»: today's share of the deeds and the next occasion. */
@Composable
fun TrackerEntry(prefs: AppPrefs, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalColors.current
    val today = LocalDate.now()
    val score = dayScore(prefs, today)
    val ring by animateFloatAsState(score, tween(500), label = "ring")
    val next = remember(today) { Occasions.map { it to nextOf(it, today) }.minByOrNull { it.second.first } }
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.card).border(1.dp, c.border, RoundedCornerShape(20.dp))
            .springClick(role = Role.Button, label = t("متابعتي"), pressedScale = 0.98f, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            val track = c.primarySoft
            val fill = c.primary
            Canvas(Modifier.fillMaxSize()) {
                val w = 5.dp.toPx()
                drawArc(track, 0f, 360f, false, Offset(w / 2, w / 2), androidx.compose.ui.geometry.Size(size.width - w, size.height - w), style = Stroke(w))
                if (ring > 0f) drawArc(fill, -90f, 360f * ring, false, Offset(w / 2, w / 2), androidx.compose.ui.geometry.Size(size.width - w, size.height - w), style = Stroke(w, cap = StrokeCap.Round))
            }
            Text(t("{0}٪", num((score * 100).roundToInt())), color = c.text, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.weight(1f)) {
            Text(t("متابعتي والتقويم"), color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            val sub = next?.let { (o, at) ->
                val left = ChronoUnit.DAYS.between(today, at.first).toInt()
                if (at.second) t("{0} الآن", o.title) else if (left in 3..10) t("{0} بعد {1} أيام", o.title, num(left)) else t("{0} بعد {1} يومًا", o.title, num(left))
            }.orEmpty()
            val all = deedsOf(today, prefs.customDeeds)
            Text(t("{0} من {1} عبادات اليوم · {2}", num(all.count { it.id in prefs.track[today.toString()].orEmpty() }), num(all.size), sub), color = c.muted, fontSize = 12.sp, maxLines = 1)
        }
        Icon(Ic.forward, null, tint = c.muted, modifier = Modifier.size(18.dp))
    }
}
