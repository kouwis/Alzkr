package com.kouwis.alzkr.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.kouwis.alzkr.audio.PlayOptions
import com.kouwis.alzkr.audio.QuranAudio
import com.kouwis.alzkr.audio.Recitation
import com.kouwis.alzkr.audio.Reciters
import com.kouwis.alzkr.data.AppFonts
import com.kouwis.alzkr.data.ArabicText.num
import com.kouwis.alzkr.data.QuranData
import com.kouwis.alzkr.data.t

private val Gold = Color(0xFFE9C77B)
private val Dark = Color(0xFF1B1B1B)

/** The recitation playing: the reciter (tap to change him), the verse, the options, previous / play-pause / next, close. */
@Composable
fun QuranPlayerBar(q: QuranData, r: Recitation, onReciter: () -> Unit, onOptions: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val reciter = Reciters.byId(r.reciter)
    val o by QuranAudio.options.collectAsState()
    Column(
        modifier.windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 12.dp, vertical = 10.dp).fillMaxWidth()
            .shadow(10.dp, RoundedCornerShape(24.dp)).clip(RoundedCornerShape(24.dp)).background(Color(0xFF1E2A28))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).springClick(label = t("القارئ"), pressedScale = 0.97f, onClick = onReciter)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(reciter.name, 38.dp)
                Column(Modifier.padding(start = 10.dp)) {
                    Text(reciter.name, color = Color.White, fontSize = 15.sp, fontFamily = AppFonts.naskhBold, maxLines = 1)
                    Text(t("{0} · الآية {1}", q.surahName(r.s), num(r.a)), color = Gold, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
            PlayerButton(Ic.sliders, t("إعدادات التلاوة"), onOptions)
            PlayerButton(Ic.close, t("إغلاق")) { QuranAudio.stop(context) }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
            // what is set: the speed and the repeats
            Text(speedLabel(o.speed), color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.width(56.dp).clip(RoundedCornerShape(10.dp)).springClick(label = t("السرعة"), onClick = onOptions).padding(vertical = 6.dp),
                textAlign = TextAlign.Center)
            PlayerButton(Ic.nextTrack, t("الآية السابقة")) { QuranAudio.previous(context) }
            Box(
                Modifier.size(52.dp).clip(CircleShape).background(Gold).springClick(label = t("تشغيل")) { QuranAudio.toggle(context) },
                contentAlignment = Alignment.Center,
            ) {
                if (r.loading && r.playing) CircularProgressIndicator(color = Dark, strokeWidth = 2.5.dp, modifier = Modifier.size(22.dp))
                else Icon(if (r.playing) Ic.pause else Ic.play, null, tint = Dark, modifier = Modifier.size(24.dp))
            }
            PlayerButton(Ic.prevTrack, t("الآية التالية")) { QuranAudio.next(context) }
            Text(
                when { o.ayahRepeat == 0 -> "${num(r.ayahTurn)}/∞"; o.ayahRepeat > 1 -> "${num(r.ayahTurn)}/${num(o.ayahRepeat)}"; else -> "" },
                color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.width(56.dp), textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun PlayerButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(Modifier.size(42.dp).clip(CircleShape).springClick(label = label, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, label, tint = Color.White, modifier = Modifier.size(20.dp))
    }
}

private fun speedLabel(v: Float): String =
    "×" + com.kouwis.alzkr.data.ArabicText.digitsOf(String.format(java.util.Locale.US, "%.2f", v).trimEnd('0').trimEnd('.'))

/** A round mark with the reciter's first letter, in a colour of his own. */
@Composable
private fun Avatar(name: String, size: androidx.compose.ui.unit.Dp) {
    val hues = listOf(Color(0xFF2E7D6B) to Color(0xFF1B4D43), Color(0xFF3B5BA5) to Color(0xFF22386E), Color(0xFFB0773A) to Color(0xFF6E4520),
        Color(0xFF8E4A8C) to Color(0xFF552B54), Color(0xFF4A7FA0) to Color(0xFF2A4C63), Color(0xFFA0503F) to Color(0xFF5F2D23))
    val (a, b) = hues[Math.floorMod(name.hashCode(), hues.size)]
    Box(Modifier.size(size).clip(CircleShape).background(Brush.linearGradient(listOf(a, b))), contentAlignment = Alignment.Center) {
        Text(name.trim().take(1), color = Color.White, fontSize = (size.value * 0.42f).sp, fontFamily = AppFonts.naskhBold)
    }
}

/**
 * A dialog that always fits the screen, whatever its size: margins all round, never wider than a
 * phone's width, never taller than the room left; its middle scrolls when it must.
 */
@Composable
fun SafeDialog(onDismiss: () -> Unit, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val c = LocalColors.current
    Dialog(onDismissRequest = onDismiss, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        androidx.compose.foundation.layout.BoxWithConstraints(
            Modifier.fillMaxSize()
                .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null, onClick = onDismiss)
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = 16.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier.widthIn(max = 520.dp).fillMaxWidth().heightIn(max = maxHeight)
                    .shadow(16.dp, RoundedCornerShape(28.dp)).clip(RoundedCornerShape(28.dp)).background(c.bg)
                    .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {},
                content = content,
            )
        }
    }
}

/** Choosing the reciter: one card for each, his styles as choices (kept for next time). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReciterPicker(onDismiss: () -> Unit) {
    val c = LocalColors.current
    val context = LocalContext.current
    val current = Reciters.current(context).id
    SafeDialog(onDismiss) {
        Text(t("اختر القارئ"), color = c.text, fontSize = 21.sp, fontFamily = AppFonts.naskhBold,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 8.dp))
        LazyColumn(Modifier.weight(1f, fill = false), contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 12.dp, end = 12.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(Reciters.byName, key = { it.first }) { (_, styles) ->
                val on = styles.any { it.id == current }
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(if (on) c.primarySoft else c.card)
                        .border(1.dp, if (on) c.primary.copy(alpha = 0.6f) else c.border, RoundedCornerShape(18.dp))
                        .springClick(label = styles.first().name, pressedScale = 0.98f) {
                            if (styles.size == 1 || !on) { QuranAudio.choose(context, styles.first().id); onDismiss() }
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(styles.first().name, 42.dp)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(styles.first().name, color = c.text, fontSize = 17.sp, fontFamily = AppFonts.naskhBold, maxLines = 1)
                        FlowRow(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            styles.forEach { r ->
                                val sel = r.id == current
                                Text(
                                    r.style, color = if (sel) c.onPrimary else c.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(if (sel) c.primary else c.chip)
                                        .springClick(label = r.style) { QuranAudio.choose(context, r.id); onDismiss() }
                                        .padding(horizontal = 10.dp, vertical = 4.dp),
                                )
                            }
                        }
                    }
                    if (on) Box(Modifier.size(24.dp).clip(CircleShape).background(c.primary), contentAlignment = Alignment.Center) {
                        Icon(Ic.check, null, tint = c.onPrimary, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}

/** What to listen to. */
enum class ListenMode { Verse, Page, Range, Continue }

/**
 * «الاستماع»: before anything plays, what to listen to (one verse, the whole page, from a verse to a
 * verse, or on from here), the reciter, the speed and the repeats. [pageKeys] are the verses of the
 * page on screen; [startKey] the verse to begin with (the one long-pressed, or the page's first).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ListenSheet(q: QuranData, pageKeys: List<String>, startKey: String, mode0: ListenMode, onReciter: () -> Unit, onDismiss: () -> Unit) {
    val c = LocalColors.current
    val context = LocalContext.current
    val saved by QuranAudio.options.collectAsState()
    fun key(k: String) = k.split(":").mapNotNull { it.toIntOrNull() }.let { (it.getOrElse(0) { 1 }) to (it.getOrElse(1) { 1 }) }
    val (s0, a0) = key(startKey)
    var mode by remember { mutableStateOf(mode0) }
    var verse by remember { mutableStateOf(s0 to a0) }
    var o by remember {
        // a new passage starts and ends at the verse on screen (the reader moves the end on from there)
        mutableStateOf(saved.copy(fromS = s0, fromA = a0, toS = s0, toA = a0))
    }
    var pickSurah by remember { mutableStateOf<Int?>(null) } // 0 from, 1 to, 2 the one verse
    var pickAyah by remember { mutableStateOf<Int?>(null) }  // the same, for the verse's number
    var pickCount by remember { mutableStateOf<Int?>(null) } // 0 each verse, 1 the passage
    val reciter = Reciters.current(context)
    fun keep(n: PlayOptions) { o = n; QuranAudio.setOptions(context, n) }

    SafeDialog(onDismiss) {
        Text(t("الاستماع"), color = c.text, fontSize = 21.sp, fontFamily = AppFonts.naskhBold,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 6.dp))
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // the reciter
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.card).springClick(label = t("القارئ"), pressedScale = 0.98f, onClick = onReciter)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(reciter.name, 38.dp)
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(reciter.name, color = c.text, fontSize = 16.sp, fontFamily = AppFonts.naskhBold, maxLines = 1)
                    Text(reciter.style, color = c.muted, fontSize = 12.sp)
                }
                Text(t("تغيير"), color = c.primary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }

            // what to listen to
            Card {
                Label(t("ماذا تسمع؟"))
                Choice(t("آية واحدة"), mode == ListenMode.Verse) { mode = ListenMode.Verse }
                if (mode == ListenMode.Verse) VerseChooser(t("الآية"), q, verse.first, verse.second, { pickSurah = 2 }) { pickAyah = 2 }
                Choice(t("الصفحة كاملة"), mode == ListenMode.Page) { mode = ListenMode.Page }
                Choice(t("من آية إلى آية"), mode == ListenMode.Range) { mode = ListenMode.Range }
                if (mode == ListenMode.Range) {
                    VerseChooser(t("من"), q, o.fromS, o.fromA, { pickSurah = 0 }) { pickAyah = 0 }
                    VerseChooser(t("إلى"), q, o.toS, o.toA, { pickSurah = 1 }) { pickAyah = 1 }
                }
                Choice(t("متابعة التلاوة من هنا"), mode == ListenMode.Continue) { mode = ListenMode.Continue }
            }

            Card {
                Label(t("سرعة التلاوة"))
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f).forEach { v ->
                        val sel = o.speed == v
                        Text(
                            speedLabel(v), color = if (sel) c.onPrimary else c.text, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                            modifier = Modifier.widthIn(min = 52.dp).clip(RoundedCornerShape(12.dp)).background(if (sel) c.primary else c.chip)
                                .springClick(label = speedLabel(v)) { keep(o.copy(speed = v)) }.padding(horizontal = 8.dp, vertical = 8.dp),
                        )
                    }
                }
                Label(t("تكرار كل آية"))
                Stepper(countLabel(o.ayahRepeat), o.ayahRepeat != 1, o.ayahRepeat != 0,
                    { keep(o.copy(ayahRepeat = if (o.ayahRepeat == 0) MAX_COUNT else o.ayahRepeat - 1)) },
                    { keep(o.copy(ayahRepeat = if (o.ayahRepeat >= MAX_COUNT) 0 else o.ayahRepeat + 1)) }) { pickCount = 0 }
                if (mode == ListenMode.Page || mode == ListenMode.Range) {
                    Label(if (mode == ListenMode.Page) t("تكرار الصفحة") else t("تكرار المقطع"))
                    Stepper(countLabel(o.rangeRepeat), o.rangeRepeat != 1, o.rangeRepeat != 0,
                        { keep(o.copy(rangeRepeat = if (o.rangeRepeat == 0) MAX_COUNT else o.rangeRepeat - 1)) },
                        { keep(o.copy(rangeRepeat = if (o.rangeRepeat >= MAX_COUNT) 0 else o.rangeRepeat + 1)) }) { pickCount = 1 }
                }
            }
        }

        // play: always in view at the bottom
        val valid = mode != ListenMode.Range || o.fromS < o.toS || (o.fromS == o.toS && o.fromA <= o.toA)
        Text(
            if (valid) t("تشغيل") else t("بداية المقطع بعد نهايته"),
            color = if (valid) Dark else c.muted, fontSize = 17.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
            modifier = Modifier.padding(14.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(if (valid) Gold else c.chip)
                .springClick(label = t("تشغيل")) {
                    if (!valid) return@springClick
                    when (mode) {
                        ListenMode.Verse -> { keep(o.copy(fromS = verse.first, fromA = verse.second, toS = verse.first, toA = verse.second)); QuranAudio.playRange(context) }
                        ListenMode.Page -> {
                            val (fs, fa) = key(pageKeys.firstOrNull() ?: startKey); val (ts, ta) = key(pageKeys.lastOrNull() ?: startKey)
                            keep(o.copy(fromS = fs, fromA = fa, toS = ts, toA = ta)); QuranAudio.playRange(context)
                        }
                        ListenMode.Range -> { QuranAudio.setOptions(context, o); QuranAudio.playRange(context) }
                        ListenMode.Continue -> QuranAudio.play(context, s0, a0)
                    }
                    onDismiss()
                }
                .padding(vertical = 14.dp),
        )
    }
    pickSurah?.let { which ->
        SurahList(q, onDismiss = { pickSurah = null }) { n ->
            when (which) {
                0 -> keep(o.copy(fromS = n, fromA = 1, toS = if (o.toS < n) n else o.toS, toA = if (o.toS < n) 1 else o.toA))
                1 -> keep(o.copy(toS = n, toA = if (n == o.fromS) o.fromA else 1))
                else -> verse = n to 1
            }
            pickSurah = null
        }
    }
    pickAyah?.let { which ->
        // the list opens at the verse the reader is near (the page's, or the passage's start), not at 1
        val (sNow, aNow) = when (which) { 0 -> o.fromS to o.fromA; 1 -> o.toS to o.toA; else -> verse }
        val anchor = when {
            which == 1 && o.toS == o.fromS -> o.fromA
            sNow == s0 -> minOf(aNow, a0)
            else -> aNow
        }
        NumberPicker(t("الآية"), Reciters.ayahs[sNow - 1], aNow, anchor, onDismiss = { pickAyah = null }) { n ->
            when (which) {
                0 -> keep(o.copy(fromA = n).let { if (it.toS == it.fromS && it.toA < n) it.copy(toA = n) else it })
                1 -> keep(o.copy(toA = n))
                else -> verse = sNow to n
            }
            pickAyah = null
        }
    }
    pickCount?.let { which ->
        CountPicker(if (which == 0) o.ayahRepeat else o.rangeRepeat, onDismiss = { pickCount = null }) { n ->
            keep(if (which == 0) o.copy(ayahRepeat = n) else o.copy(rangeRepeat = n))
            pickCount = null
        }
    }
}

@Composable
private fun Choice(label: String, on: Boolean, onClick: () -> Unit) {
    val c = LocalColors.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (on) c.primarySoft else Color.Transparent)
            .springClick(label = label, pressedScale = 0.98f, onClick = onClick).padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(20.dp).clip(CircleShape).border(2.dp, if (on) c.primary else c.muted, CircleShape), contentAlignment = Alignment.Center) {
            if (on) Box(Modifier.size(10.dp).clip(CircleShape).background(c.primary))
        }
        Text(label, color = c.text, fontSize = 16.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.padding(start = 10.dp))
    }
}

@Composable
private fun Card(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val c = LocalColors.current
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.card).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
}

@Composable
private fun Label(s: String) {
    Text(s, color = LocalColors.current.muted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun Stepper(value: String, canDown: Boolean, canUp: Boolean, onDown: () -> Unit, onUp: () -> Unit, onPick: () -> Unit) {
    val c = LocalColors.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Round("−", canDown, onDown)
        // tap the number for the list of numbers
        Text(value, color = c.text, fontSize = 17.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(c.chip).springClick(label = value, onClick = onPick).padding(vertical = 8.dp))
        Round("+", canUp, onUp)
    }
}

private const val MAX_COUNT = 50

private fun countLabel(n: Int) = when (n) { 0 -> t("بلا توقف") + " ∞"; 1 -> t("مرة واحدة"); else -> t("{0} مرات", num(n)) }

/** How many times: ∞ (without end), then 1 to [MAX_COUNT]. */
@Composable
private fun CountPicker(current: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val values = listOf(0) + (1..MAX_COUNT)
    NumberList(t("عدد المرات"), values, current, (values.indexOf(current) - 2).coerceAtLeast(0), { countLabel(it) }, onDismiss, onPick)
}

/** A verse's number, 1 to [max]; the list opens at [anchor]. */
@Composable
private fun NumberPicker(title: String, max: Int, current: Int, anchor: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    NumberList(title, (1..max).toList(), current, (anchor - 1).coerceIn(0, max - 1), { t("الآية {0}", num(it)) }, onDismiss, onPick)
}

@Composable
private fun NumberList(title: String, values: List<Int>, current: Int, first: Int, label: (Int) -> String, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val c = LocalColors.current
    val state = androidx.compose.foundation.lazy.rememberLazyListState(initialFirstVisibleItemIndex = first)
    SafeDialog(onDismiss) {
        Text(title, color = c.text, fontSize = 19.sp, fontFamily = AppFonts.naskhBold, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 6.dp))
        LazyColumn(Modifier.weight(1f, fill = false).heightIn(max = 460.dp), state = state,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 10.dp, end = 10.dp, bottom = 12.dp)) {
            items(values) { n ->
                val on = n == current
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (on) c.primarySoft else Color.Transparent)
                        .springClick(label = label(n)) { onPick(n) }.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(label(n), color = if (on) c.primary else c.text, fontSize = 17.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f))
                    if (on) Icon(Ic.check, null, tint = c.primary, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun Round(sign: String, on: Boolean, onClick: () -> Unit) {
    val c = LocalColors.current
    Box(
        Modifier.size(36.dp).clip(CircleShape).background(if (on) c.primarySoft else c.chip)
            .then(if (on) Modifier.springClick(label = sign, onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) { Text(sign, color = if (on) c.primary else c.muted, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
}

/** A verse: its surah (tap to choose) and its number (− / +). */
@Composable
private fun VerseChooser(label: String, q: QuranData, s: Int, a: Int, onSurah: () -> Unit, onAyah: (Int) -> Unit) {
    val c = LocalColors.current
    val max = Reciters.ayahs[(s - 1).coerceIn(0, 113)]
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, color = c.muted, fontSize = 14.sp, modifier = Modifier.widthIn(min = 30.dp))
        Text(
            q.surahName(s), color = c.text, fontSize = 15.sp, fontFamily = AppFonts.naskhBold, maxLines = 1, textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(c.chip).springClick(label = label, onClick = onSurah)
                .padding(vertical = 9.dp, horizontal = 8.dp),
        )
        // tap the number for the list of the surah's verses
        Text(num(a), color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 64.dp).clip(RoundedCornerShape(12.dp)).background(c.chip).springClick(label = num(a), onClick = { onAyah(a) })
                .padding(vertical = 9.dp, horizontal = 10.dp))
    }
}

@Composable
private fun SurahList(q: QuranData, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val c = LocalColors.current
    SafeDialog(onDismiss) {
        run {
            LazyColumn(Modifier.weight(1f, fill = false).padding(vertical = 8.dp)) {
                items((1..114).toList()) { n ->
                    Row(
                        Modifier.fillMaxWidth().springClick(label = q.surahName(n)) { onPick(n) }.padding(horizontal = 20.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(num(n), color = c.muted, fontSize = 13.sp, modifier = Modifier.width(34.dp))
                        Text(q.surahName(n), color = c.text, fontSize = 17.sp, fontFamily = AppFonts.naskhBold, modifier = Modifier.weight(1f))
                        Text(t("{0} آية", num(Reciters.ayahs[n - 1])), color = c.muted, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
