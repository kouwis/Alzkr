package com.kouwis.alzkr.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kouwis.alzkr.data.Adhkar
import com.kouwis.alzkr.data.AppFonts
import com.kouwis.alzkr.data.AdhkarSection
import com.kouwis.alzkr.data.ArabicText.num
import com.kouwis.alzkr.data.Dhikr
import com.kouwis.alzkr.data.QuranData
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The adhkar of one section, one per page: tap the counter — or anywhere on the dhikr — to count. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun AdhkarReaderScreen(q: QuranData, nav: Nav, id: String) = AdhkarTheme {
    val c = LocalColors.current
    val context = LocalContext.current
    val sections by produceState(Adhkar.peek()) { value = Adhkar.load(context) }
    val sec = sections?.get(id)
    Column(
        Modifier.fillMaxSize().background(c.bg)
            .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility)
            .windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility),
    ) {
        if (sec == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = c.primary) }
        } else {
            Reader(q, nav, sec)
        }
    }
}

@Composable
private fun Reader(q: QuranData, nav: Nav, sec: AdhkarSection) {
    val c = LocalColors.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val n = sec.items.size
    val counts = remember(sec.id) { mutableStateListOf(*Array(n) { 0 }) }
    val pager = rememberPagerState { n }
    var finished by remember(sec.id) { mutableStateOf(false) }
    fun target(i: Int) = sec.items[i].count
    fun complete(i: Int) = target(i) > 0 && counts[i] >= target(i)
    val doneCount = (0 until n).count { complete(it) }

    fun tap(i: Int) {
        if (finished) return
        val t = target(i)
        if (t == 0) { counts[i] = counts[i] + 1; view.haptic(Haptic.Tick); return }
        if (counts[i] >= t) {
            // already counted: take the reader on to the next one still to say
            val next = ((i + 1 until n) + (0 until i)).firstOrNull { !complete(it) }
            if (next != null) scope.launch { pager.animateScrollToPage(next) }
            return
        }
        counts[i] = counts[i] + 1
        if (counts[i] < t) { view.haptic(Haptic.Tick); return }
        view.haptic(Haptic.Confirm)
        val next = ((i + 1 until n) + (0 until i)).firstOrNull { !complete(it) }
        scope.launch {
            delay(420)
            if (next == null) finished = true else pager.animateScrollToPage(next)
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // header
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                RoundIconButton(Ic.back, "رجوع", { nav.back() })
                Column(Modifier.weight(1f)) {
                    Text(sec.title, color = c.text, fontSize = 24.sp, fontFamily = AppFonts.naskhBold, maxLines = 1)
                    Text(
                        if (n > 1) "${num(pager.currentPage + 1)} من ${num(n)}" else "ذكر واحد",
                        color = c.muted, fontSize = 13.sp,
                    )
                }
                if (counts.any { it > 0 }) {
                    RoundIconButton(Ic.refresh, "من الأول", {
                        for (k in 0 until n) counts[k] = 0
                        finished = false
                        scope.launch { pager.animateScrollToPage(0) }
                    }, haptic = Haptic.Delete)
                }
            }
            if (n > 1 && sec.items.any { it.count > 0 }) {
                val p by animateFloatAsState(doneCount / n.toFloat(), tween(400), label = "progress")
                Box(
                    Modifier.padding(horizontal = 20.dp, vertical = 12.dp).fillMaxWidth().height(4.dp)
                        .clip(RoundedCornerShape(2.dp)).background(c.primarySoft),
                ) {
                    Box(Modifier.fillMaxWidth(p).height(4.dp).clip(RoundedCornerShape(2.dp)).background(c.primary))
                }
            } else {
                Spacer(Modifier.height(12.dp))
            }

            HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth(), beyondViewportPageCount = 1) { i ->
                DhikrPage(q, sec.items[i], complete(i), Modifier.pointerInput(i) { detectTapGestures { tap(i) } })
            }

            // the counter
            val i = pager.currentPage.coerceIn(0, n - 1)
            DhikrCounter(
                count = counts[i], target = target(i), done = complete(i),
                onTap = { tap(i) },
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 10.dp, bottom = 18.dp),
            )
        }

        AnimatedVisibility(finished, enter = fadeIn(tween(300)) + scaleIn(tween(380), initialScale = 0.94f), exit = fadeOut(tween(200))) {
            Finished(sec.done, onBack = { nav.back() }, onAgain = {
                for (k in 0 until n) counts[k] = 0
                finished = false
                scope.launch { pager.scrollToPage(0) }
            })
        }
    }
}

@Composable
private fun DhikrPage(q: QuranData, d: Dhikr, done: Boolean, modifier: Modifier) {
    val c = LocalColors.current
    val textColor by androidx.compose.animation.animateColorAsState(if (done) c.muted else c.text, tween(300), label = "ink")
    Box(modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Column(
            Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)).background(c.card)
                .border(1.dp, c.border, RoundedCornerShape(24.dp))
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val onlyQuran = d.parts.all { it.q != null || it.t != null && it.t.startsWith("بِسْمِ") } && d.parts.any { it.q != null }
            val caption = d.label.ifBlank {
                if (onlyQuran) d.parts.firstNotNullOf { it.q }.let { (s, a1, a2) ->
                    "سورة ${q.surahName(s)} · " + if (a1 == a2) "الآية ${num(a1)}" else "الآيات ${num(a1)}–${num(a2)}"
                } else ""
            }
            if (caption.isNotBlank()) {
                Text(
                    caption, color = c.primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(c.primarySoft).padding(horizontal = 12.dp, vertical = 5.dp),
                )
            }
            Spacer(Modifier.height(2.dp))
            d.parts.forEach { p ->
                when {
                    p.q != null -> QuranPassage(q, p.q[0], p.q[1], p.q[2], fontSize = 25.sp, color = if (done) c.muted else c.ink)
                    p.u != null -> Text(
                        "﴿${p.u}﴾", color = if (done) c.muted else c.ink, fontSize = 23.sp, lineHeight = 44.sp,
                        fontFamily = AppFonts.hafs, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                    )
                    p.t != null -> Text(
                        p.t, color = textColor, fontSize = 22.sp, lineHeight = 42.sp, fontFamily = AppFonts.naskh,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            if (d.note.isNotBlank()) {
                Text(d.note, color = c.muted, fontSize = 14.sp, lineHeight = 24.sp, textAlign = TextAlign.Center)
            }
            if (d.fadl.isNotBlank()) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.goldSoft).padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("فضله", color = c.goldText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(d.fadl, color = c.text, fontSize = 15.sp, lineHeight = 26.sp)
                }
            }
            if (d.src.isNotBlank()) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("التخريج", color = c.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(d.src.replace(" — ", "\n"), color = c.muted, fontSize = 13.sp, lineHeight = 22.sp)
                }
            }
        }
    }
}

/** A round counter: the number left (or counted, for an open counter) inside a ring that fills as you go. */
@Composable
fun DhikrCounter(count: Int, target: Int, done: Boolean, onTap: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalColors.current
    val frac by animateFloatAsState(if (target > 0) count / target.toFloat() else 0f, tween(220), label = "ring")
    val bg by androidx.compose.animation.animateColorAsState(if (done) c.primary else c.card, tween(250), label = "bg")
    Box(
        modifier.size(104.dp).clip(CircleShape).background(bg)
            .springClick(role = Role.Button, label = "عدّ", haptic = Haptic.None, pressedScale = 0.9f, ripple = false, onClick = onTap),
        contentAlignment = Alignment.Center,
    ) {
        val track = c.primarySoft
        val fill = c.primary
        Canvas(Modifier.fillMaxSize().padding(5.dp)) {
            val w = 7.dp.toPx()
            val inset = w / 2
            val sz = Size(size.width - w, size.height - w)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), sz, style = Stroke(w))
            if (frac > 0f && !done) drawArc(fill, -90f, 360f * frac, false, Offset(inset, inset), sz, style = Stroke(w, cap = StrokeCap.Round))
        }
        if (done) {
            Icon(Ic.check, "تم", tint = c.onPrimary, modifier = Modifier.size(40.dp))
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(num(if (target > 0) target - count else count), color = c.text, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                if (target > 1) Text("من ${num(target)}", color = c.muted, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun Finished(done: String, onBack: () -> Unit, onAgain: () -> Unit) {
    val c = LocalColors.current
    Box(
        Modifier.fillMaxSize().background(c.bg.copy(alpha = 0.97f))
            .pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.padding(32.dp).widthIn(max = 420.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(Modifier.size(96.dp).clip(CircleShape).background(c.primary), contentAlignment = Alignment.Center) {
                Icon(Ic.check, null, tint = c.onPrimary, modifier = Modifier.size(48.dp))
            }
            Text(done, color = c.text, fontSize = 26.sp, fontFamily = AppFonts.naskhBold, textAlign = TextAlign.Center)
            Text("الحمد لله", color = c.primary, fontSize = 40.sp, fontFamily = AppFonts.naskhBold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Pill("رجوع", filled = true, onClick = onBack)
                Pill("من الأول", filled = false, onClick = onAgain)
            }
        }
    }
}

@Composable
private fun Pill(label: String, filled: Boolean, onClick: () -> Unit) {
    val c = LocalColors.current
    Box(
        Modifier.height(48.dp).clip(RoundedCornerShape(24.dp))
            .background(if (filled) c.primary else c.card)
            .border(1.dp, if (filled) c.primary else c.border, RoundedCornerShape(24.dp))
            .springClick(label = label, onClick = onClick)
            .padding(horizontal = 26.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (filled) c.onPrimary else c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** A free tasbih counter: pick a dhikr, tap anywhere to count. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun TasbihScreen(nav: Nav) = AdhkarTheme {
    val c = LocalColors.current
    val view = LocalView.current
    val options = listOf("سُبْحَانَ اللَّهِ", "الْحَمْدُ لِلَّهِ", "اللَّهُ أَكْبَرُ", "لَا إِلَٰهَ إِلَّا اللَّهُ", "أَسْتَغْفِرُ اللَّهَ", "اللَّهُمَّ صَلِّ عَلَى مُحَمَّدٍ")
    var pick by remember { mutableIntStateOf(0) }
    val counts = remember { mutableStateListOf(*Array(options.size) { 0 }) }
    fun tap() { counts[pick] = counts[pick] + 1; view.haptic(if (counts[pick] % 33 == 0) Haptic.Confirm else Haptic.Tick) }
    Column(
        Modifier.fillMaxSize().background(c.bg)
            .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility)
            .windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility)
            .pointerInput(Unit) { detectTapGestures { tap() } },
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RoundIconButton(Ic.back, "رجوع", { nav.back() })
            Column(Modifier.weight(1f)) {
                Text("العداد", color = c.text, fontSize = 24.sp, fontFamily = AppFonts.naskhBold)
                Text("اضغط في أي مكان على الشاشة", color = c.muted, fontSize = 13.sp)
            }
            RoundIconButton(Ic.refresh, "تصفير", { counts[pick] = 0 }, haptic = Haptic.Delete)
        }
        androidx.compose.foundation.layout.FlowRow(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEachIndexed { k, o ->
                val on = k == pick
                Text(
                    o, color = if (on) c.onPrimary else c.text, fontSize = 15.sp, fontFamily = AppFonts.naskh,
                    modifier = Modifier.clip(RoundedCornerShape(18.dp)).background(if (on) c.primary else c.card)
                        .border(1.dp, if (on) c.primary else c.border, RoundedCornerShape(18.dp))
                        .springClick(role = Role.Tab, haptic = Haptic.Tick, pressedScale = 0.95f) { pick = k }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(options[pick], color = c.text, fontSize = 30.sp, fontFamily = AppFonts.naskhBold)
                Text(num(counts[pick]), color = c.primary, fontSize = 72.sp, fontWeight = FontWeight.Bold)
                val round = counts[pick] % 33
                Text(
                    if (counts[pick] >= 33) "${num(counts[pick] / 33)} × ٣٣ و${num(round)}" else "من ٣٣",
                    color = c.muted, fontSize = 13.sp,
                )
            }
        }
        DhikrCounter(
            count = counts[pick] % 33, target = 33, done = false, onTap = { tap() },
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 28.dp),
        )
    }
}
