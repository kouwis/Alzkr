package com.kouwis.alzkr.data

import android.content.Context
import android.graphics.Typeface
import androidx.compose.ui.text.font.FontFamily
import java.util.concurrent.ConcurrentHashMap
import androidx.compose.ui.text.font.Typeface as ComposeTypeface

/**
 * KFGQPC fonts bundled in the APK (no internet needed):
 * - 604 QCF V1 page fonts — the classic Madinah mushaf (1405 print), one font per page.
 * - QCF_BSML — the real surah header frame, surah names and the basmala.
 * - Uthman Taha Naskh — the complex's naskh font, used for headings.
 */
object PageFonts {
    private val typefaces = ConcurrentHashMap<Int, Typeface>()
    private lateinit var appContext: Context

    fun init(context: Context) { appContext = context.applicationContext }

    fun cached(page: Int): Typeface? = typefaces[page]

    /** Loads (and caches) the font of a mushaf page; returns null only if the asset is missing. */
    fun get(page: Int): Typeface? = typefaces[page] ?: runCatching {
        Typeface.createFromAsset(appContext.assets, "qcf/p$page.ttf")
    }.getOrNull()?.also { typefaces[page] = it }

    @Suppress("UNUSED_PARAMETER")
    suspend fun get(context: Context, page: Int): Typeface? = get(page)
}

object AppFonts {
    private lateinit var ctx: Context
    fun init(context: Context) { ctx = context.applicationContext; PageFonts.init(context) }

    private fun asset(name: String): Typeface =
        runCatching { Typeface.createFromAsset(ctx.assets, "fonts/$name") }.getOrDefault(Typeface.DEFAULT)

    val bsml: Typeface by lazy { asset("bsml.ttf") }
    val naskhTf: Typeface by lazy { asset("naskh.otf") }
    val naskhBoldTf: Typeface by lazy { asset("naskh_bold.otf") }

    val hafsTf: Typeface by lazy { asset("hafs.otf") }
    val hafs: FontFamily by lazy { FontFamily(ComposeTypeface(hafsTf)) }
    val naskh: FontFamily by lazy { FontFamily(ComposeTypeface(naskhTf)) }
    val naskhBold: FontFamily by lazy { FontFamily(ComposeTypeface(naskhBoldTf)) }

    /** QCF_BSML character for a surah name glyph (verified against the font). */
    fun surahNameChar(n: Int): Char = when (n) {
        in 1..35 -> (0x5D + n - 1).toChar()
        in 36..47 -> (0xA1 + n - 36).toChar()
        in 48..56 -> (0xAE + n - 48).toChar()
        else -> (0xB8 + n - 57).toChar()
    }

    const val FRAME = 'ò'
    const val BASMALA = "#\"!"
}
