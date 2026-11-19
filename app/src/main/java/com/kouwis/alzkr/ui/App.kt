package com.kouwis.alzkr.ui

import androidx.activity.compose.BackHandler
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
    data class Mushaf(val page: Int, val verse: String? = null) : Screen
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
            when (val s = nav.current) {
                Screen.Home -> HomeScreen(q, state, prefs, nav)
                Screen.Search -> SearchScreen(q, nav)
                Screen.Favorites -> FavoritesScreen(q, state, prefs, nav)
                Screen.Bookmarks -> BookmarksScreen(q, state, prefs, nav)
                is Screen.Mushaf -> MushafScreen(q, state, prefs, nav, s.page, s.verse)
            }
        }
    }
}
