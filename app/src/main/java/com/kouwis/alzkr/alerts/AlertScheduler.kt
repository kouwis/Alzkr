package com.kouwis.alzkr.alerts

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import com.kouwis.alzkr.MainActivity
import com.kouwis.alzkr.data.AlertEvent
import com.kouwis.alzkr.data.AlertEvents
import com.kouwis.alzkr.data.I18n
import com.kouwis.alzkr.data.Lang
import com.kouwis.alzkr.data.Prefs
import com.kouwis.alzkr.data.Sounds
import com.kouwis.alzkr.data.t
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer

/**
 * Keeps the next alert set with the phone's AlarmManager. Only the next moment is set (with every
 * event due at that minute); when it fires, the one after it is set. Alarms («المنبّهات») are set
 * apart with setAlarmClock, so they ring like the clock app's and show in the status bar.
 */
object AlertScheduler {
    const val ACTION_FIRE = "com.kouwis.alzkr.FIRE"
    const val EXTRA_EVENTS = "events"
    private const val RC_NOTIFY = 1
    private const val RC_ALARM = 2
    const val RC_SNOOZE = 3
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun reschedule(context: Context) {
        val app = context.applicationContext
        scope.launch { runCatching { rescheduleNow(app) } }
    }

    suspend fun rescheduleNow(context: Context) {
        val prefs = Prefs(context).flow.first()
        I18n.set(context, Lang.of(prefs.lang))
        Channels.ensure(context)
        val catalog = Sounds.catalog(context)
        Silence.settle(context, prefs.alerts.silent.on)
        val events = AlertEvents.upcoming(prefs, catalog, System.currentTimeMillis() + 1_000)
        val am = context.getSystemService(AlarmManager::class.java)
        setNext(context, am, events.filter { it.kind != "alarm" }, RC_NOTIFY, clock = false)
        setNext(context, am, events.filter { it.kind == "alarm" }, RC_ALARM, clock = true)
    }

    private fun setNext(context: Context, am: AlarmManager, events: List<AlertEvent>, code: Int, clock: Boolean) {
        val first = events.firstOrNull()
        if (first == null) { am.cancel(pending(context, code, emptyList())); return }
        val due = events.filter { it.at == first.at }
        val pi = pending(context, code, due)
        setAt(context, am, first.at, pi, clock)
    }

    fun setAt(context: Context, am: AlarmManager, at: Long, pi: PendingIntent, clock: Boolean) {
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        when {
            exact && clock -> am.setAlarmClock(AlarmManager.AlarmClockInfo(at, openApp(context, null)), pi)
            exact -> am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else -> am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi) // may come a few minutes late
        }
    }

    fun pending(context: Context, code: Int, events: List<AlertEvent>): PendingIntent {
        val i = Intent(context, AlertReceiver::class.java).setAction(ACTION_FIRE)
            .putExtra(EXTRA_EVENTS, AlertEvent.json.encodeToString(ListSerializer(AlertEvent.serializer()), events))
        return PendingIntent.getBroadcast(context, code, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun decode(raw: String?): List<AlertEvent> = raw?.let {
        runCatching { AlertEvent.json.decodeFromString(ListSerializer(AlertEvent.serializer()), it) }.getOrNull()
    }.orEmpty()

    /** Opens the app, on an adhkar section when [open] names one. */
    fun openApp(context: Context, open: String?, code: Int = 0): PendingIntent {
        val i = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (open != null) i.putExtra(MainActivity.EXTRA_OPEN, open)
        return PendingIntent.getActivity(context, 100 + code, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun canExact(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
}

/** The notification channels (names in the language chosen when they are first made). */
object Channels {
    const val ADHAN = "adhan"        // the adhan plays through the app, so the channel itself is silent
    const val ALERT = "alerts"       // the phone's notification sound
    const val VIBRATE = "alerts_vib" // vibration only
    const val ALARM = "alarms"       // alarms: full screen, sound through the app
    const val REMINDER = "reminders"

    fun ensure(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        fun make(id: String, name: String, importance: Int, sound: Boolean, vibrate: Boolean) {
            if (nm.getNotificationChannel(id) != null) return
            nm.createNotificationChannel(NotificationChannel(id, name, importance).apply {
                if (!sound) setSound(null, null)
                else setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT).build(),
                )
                enableVibration(vibrate)
                if (vibrate) vibrationPattern = longArrayOf(0, 400, 200, 400)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            })
        }
        make(ADHAN, t("الأذان"), NotificationManager.IMPORTANCE_HIGH, sound = false, vibrate = false)
        make(ALERT, t("تنبيهات الصلاة"), NotificationManager.IMPORTANCE_HIGH, sound = true, vibrate = true)
        make(VIBRATE, t("تنبيهات بالاهتزاز"), NotificationManager.IMPORTANCE_HIGH, sound = false, vibrate = true)
        make(ALARM, t("المنبّهات"), NotificationManager.IMPORTANCE_HIGH, sound = false, vibrate = false)
        make(REMINDER, t("التذكيرات"), NotificationManager.IMPORTANCE_DEFAULT, sound = true, vibrate = false)
    }
}
