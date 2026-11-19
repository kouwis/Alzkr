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

sealed interface Screen {
    data object Home : Screen
    data object Search : Screen
    data object Favorites : Screen
    data object Bookmarks : Screen
    /** Opens the mushaf at [page]; [verse] ("s:a") is highlighted / scrolled to when given. */
    data class Mushaf(val page: Int, val verse: String? = null, val highlight: Boolean = false) : Screen
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

        Box(Modifier.fillMaxSize().background(c.bg)) {
            val q = data
            if (q == null) {
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
                    Screen.Home -> HomeScreen(q, state, prefs, nav)
                    Screen.Search -> SearchScreen(q, nav)
                    Screen.Favorites -> FavoritesScreen(q, state, prefs, nav)
                    Screen.Bookmarks -> BookmarksScreen(q, state, prefs, nav)
                    is Screen.Mushaf -> MushafScreen(q, state, prefs, nav, screen.page, screen.verse, screen.highlight)
                }
            }
        }
    }
}
