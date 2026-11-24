package com.kouwis.alzkr.alerts

import android.app.NotificationManager
import android.content.Context

/**
 * Turns the phone's total silence (Do Not Disturb, «none») on before a prayer and back to what it
 * was after. It only restores what it changed itself: if the user changed the mode in between,
 * their choice stays. Needs the user's grant of Do Not Disturb access.
 */
object Silence {
    private const val PREFS = "silence"

    fun allowed(context: Context): Boolean =
        context.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted

    fun on(context: Context, untilMs: Long) {
        if (!allowed(context)) return
        val nm = context.getSystemService(NotificationManager::class.java)
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val current = nm.currentInterruptionFilter
        if (current == NotificationManager.INTERRUPTION_FILTER_NONE && !sp.getBoolean("ours", false)) return // already silent by the user
        if (!sp.getBoolean("ours", false)) sp.edit().putInt("previous", current).apply()
        runCatching { nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE) }
        sp.edit().putBoolean("ours", true).putLong("until", untilMs).apply()
    }

    fun off(context: Context) {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!sp.getBoolean("ours", false)) return
        val nm = context.getSystemService(NotificationManager::class.java)
        if (allowed(context) && nm.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_NONE) {
            val prev = sp.getInt("previous", NotificationManager.INTERRUPTION_FILTER_ALL)
            runCatching { nm.setInterruptionFilter(if (prev == NotificationManager.INTERRUPTION_FILTER_UNKNOWN) NotificationManager.INTERRUPTION_FILTER_ALL else prev) }
        }
        sp.edit().clear().apply()
    }

    /** After a restart, or when the feature is turned off: a silence that should have ended is ended. */
    fun settle(context: Context, featureOn: Boolean) {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!sp.getBoolean("ours", false)) return
        if (!featureOn || System.currentTimeMillis() >= sp.getLong("until", 0)) off(context)
    }
}
