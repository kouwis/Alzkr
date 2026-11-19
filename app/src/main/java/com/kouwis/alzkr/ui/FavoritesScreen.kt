package com.kouwis.alzkr.ui

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
import com.kouwis.alzkr.data.AppPrefs
import com.kouwis.alzkr.data.ArabicText.num
import com.kouwis.alzkr.data.Prefs
import com.kouwis.alzkr.data.QuranData
import kotlinx.coroutines.launch

@Composable
fun FavoritesScreen(q: QuranData, prefs: AppPrefs, store: Prefs, nav: Nav) {
    val c = LocalColors.current
    val scope = rememberCoroutineScope()
    val favs = prefs.favorites.mapNotNull { k ->
        k.split(":").mapNotNull { it.toIntOrNull() }.takeIf { it.size == 2 }?.let { it[0] to it[1] }
    }

    Column(
        Modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RoundIconButton(Ic.back, "رجوع", { nav.back() })
            Column {
                Text("المفضلة", color = c.text, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text(if (favs.isEmpty()) "لا توجد آيات محفوظة" else "${num(favs.size)} آيات محفوظة", color = c.muted, fontSize = 13.sp)
            }
        }
        if (favs.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    "اضغط على أي آية في المصحف واختر «مفضلة»\nوهتلاقيها هنا.",
                    color = c.muted, fontSize = 15.sp, textAlign = TextAlign.Center, lineHeight = 26.sp,
                )
            }
            return@Column
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(favs, key = { "${it.first}:${it.second}" }) { (s, a) ->
              SwipeToDelete(onDelete = { scope.launch { store.toggleFavorite("$s:$a") } }) {
                CardBox(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.clickable { nav.go(Screen.Mushaf(q.pageOf(s, a), "$s:$a", highlight = true)) }.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Ic.heart, "إزالة من المفضلة", tint = c.heart,
                                modifier = Modifier.size(20.dp).clickable { scope.launch { store.toggleFavorite("$s:$a") } },
                            )
                            Text(
                                "  ${q.surahName(s)} · ${num(a)}", color = c.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                            )
                            Text("ص ${num(q.pageOf(s, a))}", color = c.muted, fontSize = 12.sp)
                        }
                        QuranVerse(q, s, a, fontSize = 24.sp)
                    }
                }
              }
            }
        }
    }
}
