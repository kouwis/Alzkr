package com.kouwis.alzkr.alerts

import android.Manifest
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.kouwis.alzkr.R
import com.kouwis.alzkr.data.AlertEvent
import com.kouwis.alzkr.data.I18n
import com.kouwis.alzkr.data.Lang
import com.kouwis.alzkr.data.Prefs
import com.kouwis.alzkr.data.t
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Fires the alerts that are due, then sets the next ones. Also re-sets everything after a restart or a clock change. */
class AlertReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(Dispatchers.Default).launch {
            try {
                if (intent.action == AlertScheduler.ACTION_FIRE) {
                    val prefs = Prefs(app).flow.first()
                    I18n.set(app, Lang.of(prefs.lang))
                    Channels.ensure(app)
                    val now = System.currentTimeMillis()
                    // a moment missed by more than 10 minutes (phone off) is not sounded late
                    val events = AlertScheduler.decode(intent.getStringExtra(AlertScheduler.EXTRA_EVENTS))
                    // a silence starts even if late (it ends by itself); other alerts missed by 10+ minutes are not sounded late
                    events.filter { now - it.at < 10 * 60_000L || it.kind.startsWith("silent") }.forEach { e ->
                        val until = if (e.kind == "silent_on") com.kouwis.alzkr.data.AlertEvents
                            .upcoming(prefs, com.kouwis.alzkr.data.Sounds.catalog(app), e.at)
                            .firstOrNull { it.kind == "silent_off" && it.prayer == e.prayer }?.at else null
                        if (e.kind == "silent_on" && until != null && now >= until) return@forEach
                        fire(app, e, prefs.alerts.playOnSilent, until)
                    }
                }
                AlertScheduler.rescheduleNow(app)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        fun fire(context: Context, e: AlertEvent, playOnSilent: Boolean, silenceUntil: Long? = null) {
            if (e.kind == "silent_on") { Silence.on(context, silenceUntil ?: (e.at + 60 * 60_000L)); return }
            if (e.kind == "silent_off") { Silence.off(context); return }
            if (e.kind == "alarm") {
                SoundService.start(context, SoundService.ACTION_ALARM, e)
                return
            }
            val recording = e.sound.startsWith("full:") || e.sound.startsWith("short:")
            val am = context.getSystemService(AudioManager::class.java)
            val quiet = am.ringerMode != AudioManager.RINGER_MODE_NORMAL
            when {
                recording && (!quiet || playOnSilent) -> SoundService.start(context, SoundService.ACTION_PLAY, e)
                e.sound == "vib" || (recording && quiet) -> notify(context, e, Channels.VIBRATE)
                e.kind == "reminder" -> notify(context, e, Channels.REMINDER)
                else -> notify(context, e, Channels.ALERT)
            }
        }

        fun notify(context: Context, e: AlertEvent, channel: String) {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) return
            val n = NotificationCompat.Builder(context, channel)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(e.title)
                .setContentText(e.text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(e.text))
                .setCategory(if (e.kind == "reminder") NotificationCompat.CATEGORY_REMINDER else NotificationCompat.CATEGORY_EVENT)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(AlertScheduler.openApp(context, e.open, e.key.hashCode() and 0xFFFF))
                .build()
            context.getSystemService(NotificationManager::class.java).notify(e.key.hashCode(), n)
        }
    }
}

/** After a restart, an app update, or a change of time or time zone, everything is set again. */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                AlertScheduler.rescheduleNow(context.applicationContext)
                runCatching { com.kouwis.alzkr.widget.WidgetUpdater.updateAll(context.applicationContext) }
            } finally { pending.finish() }
        }
    }
}
