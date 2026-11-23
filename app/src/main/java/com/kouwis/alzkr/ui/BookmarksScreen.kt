@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.kouwis.alzkr.ui

import com.kouwis.alzkr.data.t
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kouwis.alzkr.data.AppFonts
import com.kouwis.alzkr.data.AppPrefs
import com.kouwis.alzkr.data.ArabicText.num
import com.kouwis.alzkr.data.Prefs
import com.kouwis.alzkr.data.QuranData
import kotlinx.coroutines.launch

private sealed interface Mark {
    val key: String
    data class Page(override val key: String, val page: Int) : Mark
    data class Ayah(override val key: String, val s: Int, val a: Int) : Mark
}

private fun parseMark(k: String): Mark? {
    val p = k.split(":")
    return when {
        p.size == 2 && p[0] == "p" -> p[1].toIntOrNull()?.takeIf { it in 1..604 }?.let { Mark.Page(k, it) }
        p.size == 3 && p[0] == "a" -> {
            val s = p[1].toIntOrNull(); val a = p[2].toIntOrNull()
            if (s != null && a != null) Mark.Ayah(k, s, a) else null
        }
        else -> null
    }
}

/** Bookmarks: whole pages (set from the mushaf's top bar) and single ayahs (from the ayah bar). */
@Composable
fun BookmarksScreen(q: QuranData, prefs: AppPrefs, store: Prefs, nav: Nav) {
    val c = LocalColors.current
    val scope = rememberCoroutineScope()
    val marks = prefs.bookmarks.mapNotNull(::parseMark)

    Column(
        Modifier.fillMaxSize().background(c.bg).windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.statusBarsIgnoringVisibility).windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.navigationBarsIgnoringVisibility).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RoundIconButton(Ic.back, t("رجوع"), { nav.back() })
            Column {
                Text(t("العلامات"), color = c.text, fontSize = 30.sp, fontFamily = AppFonts.naskhBold)
                Text(if (marks.isEmpty()) t("لا توجد علامات") else t("{0} علامة", num(marks.size)), color = c.muted, fontSize = 13.sp)
            }
            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            // favourites can no longer be added; the ones saved before are still reachable from here
            if (prefs.favorites.isNotEmpty()) RoundIconButton(Ic.heart, t("المفضلة"), { nav.go(Screen.Favorites) }, tint = c.heart)
        }
        if (marks.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    t("لوضع علامة على صفحة: افتح المصحف، والمس الشاشة، ثم اضغط زر العلامة في الأعلى.\n\nلوضع علامة على آية: اضغط مطوّلًا على الآية واختر «علامة»."),
                    color = c.muted, fontSize = 15.sp, textAlign = TextAlign.Center, lineHeight = 26.sp,
                )
            }
            return@Column
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(marks, key = { it.key }) { m ->
              SwipeToDelete(onDelete = { scope.launch { store.toggleBookmark(m.key) } }) {
                CardBox(Modifier.fillMaxWidth()) {
                    when (m) {
                        is Mark.Page -> Row(
                            Modifier.clickable { nav.go(Screen.Mushaf(m.page)) }.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(
                                Ic.bookmarkFilled, t("إزالة العلامة"), tint = c.gold,
                                modifier = Modifier.size(22.dp).springClick(haptic = Haptic.Delete) { scope.launch { store.toggleBookmark(m.key) } },
                            )
                            Column(Modifier.weight(1f)) {
                                Text(t("صفحة {0}", num(m.page)), color = c.text, fontSize = 20.sp, fontFamily = AppFonts.naskhBold)
                                Text(
                                    t("{0} · الجزء {1} · الحزب {2}", q.surahName(q.surahOfPage(m.page)), num(q.juzOfPage(m.page)), num(q.hizbOfPage(m.page))),
                                    color = c.muted, fontSize = 12.sp,
                                )
                            }
                            Text(t("صفحة"), color = c.primary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                        is Mark.Ayah -> Column(
                            Modifier.clickable { nav.go(Screen.Mushaf(q.pageOf(m.s, m.a), "${m.s}:${m.a}", highlight = true)) }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Icon(
                                    Ic.bookmarkFilled, t("إزالة العلامة"), tint = c.gold,
                                    modifier = Modifier.size(22.dp).springClick(haptic = Haptic.Delete) { scope.launch { store.toggleBookmark(m.key) } },
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(t("{0} · آية {1}", q.surahName(m.s), num(m.a)), color = c.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        t("صفحة {0} · الجزء {1}", num(q.pageOf(m.s, m.a)), num(q.juzOfPage(q.pageOf(m.s, m.a)))),
                                        color = c.muted, fontSize = 12.sp,
                                    )
                                }
                                Text(t("آية"), color = c.primary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                            QuranVerse(q, m.s, m.a, fontSize = 24.sp)
                        }
                    }
                }
              }
            }
        }
    }
}
