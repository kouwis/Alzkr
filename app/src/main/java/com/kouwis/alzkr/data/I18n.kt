package com.kouwis.alzkr.data

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** The languages of the app's interface. The Quran, the adhkar and the hadiths stay in Arabic in all of them. */
enum class Lang(val code: String, val native: String, val rtl: Boolean) {
    AR("ar", "العربية", true),
    EN("en", "English", false),
    FR("fr", "Français", false),
    TR("tr", "Türkçe", false),
    DE("de", "Deutsch", false),
    ES("es", "Español", false),
    ID("id", "Bahasa Indonesia", false),
    MS("ms", "Bahasa Melayu", false),
    ;

    companion object {
        fun of(code: String?) = entries.firstOrNull { it.code == code } ?: AR
    }
}

/**
 * The interface's words in each language (assets/i18n/<code>.json: Arabic text → its translation).
 * The Arabic text itself is the key, so a string with no translation simply shows in Arabic.
 * Placeholders are {0}, {1}… in both.
 */
object I18n {
    @Volatile var lang: Lang = Lang.AR
        private set
    private var table: Map<String, String> = emptyMap()

    fun set(context: Context, l: Lang) {
        appLocale(context, l)
        if (l == lang && (l == Lang.AR || table.isNotEmpty())) return
        table = if (l == Lang.AR) emptyMap() else runCatching {
            val text = context.applicationContext.assets.open("i18n/${l.code}.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
            Json.parseToJsonElement(text).jsonObject.mapValues { it.value.jsonPrimitive.content }
        }.getOrDefault(emptyMap())
        lang = l
    }

    /**
     * The app's own language for the system too (Android 13 and later), so what the phone draws from
     * the app's resources follows it: the widgets' names and samples in the phone's widget lists.
     */
    private fun appLocale(context: Context, l: Lang) {
        if (android.os.Build.VERSION.SDK_INT < 33) return
        runCatching {
            val lm = context.getSystemService(android.app.LocaleManager::class.java)
            val want = android.os.LocaleList.forLanguageTags(l.code)
            if (lm.applicationLocales != want) lm.applicationLocales = want
        }
    }

    fun tr(ar: String, vararg args: Any?): String {
        var s = table[ar] ?: ar
        args.forEachIndexed { i, a -> s = s.replace("{$i}", a.toString()) }
        return s
    }
}

/** The interface text [ar] in the chosen language; {0}, {1}… are filled with [args]. */
fun t(ar: String, vararg args: Any?): String = I18n.tr(ar, *args)
