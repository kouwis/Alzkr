package com.kouwis.alzkr.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * One piece of a dhikr: plain text [t], whole verses [q] = (surah, first, last) shown from the mushaf,
 * or [u] = a few words of the verse [ref] = (surah, ayah) in Uthmani script.
 */
@Serializable
data class DhikrPart(val t: String? = null, val q: List<Int>? = null, val u: String? = null, val ref: List<Int>? = null)

/** [count] 0 = no set number (an open counter). */
@Serializable
data class Dhikr(
    val parts: List<DhikrPart>,
    val count: Int = 1,
    val label: String = "",
    val fadl: String = "",
    val note: String = "",
    val src: String = "",
    val check: String = "",
    /** a picture for a sunnah (see SunnahIcon) */
    val icon: String = "",
)

@Serializable
data class AdhkarSection(
    val id: String,
    val title: String,
    val done: String,
    val items: List<Dhikr>,
    /** "" for the main sections, "friday", "occasion", or "hisn" for the other chapters of حصن المسلم */
    val group: String = "",
)

@Serializable
private data class AdhkarFile(val sections: List<AdhkarSection>)

object Adhkar {
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    @Volatile private var cached: Map<String, AdhkarSection>? = null

    fun peek(): Map<String, AdhkarSection>? = cached

    suspend fun load(context: Context): Map<String, AdhkarSection> = cached ?: mutex.withLock {
        cached ?: withContext(Dispatchers.IO) {
            val text = context.applicationContext.assets.open("data/adhkar.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
            json.decodeFromString<AdhkarFile>(text).sections.associateBy { it.id }.also { cached = it }
        }
    }
}
