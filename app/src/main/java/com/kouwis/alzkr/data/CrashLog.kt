package com.kouwis.alzkr.data

import android.content.Context
import android.os.Build
import java.io.File

/**
 * Keeps the last crash on the phone (nothing is sent anywhere), so the user can share it from the
 * settings with «إرسال تقرير العطل».
 */
object CrashLog {
    @Volatile private var installed = false

    private fun file(context: Context) = File(context.filesDir, "last_crash.txt")

    fun install(context: Context) {
        if (installed) return
        installed = true
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            runCatching {
                val version = runCatching { app.packageManager.getPackageInfo(app.packageName, 0).versionName }.getOrNull()
                file(app).writeText(
                    "Alzkr $version · Android ${Build.VERSION.RELEASE} (${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}\n" +
                        "${java.time.LocalDateTime.now()} · thread ${thread.name}\n\n" + e.stackTraceToString().take(12_000),
                )
            }
            previous?.uncaughtException(thread, e)
        }
    }

    fun read(context: Context): String? = file(context).takeIf { it.exists() }?.readText()
    fun clear(context: Context) { file(context).delete() }
}
