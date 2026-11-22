package com.kouwis.alzkr.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kouwis.alzkr.data.Adhkar
import com.kouwis.alzkr.data.AppFonts
import com.kouwis.alzkr.data.ArabicText
import com.kouwis.alzkr.data.ArabicText.num
import com.kouwis.alzkr.data.QuranData

private data class AdhkarHit(val sectionId: String, val section: String, val index: Int, val title: String, val body: String, val count: Int)

/** Search all the adhkar (their words, and the verses in them); a result opens the dhikr itself. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun AdhkarSearchScreen(q: QuranData, nav: Nav) = AdhkarTheme {
    val c = LocalColors.current
    val context = LocalContext.current
    val sections by produceState(Adhkar.peek()) { value = Adhkar.load(context) }
    var query by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    // one searchable text per dhikr: its words without tashkeel, plus the verses it holds
    val index = remember(sections) {
        sections?.values?.flatMap { sec ->
            sec.items.mapIndexed { i, d ->
                val body = d.parts.joinToString(" ") { p ->
                    when {
                        p.q != null -> (p.q[1]..p.q[2]).joinToString(" ") { q.simple(p.q[0], it) }
                        p.u != null -> p.u
                        else -> p.t.orEmpty()
                    }
                }
                val title = d.label.ifBlank {
                    d.parts.firstNotNullOfOrNull { it.q }?.takeIf { d.parts.none { p -> p.t != null && !p.t.startsWith("بِسْمِ") } }
                        ?.let { (s, a1, a2) -> "سورة ${q.surahName(s)} " + if (a1 == a2) num(a1) else "${num(a1)}–${num(a2)}" } ?: ""
                }
                AdhkarHit(sec.id, sec.title, i, title, body, d.count) to ArabicText.normalize("$title $body ${d.fadl}")
            }
        }.orEmpty()
    }
    val needle = remember(query) { ArabicText.normalize(query) }
    val hits = remember(needle, index) {
        if (needle.length < 2) emptyList() else index.filter { it.second.contains(needle) }.map { it.first }.take(80)
    }

    Column(
        Modifier.fillMaxSize().background(c.bg)
            .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility)
            .windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility).imePadding()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RoundIconButton(Ic.back, "رجوع", { nav.back() })
            Row(
                Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(24.dp)).background(c.card)
                    .border(1.5.dp, c.primary, RoundedCornerShape(24.dp)).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Ic.search, null, tint = c.muted, modifier = Modifier.size(18.dp))
                BasicTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    textStyle = TextStyle(color = c.text, fontSize = 16.sp),
                    cursorBrush = SolidColor(c.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier.weight(1f).focusRequester(focus),
                    decorationBox = { inner ->
                        if (query.isEmpty()) Text("ابحث في الأذكار والأدعية", color = c.muted, fontSize = 16.sp)
                        inner()
                    },
                )
                if (query.isNotEmpty()) Icon(Ic.close, "مسح", tint = c.muted, modifier = Modifier.size(18.dp).clickable { query = "" })
            }
        }
        when {
            needle.length < 2 -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text("اكتب كلمة من الذكر، مثل «الكرب» أو «أصبحنا»", color = c.muted, fontSize = 15.sp, textAlign = TextAlign.Center)
            }
            hits.isEmpty() -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text("لا يوجد ذكر فيه «$query»", color = c.muted, fontSize = 15.sp)
            }
            else -> {
                Text("${num(hits.size)} نتيجة", color = c.muted, fontSize = 13.sp)
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(hits, key = { "${it.sectionId}:${it.index}" }) { h ->
                        CardBox(Modifier.fillMaxWidth()) {
                            Column(
                                Modifier.clickable { nav.go(Screen.Adhkar(h.sectionId, h.index)) }.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        h.section, color = c.primary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(c.primarySoft).padding(horizontal = 8.dp, vertical = 3.dp),
                                    )
                                    if (h.title.isNotBlank()) Text(h.title, color = c.muted, fontSize = 12.sp, maxLines = 1, modifier = Modifier.weight(1f))
                                    if (h.count > 1) Text("× ${num(h.count)}", color = c.muted, fontSize = 12.sp)
                                }
                                Text(
                                    h.body, color = c.text, fontSize = 17.sp, lineHeight = 30.sp, fontFamily = AppFonts.naskh,
                                    maxLines = 3, overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
