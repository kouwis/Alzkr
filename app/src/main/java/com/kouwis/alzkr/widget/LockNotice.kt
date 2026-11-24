package com.kouwis.alzkr.widget

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.kouwis.alzkr.MainActivity
import com.kouwis.alzkr.R
import com.kouwis.alzkr.data.ArabicText
import com.kouwis.alzkr.data.t

/*
 * «إظهار الصلاة على شاشة القفل»: a quiet notification that stays, showing the next prayer, its time
 * and a live countdown, on the lock screen and in the bar. It is redrawn with the widgets' minute tick
 * (and its countdown runs on its own between). Off unless chosen.
 */
object LockNotice {
    private const val CHANNEL = "lock_prayer"
    private const val ID = 7301

    private fun sp(context: Context) = context.getSharedPreferences("widget_state", Context.MODE_PRIVATE)
    fun enabled(context: Context) = sp(context).getBoolean("lock_notice", false)

    fun set(context: Context, on: Boolean) {
        sp(context).edit().putBoolean("lock_notice", on).apply()
        if (!on) NotificationManagerCompat.from(context).cancel(ID)
        WidgetUpdater.refresh(context)
    }

    suspend fun update(context: Context) {
        if (!enabled(context)) return
        val d = WidgetData.load(context)
        val nm = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, t("الصلاة على شاشة القفل"), NotificationManager.IMPORTANCE_LOW).apply {
                setSound(null, null); enableVibration(false); setShowBadge(false)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            })
        }
        val open = PendingIntent.getActivity(
            context, 73, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val title = ArabicText.digitsOf("${d.headline} · ${d.targetTime}")
        val text = ArabicText.digitsOf(listOf(d.remaining, d.place).filter { it.isNotBlank() }.joinToString(" · "))
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title)
            .setContentText(text)
            .setSubText(ArabicText.digitsOf(if (d.iqamaPhase) d.sub else "${d.weekday} · ${d.hijriDay} ${d.hijriMonth}"))
            .setOngoing(true).setOnlyAlertOnce(true).setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            // a live countdown to the prayer
            .setWhen(d.targetMs).setShowWhen(true).setUsesChronometer(true).setChronometerCountDown(true)
            .setContentIntent(open)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(ID, n) }
    }
}
