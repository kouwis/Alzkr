package com.kouwis.alzkr.data

import kotlin.math.abs

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
    val tajweedOn: Boolean = false,
    /** which section the app opens on: "quran" or "adhkar" */
    val section: String = "quran",
    /** the last places read, newest first: (page, "s:a") — at most 3. Reading on page by page
     *  moves the same place along; jumping somewhere else starts a new one (even in the same surah). */
    val recent: List<Pair<Int, String>> = emptyList(),
    /** the place prayer times are worked out for; null until the user shares a location */
    val lat: Double? = null,
    val lng: Double? = null,
    val place: String = "",
    /** the place's name was written by the user: kept while the phone stays in the same area */
    val placeCustom: Boolean = false,
    /** verses (and words in them) marked to review — «التصحيح»: newest first, ("s:a", word indexes;
     *  empty = the whole verse). Word indexes count the verse's words in the mushaf from 0. */
    val mistakes: List<Pair<String, Set<Int>>> = emptyList(),
    /** when each adhkar section was last finished (epoch millis) */
    val adhkarDone: Map<String, Long> = emptyMap(),
    /** the interface language (code), "ar" by default */
    val lang: String = "ar",
    /** how the prayer times are worked out (مواقيت الصلاة in the settings) */
    val prayer: PrayerConfig = PrayerConfig(),
    /** the place updates itself from the phone's location; off once a place is picked by hand */
    val autoLocate: Boolean = true,
    /** «متابعة العبادات»: what was done on each day ("2026-11-23" -> ids), the last 90 days */
    val track: Map<String, Set<String>> = emptyMap(),
    /** the tasbih counter's adhkar and how many times each round is; null = the default list */
    val tasbih: List<Pair<String, Int>>? = null,
    /** «الأذان والتنبيهات» */
    val alerts: AlertsConfig = AlertsConfig(),
    /** «التاريخ»: days added to the Hijri date (−2…+2), and how each date is written */
    val hijriOffset: Int = 0,
    val hijriFormat: Int = 0,
    val gregFormat: Int = 0,
    /** «المصحف»: the page background ("" = follow the phone's light or dark mode) */
    val mushafBg: String = "",
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
        val tajweed = booleanPreferencesKey("tajweed_on")
        val section = stringPreferencesKey("section")
        val recent = stringPreferencesKey("recent_reads")
        val lat = androidx.datastore.preferences.core.doublePreferencesKey("lat")
        val lng = androidx.datastore.preferences.core.doublePreferencesKey("lng")
        val place = stringPreferencesKey("place")
        val placeCustom = booleanPreferencesKey("place_custom")
        val mistakes = stringPreferencesKey("mistakes") // "s:a|0.3;s:a|"
        val adhkarDone = stringPreferencesKey("adhkar_done") // "morning=1732300000000;sleep=…"
        val prayer = stringPreferencesKey("prayer_config")
        val lang = stringPreferencesKey("lang")
        val autoLocate = booleanPreferencesKey("auto_locate")
        val track = stringPreferencesKey("track") // "2026-11-23=morning,qiyam;2026-11-22=…"
        val alerts = stringPreferencesKey("alerts") // JSON
        val hijriOffset = intPreferencesKey("hijri_offset")
        val hijriFormat = intPreferencesKey("hijri_format")
        val gregFormat = intPreferencesKey("greg_format")
        val mushafBg = stringPreferencesKey("mushaf_bg")
        val tasbih = stringPreferencesKey("tasbih") // "text\u001F33\u001Etext\u001F100"
    }

    private fun parseMistakes(raw: String?): List<Pair<String, Set<Int>>> = raw.orEmpty().split(';').mapNotNull { e ->
        val parts = e.split('|')
        val key = parts[0].takeIf { Regex("\\d+:\\d+").matches(it) } ?: return@mapNotNull null
        key to parts.getOrNull(1).orEmpty().split('.').mapNotNull { it.toIntOrNull() }.toSet()
    }

    private fun writeMistakes(list: List<Pair<String, Set<Int>>>) =
        list.joinToString(";") { (k, w) -> k + "|" + w.sorted().joinToString(".") }

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
            tajweedOn = p[K.tajweed] ?: false,
            section = p[K.section] ?: "quran",
            recent = parseRecent(p[K.recent]).ifEmpty {
                // before this list existed only the last place was kept
                val lp = p[K.lastPage] ?: 0
                if (lp in 1..604) listOf(lp to (p[K.lastVerse] ?: "")) else emptyList()
            },
            lat = p[K.lat],
            lng = p[K.lng],
            place = p[K.place] ?: "",
            placeCustom = p[K.placeCustom] ?: false,
            mistakes = parseMistakes(p[K.mistakes]),
            adhkarDone = p[K.adhkarDone].orEmpty().split(';').mapNotNull { e ->
                val (k, v) = e.split('=').takeIf { it.size == 2 } ?: return@mapNotNull null
                v.toLongOrNull()?.let { k to it }
            }.toMap(),
            track = parseTrack(p[K.track]),
            prayer = PrayerConfig.decode(p[K.prayer]),
            lang = p[K.lang] ?: "ar",
            autoLocate = p[K.autoLocate] ?: true,
            alerts = AlertsConfig.decode(p[K.alerts]),
            hijriOffset = p[K.hijriOffset] ?: 0,
            hijriFormat = p[K.hijriFormat] ?: 0,
            gregFormat = p[K.gregFormat] ?: 0,
            mushafBg = p[K.mushafBg] ?: "",
            tasbih = p[K.tasbih]?.split('\u001E')?.mapNotNull { e ->
                val f = e.split('\u001F')
                val t = f[0].trim()
                if (t.isEmpty()) null else t to (f.getOrNull(1)?.toIntOrNull() ?: 33)
            },
        )
    }

    suspend fun setHijriOffset(n: Int) = context.store.edit { it[K.hijriOffset] = n.coerceIn(-2, 2) }
    suspend fun setDateFormats(hijri: Int, greg: Int) = context.store.edit { it[K.hijriFormat] = hijri; it[K.gregFormat] = greg }
    suspend fun setMushafBg(id: String) = context.store.edit { it[K.mushafBg] = id }
    suspend fun setAlerts(a: AlertsConfig) = context.store.edit { it[K.alerts] = a.encode() }
    suspend fun setLang(code: String) = context.store.edit { it[K.lang] = code }
    suspend fun setPrayer(c: PrayerConfig) = context.store.edit { it[K.prayer] = c.encode() }
    suspend fun setAutoLocate(on: Boolean) = context.store.edit { it[K.autoLocate] = on }

    suspend fun setTasbih(list: List<Pair<String, Int>>) = context.store.edit { p ->
        p[K.tasbih] = list.joinToString("\u001E") { (t, n) -> t.replace('\u001E', ' ').replace('\u001F', ' ') + "\u001F" + n }
    }

    /** Marks a verse (words = which words; empty = the whole verse) to review; empty set + [remove] takes it off. */
    suspend fun setMistake(key: String, words: Set<Int>?, ) = context.store.edit { p ->
        val list = parseMistakes(p[K.mistakes]).filter { it.first != key }
        p[K.mistakes] = writeMistakes(if (words == null) list else listOf(key to words) + list)
    }

    suspend fun markAdhkarDone(id: String, at: Long = System.currentTimeMillis()) = context.store.edit { p ->
        val m = p[K.adhkarDone].orEmpty().split(';').filter { it.isNotBlank() && !it.startsWith("$id=") }
        p[K.adhkarDone] = (m + "$id=$at").joinToString(";")
        // a section finished is ticked in the day's tracker too
        p[K.track] = writeTrack(parseTrack(p[K.track]).let { t -> t + (today() to (t[today()].orEmpty() + id)) })
    }

    private fun parseTrack(raw: String?): Map<String, Set<String>> = raw.orEmpty().split(';').mapNotNull { e ->
        val (d, ids) = e.split('=').takeIf { it.size == 2 } ?: return@mapNotNull null
        d to ids.split(',').filter { it.isNotBlank() }.toSet()
    }.toMap()

    private fun writeTrack(t: Map<String, Set<String>>): String =
        t.entries.sortedByDescending { it.key }.take(90).joinToString(";") { (d, ids) -> d + "=" + ids.joinToString(",") }

    /** Ticks (or unticks) one deed for a day in «متابعة العبادات». */
    suspend fun setTracked(day: String, id: String, on: Boolean) = context.store.edit { p ->
        val t = parseTrack(p[K.track])
        val ids = t[day].orEmpty().let { if (on) it + id else it - id }
        p[K.track] = writeTrack(t + (day to ids))
    }

    suspend fun setTafsir(on: Boolean) = context.store.edit { it[K.tafsir] = on }
    suspend fun setTranslation(on: Boolean) = context.store.edit { it[K.translation] = on }
    suspend fun setVerseMode(on: Boolean) = context.store.edit { it[K.verseMode] = on }
    suspend fun setTajweed(on: Boolean) = context.store.edit { it[K.tajweed] = on }
    suspend fun setSection(s: String) = context.store.edit { it[K.section] = s }
    suspend fun setLocation(lat: Double, lng: Double, place: String, custom: Boolean = false) = context.store.edit {
        it[K.lat] = lat; it[K.lng] = lng; it[K.place] = place; it[K.placeCustom] = custom
    }

    /**
     * A location found by the phone: within 2 km of the place already set, a name the user wrote
     * stays (the phone's lookup may call the same place «قسم أول أكتوبر»); further away it is a new place.
     * Returns the name now used.
     */
    suspend fun setFoundLocation(cur: AppPrefs, lat: Double, lng: Double, found: String): String {
        val moved = cur.lat == null || cur.lng == null || Locator.km(cur.lat, cur.lng, lat, lng) > 2.0
        val keep = !moved && (cur.placeCustom || found.isBlank())
        val name = if (keep) cur.place else found
        if (moved || name != cur.place) setLocation(lat, lng, name, custom = keep && cur.placeCustom)
        return name
    }

    /**
     * [newSession]: the mushaf was just opened. Opening it on a page that is already one of the
     * places continues that place; anywhere else starts a new place (same surah or not). After
     * that, turning pages moves the current place along with the reader.
     */
    suspend fun markRead(page: Int, verseKey: String, newSession: Boolean = false) = context.store.edit { p ->
        p[K.lastPage] = page
        p[K.lastVerse] = verseKey
        // newest first, three places
        val old = parseRecent(p[K.recent])
        val rest = if (newSession) old.filter { abs(it.first - page) > 1 } // reopening a place picks it up
        else old.drop(1).filter { it.first != page } // still reading: the newest place moves on
        val list = (listOf(page to verseKey) + rest).take(3)
        p[K.recent] = list.joinToString(";") { "${it.first}|${it.second}" }
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

    private fun parseRecent(raw: String?): List<Pair<Int, String>> =
        raw.orEmpty().split(';').mapNotNull { e ->
            val page = e.substringBefore('|').toIntOrNull() ?: return@mapNotNull null
            if (page !in 1..604) null else page to e.substringAfter('|', "")
        }
}
