package com.kouwis.alzkr.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kouwis.alzkr.data.AppPrefs
import com.kouwis.alzkr.data.Prefs
import com.kouwis.alzkr.data.QuranData
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Height the section bar takes over the bottom of each section's home. */
val SectionBarSpace = 84.dp

private val ITEM_W = 92.dp

/** The two homes line up: same header height and same top-card height, so switching
 *  section leaves the card exactly where it was. */
val HomeHeaderHeight = 76.dp
val HomeCardHeight = 384.dp

/**
 * The sections' homes side by side, like tabs of one app: the bar at the bottom stays put and
 * its pill slides (and changes colour) with the page while the homes slide under it.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SectionsHost(q: QuranData, prefs: AppPrefs, store: Prefs, nav: Nav, section: Int, onSection: (Int) -> Unit) {
    val pager = rememberPagerState(initialPage = section) { 2 }
    val scope = rememberCoroutineScope()
    val soon = soon()
    val go: (Int) -> Unit = { i ->
        scope.launch { pager.animateScrollToPage(i, animationSpec = tween(520, easing = FastOutSlowInEasing)) }
    }
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect { p ->
            onSection(p)
            store.setSection(if (p == 1) "adhkar" else "quran")
        }
    }

    Box(Modifier.fillMaxSize()) {
        // sliding is driven by the bar and by swiping the top of each home (the lists keep
        // their own sideways swipes), so the pager itself does not take drags
        HorizontalPager(
            state = pager,
            userScrollEnabled = false,
            beyondViewportPageCount = 1,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            if (page == 0) HomeScreen(q, prefs, store, nav) { dir -> if (dir > 0) go(1) }
            else AdhkarHomeScreen(q, prefs, store, nav) { dir -> if (dir < 0) go(0) else soon("الفقه") }
        }
        SectionBar(
            position = pager.currentPage + pager.currentPageOffsetFraction,
            modifier = Modifier.align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility)
                .padding(bottom = 12.dp),
            onPick = { i -> if (i == 2) soon("الفقه") else go(i) },
        )
    }
}

@Composable
private fun SectionBar(position: Float, modifier: Modifier, onPick: (Int) -> Unit) {
    val c = LocalColors.current
    val quran = c.primary // the base theme's green
    val adhkar = if (c.dark) Color(0xFF2C6496) else Color(0xFF1E4E79)
    val pillColor = lerp(quran, adhkar, position.coerceIn(0f, 1f))
    val shape = RoundedCornerShape(28.dp)
    Box(
        modifier
            .shadow(18.dp, shape, ambientColor = Color(0x33101820), spotColor = Color(0x55101820))
            .clip(shape)
            .background(c.card)
            .padding(5.dp),
    ) {
        // the sliding pill (offset follows the layout direction, so it starts at the right)
        Box(
            Modifier
                .offset(x = ITEM_W * position)
                .width(ITEM_W)
                .height(44.dp)
                .shadow(6.dp, RoundedCornerShape(22.dp), ambientColor = pillColor, spotColor = pillColor)
                .clip(RoundedCornerShape(22.dp))
                .background(pillColor),
        )
        Row {
            listOf("القرآن", "الأذكار", "الفقه").forEachIndexed { i, label ->
                val near = 1f - abs(position - i).coerceIn(0f, 1f) // 1 when the pill is on it
                Box(
                    Modifier
                        .width(ITEM_W)
                        .height(44.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .springClick(role = Role.Tab, label = label, haptic = Haptic.Tick, ripple = false) { onPick(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (i == 2) "$label · قريبًا" else label,
                        color = lerp(c.muted, c.onPrimary, near),
                        fontSize = if (i == 2) 12.sp else 15.sp,
                        fontWeight = if (near > 0.5f) FontWeight.Bold else FontWeight.Medium,
                    )
                }
            }
        }
    }
}
