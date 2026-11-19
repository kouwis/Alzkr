package com.kouwis.alzkr.ui

import android.graphics.Typeface as AndroidTypeface
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDirection
import com.kouwis.alzkr.data.AppFonts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.Typeface
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit
import com.kouwis.alzkr.data.AppPrefs
import com.kouwis.alzkr.data.ArabicText.num
import com.kouwis.alzkr.data.PageFonts
import com.kouwis.alzkr.data.QuranData
import kotlinx.coroutines.flow.distinctUntilChanged

/** "Verse mode": every ayah on its own, one under the other, in large mushaf script. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VerseList(
    q: QuranData,
    prefs: AppPrefs,
    selected: String?,
    startKey: String,
    onVisibleVerse: (Int, Int) -> Unit,
    onLongPress: (String) -> Unit,
    onTap: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val c = LocalColors.current
    val all = q.allVerses
    val start = remember {
        val p = startKey.split(":").mapNotNull { it.toIntOrNull() }
        if (p.size == 2) q.indexOfVerse(p[0], p[1]) else 0
    }
    val state = rememberLazyListState(initialFirstVisibleItemIndex = start)
    LaunchedEffect(state) {
        snapshotFlow { state.firstVisibleItemIndex }.distinctUntilChanged().collect { i ->
            all.getOrNull(i)?.let { (s, a) -> onVisibleVerse(s, a) }
        }
    }
    LazyColumn(state = state, modifier = Modifier.fillMaxSize().background(c.paper).displayCutoutPadding()) {
        items(all.size, key = { all[it].let { (s, a) -> "$s:$a" } }) { i ->
            val (s, a) = all[i]
            val key = "$s:$a"
            Column {
                if (a == 1) SurahHeader(q, s)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            when {
                                selected == key -> c.highlight
                                i % 2 == 1 -> c.bg
                                else -> c.paper
                            },
                        )
                        .combinedClickable(
                            onClick = onTap,
                            onLongClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onLongPress(key)
                            },
                        )
                        .padding(horizontal = 22.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if ("a:$key" in prefs.bookmarks) {
                        Icon(Ic.bookmarkFilled, "علامة", tint = c.gold, modifier = Modifier.size(20.dp))
                    }
                    QuranVerse(q, s, a)
                    if (prefs.tafsirOn) Inline("التفسير", c.primary, q.tafsirOf(s, a))
                    if (prefs.translationOn) Inline("الترجمة", c.goldText, q.translationOf(s, a))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${q.surahName(s)} · ${num(a)}", color = c.primary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(c.primarySoft).padding(horizontal = 10.dp, vertical = 3.dp),
                        )
                        Box(Modifier.weight(1f))
                        Text("ص ${num(q.pageOf(s, a))}", color = c.muted, fontSize = 12.sp)
                    }
                }
                HorizontalDivider(color = c.frame2)
            }
        }
    }
}

@Composable
private fun SurahHeader(q: QuranData, s: Int) {
    val c = LocalColors.current
    Column(Modifier.fillMaxWidth().background(c.paper).padding(horizontal = 18.dp, vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        SurahFrame(s, Modifier.fillMaxWidth().height(56.dp))
        if (s != 1 && s != 9) {
            Text(AppFonts.BASMALA, color = c.ink, fontSize = 34.sp, fontFamily = bsmlFamily, modifier = Modifier.padding(top = 8.dp), style = TextStyle(textDirection = TextDirection.Ltr))
        }
    }
}

private val bsmlFamily by lazy { FontFamily(Typeface(AppFonts.bsml)) }

/** A verse in real mushaf script (QCF glyphs, one font per page it spans). */
@Composable
fun QuranVerse(
    q: QuranData,
    s: Int,
    a: Int,
    fontSize: TextUnit = 30.sp,
    color: Color = LocalColors.current.ink,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    val chunks = q.glyphs["$s:$a"].orEmpty()
    val fontsLoaded by androidx.compose.runtime.produceState<Map<Int, android.graphics.Typeface>?>(
        chunks.mapNotNull { ch -> PageFonts.cached(ch.p)?.let { ch.p to it } }.toMap().takeIf { m -> chunks.all { it.p in m } },
        s, a,
    ) {
        if (value == null) value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            chunks.mapNotNull { ch -> PageFonts.get(ch.p)?.let { ch.p to it } }.toMap()
        }
    }
    val text = remember(s, a, fontsLoaded) {
        val fonts = fontsLoaded ?: emptyMap()
        if (chunks.isEmpty() || fonts.size != chunks.map { it.p }.toSet().size) null
        else buildAnnotatedString {
            chunks.forEachIndexed { i, ch ->
                withStyle(SpanStyle(fontFamily = FontFamily(Typeface(fonts.getValue(ch.p))))) { append(ch.g) }
                if (i < chunks.lastIndex) append(" ")
            }
        }
    }
    if (text == null) {
        Text(q.simple(s, a) + " ﴿${num(a)}﴾", color = color, fontSize = fontSize * 0.8f, lineHeight = fontSize * 1.6f, fontFamily = AppFonts.hafs, modifier = modifier)
    } else {
        Text(text, color = color, fontSize = fontSize, lineHeight = fontSize * 2f, modifier = modifier)
    }
}

@Composable
private fun Inline(label: String, accent: Color, body: String) {
    val c = LocalColors.current
    Row(Modifier.fillMaxWidth()) {
        Box(Modifier.width(3.dp).height(22.dp).background(accent))
        Column(Modifier.padding(start = 10.dp)) {
            Text(label, color = accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(body.ifBlank { "غير متوفر" }, color = c.text, fontSize = 15.sp, lineHeight = 26.sp)
        }
    }
}
