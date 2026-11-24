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
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/** The deeds of the day in «متابعة العبادات». The adhkar sections tick themselves when finished, the ward too. */
data class Deed(val id: String, val arTitle: String, val icon: ImageVector) { val title: String get() = t(arTitle) }

val Deeds = listOf(
    Deed("morning", "أذكار الصباح", AdhkarIc.sun),
    Deed("evening", "أذكار المساء", AdhkarIc.moon),
    Deed("after_prayer", "أذكار الصلاة", AdhkarIc.afterPrayer),
    Deed("sleep", "أذكار النوم", AdhkarIc.bed),
    Deed("ward", "ورد القرآن", AdhkarIc.book),
    Deed("duha", "صلاة الضحى", AdhkarIc.rise),
    Deed("qiyam", "قيام الليل", AdhkarIc.prayer),
    Deed("witr", "صلاة الوتر", AdhkarIc.star),
)

/** A day's share of the deeds done, 0..1. */
fun dayScore(prefs: AppPrefs, day: LocalDate): Float =
    prefs.track[day.toString()].orEmpty().count { id -> Deeds.any { it.id == id } } / Deeds.size.toFloat()

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

/** «متابعة العبادات», the progress of the last week, the Hijri calendar and the coming occasions. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun TrackerScreen(prefs: AppPrefs, store: Prefs, nav: Nav) = AdhkarTheme {
    val c = LocalColors.current
    val scope = rememberCoroutineScope()
    val today = remember { LocalDate.now() }
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
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RoundIconButton(Ic.back, t("رجوع"), { nav.back() })
            Column {
                Text(t("متابعتي"), color = c.text, fontSize = 26.sp, fontFamily = AppFonts.naskhBold)
                Text(t("العبادات والتقويم الهجري والمناسبات"), color = c.muted, fontSize = 13.sp)
            }
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { DeedsCard(prefs, today) { id, on -> scope.launch { store.setTracked(today.toString(), id, on) } } }
            item { ProgressCard(prefs, today) }
            item { CalendarCard(today) }
            item { OccasionsCard(today) { id -> nav.go(Screen.Adhkar(id)) } }
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

@Composable
private fun DeedsCard(prefs: AppPrefs, today: LocalDate, onToggle: (String, Boolean) -> Unit) {
    val c = LocalColors.current
    val done = prefs.track[today.toString()].orEmpty()
    Panel(t("متابعة العبادات"), trailing = {
        Text(t("{0} من {1}", num(Deeds.count { it.id in done }), num(Deeds.size)), color = c.muted, fontSize = 13.sp)
    }) {
        Deeds.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { d ->
                    val on = d.id in done
                    val bg by animateColorAsState(if (on) c.primarySoft else c.bg, tween(200), label = "deed")
                    Row(
                        Modifier.weight(1f).height(58.dp).clip(RoundedCornerShape(18.dp)).background(bg)
                            .springClick(role = Role.Checkbox, label = d.title, haptic = Haptic.Toggle, pressedScale = 0.97f) { onToggle(d.id, !on) }
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            Modifier.size(26.dp).clip(CircleShape).background(if (on) c.primary else Color.Transparent)
                                .border(1.5.dp, if (on) c.primary else c.muted, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { if (on) Icon(Ic.check, null, tint = c.onPrimary, modifier = Modifier.size(15.dp)) }
                        Text(d.title, color = c.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
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
            Text(t("{0} من {1} عبادات اليوم · {2}", num(Deeds.count { it.id in prefs.track[today.toString()].orEmpty() }), num(Deeds.size), sub), color = c.muted, fontSize = 12.sp, maxLines = 1)
        }
        Icon(Ic.forward, null, tint = c.muted, modifier = Modifier.size(18.dp))
    }
}
