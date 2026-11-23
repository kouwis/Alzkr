package com.kouwis.alzkr.ui

import com.kouwis.alzkr.data.t
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.Typeface
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kouwis.alzkr.data.AppFonts
import com.kouwis.alzkr.data.AppPrefs
import com.kouwis.alzkr.data.ArabicText.num
import com.kouwis.alzkr.data.PageFonts
import com.kouwis.alzkr.data.Prefs
import com.kouwis.alzkr.data.QuranData
import kotlinx.coroutines.launch

/** The page fonts a verse's words need, loaded off the main thread. */
@Composable
private fun verseFonts(q: QuranData, s: Int, a: Int): Map<Int, android.graphics.Typeface>? {
    val pages = remember(s, a) { q.verseWords(s, a).map { it.first }.toSet() }
    val fonts by produceState(
        pages.mapNotNull { p -> PageFonts.cached(p)?.let { p to it } }.toMap().takeIf { it.size == pages.size }, s, a,
    ) {
        if (value == null) value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            pages.mapNotNull { p -> PageFonts.get(p)?.let { p to it } }.toMap()
        }
    }
    return fonts?.takeIf { it.size == pages.size }
}

/** A verse in mushaf script with the words to review in red ([marked] empty = the whole verse). */
@Composable
fun MarkedVerse(q: QuranData, s: Int, a: Int, marked: Set<Int>, fontSize: TextUnit = 24.sp) {
    val c = LocalColors.current
    val words = remember(s, a) { q.verseWords(s, a) }
    val fonts = verseFonts(q, s, a)
    if (fonts == null) {
        Text(q.simple(s, a), color = Color.Transparent, fontSize = fontSize * 0.8f, lineHeight = fontSize * 1.6f, fontFamily = AppFonts.hafs)
        return
    }
    val red = c.heart
    val text = buildAnnotatedString {
        words.forEachIndexed { i, (p, g) ->
            val wrong = i < words.lastIndex && (marked.isEmpty() || i in marked)
            withStyle(SpanStyle(fontFamily = FontFamily(Typeface(fonts.getValue(p))), color = if (wrong) red else c.ink)) { append(g) }
            if (i < words.lastIndex) append(" ")
        }
    }
    Text(text, fontSize = fontSize, lineHeight = fontSize * 1.9f, modifier = Modifier.fillMaxWidth())
}

/**
 * Pick the words of a verse you got wrong (none picked = the whole verse). [onSave] gets the words,
 * or null to take the verse off the list.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MistakePicker(q: QuranData, s: Int, a: Int, current: Set<Int>?, onDismiss: () -> Unit, onSave: (Set<Int>?) -> Unit) {
    val c = LocalColors.current
    val words = remember(s, a) { q.verseWords(s, a).dropLast(1) } // the last one is the ayah number
    var picked by remember { mutableStateOf(current ?: emptySet()) }
    val fonts = verseFonts(q, s, a)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.card,
        title = {
            Column {
                Text(t("للمراجعة · {0} {1}", q.surahName(s), num(a)), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = c.text)
                Text(t("اختر الكلمات التي تحتاج إلى مراجعتها، أو لا تختر شيئًا لتحديد الآية كلها"), fontSize = 13.sp, color = c.muted)
            }
        },
        text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
                if (fonts != null) FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    words.forEachIndexed { i, (p, g) ->
                        val on = i in picked
                        Box(
                            Modifier.clip(RoundedCornerShape(12.dp))
                                .background(if (on) c.heart.copy(alpha = 0.16f) else c.chip)
                                .border(1.dp, if (on) c.heart else Color.Transparent, RoundedCornerShape(12.dp))
                                .clickable { picked = if (on) picked - i else picked + i }
                                .padding(horizontal = 10.dp, vertical = 2.dp),
                        ) {
                            Text(
                                g, fontSize = 26.sp, color = if (on) c.heart else c.ink,
                                fontFamily = FontFamily(Typeface(fonts.getValue(p))),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(picked) }) {
                Text(if (picked.isEmpty()) t("الآية كلها") else t("حفظ {0} كلمة", num(picked.size)), color = c.primary, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Row {
                if (current != null) TextButton(onClick = { onSave(null) }) { Text(t("إزالتها من المراجعة"), color = c.heart) }
                TextButton(onClick = onDismiss) { Text(t("إلغاء"), color = c.muted) }
            }
        },
    )
}

/** «مراجعة الحفظ»: verses (and words) marked to review, newest first. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MistakesScreen(q: QuranData, prefs: AppPrefs, store: Prefs, nav: Nav) {
    val c = LocalColors.current
    val scope = rememberCoroutineScope()
    val list = prefs.mistakes.mapNotNull { (k, w) ->
        k.split(":").mapNotNull { it.toIntOrNull() }.takeIf { it.size == 2 }?.let { Triple(it[0], it[1], w) }
    }
    var editing by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    Column(
        Modifier.fillMaxSize().background(c.bg)
            .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility)
            .windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility)
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RoundIconButton(Ic.back, t("رجوع"), { nav.back() })
            Column {
                Text(t("مراجعة الحفظ"), color = c.text, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text(if (list.isEmpty()) t("لا توجد آيات للمراجعة") else t("{0} آيات للمراجعة", num(list.size)), color = c.muted, fontSize = 13.sp)
            }
        }
        if (list.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    t("اضغط مطوّلًا على أي آية في المصحف واختر «مراجعة»،\nثم حدّد الكلمات التي تحتاج إلى مراجعتها."),
                    color = c.muted, fontSize = 15.sp, textAlign = TextAlign.Center, lineHeight = 26.sp,
                )
            }
            return@Column
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(list, key = { "${it.first}:${it.second}" }) { (s, a, w) ->
                SwipeToDelete(onDelete = { scope.launch { store.setMistake("$s:$a", null) } }) {
                    CardBox(Modifier.fillMaxWidth()) {
                        Column(
                            Modifier.clickable { nav.go(Screen.Mushaf(q.pageOf(s, a), "$s:$a", highlight = true)) }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Ic.pen, null, tint = c.heart, modifier = Modifier.size(18.dp))
                                Text(
                                    "  ${q.surahName(s)} · ${num(a)}", color = c.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    if (w.isEmpty()) t("الآية كلها") else t("{0} كلمة", num(w.size)), color = c.heart, fontSize = 12.sp,
                                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { editing = s to a }.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                                Text(t("  ص {0}", num(q.pageOf(s, a))), color = c.muted, fontSize = 12.sp)
                            }
                            MarkedVerse(q, s, a, w)
                        }
                    }
                }
            }
        }
    }
    editing?.let { (s, a) ->
        val key = "$s:$a"
        MistakePicker(q, s, a, prefs.mistakes.toMap()[key], onDismiss = { editing = null }, onSave = { words ->
            scope.launch { store.setMistake(key, words) }
            editing = null
        })
    }
}
