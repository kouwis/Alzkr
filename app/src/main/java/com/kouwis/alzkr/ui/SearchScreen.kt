@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.kouwis.alzkr.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kouwis.alzkr.data.ArabicText
import com.kouwis.alzkr.data.ArabicText.num
import com.kouwis.alzkr.data.QuranData

private const val MAX_VERSES = 300

@Composable
fun SearchScreen(q: QuranData, nav: Nav) {
    val c = LocalColors.current
    var query by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    val needle = remember(query) { ArabicText.normalize(query) }
    // a page number ("٢٥"، "25"، "ص ٢٥"، "صفحة 25") goes straight to that page
    val pageHit = remember(query) {
        val digits = query.trim()
            .removePrefix("صفحة").removePrefix("صفحه").removePrefix("ص").trim()
            .map { ch ->
                when (ch) {
                    in '٠'..'٩' -> '0' + (ch - '٠')
                    in '۰'..'۹' -> '0' + (ch - '۰')
                    else -> ch
                }
            }.joinToString("")
        digits.takeIf { it.isNotEmpty() && it.all(Char::isDigit) && it.length <= 3 }?.toInt()?.takeIf { it in 1..604 }
    }
    val surahHits = remember(needle) {
        if (needle.isEmpty()) emptyList()
        else q.surahs.filter { ArabicText.normalize(it.name).contains(needle) || it.n.toString() == needle }
    }
    val verseHits = remember(needle) {
        if (needle.length < 2) emptyList()
        else buildList {
            outer@ for (s in 0 until 114) {
                val sura = q.verses.clean[s]
                for (a in sura.indices) {
                    if (sura[a].contains(needle)) {
                        add(s + 1 to a + 1)
                        if (size >= MAX_VERSES) break@outer
                    }
                }
            }
        }
    }

    Column(
        Modifier.fillMaxSize().background(c.bg).windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.statusBarsIgnoringVisibility).windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.navigationBarsIgnoringVisibility).imePadding()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RoundIconButton(Ic.back, "رجوع", { nav.back() })
            Row(
                Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(c.card)
                    .border(1.5.dp, c.primary, RoundedCornerShape(24.dp))
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Ic.search, null, tint = c.muted, modifier = Modifier.size(18.dp))
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(color = c.text, fontSize = 16.sp),
                    cursorBrush = SolidColor(c.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { pageHit?.let { nav.openFromSearch(Screen.Mushaf(it)) } }),
                    modifier = Modifier.weight(1f).focusRequester(focus),
                    decorationBox = { inner ->
                        if (query.isEmpty()) Text("سورة، كلمة، أو رقم صفحة", color = c.muted, fontSize = 16.sp)
                        inner()
                    },
                )
                if (query.isNotEmpty()) {
                    Icon(Ic.close, "مسح", tint = c.muted, modifier = Modifier.size(18.dp).clickable { query = "" })
                }
            }
        }

        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (pageHit != null) {
                item { SectionTitle("الصفحة") }
                item(key = "page") {
                    CardBox(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.clickable { nav.openFromSearch(Screen.Mushaf(pageHit)) }.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            NumberBadge(num(pageHit))
                            Column(Modifier.weight(1f)) {
                                Text("صفحة ${num(pageHit)}", color = c.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                Text("سورة ${q.surahName(q.surahOfPage(pageHit))} · الجزء ${num(q.juzOfPage(pageHit))}", color = c.muted, fontSize = 12.sp)
                            }
                            Icon(Ic.forward, null, tint = c.muted, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
            if (needle.isNotEmpty() && pageHit == null && surahHits.isEmpty() && verseHits.isEmpty()) {
                item { Text("لا توجد نتائج", color = c.muted, modifier = Modifier.padding(top = 24.dp).fillMaxWidth()) }
            }
            if (surahHits.isNotEmpty()) {
                item { SectionTitle("السور") }
                items(surahHits, key = { "s${it.n}" }) { s ->
                    CardBox(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.clickable { nav.openFromSearch(Screen.Mushaf(s.page)) }.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            NumberBadge(num(s.n))
                            Column(Modifier.weight(1f)) {
                                Text("سورة ${s.name}", color = c.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                Text("${s.type} · ${num(s.ayahs)} آية", color = c.muted, fontSize = 12.sp)
                            }
                            Text("ص ${num(s.page)}", color = c.muted, fontSize = 13.sp)
                        }
                    }
                }
            }
            if (verseHits.isNotEmpty()) {
                item {
                    val more = if (verseHits.size >= MAX_VERSES) "أول ${num(MAX_VERSES)} نتيجة" else "${num(verseHits.size)} آية"
                    SectionTitle("الآيات · $more")
                }
                items(verseHits, key = { "v${it.first}:${it.second}" }) { (s, a) ->
                    CardBox(Modifier.fillMaxWidth()) {
                        Column(
                            Modifier.clickable { nav.openFromSearch(Screen.Mushaf(q.pageOf(s, a), "$s:$a", highlight = true)) }.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Row {
                                Text("${q.surahName(s)} · ${num(a)}", color = c.primary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                Text("ص ${num(q.pageOf(s, a))}", color = c.muted, fontSize = 12.sp)
                            }
                            QuranVerse(q, s, a, fontSize = 22.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(t: String) {
    Text(t, color = LocalColors.current.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
}

/** Highlights words whose normalised form contains the query. */
private fun highlighted(text: String, needle: String, bg: androidx.compose.ui.graphics.Color) = buildAnnotatedString {
    val words = text.split(" ")
    words.forEachIndexed { i, w ->
        if (needle.isNotEmpty() && ArabicText.normalize(w).contains(needle)) {
            withStyle(SpanStyle(background = bg)) { append(w) }
        } else append(w)
        if (i < words.lastIndex) append(" ")
    }
}
