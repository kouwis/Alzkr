@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.kouwis.alzkr.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.background
import com.kouwis.alzkr.data.AppFonts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kouwis.alzkr.data.AppPrefs
import com.kouwis.alzkr.data.ArabicText.num
import com.kouwis.alzkr.data.Prefs
import com.kouwis.alzkr.data.QuranData
import kotlinx.coroutines.launch

private data class Row4(val n: String, val title: String, val sub: String, val page: Int)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(q: QuranData, prefs: AppPrefs, store: Prefs, nav: Nav) {
    val c = LocalColors.current
    val scope = rememberCoroutineScope()
    val soon = soon()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showLegend by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current

    val rows = remember(tab) {
        when (tab) {
            0 -> q.surahs.map { Row4(num(it.n), it.name, "${it.type} · ${num(it.ayahs)} آية", it.page) }
            1 -> q.meta.rub.map { r ->
                val hizb = (r.n - 1) / 4 + 1
                val quarter = listOf("", "ربع ", "نصف ", "ثلاثة أرباع ")[(r.n - 1) % 4]
                Row4(num(r.n), r.text, "${quarter}الحزب ${num(hizb)} · ${q.surahName(r.s)} ${num(r.a)}", r.page)
            }
            2 -> q.meta.hizb.map { h -> Row4(num(h.n), "الحزب ${num(h.n)}", "يبدأ من ${q.surahName(h.s)} ${num(h.a)}", h.page) }
            else -> q.meta.juz.map { j -> Row4(num(j.n), "الجزء ${num(j.n)}", "يبدأ من ${q.surahName(j.s)} ${num(j.a)}", j.page) }
        }
    }

    // swipe the list left/right to move between السور / الأرباع / الأحزاب / الأجزاء
    val view = LocalView.current
    val density = LocalDensity.current
    var dragX by remember { mutableFloatStateOf(0f) }
    // calm switch: the new list fades in with a small slide, no bounce, no follow-the-finger
    val enter = remember { Animatable(1f) }
    var lastTab by remember { mutableIntStateOf(tab) }
    var dir by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(tab) {
        if (tab != lastTab) {
            dir = if (tab > lastTab) -1f else 1f
            lastTab = tab
            enter.snapTo(0f)
            enter.animateTo(1f, androidx.compose.animation.core.tween(340, easing = androidx.compose.animation.core.LinearOutSlowInEasing))
        }
    }
    val swipeTabs = Modifier.pointerInput(Unit) {
        detectHorizontalDragGestures(
            onDragEnd = {
                val limit = 70.dp.toPx()
                val next = when {
                    dragX > limit -> (tab + 1).coerceAtMost(3) // left -> right: next (السور ← الأرباع …)
                    dragX < -limit -> (tab - 1).coerceAtLeast(0) // right -> left: previous
                    else -> tab
                }
                if (next != tab) { view.haptic(Haptic.Tick); tab = next }
                dragX = 0f
            },
            onDragCancel = { dragX = 0f },
        ) { change, dx -> change.consume(); dragX += dx }
    }
    var sectionDrag by remember { mutableFloatStateOf(0f) }
    val swipeSection = Modifier.pointerInput(Unit) {
        detectHorizontalDragGestures(
            onDragEnd = {
                if (sectionDrag > 70.dp.toPx()) { view.haptic(Haptic.Tick); soon("الأذكار") }
                sectionDrag = 0f
            },
            onDragCancel = { sectionDrag = 0f },
        ) { change, dx -> change.consume(); sectionDrag += dx }
    }
    // movement only — no fading, so the list never blinks
    val slidePx = with(density) { 56.dp.toPx() }
    val rowShift = Modifier.graphicsLayer {
        translationX = dir * slidePx * (1f - enter.value)
    }

    Column(Modifier.fillMaxSize().background(c.bg).windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.statusBarsIgnoringVisibility).windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.navigationBarsIgnoringVisibility)) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            item {
                // swiping the top part (continue card, ward) moves between sections: القرآن → الأذكار
                Column(Modifier.then(swipeSection).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("القرآن الكريم", color = c.text, fontSize = 30.sp, fontFamily = AppFonts.naskhBold)
                            Text("مصحف المدينة · رواية حفص", color = c.muted, fontSize = 13.sp)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            RoundIconButton(Ic.search, "بحث", { nav.go(Screen.Search) })
                            RoundIconButton(Ic.bookmarkFilled, "العلامات", { nav.go(Screen.Bookmarks) }, tint = c.gold)
                            RoundIconButton(Ic.heart, "المفضلة", { nav.go(Screen.Favorites) }, tint = c.heart)
                            RoundIconButton(Ic.settings, "الإعدادات", { soon("الإعدادات") })
                        }
                    }
                    ContinueCard(q, prefs, nav,
                        onTafsir = { scope.launch { store.setTafsir(!prefs.tafsirOn) } },
                        onTranslation = { scope.launch { store.setTranslation(!prefs.translationOn) } },
                        onTajweed = {
                            if (!prefs.tajweedOn) {
                                com.kouwis.alzkr.data.TajweedFonts.prefetchAll(context)
                                android.widget.Toast.makeText(
                                    context,
                                    "بننزّل مصحف التجويد مرة واحدة (حوالي ٧٠ ميجا)، والصفحات بتتلوّن أول ما توصل",
                                    android.widget.Toast.LENGTH_LONG,
                                ).show()
                            }
                            scope.launch { store.setTajweed(!prefs.tajweedOn) }
                        },
                        onLegend = { showLegend = true },
                    )
                    CardBox(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("وردك اليوم", color = c.text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                                val done = prefs.wardDone.coerceAtMost(prefs.wardTarget)
                                Text(
                                    if (done >= prefs.wardTarget) "أتممت وردك ✓" else "${num(done)} من ${num(prefs.wardTarget)} صفحات",
                                    color = if (done >= prefs.wardTarget) c.primary else c.muted, fontSize = 13.sp,
                                )
                            }
                            val frac = (prefs.wardDone.toFloat() / prefs.wardTarget).coerceIn(0f, 1f)
                            Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(c.chip)) {
                                Box(Modifier.fillMaxWidth(frac).height(8.dp).clip(RoundedCornerShape(4.dp)).background(c.gold))
                            }
                        }
                    }
                    Spacer(Modifier.height(0.dp))
                }
            }
            stickyHeader {
                Box(Modifier.fillMaxWidth().background(c.bg).then(swipeTabs).padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Segmented(listOf("السور", "الأرباع", "الأحزاب", "الأجزاء"), tab, { tab = it }, Modifier.fillMaxWidth())
                }
            }
            itemsIndexed(rows, key = { _, r -> "$tab-${r.n}" }) { i, r ->
                val shape = when {
                    rows.size == 1 -> RoundedCornerShape(18.dp)
                    i == 0 -> RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)
                    i == rows.lastIndex -> RoundedCornerShape(bottomStart = 18.dp, bottomEnd = 18.dp)
                    else -> RoundedCornerShape(0.dp)
                }
                Column(Modifier.then(swipeTabs).then(rowShift).padding(horizontal = 20.dp).clip(shape).background(c.card)) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { nav.go(Screen.Mushaf(r.page)) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        NumberBadge(r.n)
                        Column(Modifier.weight(1f)) {
                            Text(r.title, color = c.text, fontSize = 20.sp, fontFamily = AppFonts.naskhBold, maxLines = 1)
                            Text(r.sub, color = c.muted, fontSize = 12.sp, maxLines = 1)
                        }
                        Text("ص ${num(r.page)}", color = c.muted, fontSize = 13.sp)
                    }
                    if (i != rows.lastIndex) HorizontalDivider(color = c.border.copy(alpha = 0.6f))
                }
            }
            item { Spacer(Modifier.height(12.dp)) }
        }

        if (showLegend) TajweedLegendDialog(onDismiss = { showLegend = false })

        // Section switcher
        Row(
            Modifier
                .align(Alignment.CenterHorizontally)
                .padding(vertical = 8.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(c.card)
                .border(1.dp, c.border, RoundedCornerShape(24.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Pill("القرآن", true) {}
            Pill("الأذكار", false) { soon("الأذكار") }
            Pill("الفقه", false) { soon("الفقه") }
        }
    }
}

@Composable
private fun Pill(label: String, on: Boolean, onClick: () -> Unit) {
    val c = LocalColors.current
    Box(
        Modifier
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (on) c.primary else Color.Transparent)
            .springClick(role = Role.Tab, haptic = Haptic.Tick, ripple = false, onClick = onClick)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (on) c.onPrimary else c.muted, fontSize = 14.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium)
    }
}

@Composable
private fun ContinueCard(
    q: QuranData,
    prefs: AppPrefs,
    nav: Nav,
    onTafsir: () -> Unit,
    onTranslation: () -> Unit,
    onTajweed: () -> Unit,
    onLegend: () -> Unit,
) {
    // the switches flip at once; the setting is saved in the background
    var tafsirL by remember(prefs.tafsirOn) { mutableStateOf(prefs.tafsirOn) }
    var translationL by remember(prefs.translationOn) { mutableStateOf(prefs.translationOn) }
    var tajweedL by remember(prefs.tajweedOn) { mutableStateOf(prefs.tajweedOn) }
    val c = LocalColors.current
    val page = if (prefs.lastPage in 1..604) prefs.lastPage else 1
    val started = prefs.lastPage in 1..604
    val verse = prefs.lastVerse.split(":").mapNotNull { it.toIntOrNull() }.takeIf { it.size == 2 }
    val surah = verse?.get(0) ?: q.surahOfPage(page)

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(c.primary)
            .mosqueBackdrop()
            .padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(if (started) "أكمل القراءة" else "ابدأ القراءة", color = Color(0xFFCFE3DD), fontSize = 13.sp)
                Text("سورة ${q.surahName(surah)}", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                val ayahPart = verse?.let { " · آية ${num(it[1])}" } ?: ""
                Text("الجزء ${num(q.juzOfPage(page))} · صفحة ${num(page)}$ayahPart", color = Color(0xFFE3EEEA), fontSize = 13.sp)
            }
            Row(
                Modifier
                    .height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color.White)
                    .springClick { nav.go(Screen.Mushaf(page, prefs.lastVerse.ifBlank { null })) }
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("متابعة", color = LightColors.primary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Icon(Ic.forward, null, tint = LightColors.primary, modifier = Modifier.size(16.dp))
            }
        }
        HorizontalDivider(color = c.primaryLine)
        Row(Modifier.fillMaxWidth().height(26.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("اعرض أسفل الصفحة أثناء القراءة", color = Color(0xFFCFE3DD), fontSize = 12.sp, modifier = Modifier.weight(1f))
            // the tajweed colour guide sits on this same line and only fades, so nothing moves
            val legendAlpha by androidx.compose.animation.core.animateFloatAsState(
                if (tajweedL) 1f else 0f, androidx.compose.animation.core.tween(280), label = "legend",
            )
            Row(
                Modifier
                    .graphicsLayer { alpha = legendAlpha }
                    .clip(RoundedCornerShape(12.dp))
                    .then(if (tajweedL) Modifier.springClick(haptic = Haptic.Tap, ripple = false, onClick = onLegend) else Modifier)
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TajweedLegend.take(4).forEach { (col, _, _) ->
                    Box(Modifier.padding(end = 2.dp).size(8.dp).clip(RoundedCornerShape(4.dp)).background(col))
                }
                Text(" دليل الألوان", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
            ToggleChip("التفسير", Ic.book, tafsirL, { tafsirL = !tafsirL; onTafsir() }, Modifier.weight(1f))
            ToggleChip("الترجمة", Ic.translate, translationL, { translationL = !translationL; onTranslation() }, Modifier.weight(1f))
            ToggleChip("المجوّد", Ic.tajweed, tajweedL, { tajweedL = !tajweedL; onTajweed() }, Modifier.weight(1f))
        }
    }
}

@Composable
private fun ToggleChip(label: String, icon: ImageVector, on: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val c = LocalColors.current
    val bg by animateColorAsState(if (on) Color.White else Color.Transparent, label = "chipBg")
    val fg by animateColorAsState(if (on) LightColors.primary else Color.White, label = "chipFg")
    Row(
        modifier
            .height(46.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .border(1.dp, if (on) Color.White else c.primaryLine, RoundedCornerShape(14.dp))
            .springClick(role = Role.Switch, haptic = Haptic.Toggle, pressedScale = 0.94f, ripple = false, onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        val pop = popScale(on)
        Icon(if (on) Ic.check else icon, null, tint = fg, modifier = Modifier.size(17.dp).graphicsLayer { scaleX = pop; scaleY = pop })
        Spacer(Modifier.width(5.dp))
        Text(label, color = fg, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1)
    }
}
