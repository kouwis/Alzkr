@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.kouwis.alzkr.ui

import com.kouwis.alzkr.data.t
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

private data class Row4(val n: String, val title: String, val sub: String, val page: Int, val verse: String? = null, val quran: Boolean = false)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(q: QuranData, prefs: AppPrefs, store: Prefs, nav: Nav, onSectionSwipe: (Int) -> Unit) {
    val c = LocalColors.current
    val scope = rememberCoroutineScope()
    val soon = soon()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showLegend by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current

    val rows = remember(tab) {
        when (tab) {
            0 -> q.surahs.map { Row4(num(it.n), it.name, t("{0} · {1} آية", t(it.type), num(it.ayahs)), it.page, "${it.n}:1") }
            1 -> q.meta.rub.map { r ->
                val hizb = (r.n - 1) / 4 + 1
                val quarter = listOf("", t("ربع "), t("نصف "), t("ثلاثة أرباع "))[(r.n - 1) % 4]
                Row4(num(r.n), r.text, t("{0}الحزب {1} · {2} {3}", quarter, num(hizb), q.surahName(r.s), num(r.a)), r.page, "${r.s}:${r.a}", quran = true)
            }
            2 -> q.meta.hizb.map { h -> Row4(num(h.n), t("الحزب {0}", num(h.n)), t("يبدأ من {0} {1}", q.surahName(h.s), num(h.a)), h.page, "${h.s}:${h.a}") }
            else -> q.meta.juz.map { j -> Row4(num(j.n), t("الجزء {0}", num(j.n)), t("يبدأ من {0} {1}", q.surahName(j.s), num(j.a)), j.page, "${j.s}:${j.a}") }
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
    val toAdhkar = { onSectionSwipe(1) }
    val swipeSection = Modifier.pointerInput(Unit) {
        detectHorizontalDragGestures(
            onDragEnd = {
                if (sectionDrag > 70.dp.toPx()) { view.haptic(Haptic.Tick); toAdhkar() }
                sectionDrag = 0f
            },
            onDragCancel = { sectionDrag = 0f },
        ) { change, dx -> change.consume(); sectionDrag += if (com.kouwis.alzkr.data.I18n.lang.rtl) dx else -dx }
    }
    // movement only — no fading, so the list never blinks
    val slidePx = with(density) { 56.dp.toPx() }
    val rowShift = Modifier.graphicsLayer {
        translationX = dir * slidePx * (1f - enter.value)
    }

    Column(Modifier.fillMaxSize().background(c.bg).windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.statusBarsIgnoringVisibility).windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.navigationBarsIgnoringVisibility)) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = SectionBarSpace)) {
            item {
                // swiping the top part (continue card, ward) moves between sections: القرآن → الأذكار
                Column(Modifier.then(swipeSection).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp).height(HomeHeaderHeight), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(t("القرآن الكريم"), color = c.text, fontSize = 30.sp, fontFamily = AppFonts.naskhBold)
                            Text(t("مصحف المدينة · رواية حفص"), color = c.muted, fontSize = 13.sp)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            RoundIconButton(Ic.search, t("بحث"), { nav.go(Screen.Search) })
                            RoundIconButton(Ic.bookmarkFilled, t("العلامات"), { nav.go(Screen.Bookmarks) }, tint = c.gold)
                            // favourites open from the bookmarks screen
                            RoundIconButton(Ic.pen, t("مراجعة الحفظ"), { nav.go(Screen.Mistakes) }, tint = c.heart)
                            RoundIconButton(Ic.settings, t("الإعدادات"), { nav.go(Screen.Settings) })
                        }
                    }
                    ContinueCard(q, prefs, nav,
                        onTafsir = { scope.launch { store.setTafsir(!prefs.tafsirOn) } },
                        onTranslation = { scope.launch { store.setTranslation(!prefs.translationOn) } },
                        onTajweed = {
                            if (!prefs.tajweedOn) {
                                com.kouwis.alzkr.data.TajweedFonts.prefetchAll(context)
                                // only tell the user about the download while it's actually needed
                                scope.launch {
                                    val have = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                        com.kouwis.alzkr.data.TajweedFonts.downloadedCount(context)
                                    }
                                    if (have < 604) android.widget.Toast.makeText(
                                        context,
                                        t("يُنزَّل مصحف التجويد مرة واحدة (نحو ٧٠ ميجابايت)، وتتلوّن الصفحات فور وصولها"),
                                        android.widget.Toast.LENGTH_LONG,
                                    ).show()
                                }
                            }
                            scope.launch { store.setTajweed(!prefs.tajweedOn) }
                        },
                        onLegend = { showLegend = true },
                    )
                    Spacer(Modifier.height(0.dp))
                }
            }
            stickyHeader {
                Box(Modifier.fillMaxWidth().background(c.bg).then(swipeTabs).padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Segmented(listOf(t("السور"), t("الأرباع"), t("الأحزاب"), t("الأجزاء")), tab, { tab = it }, Modifier.fillMaxWidth())
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
                            // by verse, so the tajweed print (own page breaks) opens on the right page too
                            .clickable { nav.go(Screen.Mushaf(r.page, r.verse)) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        NumberBadge(r.n)
                        Column(Modifier.weight(1f)) {
                            Text(r.title, color = c.text, fontSize = 20.sp, fontFamily = if (r.quran) AppFonts.hafs else AppFonts.naskhBold, maxLines = 1)
                            Text(r.sub, color = c.muted, fontSize = 12.sp, maxLines = 1)
                        }
                        Text(t("ص {0}", num(r.page)), color = c.muted, fontSize = 13.sp)
                    }
                    if (i != rows.lastIndex) HorizontalDivider(color = c.border.copy(alpha = 0.6f))
                }
            }
            item { Spacer(Modifier.height(12.dp)) }
        }

        if (showLegend) TajweedLegendDialog(onDismiss = { showLegend = false })

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
            .height(HomeCardHeight)
            .clip(RoundedCornerShape(24.dp))
            .background(c.primary)
            .mosqueBackdrop()
            .padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(if (started) t("أكمل القراءة") else t("ابدأ القراءة"), color = Color(0xFFCFE3DD), fontSize = 13.sp)
                Text(t("سورة {0}", q.surahName(surah)), color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                val ayahPart = verse?.let { t(" · آية {0}", num(it[1])) } ?: ""
                Text(t("الجزء {0} · صفحة {1}{2}", num(q.juzOfPage(page)), num(page), ayahPart), color = Color(0xFFE3EEEA), fontSize = 13.sp)
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
                Text(t("متابعة"), color = LightColors.primary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Icon(Ic.forward, null, tint = LightColors.primary, modifier = Modifier.size(16.dp))
            }
        }
        // the two other places read lately (one per surah)
        val others = prefs.recent.drop(1).take(2)
        if (others.isEmpty()) {
            // until a second surah is read, keep the row so the card's layout doesn't jump
            Row(
                Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(14.dp))
                    .border(1.dp, c.primaryLine, RoundedCornerShape(14.dp)).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Ic.bookmark, null, tint = Color(0xFFCFE3DD), modifier = Modifier.size(15.dp))
                Text(t("ستظهر هنا آخر ثلاثة مواضع قرأت فيها"), color = Color(0xFFCFE3DD), fontSize = 13.sp, maxLines = 1)
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                others.forEach { (pg, vk) ->
                    val s = vk.substringBefore(':').toIntOrNull() ?: q.surahOfPage(pg)
                    Row(
                        Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White.copy(alpha = 0.12f))
                            .springClick(role = Role.Button, label = q.surahName(s), pressedScale = 0.96f, ripple = false) {
                                nav.go(Screen.Mushaf(pg, vk.ifBlank { null }))
                            }
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Ic.bookmark, null, tint = Color(0xFFCFE3DD), modifier = Modifier.size(15.dp))
                        Text(q.surahName(s), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.weight(1f))
                        Text(t("ص {0}", num(pg)), color = Color(0xFFCFE3DD), fontSize = 12.sp, maxLines = 1)
                    }
                }
                if (others.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        // today's ward, inside the card
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val done = prefs.wardDone.coerceAtMost(prefs.wardTarget)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t("وردك اليوم"), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(
                    if (done >= prefs.wardTarget) t("أتممت وردك ✓") else t("{0} من {1} صفحات", num(done), num(prefs.wardTarget)),
                    color = Color(0xFFE3EEEA), fontSize = 12.sp,
                )
            }
            val frac by androidx.compose.animation.core.animateFloatAsState((prefs.wardDone.toFloat() / prefs.wardTarget).coerceIn(0f, 1f), label = "ward")
            Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(c.primaryLine)) {
                Box(Modifier.fillMaxWidth(frac).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFFE2C48F)))
            }
        }
        // the khatma: how far the reading place is through the mushaf, and when the ward would finish it
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val left = 604 - page
            val pct = (page * 100) / 604
            val days = if (prefs.wardTarget > 0) (left + prefs.wardTarget - 1) / prefs.wardTarget else 0
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t("الختمة · {0}٪", num(pct)), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(
                    if (left <= 0) t("ختمت المصحف ✓") else t("بوردك تختم بعد {0} يومًا", num(days)),
                    color = Color(0xFFE3EEEA), fontSize = 12.sp,
                )
            }
            val kfrac by androidx.compose.animation.core.animateFloatAsState(page / 604f, label = "khatma")
            Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(c.primaryLine)) {
                Box(Modifier.fillMaxWidth(kfrac).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.85f)))
            }
        }
        Spacer(Modifier.weight(1f))
        HorizontalDivider(color = c.primaryLine)
        Row(Modifier.fillMaxWidth().height(26.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(t("اعرض أسفل الصفحة أثناء القراءة"), color = Color(0xFFCFE3DD), fontSize = 12.sp, modifier = Modifier.weight(1f))
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
                Text(t(" دليل الألوان"), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
            ToggleChip(t("التفسير"), Ic.book, tafsirL, { tafsirL = !tafsirL; onTafsir() }, Modifier.weight(1f))
            ToggleChip(t("الترجمة"), Ic.translate, translationL, { translationL = !translationL; onTranslation() }, Modifier.weight(1f))
            ToggleChip(t("المجوّد"), Ic.tajweed, tajweedL, { tajweedL = !tajweedL; onTajweed() }, Modifier.weight(1f))
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
