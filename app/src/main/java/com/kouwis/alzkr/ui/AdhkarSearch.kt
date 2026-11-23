package com.kouwis.alzkr.ui

import com.kouwis.alzkr.data.t
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
                        ?.let { (s, a1, a2) -> t("سورة {0} ", q.surahName(s)) + if (a1 == a2) num(a1) else "${num(a1)}–${num(a2)}" } ?: ""
                }
                AdhkarHit(sec.id, sec.title, i, title, body, d.count) to ArabicText.normalize("${sec.title} $title $body ${d.fadl}")
            }
        }.orEmpty()
    }
    val needle = remember(query) { ArabicText.normalize(query) }
    val tokens = remember(needle) { searchTokens(needle) }
    fun matches(text: String) = tokens.isNotEmpty() && tokens.all { alts -> alts.any { text.contains(it) } }
    // chapters whose name answers the search come first («المطر» → «الدعاء إذا نزل المطر»)
    val chapterHits = remember(tokens, sections) {
        sections?.values?.filter { matches(ArabicText.normalize(it.title)) }.orEmpty()
    }
    val hits = remember(tokens, index) {
        if (tokens.isEmpty()) emptyList() else index.filter { matches(it.second) }.map { it.first }.take(80)
    }

    Column(
        Modifier.fillMaxSize().background(c.bg)
            .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility)
            .windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility).imePadding()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RoundIconButton(Ic.back, t("رجوع"), { nav.back() })
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
                        if (query.isEmpty()) Text(t("ابحث في الأذكار والأدعية"), color = c.muted, fontSize = 16.sp)
                        inner()
                    },
                )
                if (query.isNotEmpty()) Icon(Ic.close, t("مسح"), tint = c.muted, modifier = Modifier.size(18.dp).clickable { query = "" })
            }
        }
        when {
            tokens.isEmpty() -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text(t("اكتب ما تبحث عنه، مثل «المطر» أو «السفر» أو «السوق» أو «الكرب»"), color = c.muted, fontSize = 15.sp, textAlign = TextAlign.Center, lineHeight = 26.sp)
            }
            hits.isEmpty() && chapterHits.isEmpty() -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text(t("لا يوجد ذكر فيه «{0}»", query), color = c.muted, fontSize = 15.sp)
            }
            else -> {
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (chapterHits.isNotEmpty()) {
                        item { Text(t("الأبواب"), color = c.muted, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                        items(chapterHits, key = { "sec:" + it.id }) { sec -> ChapterRow(sec.title, sec.items.size) { nav.go(Screen.Adhkar(sec.id)) } }
                    }
                    if (hits.isNotEmpty()) item { Text(t("الأذكار · {0}", num(hits.size)), color = c.muted, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp)) }
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


private val STOP = setOf("دعاء", "الدعاء", "ادعيه", "الادعيه", "اذكار", "الاذكار", "ذكر", "الذكر", "عند", "في", "من", "اذا", "ما", "يقول", "على", "الى", "عن", "او", "و")

/** What a word in the search may also be written as in the book («التسوق» is in «دعاء دخول السوق»). */
private val SYNONYMS = mapOf(
    "تسوق" to listOf("السوق"), "التسوق" to listOf("السوق"), "سوق" to listOf("السوق"),
    "مسافر" to listOf("السفر", "المسافر"), "المسافر" to listOf("السفر", "المسافر"), "سفر" to listOf("السفر"),
    "اكل" to listOf("الطعام", "اكل"), "الاكل" to listOf("الطعام", "اكل"), "طعام" to listOf("الطعام"), "اكله" to listOf("الطعام"),
    "مطر" to listOf("المطر"), "الامطار" to listOf("المطر"), "امطار" to listOf("المطر"),
    "زواج" to listOf("المتزوج", "الزوجه"), "الزواج" to listOf("المتزوج", "الزوجه"),
    "نوم" to listOf("النوم"), "مريض" to listOf("المريض"), "مرض" to listOf("المريض", "وجعا"), "وجع" to listOf("وجعا"), "الم" to listOf("وجعا", "تالم"),
    "ميت" to listOf("الميت"), "جنازه" to listOf("الميت"), "الجنازه" to listOf("الميت"), "موت" to listOf("الميت", "المحتضر"), "قبر" to listOf("القبر", "القبور"),
    "حمام" to listOf("الخلاء"), "الحمام" to listOf("الخلاء"), "بيت" to listOf("المنزل"), "البيت" to listOf("المنزل"),
    "لبس" to listOf("الثوب"), "ملابس" to listOf("الثوب"), "الملابس" to listOf("الثوب"), "هدوم" to listOf("الثوب"),
    "رعد" to listOf("الرعد"), "ريح" to listOf("الريح"), "رياح" to listOf("الريح"), "الرياح" to listOf("الريح"),
    "عطس" to listOf("العطاس", "عطس"), "عطاس" to listOf("العطاس"), "غضب" to listOf("الغضب"),
    "حزن" to listOf("الحزن"), "هم" to listOf("الهم"), "ضيق" to listOf("الكرب", "الهم"), "كرب" to listOf("الكرب"),
    "دين" to listOf("الدين"), "ديون" to listOf("الدين"), "قرض" to listOf("الدين", "اقرض"),
    "سياره" to listOf("الركوب", "المركوب"), "ركوب" to listOf("الركوب"), "صيام" to listOf("الصائم", "صيام"), "صوم" to listOf("الصائم", "صوم"),
    "فطار" to listOf("افطار"), "الفطار" to listOf("افطار"), "سحور" to listOf("السحور"), "صلاه" to listOf("الصلاه"),
    "وضوء" to listOf("الوضوء"), "مسجد" to listOf("المسجد"), "اذان" to listOf("الاذان", "الاذان"), "خوف" to listOf("خاف", "الفزع"), "خايف" to listOf("خاف", "الفزع"),
    "عين" to listOf("بعينه", "العين"), "حسد" to listOf("بعينه", "حاسد"), "رقيه" to listOf("الرقيه", "يعوذ"), "شيطان" to listOf("الشيطان"),
    "مولود" to listOf("المولود"), "طفل" to listOf("المولود", "الاولاد"), "اولاد" to listOf("الاولاد"), "هلال" to listOf("الهلال"),
    "حج" to listOf("الحج", "الحاج"), "عمره" to listOf("العمره"), "استغفار" to listOf("الاستغفار"), "توبه" to listOf("التوبه"),
)

/** The words of a search, each with the ways it may be written; small words like «دعاء» or «عند» are left out. */
fun searchTokens(needle: String): List<List<String>> = needle.split(' ')
    .filter { it.length >= 2 && it !in STOP }
    .map { w ->
        val bare = if (w.startsWith("ال") && w.length > 3) w.drop(2) else w
        (listOf(w, bare) + SYNONYMS[w].orEmpty() + SYNONYMS[bare].orEmpty()).distinct()
    }

@Composable
private fun ChapterRow(title: String, count: Int, onClick: () -> Unit) {
    val c = LocalColors.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.card).border(1.dp, c.border, RoundedCornerShape(16.dp))
            .springClick(label = title, pressedScale = 0.98f, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(34.dp).background(c.primarySoft, RoundedCornerShape(17.dp)), contentAlignment = Alignment.Center) {
            Icon(AdhkarIc.books, null, tint = c.primary, modifier = Modifier.size(17.dp))
        }
        Text(title, color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Text(if (count == 1) t("ذكر واحد") else t("{0} أذكار", num(count)), color = c.muted, fontSize = 12.sp)
        Icon(Ic.forward, null, tint = c.muted, modifier = Modifier.size(16.dp))
    }
}

/** Every chapter of حصن المسلم and the app's sections, with a filter. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun HisnIndexScreen(nav: Nav) = AdhkarTheme {
    val c = LocalColors.current
    val context = LocalContext.current
    val sections by produceState(Adhkar.peek()) { value = Adhkar.load(context) }
    var query by rememberSaveable { mutableStateOf("") }
    val tokens = remember(query) { searchTokens(ArabicText.normalize(query)) }
    val list = remember(sections, tokens) {
        val all = sections?.values?.filter { it.group == "" || it.group == "hisn" }.orEmpty()
        if (tokens.isEmpty()) all
        else all.filter { s -> val t = ArabicText.normalize(s.title); tokens.all { alts -> alts.any { t.contains(it) } } }
    }
    Column(
        Modifier.fillMaxSize().background(c.bg)
            .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility)
            .windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility).imePadding()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RoundIconButton(Ic.back, t("رجوع"), { nav.back() })
            Column {
                Text(t("حصن المسلم"), color = c.text, fontSize = 26.sp, fontFamily = AppFonts.naskhBold)
                Text(t("{0} بابًا", num(list.size)), color = c.muted, fontSize = 13.sp)
            }
        }
        Row(
            Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(23.dp)).background(c.card)
                .border(1.dp, c.border, RoundedCornerShape(23.dp)).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Ic.search, null, tint = c.muted, modifier = Modifier.size(18.dp))
            BasicTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                textStyle = TextStyle(color = c.text, fontSize = 15.sp), cursorBrush = SolidColor(c.primary),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    if (query.isEmpty()) Text(t("ابحث في الأبواب: السفر، المطر، الطعام…"), color = c.muted, fontSize = 15.sp)
                    inner()
                },
            )
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(list, key = { it.id }) { sec -> ChapterRow(sec.title, sec.items.size) { nav.go(Screen.Adhkar(sec.id)) } }
            item { androidx.compose.foundation.layout.Spacer(Modifier.height(16.dp)) }
        }
    }
}
