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
                if (o.ayahRepeat > 1) "${num(r.ayahTurn)}/${num(o.ayahRepeat)}" else "",
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

/** Choosing the reciter: one card for each, his styles as choices (kept for next time). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReciterPicker(onDismiss: () -> Unit) {
    val c = LocalColors.current
    val context = LocalContext.current
    val current = Reciters.current(context).id
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(c.bg).padding(top = 18.dp, bottom = 10.dp)) {
            Text(t("اختر القارئ"), color = c.text, fontSize = 22.sp, fontFamily = AppFonts.naskhBold,
                modifier = Modifier.padding(horizontal = 22.dp).padding(bottom = 10.dp))
            LazyColumn(Modifier.heightIn(max = 560.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(Reciters.byName, key = { it.first }) { (arName, styles) ->
                    val on = styles.any { it.id == current }
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(if (on) c.primarySoft else c.card)
                            .border(1.dp, if (on) c.primary.copy(alpha = 0.6f) else c.border, RoundedCornerShape(20.dp))
                            .springClick(label = styles.first().name, pressedScale = 0.98f) {
                                if (styles.size == 1 || !on) { QuranAudio.choose(context, styles.first().id); onDismiss() }
                            }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(styles.first().name, 46.dp)
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(styles.first().name, color = c.text, fontSize = 18.sp, fontFamily = AppFonts.naskhBold, maxLines = 1)
                            FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                styles.forEach { r ->
                                    val sel = r.id == current
                                    Text(
                                        r.style, color = if (sel) c.onPrimary else c.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                        modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(if (sel) c.primary else c.chip)
                                            .springClick(label = r.style) { QuranAudio.choose(context, r.id); onDismiss() }
                                            .padding(horizontal = 12.dp, vertical = 5.dp),
                                    )
                                }
                            }
                        }
                        if (on) Box(Modifier.size(26.dp).clip(CircleShape).background(c.primary), contentAlignment = Alignment.Center) {
                            Icon(Ic.check, null, tint = c.onPrimary, modifier = Modifier.size(15.dp))
                        }
                    }
                }
            }
        }
    }
}

/**
 * «إعدادات التلاوة»: the speed, each verse's repeats, and a range (from a verse to a verse) with its
 * repeats. [startKey] is where a new range starts by default (the verse on screen).
 */
@Composable
fun PlaybackOptions(q: QuranData, startKey: String, onDismiss: () -> Unit) {
    val c = LocalColors.current
    val context = LocalContext.current
    val saved by QuranAudio.options.collectAsState()
    var o by remember {
        val p = startKey.split(":").mapNotNull { it.toIntOrNull() }
        val s0 = p.getOrElse(0) { 1 }; val a0 = p.getOrElse(1) { 1 }
        mutableStateOf(if (saved.fromS > 0) saved else saved.copy(fromS = s0, fromA = a0, toS = s0, toA = Reciters.ayahs[s0 - 1]))
    }
    var pickSurah by remember { mutableStateOf<Int?>(null) } // 0: start, 1: end
    fun save(n: PlayOptions) { o = n; QuranAudio.setOptions(context, n) }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(c.bg).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(t("إعدادات التلاوة"), color = c.text, fontSize = 22.sp, fontFamily = AppFonts.naskhBold)

            Card {
                Label(t("سرعة التلاوة"))
                val speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    speeds.forEach { v ->
                        val sel = o.speed == v
                        Text(
                            speedLabel(v), color = if (sel) c.onPrimary else c.text, fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(if (sel) c.primary else c.chip)
                                .springClick(label = speedLabel(v)) { save(o.copy(speed = v)) }.padding(vertical = 9.dp),
                        )
                    }
                }
            }

            Card {
                Label(t("تكرار كل آية"))
                Stepper(if (o.ayahRepeat <= 1) t("مرة واحدة") else t("{0} مرات", num(o.ayahRepeat)),
                    o.ayahRepeat > 1, o.ayahRepeat < 20,
                    { save(o.copy(ayahRepeat = o.ayahRepeat - 1)) }, { save(o.copy(ayahRepeat = o.ayahRepeat + 1)) })
            }

            Card {
                Label(t("تلاوة مقطع"))
                VerseChooser(t("من"), q, o.fromS, o.fromA, { pickSurah = 0 }) { a -> save(o.copy(fromA = a)) }
                VerseChooser(t("إلى"), q, o.toS, o.toA, { pickSurah = 1 }) { a -> save(o.copy(toA = a)) }
                Label(t("تكرار المقطع"))
                Stepper(when (o.rangeRepeat) { 0 -> t("بلا توقف"); 1 -> t("مرة واحدة"); else -> t("{0} مرات", num(o.rangeRepeat)) },
                    o.rangeRepeat != 1, true,
                    { save(o.copy(rangeRepeat = if (o.rangeRepeat == 0) 20 else o.rangeRepeat - 1)) },
                    { save(o.copy(rangeRepeat = if (o.rangeRepeat >= 20) 0 else if (o.rangeRepeat == 0) 0 else o.rangeRepeat + 1)) })
                val valid = o.fromS < o.toS || (o.fromS == o.toS && o.fromA <= o.toA)
                Text(
                    if (valid) t("تشغيل المقطع") else t("بداية المقطع بعد نهايته"),
                    color = if (valid) Dark else c.muted, fontSize = 16.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp).clip(RoundedCornerShape(16.dp)).background(if (valid) Gold else c.chip)
                        .springClick(label = t("تشغيل المقطع")) { if (valid) { QuranAudio.setOptions(context, o); QuranAudio.playRange(context); onDismiss() } }
                        .padding(vertical = 13.dp),
                )
            }
        }
    }
    pickSurah?.let { which ->
        SurahList(q, onDismiss = { pickSurah = null }) { n ->
            save(if (which == 0) o.copy(fromS = n, fromA = 1) else o.copy(toS = n, toA = Reciters.ayahs[n - 1]))
            pickSurah = null
        }
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
private fun Stepper(value: String, canDown: Boolean, canUp: Boolean, onDown: () -> Unit, onUp: () -> Unit) {
    val c = LocalColors.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Round("−", canDown, onDown)
        Text(value, color = c.text, fontSize = 17.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
        Round("+", canUp, onUp)
    }
}

@Composable
private fun Round(sign: String, on: Boolean, onClick: () -> Unit) {
    val c = LocalColors.current
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(if (on) c.primarySoft else c.chip)
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
        Text(label, color = c.muted, fontSize = 14.sp, modifier = Modifier.width(30.dp))
        Text(
            q.surahName(s), color = c.text, fontSize = 15.sp, fontFamily = AppFonts.naskhBold, maxLines = 1, textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(c.chip).springClick(label = label, onClick = onSurah)
                .padding(vertical = 9.dp, horizontal = 8.dp),
        )
        Round("−", a > 1) { onAyah(a - 1) }
        Text(num(a), color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.width(36.dp))
        Round("+", a < max) { onAyah(a + 1) }
    }
}

@Composable
private fun SurahList(q: QuranData, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val c = LocalColors.current
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(c.bg).padding(vertical = 10.dp)) {
            LazyColumn(Modifier.heightIn(max = 520.dp)) {
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
