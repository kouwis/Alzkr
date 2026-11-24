package com.kouwis.alzkr.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kouwis.alzkr.data.AppFonts
import com.kouwis.alzkr.data.AppPrefs
import com.kouwis.alzkr.data.ArabicText.num
import com.kouwis.alzkr.data.Hijri
import com.kouwis.alzkr.data.Prefs
import com.kouwis.alzkr.data.t
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsPage(title: String, nav: Nav, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    val c = LocalColors.current
    Column(
        Modifier.fillMaxSize().background(c.bg)
            .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility)
            .windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility),
    ) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.align(Alignment.CenterStart)) { RoundIconButton(Ic.back, t("رجوع"), { nav.back() }) }
            Text(title, color = c.text, fontSize = 22.sp, fontFamily = AppFonts.naskhBold, maxLines = 1)
        }
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

/** «التاريخ»: move the Hijri date a day or two (to match the local sighting), and choose how each date is written. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DateSettingsScreen(prefs: AppPrefs, store: Prefs, nav: Nav) = AdhkarTheme {
    val c = LocalColors.current
    val scope = rememberCoroutineScope()
    val today = remember { LocalDate.now() }
    val day = remember(prefs.hijriOffset) { Hijri.of(today) }
    var pick by remember { mutableStateOf<String?>(null) }
    SettingsPage(t("التاريخ"), nav) {
        item {
            Group {
                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(t("تعديل التاريخ الهجري"), color = c.text, fontSize = 17.sp, modifier = Modifier.weight(1f))
                    Step("−", prefs.hijriOffset > -2) { scope.launch { store.setHijriOffset(prefs.hijriOffset - 1) } }
                    Text(
                        (if (prefs.hijriOffset > 0) "+" else "") + num(prefs.hijriOffset), color = c.text, fontSize = 18.sp,
                        fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.width(52.dp),
                    )
                    Step("+", prefs.hijriOffset < 2) { scope.launch { store.setHijriOffset(prefs.hijriOffset + 1) } }
                }
                Line()
                Text(
                    Hijri.hijriText(day, 0), color = c.goldText, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 14.dp),
                )
            }
        }
        item {
            Text(
                t("التقويم على حساب أم القرى، ويبدأ اليوم الهجري من المغرب. إن خالف رؤية الهلال في بلدك فقدّمه أو أخّره يومًا."),
                color = c.muted, fontSize = 13.sp, lineHeight = 20.sp, modifier = Modifier.padding(horizontal = 6.dp),
            )
        }
        item {
            Group {
                ValueRow(t("تنسيق التاريخ الهجري"), Hijri.hijriText(day, prefs.hijriFormat)) { pick = "h" }
                Line()
                ValueRow(t("تنسيق التاريخ الميلادي"), Hijri.gregorianText(today, prefs.gregFormat)) { pick = "g" }
            }
        }
    }
    when (pick) {
        "h" -> Choice(t("تنسيق التاريخ الهجري"), (0..3).map { Hijri.hijriText(day, it) to "" }, prefs.hijriFormat,
            { i -> scope.launch { store.setDateFormats(i, prefs.gregFormat) } }) { pick = null }
        "g" -> Choice(t("تنسيق التاريخ الميلادي"), (0..3).map { Hijri.gregorianText(today, it) to "" }, prefs.gregFormat,
            { i -> scope.launch { store.setDateFormats(prefs.hijriFormat, i) } }) { pick = null }
    }
}

/** «المصحف»: the page background, shown as a word of the mushaf on each colour. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MushafSettingsScreen(prefs: AppPrefs, store: Prefs, nav: Nav) = AdhkarTheme {
    val c = LocalColors.current
    val scope = rememberCoroutineScope()
    fun choose(id: String) = scope.launch { store.setMushafBg(id) }
    SettingsPage(t("المصحف"), nav) {
        item { Header(t("لون خلفية الصفحة")) }
        item {
            Group {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).springClick(label = t("حسب وضع الهاتف"), pressedScale = 0.98f) { choose("") }
                        .padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(t("حسب وضع الهاتف"), color = c.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        Text(t("أبيض في الوضع الفاتح، وداكن في الوضع الليلي"), color = c.muted, fontSize = 12.sp)
                    }
                    Radio(prefs.mushafBg.isEmpty())
                }
            }
        }
        items(MushafBgs.chunked(2).size) { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MushafBgs.chunked(2)[row].forEach { b ->
                    val on = prefs.mushafBg == b.id
                    val edge by animateColorAsState(if (on) c.gold else b.accent.copy(alpha = 0.35f), label = "edge")
                    val w by animateDpAsState(if (on) 3.dp else 1.dp, label = "w")
                    Box(
                        Modifier.weight(1f).aspectRatio(1.7f).clip(RoundedCornerShape(22.dp)).background(b.paper)
                            .border(w, edge, RoundedCornerShape(22.dp))
                            .springClick(label = t("لون خلفية الصفحة"), pressedScale = 0.95f) { choose(b.id) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("الرَّحْمَٰنُ", color = b.ink, fontSize = 26.sp, fontFamily = AppFonts.naskhBold)
                            Box(Modifier.size(26.dp).border(2.dp, b.accent, CircleShape), contentAlignment = Alignment.Center) {
                                Text(num(1), color = b.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        if (on) Box(Modifier.align(Alignment.TopEnd).padding(8.dp).size(22.dp).clip(CircleShape).background(c.gold), contentAlignment = Alignment.Center) {
                            Icon(Ic.check, null, tint = Color.White, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Radio(on: Boolean) {
    val c = LocalColors.current
    val edge by animateColorAsState(if (on) c.gold else c.muted, label = "radio")
    val dot by animateDpAsState(if (on) 12.dp else 0.dp, label = "dot")
    Box(Modifier.size(24.dp).border(2.dp, edge, CircleShape), contentAlignment = Alignment.Center) {
        Box(Modifier.size(dot).clip(CircleShape).background(c.gold))
    }
}
