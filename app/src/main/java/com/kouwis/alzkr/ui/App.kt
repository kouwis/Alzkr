package com.kouwis.alzkr.ui

import com.kouwis.alzkr.data.t
import androidx.activity.compose.BackHandler
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kouwis.alzkr.data.AppPrefs
import com.kouwis.alzkr.data.Prefs
import com.kouwis.alzkr.data.Quran
import com.kouwis.alzkr.data.QuranData
import kotlinx.coroutines.flow.first

sealed interface Screen {
    data object Home : Screen
    data object Search : Screen
    data object Favorites : Screen
    data object Bookmarks : Screen
    /** Opens the mushaf at [page]; [verse] ("s:a") is highlighted / scrolled to when given. */
    data class Mushaf(val page: Int, val verse: String? = null, val highlight: Boolean = false) : Screen
    /** The adhkar of one section (see assets/data/adhkar.json). */
    data class Adhkar(val id: String, val start: Int = 0) : Screen
    data object Tasbih : Screen
    /** «التصحيح»: verses marked to review */
    data object Mistakes : Screen
    data object Qibla : Screen
    data object AdhkarSearch : Screen
    /** every chapter of حصن المسلم */
    data object HisnIndex : Screen
    /** «متابعتي»: the day's deeds, the week's progress, the Hijri calendar and the occasions */
    data object Tracker : Screen
    data object Settings : Screen
    data object PrayerSettings : Screen
    data object Alerts : Screen
    data object Silent : Screen
    data class PrayerAlert(val prayer: String) : Screen
}

class Nav {
    val stack = mutableStateListOf<Screen>(Screen.Home)
    val current: Screen get() = stack.last()
    fun go(s: Screen) { stack.add(s) }
    fun back(): Boolean = if (stack.size > 1) { stack.removeAt(stack.lastIndex); true } else false
    /** From the search: opened over the mushaf, the result replaces that mushaf (back goes where it went before). */
    fun openFromSearch(m: Screen) {
        if (stack.size >= 3 && stack.last() == Screen.Search && stack[stack.lastIndex - 1] is Screen.Mushaf) {
            stack.removeAt(stack.lastIndex); stack.removeAt(stack.lastIndex)
        }
        stack.add(m)
    }

}

@Composable
fun AlzkrApp(open: String? = null, onOpened: () -> Unit = {}) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context.applicationContext) }
    val state by prefs.flow.collectAsState(initial = AppPrefs())
    // the adhan and alerts follow every change of their settings, the prayer times and the place
    LaunchedEffect(state.alerts, state.prayer, state.lat, state.lng, state.lang) {
        kotlinx.coroutines.delay(600)
        com.kouwis.alzkr.alerts.AlertScheduler.reschedule(context)
    }
    // the interface language: everything below is rebuilt when it changes (text, and right-to-left or left-to-right)
    val lang = com.kouwis.alzkr.data.Lang.of(state.lang)
    com.kouwis.alzkr.data.I18n.set(context, lang)
    androidx.compose.runtime.key(lang) { AlzkrTheme {
        val c = LocalColors.current
        val data by produceState<QuranData?>(Quran.peek()) { value = Quran.load(context) }
        val nav = remember { Nav() }
        // open on the section the user was last in
        val firstPrefs by produceState<AppPrefs?>(null) { value = prefs.flow.first() }
        var opened by remember { androidx.compose.runtime.mutableStateOf(false) }
        // which section's home is showing (0 القرآن, 1 الأذكار); kept here so it survives a trip into the mushaf
        var section by remember { androidx.compose.runtime.mutableIntStateOf(0) }
        LaunchedEffect(firstPrefs) {
            val f = firstPrefs ?: return@LaunchedEffect
            if (!opened) { opened = true; section = if (f.section == "adhkar") 1 else 0 }
        }

        Box(Modifier.fillMaxSize().background(c.bg)) {
            val q = data
            if (q == null || !opened) {
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = c.primary)
                    Text(com.kouwis.alzkr.data.t("جارٍ التحميل…"), color = c.muted, modifier = Modifier.padding(top = 12.dp))
                }
                return@Box
            }
            BackHandler(enabled = nav.stack.size > 1) { nav.back() }
            // opened from a reminder: straight to its adhkar
            LaunchedEffect(open) { if (open != null) { nav.go(Screen.Adhkar(open)); onOpened() } }
            // each screen keeps its scroll position (and other saved state) while it is in the stack,
            // so going back lands where you were; a screen taken off the stack forgets it
            val holder = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
            val keys = remember { mutableSetOf<String>() }
            LaunchedEffect(nav.stack.size) {
                kotlinx.coroutines.delay(700) // after the screen change has finished animating
                val live = nav.stack.mapIndexed { i, sc -> "${i + 1}:$sc" }.toSet()
                keys.filter { it !in live }.forEach { holder.removeState(it); keys.remove(it) }
            }
            // calm screen changes: the new screen fades in while easing up from 96%, the old one
            // fades out and settles back a little (and the reverse when going back)
            androidx.compose.animation.AnimatedContent(
                targetState = nav.stack.size to nav.current,
                transitionSpec = {
                    val forward = targetState.first >= initialState.first
                    val ease = androidx.compose.animation.core.FastOutSlowInEasing
                    if (forward) {
                        (androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(320, delayMillis = 60, easing = ease)) +
                            androidx.compose.animation.scaleIn(androidx.compose.animation.core.tween(380, easing = ease), initialScale = 0.96f)) togetherWith
                            (androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(200)) +
                                androidx.compose.animation.scaleOut(androidx.compose.animation.core.tween(380, easing = ease), targetScale = 1.03f))
                    } else {
                        (androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(320, delayMillis = 60, easing = ease)) +
                            androidx.compose.animation.scaleIn(androidx.compose.animation.core.tween(380, easing = ease), initialScale = 1.03f)) togetherWith
                            (androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(200)) +
                                androidx.compose.animation.scaleOut(androidx.compose.animation.core.tween(380, easing = ease), targetScale = 0.96f))
                    }
                },
                label = "screens",
            ) { (depth, screen) ->
                val key = "$depth:$screen"
                keys += key
                holder.SaveableStateProvider(key) { when (screen) {
                    Screen.Home -> SectionsHost(q, state, prefs, nav, section) { section = it }
                    Screen.Search -> SearchScreen(q, nav)
                    Screen.Favorites -> FavoritesScreen(q, state, prefs, nav)
                    Screen.Bookmarks -> BookmarksScreen(q, state, prefs, nav)
                    is Screen.Mushaf -> MushafScreen(q, state, prefs, nav, screen.page, screen.verse, screen.highlight)
                    Screen.Tasbih -> TasbihScreen(state, prefs, nav)
                    Screen.Mistakes -> MistakesScreen(q, state, prefs, nav)
                    is Screen.Adhkar -> AdhkarReaderScreen(q, state, prefs, nav, screen.id, screen.start)
                    Screen.Qibla -> QiblaScreen(state, nav)
                    Screen.AdhkarSearch -> AdhkarSearchScreen(q, nav)
                    Screen.HisnIndex -> HisnIndexScreen(nav)
                    Screen.Tracker -> TrackerScreen(state, prefs, nav)
                    Screen.Settings -> SettingsScreen(state, prefs, nav)
                    Screen.PrayerSettings -> PrayerSettingsScreen(state, prefs, nav)
                    Screen.Alerts -> AlertsScreen(state, prefs, nav)
                    Screen.Silent -> SilentScreen(state, prefs, nav)
                    is Screen.PrayerAlert -> PrayerAlertScreen(state, prefs, nav, screen.prayer)
                } }
            }
        }
    }
} }
