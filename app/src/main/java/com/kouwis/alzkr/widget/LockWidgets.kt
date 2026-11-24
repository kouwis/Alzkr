package com.kouwis.alzkr.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.kouwis.alzkr.MainActivity
import com.kouwis.alzkr.R
import com.kouwis.alzkr.data.ArabicText
import com.kouwis.alzkr.data.t
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/*
 * The lock-screen widgets (Samsung One UI's lock screen and always-on display). The phone draws the
 * capsule and may tint the text, so they are plain white text in the phone's layout, not a picture.
 */
object LockWidgets {
    private val kinds = WidgetKind.entries.filter { it.lock }

    suspend fun updateAll(context: Context) {
        val m = AppWidgetManager.getInstance(context)
        val placed = kinds.associateWith { k -> runCatching { m.getAppWidgetIds(ComponentName(context, k.receiver())) }.getOrDefault(IntArray(0)) }
        if (placed.values.all { it.isEmpty() }) return
        val d = WidgetData.load(context)
        placed.forEach { (k, ids) -> if (ids.isNotEmpty()) runCatching { m.updateAppWidget(ids, views(context, k, d)) } }
    }

    fun views(context: Context, k: WidgetKind, d: WidgetSnapshot): RemoteViews {
        fun dg(s: String) = ArabicText.digitsOf(s)
        val open = PendingIntent.getActivity(
            context, 80 + k.ordinal,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        if (k == WidgetKind.LockTimes) {
            val v = RemoteViews(context.packageName, R.layout.lock_times)
            val five = d.rows.filter { it.prayer.name != "Sunrise" }
            val names = listOf(R.id.n0, R.id.n1, R.id.n2, R.id.n3, R.id.n4)
            val times = listOf(R.id.t0, R.id.t1, R.id.t2, R.id.t3, R.id.t4)
            five.take(5).forEachIndexed { i, r ->
                v.setTextViewText(names[i], r.name)
                v.setTextViewText(times[i], dg(r.time))
            }
            v.setOnClickPendingIntent(android.R.id.background, open)
            return v
        }
        val v = RemoteViews(context.packageName, R.layout.lock_lines)
        fun line(id: Int, s: String?, sp: Float? = null) {
            if (s.isNullOrBlank()) { v.setViewVisibility(id, View.GONE); return }
            v.setViewVisibility(id, View.VISIBLE)
            v.setTextViewText(id, s)
            if (sp != null) v.setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_SP, sp)
        }
        val hijri = dg("${d.hijriDay} ${d.hijriMonth}")
        when (k) {
            WidgetKind.LockDay -> { line(R.id.line1, d.weekday, 22f); line(R.id.line2, null); line(R.id.line3, null) }
            WidgetKind.LockHijri -> { line(R.id.line1, hijri, 17f); line(R.id.line2, dg(t("{0} هـ", d.hijriYear.toString()))); line(R.id.line3, null) }
            WidgetKind.LockDates -> { line(R.id.line1, d.weekday, 13f); line(R.id.line2, dg("$hijri ${d.hijriYear}")); line(R.id.line3, dg(d.gregorian)) }
            WidgetKind.LockNext -> { line(R.id.line1, "${d.headline} ${dg(d.targetTime)}", 16f); line(R.id.line2, dg(d.remaining)); line(R.id.line3, null) }
            WidgetKind.LockBar -> {
                line(R.id.line1, d.headline, 15f); line(R.id.line2, dg(d.targetTime)); line(R.id.line3, null)
                v.setViewVisibility(R.id.bar, View.VISIBLE)
                v.setProgressBar(R.id.bar, 1000, (d.progress.coerceIn(0f, 1f) * 1000).toInt(), false)
            }
            else -> {
                val list = WidgetRenderer.shortAdhkar
                val txt = list[(java.time.LocalDate.now().dayOfYear * 24 + java.time.LocalTime.now().hour) % list.size]
                line(R.id.line1, txt, 14f); line(R.id.line2, null); line(R.id.line3, null)
            }
        }
        v.setOnClickPendingIntent(android.R.id.background, open)
        return v
    }
}

abstract class LockWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                runCatching { LockWidgets.updateAll(context.applicationContext) }
                WidgetUpdater.scheduleNext(context.applicationContext)
            } finally { pending.finish() }
        }
    }
}

class LockDayWidgetReceiver : LockWidgetProvider()
class LockHijriWidgetReceiver : LockWidgetProvider()
class LockDatesWidgetReceiver : LockWidgetProvider()
class LockNextWidgetReceiver : LockWidgetProvider()
class LockBarWidgetReceiver : LockWidgetProvider()
class LockTimesWidgetReceiver : LockWidgetProvider()
class LockDhikrWidgetReceiver : LockWidgetProvider()
