package com.kouwis.alzkr.ui

import com.kouwis.alzkr.data.t
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
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import com.kouwis.alzkr.data.Adhkar
import com.kouwis.alzkr.data.AppPrefs
import com.kouwis.alzkr.data.Prefs
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
fun AdhkarReaderScreen(q: QuranData, prefs: AppPrefs, store: Prefs, nav: Nav, id: String, start: Int = 0) = AdhkarTheme {
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
            Reader(q, store, nav, sec, start)
        }
    }
}

/** The ornate brackets kept facing the verse they hold (see ArabicText.brackets). */
private fun facingBrackets(s: String) = com.kouwis.alzkr.data.ArabicText.brackets(s)

/** The dhikr as plain text, for copying and sharing. */
private fun plainText(q: QuranData, d: Dhikr, sec: AdhkarSection): String = buildString {
    d.parts.forEach { p ->
        when {
            p.q != null -> {
                val (s, a1, a2) = p.q
                append((a1..a2).joinToString(" ") { "${q.simple(s, it)} ﴿${num(it)}﴾" })
                append(t(" [سورة {0}]", q.surahName(s)))
            }
            p.u != null -> append("﴿${p.u}﴾")
            p.t != null -> append(p.t)
        }
        append("\n")
    }
    if (d.count > 1) append(t("({0} مرات)\n", num(d.count)))
    if (d.fadl.isNotBlank()) append(t("\nفضله: {0}\n", d.fadl))
    if (d.src.isNotBlank()) append("\n${d.src.replace(" — ", "\n")}\n")
    append(t("\n— {0} · تطبيق الذِّكر", sec.title))
}

/** How long the dhikr is, to pick a text size that fits the page without scrolling. */
private fun Dhikr.weight(): Int = parts.sumOf { p ->
    when {
        p.q != null -> (p.q[2] - p.q[1] + 1) * 90
        p.u != null -> p.u.length
        else -> p.t!!.length
    }
}

@Composable
private fun Reader(q: QuranData, store: Prefs, nav: Nav, sec: AdhkarSection, start: Int) {
    val c = LocalColors.current
    val view = LocalView.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val n = sec.items.size
    val counts = remember(sec.id) { mutableStateListOf(*Array(n) { 0 }) }
    val pager = rememberPagerState(initialPage = start.coerceIn(0, n - 1)) { n }
    var finished by remember(sec.id) { mutableStateOf(false) }
    var showList by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<Dhikr?>(null) }
    fun target(i: Int) = sec.items[i].count
    fun complete(i: Int) = target(i) > 0 && counts[i] >= target(i)
    val doneCount = (0 until n).count { complete(it) }

    fun afterComplete(i: Int) {
        view.haptic(Haptic.Confirm)
        val next = ((i + 1 until n) + (0 until i)).firstOrNull { !complete(it) }
        scope.launch {
            delay(420)
            if (next == null) {
                finished = true
                store.markAdhkarDone(sec.id)
            } else pager.animateScrollToPage(next)
        }
    }

    fun tap(i: Int) {
        if (finished) return
        val t = target(i)
        if (t == 0) { counts[i] = counts[i] + 1; view.haptic(Haptic.Tick); return }
        if (counts[i] >= t) {
            // already said: take the reader on to the next one still to say
            val next = ((i + 1 until n) + (0 until i)).firstOrNull { !complete(it) }
            if (next != null) scope.launch { pager.animateScrollToPage(next) }
            return
        }
        counts[i] = counts[i] + 1
        if (counts[i] < t) view.haptic(Haptic.Tick) else afterComplete(i)
    }

    fun completeNow(i: Int) {
        if (finished || target(i) == 0 || complete(i)) return
        counts[i] = target(i)
        afterComplete(i)
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // header
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                RoundIconButton(Ic.back, t("رجوع"), { nav.back() })
                Column(Modifier.weight(1f)) {
                    Text(sec.title, color = c.text, fontSize = 24.sp, fontFamily = AppFonts.naskhBold, maxLines = 1)
                    Text(
                        if (n > 1) t("{0} من {1}", num(pager.currentPage + 1), num(n)) else t("ذكر واحد"),
                        color = c.muted, fontSize = 13.sp,
                    )
                }
                if (counts.any { it > 0 }) {
                    RoundIconButton(Ic.refresh, t("البدء من جديد"), {
                        for (k in 0 until n) counts[k] = 0
                        finished = false
                        scope.launch { pager.animateScrollToPage(0) }
                    }, haptic = Haptic.Delete)
                }
                if (n > 1) RoundIconButton(Ic.lines, t("قائمة الأذكار"), { showList = true }, tint = c.primary)
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
                val d = sec.items[i]
                DhikrPage(
                    q, d, complete(i),
                    onInfo = { info = d },
                    onCopy = {
                        clipboard.setText(AnnotatedString(plainText(q, d, sec)))
                        Toast.makeText(context, t("تم النسخ"), Toast.LENGTH_SHORT).show()
                    },
                    onShare = {
                        val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, plainText(q, d, sec)) }
                        context.startActivity(Intent.createChooser(send, t("مشاركة الذكر")))
                    },
                    modifier = Modifier.pointerInput(i) { detectTapGestures { tap(i) } },
                )
            }

            // the counter, with «reset this one» and «I've said it» beside it
            val i = pager.currentPage.coerceIn(0, n - 1)
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp, start = 24.dp, end = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    if (counts[i] > 0) SmallAction(Ic.refresh, t("تصفير")) { counts[i] = 0; finished = false; view.haptic(Haptic.Delete) }
                }
                DhikrCounter(count = counts[i], target = target(i), done = complete(i), onTap = { tap(i) })
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    if (target(i) > 0 && !complete(i)) SmallAction(Ic.check, t("أتممته")) { completeNow(i) }
                }
            }
        }

        AnimatedVisibility(showList, enter = fadeIn(tween(200)), exit = fadeOut(tween(160))) {
            AdhkarList(q, sec, counts, current = pager.currentPage, onClose = { showList = false }) { k ->
                showList = false
                scope.launch { pager.scrollToPage(k) }
            }
        }

        AnimatedVisibility(finished, enter = fadeIn(tween(300)) + scaleIn(tween(380), initialScale = 0.94f), exit = fadeOut(tween(200))) {
            Finished(sec.done, onBack = { nav.back() }, onAgain = {
                for (k in 0 until n) counts[k] = 0
                finished = false
                scope.launch { pager.scrollToPage(0) }
            })
        }
    }

    info?.let { d ->
        AlertDialog(
            onDismissRequest = { info = null },
            containerColor = c.card,
            confirmButton = { TextButton(onClick = { info = null }) { Text(t("تم"), color = c.primary) } },
            title = { Text(t("الفضل والتخريج"), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = c.text) },
            text = {
                Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (d.fadl.isNotBlank()) Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.goldSoft).padding(12.dp),
                    ) {
                        Text(t("فضله"), color = c.goldText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(d.fadl, color = c.text, fontSize = 15.sp, lineHeight = 26.sp)
                    }
                    if (d.src.isNotBlank()) Column {
                        Text(t("التخريج"), color = c.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(d.src.replace(" — ", "\n"), color = c.text, fontSize = 14.sp, lineHeight = 24.sp)
                    }
                }
            },
        )
    }
}

@Composable
private fun SmallAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    val c = LocalColors.current
    Column(
        Modifier.clip(RoundedCornerShape(16.dp)).springClick(label = label, haptic = Haptic.None, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(c.card).border(1.dp, c.border, CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = c.primary, modifier = Modifier.size(20.dp))
        }
        Text(label, color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

/** The dhikr on the page itself: centred, sized to fit; it only scrolls when it is really long. */
@Composable
private fun DhikrPage(
    q: QuranData, d: Dhikr, done: Boolean,
    onInfo: () -> Unit, onCopy: () -> Unit, onShare: () -> Unit,
    modifier: Modifier,
) {
    val c = LocalColors.current
    val textColor by androidx.compose.animation.animateColorAsState(if (done) c.muted else c.text, tween(300), label = "ink")
    val w = remember(d) { d.weight() }
    val textSize = when { w < 120 -> 26.sp; w < 260 -> 23.sp; w < 450 -> 21.sp; w < 700 -> 19.sp; else -> 18.sp }
    val quranSize = when { w < 200 -> 27.sp; w < 450 -> 24.sp; w < 800 -> 22.sp; else -> 21.sp }
    androidx.compose.foundation.layout.BoxWithConstraints(modifier.fillMaxSize()) {
        val minH = maxHeight
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        ) {
            Column(
                Modifier.fillMaxWidth().heightIn(min = minH).padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            ) {
                val onlyQuran = d.parts.any { it.q != null } && d.parts.all { it.q != null || it.t?.startsWith("بِسْمِ") == true }
                val caption = d.label.ifBlank {
                    if (onlyQuran) d.parts.firstNotNullOf { it.q }.let { (s, a1, a2) ->
                        t("سورة {0} · ", q.surahName(s)) + if (a1 == a2) t("الآية {0}", num(a1)) else t("الآيات {0}–{1}", num(a1), num(a2))
                    } else ""
                }
                AdhkarIc.byName(d.icon)?.let { pic ->
                    Box(Modifier.size(76.dp).clip(CircleShape).background(c.primarySoft), contentAlignment = Alignment.Center) {
                        Icon(pic, null, tint = c.primary, modifier = Modifier.size(38.dp))
                    }
                }
                if (caption.isNotBlank()) {
                    Text(
                        caption, color = c.primary, fontSize = if (d.icon.isNotBlank()) 18.sp else 13.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(c.primarySoft).padding(horizontal = 12.dp, vertical = 5.dp),
                    )
                }
                d.parts.forEach { p ->
                    when {
                        p.q != null -> QuranPassage(q, p.q[0], p.q[1], p.q[2], fontSize = quranSize, color = if (done) c.muted else c.ink)
                        p.u != null -> Text(
                            // the Quran font has no ornate brackets: they come from the naskh font, facing the verse
                            androidx.compose.ui.text.buildAnnotatedString {
                                val br = androidx.compose.ui.text.SpanStyle(fontFamily = AppFonts.naskh)
                                pushStyle(br); append(com.kouwis.alzkr.data.ArabicText.brackets("\uFD3F")); pop()
                                append(p.u)
                                pushStyle(br); append(com.kouwis.alzkr.data.ArabicText.brackets("\uFD3E")); pop()
                            }, color = if (done) c.muted else c.ink, fontSize = textSize * 1.05f, lineHeight = textSize * 2f,
                            fontFamily = AppFonts.hafs, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                        )
                        p.t != null -> Text(
                            facingBrackets(p.t), color = textColor, fontSize = textSize, lineHeight = textSize * 1.85f, fontFamily = AppFonts.naskh,
                            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                if (d.note.isNotBlank()) {
                    Text(d.note, color = c.muted, fontSize = 14.sp, lineHeight = 24.sp, textAlign = TextAlign.Center)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (d.fadl.isNotBlank() || d.src.isNotBlank()) {
                        Text(
                            if (d.fadl.isNotBlank()) t("الفضل والتخريج") else t("التخريج"), color = c.goldText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(c.goldSoft)
                                .springClick(label = t("الفضل والتخريج"), pressedScale = 0.95f, onClick = onInfo)
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                        )
                    }
                    MiniIcon(Ic.copy, t("نسخ"), onCopy)
                    MiniIcon(Ic.share, t("مشاركة"), onShare)
                }
            }
        }
    }
}

@Composable
private fun MiniIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    val c = LocalColors.current
    Box(
        Modifier.size(34.dp).clip(CircleShape).background(c.chip).springClick(label = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = c.muted, modifier = Modifier.size(16.dp)) }
}

/** All the adhkar of the section in a list: tick for the ones said, tap one to go to it. */
@Composable
private fun AdhkarList(q: QuranData, sec: AdhkarSection, counts: List<Int>, current: Int, onClose: () -> Unit, onPick: (Int) -> Unit) {
    val c = LocalColors.current
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)).pointerInput(Unit) { detectTapGestures { onClose() } },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier.fillMaxWidth().fillMaxHeight(0.78f)
                .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)).background(c.bg)
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(top = 10.dp),
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).size(width = 40.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(c.border))
            Text(
                sec.title, color = c.text, fontSize = 20.sp, fontFamily = AppFonts.naskhBold,
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 10.dp),
            )
            val listState = androidx.compose.foundation.lazy.rememberLazyListState(initialFirstVisibleItemIndex = (current - 2).coerceAtLeast(0))
            androidx.compose.foundation.lazy.LazyColumn(
                state = listState,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(sec.items.size) { k ->
                    val d = sec.items[k]
                    val said = d.count > 0 && counts[k] >= d.count
                    val title = d.label.ifBlank {
                        d.parts.firstOrNull { it.t != null && !it.t.startsWith("بِسْمِ") }?.t?.let { if (it.length > 70) it.take(70) + "…" else it }
                            ?: d.parts.firstNotNullOfOrNull { it.q }?.let { (s, a1, a2) ->
                                t("سورة {0} ", q.surahName(s)) + if (a1 == a2) num(a1) else "${num(a1)}–${num(a2)}"
                            } ?: ""
                    }
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                            .background(if (k == current) c.primarySoft else c.card)
                            .springClick(label = title, pressedScale = 0.98f) { onPick(k) }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            Modifier.size(30.dp).clip(CircleShape).background(if (said) c.primary else c.primarySoft),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (said) Icon(Ic.check, null, tint = c.onPrimary, modifier = Modifier.size(16.dp))
                            else Text(num(k + 1), color = c.primary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            title, color = if (said) c.muted else c.text, fontSize = 15.sp, fontFamily = AppFonts.naskh,
                            maxLines = 2, modifier = Modifier.weight(1f),
                        )
                        if (d.count > 1) Text("× ${num(d.count)}", color = c.muted, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

/** A round counter: counts up from 0 to the target inside a ring that fills as you go. */
@Composable
fun DhikrCounter(count: Int, target: Int, done: Boolean, onTap: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalColors.current
    val frac by animateFloatAsState(if (target > 0) count / target.toFloat() else 0f, tween(220), label = "ring")
    val bg by androidx.compose.animation.animateColorAsState(if (done) c.primary else c.card, tween(250), label = "bg")
    Box(
        modifier.size(104.dp).clip(CircleShape).background(bg)
            .springClick(role = Role.Button, label = t("عدّ"), haptic = Haptic.None, pressedScale = 0.9f, ripple = false, onClick = onTap),
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
            Icon(Ic.check, t("تم"), tint = c.onPrimary, modifier = Modifier.size(40.dp))
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(num(count), color = c.text, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                if (target > 0) Text(t("من {0}", num(target)), color = c.muted, fontSize = 11.sp)
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
            Text(t("الحمد لله"), color = c.primary, fontSize = 40.sp, fontFamily = AppFonts.naskhBold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Pill(t("رجوع"), filled = true, onClick = onBack)
                Pill(t("من جديد"), filled = false, onClick = onAgain)
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

