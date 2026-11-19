package com.kouwis.alzkr.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.foundation.layout.displayCutoutPadding
import com.kouwis.alzkr.data.AppFonts
import kotlinx.coroutines.delay
import com.kouwis.alzkr.data.AppPrefs
import com.kouwis.alzkr.data.ArabicText.num
import com.kouwis.alzkr.data.Prefs
import com.kouwis.alzkr.data.QuranData
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private fun parseKey(k: String?): Pair<Int, Int>? =
    k?.split(":")?.mapNotNull { it.toIntOrNull() }?.takeIf { it.size == 2 }?.let { it[0] to it[1] }

@Composable
fun MushafScreen(
    q: QuranData,
    prefs: AppPrefs,
    store: Prefs,
    nav: Nav,
    startPage: Int,
    startVerse: String?,
    highlightStart: Boolean = false,
) {
    // the ayah opened from favourites / bookmarks / search glows until the reader taps
    var focus by remember { mutableStateOf(if (highlightStart) startVerse else null) }
    val c = LocalColors.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val clipboard = LocalClipboardManager.current

    // Quran only on screen: keep it awake and hide the system bars while reading.
    DisposableEffect(view) {
        view.keepScreenOn = true
        val window = (view.context as? android.app.Activity)?.window
        val ctrl = window?.let { WindowCompat.getInsetsController(it, view) }
        ctrl?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        ctrl?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            view.keepScreenOn = false
            ctrl?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // opening at a verse only positions the reader there; selecting is a long-press
    var selected by remember { mutableStateOf<String?>(null) }
    var barsVisible by remember { mutableStateOf(false) }
    var barsTick by remember { mutableIntStateOf(0) }
    var dialog by remember { mutableStateOf<Pair<String, String>?>(null) }
    var currentPage by remember { mutableIntStateOf(startPage.coerceIn(1, 604)) }
    val verseMode = prefs.verseMode
    // page and ayah bookmarks are linked: an ayah mark lights the page's mark too
    val ayahsOnPage = remember(currentPage) { q.versesOnPage(currentPage).map { "${it.first}:${it.second}" }.toSet() }
    val pageMarked = "p:$currentPage" in prefs.bookmarks || ayahsOnPage.any { "a:$it" in prefs.bookmarks }

    // system bars come back together with our top bar, so it sits in its normal place
    LaunchedEffect(barsVisible) {
        val window = (view.context as? android.app.Activity)?.window ?: return@LaunchedEffect
        val ctrl = WindowCompat.getInsetsController(window, view)
        if (barsVisible) ctrl.show(WindowInsetsCompat.Type.systemBars()) else ctrl.hide(WindowInsetsCompat.Type.systemBars())
    }
    // the top bar shows on tap and hides itself after a few seconds
    LaunchedEffect(barsVisible, barsTick) {
        if (barsVisible) {
            delay(3500)
            barsVisible = false
        }
    }
    val showBars = { barsVisible = true; barsTick++ }
    val onTap: () -> Unit = {
        when {
            selected != null -> selected = null
            focus != null -> focus = null
            barsVisible -> barsVisible = false
            else -> showBars()
        }
    }

    // a small, quiet note when you turn onto a page where a juz / hizb / quarter begins
    var markNote by remember { mutableStateOf<String?>(null) }
    var noteTick by remember { mutableIntStateOf(0) }
    var firstPageSeen by remember { mutableStateOf(false) }
    LaunchedEffect(currentPage) {
        if (!firstPageSeen) firstPageSeen = true
        else if (!verseMode) {
            val juz = q.meta.juz.firstOrNull { it.page == currentPage }
            val rub = q.meta.rub.firstOrNull { it.page == currentPage }
            val note = when {
                juz != null -> "بداية الجزء ${num(juz.n)}"
                rub != null -> {
                    val hizb = (rub.n - 1) / 4 + 1
                    when ((rub.n - 1) % 4) {
                        0 -> "بداية الحزب ${num(hizb)}"
                        1 -> "ربع الحزب ${num(hizb)}"
                        2 -> "نصف الحزب ${num(hizb)}"
                        else -> "ثلاثة أرباع الحزب ${num(hizb)}"
                    }
                }
                else -> null
            }
            if (note != null) { markNote = note; noteTick++ }
        }
    }
    LaunchedEffect(noteTick) {
        if (markNote != null) { delay(2600); markNote = null }
    }

    LaunchedEffect(currentPage) {
        val first = q.versesOnPage(currentPage).firstOrNull()
        val key = selected?.takeIf { parseKey(it)?.let { (s, a) -> q.pageOf(s, a) == currentPage } == true }
            ?: first?.let { "${it.first}:${it.second}" } ?: ""
        store.markRead(currentPage, key)
    }

    Box(Modifier.fillMaxSize().background(c.paper)) {
        if (verseMode) {
            VerseList(
                q, prefs, selected ?: focus,
                startKey = selected ?: startVerse?.takeIf { parseKey(it)?.let { (s, a) -> q.pageOf(s, a) == currentPage } == true } ?: q.versesOnPage(currentPage).firstOrNull()?.let { "${it.first}:${it.second}" } ?: "1:1",
                onVisibleVerse = { s, a -> currentPage = q.pageOf(s, a) },
                onLongPress = { selected = it },
                onTap = onTap,
            )
        } else {
            PagesPager(q, currentPage, selected, focus, prefs.bookmarks,
                onPage = { currentPage = it },
                onLongPressVerse = { selected = it },
                onTap = onTap,
            )
            AnimatedVisibility(
                visible = prefs.tafsirOn || prefs.translationOn,
                enter = slideInVertically(tween(450)) { it } + fadeIn(tween(450)),
                exit = slideOutVertically(tween(350)) { it } + fadeOut(tween(350)),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                StudyPanel(q, currentPage, prefs.tafsirOn || !prefs.translationOn, prefs.translationOn, selected, Modifier.align(Alignment.BottomCenter))
            }
        }

        AnimatedVisibility(
            visible = markNote != null && !barsVisible,
            enter = slideInVertically(tween(350)) { -it } + fadeIn(tween(350)),
            exit = slideOutVertically(tween(300)) { -it } + fadeOut(tween(300)),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 36.dp),
        ) {
            var shown by remember { mutableStateOf("") }
            markNote?.let { shown = it }
            Text(
                shown,
                color = Color.White,
                fontSize = 14.sp,
                fontFamily = AppFonts.naskhBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(c.primary.copy(alpha = 0.92f))
                    .padding(horizontal = 18.dp, vertical = 8.dp),
            )
        }

        AnimatedVisibility(
            visible = barsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            TopBar(
                q, currentPage, verseMode,
                pageBookmarked = pageMarked,
                onBookmarkPage = {
                    val wasOn = pageMarked
                    scope.launch { store.togglePageBookmark(currentPage, ayahsOnPage) }
                    Toast.makeText(ctx, if (wasOn) "اتشالت علامة الصفحة" else "اتحطت علامة على صفحة ${num(currentPage)}", Toast.LENGTH_SHORT).show()
                    showBars()
                },
                onBack = { nav.back() },
                onToggleMode = { scope.launch { store.setVerseMode(!verseMode) }; showBars() },
                onTajweed = { Toast.makeText(ctx, "المصحف المجوّد — قريبًا إن شاء الله", Toast.LENGTH_SHORT).show() },
            )
        }

        val sel = selected
        if (sel != null) {
            AyahActions(
                isFavorite = sel in prefs.favorites,
                isBookmarked = "a:$sel" in prefs.bookmarks,
                onBookmark = {
                    val wasOn = "a:$sel" in prefs.bookmarks
                    scope.launch { store.toggleBookmark("a:$sel") }
                    Toast.makeText(ctx, if (wasOn) "اتشالت علامة الآية" else "اتحطت علامة على الآية", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp),
                onFavorite = { scope.launch { store.toggleFavorite(sel) } },
                onTafsir = { dialog = "التفسير الميسر" to sel },
                onTranslation = { dialog = "الترجمة (English)" to sel },
                onCopy = {
                    verseText(q, sel)?.let {
                        clipboard.setText(AnnotatedString(it))
                        Toast.makeText(ctx, "تم النسخ", Toast.LENGTH_SHORT).show()
                    }
                },
                onShare = {
                    val text = verseText(q, sel) ?: return@AyahActions
                    val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
                    ctx.startActivity(Intent.createChooser(send, "مشاركة الآية"))
                },
            )
        }
    }

    dialog?.let { (title, key) ->
        val (s, a) = parseKey(key) ?: return@let
        val body = if (title.startsWith("التفسير")) q.tafsirOf(s, a) else q.translationOf(s, a)
        AlertDialog(
            onDismissRequest = { dialog = null },
            confirmButton = { TextButton(onClick = { dialog = null }) { Text("تم", color = c.primary) } },
            title = { Text("$title · ${q.surahName(s)} ${num(a)}", fontSize = 17.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    QuranVerse(q, s, a, fontSize = 22.sp, color = c.primary)
                    Text(
                        body.ifBlank { "غير متوفر" },
                        fontSize = 15.sp, lineHeight = 26.sp, color = c.text,
                        modifier = Modifier.padding(top = 10.dp),
                        style = TextStyle(textDirection = if (title.startsWith("التفسير")) TextDirection.Rtl else TextDirection.Ltr),
                    )
                }
            },
            containerColor = c.card,
        )
    }
}

private fun verseText(q: QuranData, key: String): String? {
    val (s, a) = parseKey(key) ?: return null
    return "${q.simple(s, a)} ﴿${num(a)}﴾\n[سورة ${q.surahName(s)}]"
}

@Composable
private fun PagesPager(
    q: QuranData,
    page: Int,
    selected: String?,
    focus: String?,
    bookmarks: List<String>,
    onPage: (Int) -> Unit,
    onLongPressVerse: (String) -> Unit,
    onTap: () -> Unit,
) {
    // pages that carry a bookmark (the page itself, or an ayah on it) show a ribbon
    val marked = remember(bookmarks) {
        bookmarks.mapNotNull { b ->
            val parts = b.split(":")
            when {
                parts.size == 2 && parts[0] == "p" -> parts[1].toIntOrNull()
                parts.size == 3 && parts[0] == "a" -> {
                    val s = parts[1].toIntOrNull(); val a = parts[2].toIntOrNull()
                    if (s != null && a != null) runCatching { q.pageOf(s, a) }.getOrNull() else null
                }
                else -> null
            }
        }.toSet()
    }
    val state = rememberPagerState(initialPage = page - 1) { 604 }
    LaunchedEffect(state) {
        androidx.compose.runtime.snapshotFlow { state.currentPage }.distinctUntilChanged().collect { onPage(it + 1) }
    }
    val landscape = LocalConfiguration.current.screenWidthDp > LocalConfiguration.current.screenHeightDp
    HorizontalPager(state = state, modifier = Modifier.fillMaxSize(), beyondViewportPageCount = 1, key = { it }) { idx ->
        val p = idx + 1
        val sel = selected?.takeIf { k -> parseKey(k)?.let { (s, a) -> q.pageOf(s, a) == p } == true }
        val foc = focus?.takeIf { k -> parseKey(k)?.let { (s, a) -> q.pageOf(s, a) == p } == true }
        if (landscape) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val w = maxWidth
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    MushafPageTall(q, p, sel, onLongPressVerse, onTap, w, foc)
                }
            }
        } else {
            MushafPage(
                q, p, sel, onLongPressVerse, onTap,
                Modifier.fillMaxSize().background(LocalColors.current.paper).pageEdges(p, LocalColors.current.dark, bookmarked = p in marked, ribbon = LocalColors.current.gold).displayCutoutPadding(),
                focus = foc,
            )
        }
    }
}

@Composable
private fun TopBar(
    q: QuranData,
    page: Int,
    verseMode: Boolean,
    pageBookmarked: Boolean,
    onBookmarkPage: () -> Unit,
    onBack: () -> Unit,
    onToggleMode: () -> Unit,
    onTajweed: () -> Unit,
) {
    val c = LocalColors.current
    Column(Modifier.fillMaxWidth().shadow(6.dp).background(c.card)) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(60.dp)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(22.dp)).springClick(label = "رجوع", onClick = onBack), contentAlignment = Alignment.Center) {
                Icon(Ic.back, "رجوع", tint = c.text, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(q.surahName(q.surahOfPage(page)), color = c.text, fontSize = 18.sp, fontFamily = AppFonts.naskhBold, maxLines = 1)
                Text(
                    "صفحة ${num(page)} · الجزء ${num(q.juzOfPage(page))} · الحزب ${num(q.hizbOfPage(page))}",
                    color = c.muted, fontSize = 11.sp, maxLines = 1,
                )
            }
            RoundIconButton(
                if (pageBookmarked) Ic.bookmarkFilled else Ic.bookmark,
                if (pageBookmarked) "شيل علامة الصفحة" else "علامة على الصفحة",
                onBookmarkPage, tint = if (pageBookmarked) c.gold else c.text, size = 40.dp, haptic = Haptic.Confirm,
            )
            RoundIconButton(if (verseMode) Ic.page else Ic.lines, if (verseMode) "وضع الصفحات" else "وضع الآيات", onToggleMode, size = 40.dp)
            Segmented(listOf("عادي", "مجوّد"), 0, { if (it == 1) onTajweed() }, Modifier.width(112.dp), height = 32.dp)
        }
    }
}

@Composable
private fun AyahActions(
    isFavorite: Boolean,
    isBookmarked: Boolean,
    onBookmark: () -> Unit,
    modifier: Modifier,
    onFavorite: () -> Unit,
    onTafsir: () -> Unit,
    onTranslation: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
) {
    Row(
        modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .shadow(10.dp, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF1E2A28))
            .padding(6.dp),
    ) {
        ActionItem(if (isBookmarked) Ic.bookmarkFilled else Ic.bookmark, "علامة", Color(0xFFE9C77B), onBookmark, Haptic.Confirm)
        ActionItem(if (isFavorite) Ic.heart else Ic.heartOutline, if (isFavorite) "محفوظة" else "مفضلة", Color(0xFFF3A6AF), onFavorite, Haptic.Confirm)
        ActionItem(Ic.book, "تفسير", Color.White, onTafsir)
        ActionItem(Ic.translate, "ترجمة", Color.White, onTranslation)
        ActionItem(Ic.copy, "نسخ", Color.White, onCopy)
        ActionItem(Ic.share, "مشاركة", Color.White, onShare)
    }
}

@Composable
private fun RowScope.ActionItem(icon: ImageVector, label: String, tint: Color, onClick: () -> Unit, haptic: Haptic = Haptic.Tap) {
    Column(
        Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(12.dp)).springClick(haptic = haptic, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val pop = popScale(icon)
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp).graphicsLayer { scaleX = pop; scaleY = pop })
        Text(label, color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

/**
 * Bottom panel with tafsir / translation of the open page. Collapsed it is just a
 * small bar; tap (or drag) it to slide it up, tap again to bring it down.
 */
@Composable
private fun StudyPanel(q: QuranData, page: Int, tafsir: Boolean, translation: Boolean, selected: String?, modifier: Modifier) {
    val c = LocalColors.current
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val maxH = with(density) { constraints.maxHeight.toFloat().toDp() }
        // three states: 0 = small floating bar, 1 = half screen, 2 = full screen
        var headerPx by remember { mutableIntStateOf(0) }
        val dividerColor = c.border
        val headerH = 64.dp // fixed: the bar always keeps the same shape
        var state by remember { mutableIntStateOf(0) }
        var dragOffset by remember { mutableFloatStateOf(0f) }
        LaunchedEffect(selected) { if (selected != null && state == 0) state = 1 }
        BackHandler(enabled = state > 0) { state = 0 }
        val stateH = when (state) { 0 -> headerH; 1 -> maxH * 0.58f; else -> maxH }
        val target = stateH - dragOffset.dp
        val anim = if (dragOffset == 0f) spring<androidx.compose.ui.unit.Dp>(dampingRatio = 0.82f, stiffness = 380f) else snap<androidx.compose.ui.unit.Dp>()
        val height by animateDpAsState(target.coerceIn(headerH, maxH), anim, label = "panel")
        // collapsed it floats above the bottom edge with rounded corners; open it spans the width
        val side by animateDpAsState(if (state == 0 && dragOffset == 0f) 14.dp else 0.dp, label = "side")
        val bottom by animateDpAsState(if (state == 0 && dragOffset == 0f) 22.dp else 0.dp, label = "bottom")
        val corner by animateDpAsState(if (state == 2) 0.dp else 24.dp, label = "corner")
        val bottomCorner by animateDpAsState(if (state == 0 && dragOffset == 0f) 24.dp else 0.dp, label = "bcorner")
        val expanded = state > 0
        val verses = remember(page) { q.versesOnPage(page) }
        val listState = androidx.compose.foundation.lazy.rememberLazyListState()
        LaunchedEffect(selected, page) {
            val idx = verses.indexOfFirst { "${it.first}:${it.second}" == selected }
            if (idx >= 0) listState.animateScrollToItem(idx)
        }
        val shape = RoundedCornerShape(topStart = corner, topEnd = corner, bottomStart = bottomCorner, bottomEnd = bottomCorner)
        Column(
            modifier
                .padding(start = side, end = side, bottom = bottom)
                .then(if (state == 2) Modifier.statusBarsPadding() else Modifier)
                .fillMaxWidth()
                .height(height)
                .shadow(if (state == 0) 3.dp else 6.dp, shape, ambientColor = Color.Black.copy(alpha = 0.25f), spotColor = Color.Black.copy(alpha = 0.18f))
                .clip(shape)
                .background(c.card)
                .border(1.dp, c.border, shape),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .springClick(label = if (expanded) "تصغير" else "فتح", haptic = Haptic.Tick, pressedScale = 0.98f) { state = if (state == 0) 1 else 0 }
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragEnd = {
                                // pull up -> next state (bar, half, full screen); pull down -> previous
                                if (dragOffset < -40f) state = (state + 1).coerceAtMost(2)
                                else if (dragOffset > 40f) state = if (dragOffset > 260f) 0 else (state - 1).coerceAtLeast(0)
                                dragOffset = 0f
                            },
                            onDragCancel = { dragOffset = 0f },
                        ) { _, dy -> dragOffset += dy / density.density }
                    }
                    .height(headerH)
                    .drawBehind {
                        if (expanded) {
                            val y = size.height - 1.dp.toPx()
                            drawLine(dividerColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                        }
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // small grip on top; the title row is centred in equal space above and below
                Box(Modifier.padding(top = 8.dp).width(36.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(c.border))
                Row(
                    Modifier.fillMaxWidth().height(52.dp).padding(start = 20.dp, end = 16.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val title = when {
                        tafsir && translation -> "التفسير والترجمة"
                        tafsir -> "التفسير"
                        else -> "الترجمة"
                    }
                    Text("$title · صفحة ${num(page)}", color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    if (tafsir) Chip("الميسر", c.primarySoft, c.primary)
                    if (translation) Chip("English", c.goldSoft, c.goldText)
                    val rot by animateFloatAsState(if (expanded) 180f else 0f, label = "chev")
                    Icon(Ic.up, null, tint = c.muted, modifier = Modifier.padding(start = 8.dp).size(20.dp).rotate(rot))
                }
            }
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize().navigationBarsPadding()) {
                items(verses, key = { "${it.first}:${it.second}" }) { (s, a) ->
                    val isSel = selected == "$s:$a"
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(if (isSel) c.highlight.copy(alpha = 0.5f) else Color.Transparent)
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Chip("${q.surahName(s)} · ${num(a)}", c.chip, c.text)
                        QuranVerse(q, s, a, fontSize = 22.sp, color = c.primary)
                        if (tafsir) Labeled("التفسير", c.primary, q.tafsirOf(s, a), rtl = true)
                        if (translation) Labeled("الترجمة", c.goldText, q.translationOf(s, a), rtl = false)
                    }
                    HorizontalDivider(color = c.border.copy(alpha = 0.5f))
                }
            }
        }
    }
}

@Composable
private fun Chip(text: String, bg: Color, fg: Color) {
    Text(
        text, color = fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 6.dp).clip(RoundedCornerShape(12.dp)).background(bg).padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

@Composable
private fun Labeled(label: String, accent: Color, body: String, rtl: Boolean) {
    val c = LocalColors.current
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(accent))
        Column(Modifier.padding(start = 10.dp)) {
            Text(label, color = accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                Text(
                    body.ifBlank { "غير متوفر" }, color = c.text, fontSize = 15.sp, lineHeight = 26.sp,
                    textAlign = TextAlign.Start, modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
