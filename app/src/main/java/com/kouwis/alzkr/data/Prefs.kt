package com.kouwis.alzkr.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

private val Context.store by preferencesDataStore(name = "alzkr")

data class AppPrefs(
    val lastPage: Int = 0,
    val lastVerse: String = "",
    val tafsirOn: Boolean = false,
    val translationOn: Boolean = false,
    val favorites: List<String> = emptyList(),
    /** newest first: "p:<page>" for a page bookmark, "a:<surah>:<ayah>" for an ayah bookmark */
    val bookmarks: List<String> = emptyList(),
    val wardTarget: Int = 3,
    val wardDone: Int = 0,
    val verseMode: Boolean = false,
)

class Prefs(private val context: Context) {
    private object K {
        val lastPage = intPreferencesKey("last_page")
        val lastVerse = stringPreferencesKey("last_verse")
        val tafsir = booleanPreferencesKey("tafsir_on")
        val translation = booleanPreferencesKey("translation_on")
        val favorites = stringPreferencesKey("favorites") // newest first, comma separated "s:a"
        val bookmarks = stringPreferencesKey("bookmarks") // newest first, "p:151" / "a:7:1"
        val wardTarget = intPreferencesKey("ward_target")
        val wardDay = stringPreferencesKey("ward_day")
        val wardPages = stringSetPreferencesKey("ward_pages")
        val verseMode = booleanPreferencesKey("verse_mode")
    }

    private fun today() = LocalDate.now().toString()

    val flow: Flow<AppPrefs> = context.store.data.map { p: Preferences ->
        val sameDay = p[K.wardDay] == today()
        AppPrefs(
            lastPage = p[K.lastPage] ?: 0,
            lastVerse = p[K.lastVerse] ?: "",
            tafsirOn = p[K.tafsir] ?: false,
            translationOn = p[K.translation] ?: false,
            favorites = p[K.favorites]?.split(',')?.filter { it.isNotBlank() } ?: emptyList(),
            bookmarks = p[K.bookmarks]?.split(',')?.filter { it.isNotBlank() } ?: emptyList(),
            wardTarget = p[K.wardTarget] ?: 3,
            wardDone = if (sameDay) p[K.wardPages]?.size ?: 0 else 0,
            verseMode = p[K.verseMode] ?: false,
        )
    }

    suspend fun setTafsir(on: Boolean) = context.store.edit { it[K.tafsir] = on }
    suspend fun setTranslation(on: Boolean) = context.store.edit { it[K.translation] = on }
    suspend fun setVerseMode(on: Boolean) = context.store.edit { it[K.verseMode] = on }

    suspend fun markRead(page: Int, verseKey: String) = context.store.edit { p ->
        p[K.lastPage] = page
        p[K.lastVerse] = verseKey
        val day = today()
        val pages = if (p[K.wardDay] == day) p[K.wardPages].orEmpty() else emptySet()
        p[K.wardDay] = day
        p[K.wardPages] = pages + page.toString()
    }

    /**
     * The page's bookmark button: if the page is marked (by itself or through an ayah on it),
     * this clears the page mark and every ayah mark on it; otherwise it marks the page.
     */
    suspend fun togglePageBookmark(page: Int, ayahKeysOnPage: Set<String>) = context.store.edit { p ->
        val list = p[K.bookmarks]?.split(',')?.filter { it.isNotBlank() }.orEmpty()
        val linked = ayahKeysOnPage.map { "a:$it" }.toSet() + "p:$page"
        p[K.bookmarks] = (if (list.any { it in linked }) list.filterNot { it in linked } else listOf("p:$page") + list).joinToString(",")
    }

    suspend fun toggleBookmark(key: String) = context.store.edit { p ->
        val list = p[K.bookmarks]?.split(',')?.filter { it.isNotBlank() }.orEmpty()
        p[K.bookmarks] = (if (key in list) list - key else listOf(key) + list).joinToString(",")
    }

    suspend fun toggleFavorite(key: String) = context.store.edit { p ->
        val list = p[K.favorites]?.split(',')?.filter { it.isNotBlank() }.orEmpty()
        p[K.favorites] = (if (key in list) list - key else listOf(key) + list).joinToString(",")
    }
}
