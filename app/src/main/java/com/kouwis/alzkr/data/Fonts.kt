package com.kouwis.alzkr.data

import android.content.Context
import android.graphics.Typeface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * The 604 KFGQPC QCF V2 page fonts (~200 MB together) are too big for the APK,
 * so each page font is downloaded once on first use and kept on the phone.
 * No server of ours: the files come straight from a public GitHub repository.
 */
object PageFonts {
    private const val BASE = "https://raw.githubusercontent.com/nuqayah/qpc-fonts/master/mushaf-v2/QCF2%03d.ttf"

    private val typefaces = ConcurrentHashMap<Int, Typeface>()
    private val locks = ConcurrentHashMap<Int, Mutex>()

    fun cached(page: Int): Typeface? = typefaces[page]

    private fun dir(context: Context) = File(context.filesDir, "qcf").apply { mkdirs() }

    fun isDownloaded(context: Context, page: Int) = File(dir(context), "p$page.ttf").let { it.exists() && it.length() > 0 }

    /** Returns the page's typeface, downloading it if needed; null when offline and not yet downloaded. */
    suspend fun get(context: Context, page: Int): Typeface? {
        typefaces[page]?.let { return it }
        val lock = locks.getOrPut(page) { Mutex() }
        return lock.withLock {
            typefaces[page] ?: withContext(Dispatchers.IO) {
                val file = File(dir(context), "p$page.ttf")
                if (!file.exists() || file.length() == 0L) {
                    if (!download(page, file)) return@withContext null
                }
                runCatching { Typeface.createFromFile(file) }
                    .onFailure { file.delete() }
                    .getOrNull()
                    ?.also { typefaces[page] = it }
            }
        }
    }

    private fun download(page: Int, target: File): Boolean = runCatching {
        val tmp = File(target.parentFile, target.name + ".part")
        val conn = URL(BASE.format(page)).openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 30000
        try {
            if (conn.responseCode != 200) return false
            conn.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
        } finally {
            conn.disconnect()
        }
        tmp.renameTo(target)
    }.getOrDefault(false)
}
