package com.kouwis.alzkr.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class LineDto(val t: String, val s: Int = 0, val w: List<List<String>> = emptyList())

@Serializable
data class SurahDto(val n: Int, val name: String, val ayahs: Int, val type: String, val page: Int)

@Serializable
data class MarkDto(val n: Int, val s: Int, val a: Int, val page: Int, val text: String = "")

@Serializable
data class MetaDto(
    val surahs: List<SurahDto>,
    val juz: List<MarkDto>,
    val hizb: List<MarkDto>,
    val rub: List<MarkDto>,
    val basmala: String? = null,
    /** per-page stroke (fraction of font size) that evens out boldness across page fonts */
    val bold: List<Float> = emptyList(),
    /** width of a full line in em; the same text size is used for every page */
    val lineEm: Float = 0f,
    /** same for the tajweed (QCF V4, 1421 print) layout */
    val lineEmTajweed: Float = 0f,
)

@Serializable
data class VersesDto(
    val page: List<List<Int>>,
    val simple: List<List<String>>,
    val clean: List<List<String>>,
)

@Serializable
data class GlyphChunk(val p: Int, val g: String)

/** One word on a page: the verse it belongs to and its QCF glyph string. */
data class PageWord(val surah: Int, val ayah: Int, val glyph: String) {
    val key: String get() = "$surah:$ayah"
}

sealed interface PageLine {
    data class Header(val surah: Int) : PageLine
    data object Basmala : PageLine
    data class Text(val words: List<PageWord>) : PageLine
}

class QuranData(
    val pages: List<List<PageLine>>,
    /** the colour-coded tajweed mushaf: 1421-print line breaks with QCF V4 glyphs */
    val pagesTajweed: List<List<PageLine>>,
    val meta: MetaDto,
    val verses: VersesDto,
    val glyphs: Map<String, List<GlyphChunk>>,
    val tafsir: List<List<String>>,
    val translation: List<List<String>>,
) {
    val surahs get() = meta.surahs

    fun surahName(n: Int) = meta.surahs[n - 1].name
    fun pageOf(s: Int, a: Int) = verses.page[s - 1][a - 1]
    fun simple(s: Int, a: Int) = verses.simple[s - 1][a - 1]
    fun tafsirOf(s: Int, a: Int) = tafsir.getOrNull(s - 1)?.getOrNull(a - 1).orEmpty()
    fun translationOf(s: Int, a: Int) = translation.getOrNull(s - 1)?.getOrNull(a - 1).orEmpty()

    /** True when the tajweed print (1421, its own page breaks) is what the reader sees. */
    fun tajweedReady(tajweed: Boolean) = tajweed && pagesTajweed.size == 604

    private fun pagesFor(tajweed: Boolean) = if (tajweedReady(tajweed)) pagesTajweed else pages

    /** Verses (surah, ayah) shown on a page, in reading order (also those that only end or start there). */
    fun versesOnPage(page: Int, tajweed: Boolean = false): List<Pair<Int, Int>> {
        val out = LinkedHashSet<Pair<Int, Int>>()
        pagesFor(tajweed)[page - 1].forEach { line ->
            if (line is PageLine.Text) line.words.forEach { out.add(it.surah to it.ayah) }
        }
        return out.toList()
    }

    private val tajweedVersePage: Map<String, Int> by lazy {
        val m = HashMap<String, Int>()
        pagesTajweed.forEachIndexed { i, lines ->
            lines.forEach { l -> if (l is PageLine.Text) l.words.forEach { m.putIfAbsent(it.key, i + 1) } }
        }
        m
    }

    /** The page a verse starts on, in the print being read. */
    fun pageOf(s: Int, a: Int, tajweed: Boolean): Int =
        if (tajweedReady(tajweed)) tajweedVersePage["$s:$a"] ?: pageOf(s, a) else pageOf(s, a)

    /** The surah whose text dominates the top of a page. */
    fun surahOfPage(page: Int, tajweed: Boolean = false): Int {
        pagesFor(tajweed)[page - 1].forEach { line ->
            if (line is PageLine.Text && line.words.isNotEmpty()) return line.words.first().surah
            if (line is PageLine.Header) return line.surah
        }
        return 1
    }

    fun juzOfPage(page: Int, tajweed: Boolean = false) = meta.juz.lastOrNull { pageOf(it.s, it.a, tajweed) <= page }?.n ?: 1
    fun hizbOfPage(page: Int, tajweed: Boolean = false) = meta.hizb.lastOrNull { pageOf(it.s, it.a, tajweed) <= page }?.n ?: 1

    /** Flat list of all verses for the verse-by-verse mode. */
    val allVerses: List<Pair<Int, Int>> by lazy {
        buildList { meta.surahs.forEach { s -> for (a in 1..s.ayahs) add(s.n to a) } }
    }

    fun indexOfVerse(s: Int, a: Int): Int {
        var idx = 0
        for (i in 0 until s - 1) idx += meta.surahs[i].ayahs
        return idx + a - 1
    }
}

object Quran {
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    @Volatile private var cached: QuranData? = null

    fun peek(): QuranData? = cached

    suspend fun load(context: Context): QuranData = cached ?: mutex.withLock {
        cached ?: withContext(Dispatchers.IO) {
            val assets = context.applicationContext.assets
            fun read(name: String) = assets.open("data/$name").bufferedReader(Charsets.UTF_8).use { it.readText() }

            fun parsePages(name: String): List<List<PageLine>> = json.decodeFromString<List<List<LineDto>>>(read(name)).map { lines ->
                lines.map { l ->
                    when (l.t) {
                        "h" -> PageLine.Header(l.s)
                        "b" -> PageLine.Basmala
                        else -> PageLine.Text(l.w.map { w ->
                            val parts = w[0].split(":")
                            PageWord(parts[0].toInt(), parts[1].toInt(), w[1])
                        })
                    }
                }
            }
            QuranData(
                pages = parsePages("pages.json"),
                pagesTajweed = runCatching { parsePages("pages_tajweed.json") }.getOrDefault(emptyList()),
                meta = json.decodeFromString(read("meta.json")),
                verses = json.decodeFromString(read("verses.json")),
                glyphs = json.decodeFromString(read("glyphs.json")),
                tafsir = json.decodeFromString(read("tafsir_muyassar.json")),
                translation = json.decodeFromString(read("translation_en.json")),
            ).also { cached = it }
        }
    }
}

/** Arabic search normalisation; must match scripts/build_data.py normalize(). */
object ArabicText {
    private val tashkeel = Regex("[\\u0610-\\u061A\\u064B-\\u065F\\u0670\\u06D6-\\u06ED\\u0640]")
    private val alef = Regex("[إأآٱ]")
    private val spaces = Regex("\\s+")

    fun normalize(s: String): String = tashkeel.replace(s, "")
        .let { alef.replace(it, "ا") }
        .replace('ى', 'ي').replace('ة', 'ه').replace('ؤ', 'و').replace('ئ', 'ي')
        .let { spaces.replace(it, " ") }
        .trim()

    private val digits = charArrayOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')
    fun num(n: Int): String = n.toString().map { if (it in '0'..'9') digits[it - '0'] else it }.joinToString("")
}
