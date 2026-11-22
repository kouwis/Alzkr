package com.kouwis.alzkr.ui

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
    data class Adhkar(val id: String) : Screen
    data object Tasbih : Screen
}

class Nav {
    val stack = mutableStateListOf<Screen>(Screen.Home)
    val current: Screen get() = stack.last()
    fun go(s: Screen) { stack.add(s) }
    fun back(): Boolean = if (stack.size > 1) { stack.removeAt(stack.lastIndex); true } else false

}

@Composable
fun AlzkrApp() {
    AlzkrTheme {
        val c = LocalColors.current
        val context = LocalContext.current
        val prefs = remember { Prefs(context.applicationContext) }
        val state by prefs.flow.collectAsState(initial = AppPrefs())
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
                    Text("جارٍ التحميل…", color = c.muted, modifier = Modifier.padding(top = 12.dp))
                }
                return@Box
            }
            BackHandler(enabled = nav.stack.size > 1) { nav.back() }
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
            ) { (_, screen) ->
                when (screen) {
                    Screen.Home -> SectionsHost(q, state, prefs, nav, section) { section = it }
                    Screen.Search -> SearchScreen(q, nav)
                    Screen.Favorites -> FavoritesScreen(q, state, prefs, nav)
                    Screen.Bookmarks -> BookmarksScreen(q, state, prefs, nav)
                    is Screen.Mushaf -> MushafScreen(q, state, prefs, nav, screen.page, screen.verse, screen.highlight)
                    is Screen.Adhkar -> AdhkarReaderScreen(q, nav, screen.id)
                    Screen.Tasbih -> TasbihScreen(nav)
                }
            }
        }
    }
}
