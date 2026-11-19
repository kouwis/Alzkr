package com.kouwis.alzkr.data

import android.content.Context
import android.graphics.Typeface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.GZIPInputStream

/**
 * The colour-coded tajweed mushaf: 604 KFGQPC QCF V4 page fonts with the tajweed colours
 * inside them (COLR/CPAL). ~70 MB, so they are downloaded once (from GitHub, no server of
 * ours) when the reader turns tajweed on, and kept on the phone.
 * For the dark theme a copy of each font gets its black ink recoloured to the light ink.
 */
object TajweedFonts {
    private const val URL_FMT =
        "https://raw.githubusercontent.com/mahmoud123-karash/quran_fonts/main/assets/fonts/qcf_tajweed/QCF4%03d_COLOR-Regular.ttf.gz"
    private val faces = ConcurrentHashMap<String, Typeface>()
    private val locks = ConcurrentHashMap<Int, Mutex>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var prefetch: Job? = null

    private fun dir(ctx: Context) = File(ctx.filesDir, "tajweed").apply { mkdirs() }
    private fun file(ctx: Context, page: Int) = File(dir(ctx), "p$page.ttf")
    private fun darkFile(ctx: Context, page: Int) = File(dir(ctx), "p${page}_dark.ttf")

    fun cached(page: Int, dark: Boolean): Typeface? = faces["$page-$dark"]

    fun downloadedCount(ctx: Context): Int = (1..604).count { file(ctx, it).let { f -> f.exists() && f.length() > 0 } }

    suspend fun get(ctx: Context, page: Int, dark: Boolean): Typeface? {
        faces["$page-$dark"]?.let { return it }
        return locks.getOrPut(page) { Mutex() }.withLock {
            faces["$page-$dark"] ?: withContext(Dispatchers.IO) {
                val f = file(ctx, page)
                if (!(f.exists() && f.length() > 0) && !download(page, f)) return@withContext null
                val use = if (dark) {
                    val d = darkFile(ctx, page)
                    if (!(d.exists() && d.length() > 0)) runCatching { d.writeBytes(darkInk(f.readBytes())) }
                    d
                } else f
                runCatching { Typeface.createFromFile(use) }.getOrNull()?.also { faces["$page-$dark"] = it }
            }
        }
    }

    /** Downloads every page in the background (skips pages already on the phone). */
    fun prefetchAll(ctx: Context) {
        if (prefetch?.isActive == true) return
        val app = ctx.applicationContext
        prefetch = scope.launch {
            kotlinx.coroutines.delay(1500)
            for (p in 1..604) {
                val f = file(app, p)
                if (!(f.exists() && f.length() > 0)) download(p, f)
            }
        }
    }

    private fun download(page: Int, target: File): Boolean = runCatching {
        val tmp = File(target.parentFile, target.name + ".part")
        val conn = URL(URL_FMT.format(page)).openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 30000
        try {
            if (conn.responseCode != 200) return false
            GZIPInputStream(conn.inputStream).use { input -> tmp.outputStream().use { input.copyTo(it) } }
        } finally {
            conn.disconnect()
        }
        tmp.renameTo(target)
    }.getOrDefault(false)

    /** Copy of the font whose near-black palette colours become the dark theme's light ink. */
    private fun darkInk(bytes: ByteArray): ByteArray {
        val out = bytes.copyOf()
        val b = ByteBuffer.wrap(out).order(ByteOrder.BIG_ENDIAN)
        val numTables = b.getShort(4).toInt() and 0xFFFF
        for (i in 0 until numTables) {
            val rec = 12 + i * 16
            val tag = String(out, rec, 4, Charsets.ISO_8859_1)
            if (tag != "CPAL") continue
            val cpal = b.getInt(rec + 8)
            val numColorRecords = b.getShort(cpal + 6).toInt() and 0xFFFF
            val arr = cpal + b.getInt(cpal + 8)
            for (k in 0 until numColorRecords) {
                val o = arr + k * 4 // B, G, R, A
                val bl = out[o].toInt() and 0xFF
                val g = out[o + 1].toInt() and 0xFF
                val r = out[o + 2].toInt() and 0xFF
                if (r < 0x30 && g < 0x30 && bl < 0x30) {
                    out[o] = 0xD4.toByte(); out[o + 1] = 0xE2.toByte(); out[o + 2] = 0xE8.toByte()
                }
            }
        }
        return out
    }
}
