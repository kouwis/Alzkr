package com.kouwis.alzkr.ui

import android.graphics.Typeface as AndroidTypeface
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import com.kouwis.alzkr.data.AppPrefs
import com.kouwis.alzkr.data.ArabicText.num
import com.kouwis.alzkr.data.PageFonts
import com.kouwis.alzkr.data.QuranData
import kotlinx.coroutines.flow.distinctUntilChanged

/** "Verse mode": every ayah on its own, one under the other, in large mushaf script. */
@Composable
fun VerseList(
    q: QuranData,
    prefs: AppPrefs,
    selected: String?,
    startKey: String,
    onVisibleVerse: (Int, Int) -> Unit,
    onSelect: (String) -> Unit,
) {
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
    LazyColumn(state = state, modifier = Modifier.fillMaxSize().background(c.paper).navigationBarsPadding()) {
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
                        .clickable { onSelect(key) }
                        .padding(horizontal = 22.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    VerseGlyphs(q, s, a)
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
    Column(Modifier.fillMaxWidth().background(c.paper).padding(horizontal = 22.dp, vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(c.primarySoft)
                .border(1.5.dp, c.primary, RoundedCornerShape(26.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text("سورة ${q.surahName(s)}", color = c.primary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        if (s != 1 && s != 9) {
            Text("بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ", color = c.ink, fontSize = 22.sp, modifier = Modifier.padding(top = 12.dp))
        }
    }
}

/** The verse in its mushaf glyphs (one font per page it spans); plain text until fonts are ready. */
@Composable
private fun VerseGlyphs(q: QuranData, s: Int, a: Int) {
    val c = LocalColors.current
    val ctx = LocalContext.current
    val chunks = q.glyphs["$s:$a"].orEmpty()
    val fonts by produceState<Map<Int, AndroidTypeface>?>(
        chunks.mapNotNull { ch -> PageFonts.cached(ch.p)?.let { ch.p to it } }.toMap().takeIf { it.size == chunks.size },
        s, a,
    ) {
        if (value == null) {
            val m = HashMap<Int, AndroidTypeface>()
            for (ch in chunks) PageFonts.get(ctx, ch.p)?.let { m[ch.p] = it }
            value = if (m.size == chunks.size && chunks.isNotEmpty()) m else null
        }
    }
    val f = fonts
    if (f == null) {
        Text(q.simple(s, a) + " ﴿${num(a)}﴾", color = c.ink, fontSize = 24.sp, lineHeight = 46.sp, modifier = Modifier.fillMaxWidth())
    } else {
        val text = remember(f) {
            buildAnnotatedString {
                chunks.forEachIndexed { i, ch ->
                    withStyle(SpanStyle(fontFamily = FontFamily(Typeface(f.getValue(ch.p))))) { append(ch.g) }
                    if (i < chunks.lastIndex) append(" ")
                }
            }
        }
        Text(text, color = c.ink, fontSize = 30.sp, lineHeight = 60.sp, modifier = Modifier.fillMaxWidth())
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
