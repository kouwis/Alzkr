package com.kouwis.alzkr.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.glance.appwidget.AndroidRemoteViews
import com.kouwis.alzkr.R
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import com.kouwis.alzkr.MainActivity
import com.kouwis.alzkr.data.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/* ---------- the widgets ---------- */

/*
 * Each widget is one picture the app draws (WidgetRenderer), so the home screen shows exactly the
 * preview, with the app's fonts and nothing cut off. Over it lie invisible tap areas.
 */

/** Bumped after a tap or a minute's tick: placed widgets read their data again and redraw. */
object WidgetBus {
    val version = kotlinx.coroutines.flow.MutableStateFlow(0)
    fun bump() { version.value = version.value + 1 }
}

abstract class AlzkrWidget(val kind: WidgetKind) : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val first = WidgetData.load(context)
        val appWidgetId = runCatching { GlanceAppWidgetManager(context).getAppWidgetId(id) }.getOrDefault(-1)
        provideContent {
            // the session lives on between updates, so the data is read again on every bump
            val v by WidgetBus.version.collectAsState()
            val data by produceState(first, v) { value = runCatching { WidgetData.load(context) }.getOrDefault(value) }
            val style = remember(v) { WidgetStyles.get(context, appWidgetId, kind) }
            Body(context, appWidgetId, data, style)
        }
    }

    @Composable
    private fun Body(context: Context, appWidgetId: Int, d: WidgetSnapshot, s: WidgetStyle) {
        val size = LocalSize.current
        val wDp = size.width.value; val hDp = size.height.value
        val uri = remember(d, s, wDp, hDp) {
            runCatching {
                val bmp = WidgetRenderer.render(context, kind, s, d, wDp, hDp, context.resources.displayMetrics.density)
                WidgetImageProvider.save(context, "w${appWidgetId}_${wDp.toInt()}x${hDp.toInt()}", bmp)
            }.getOrNull()
        }
        val k = WidgetRenderer.scaleOf(kind, wDp, hDp)
        val open = Intent(context, MainActivity::class.java)
            .setData(Uri.parse("alzkr://widget/${kind.id}"))
            .putExtra(MainActivity.EXTRA_OPEN, "widget:${kind.id}")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        Box(GlanceModifier.fillMaxSize().clickable(actionStartActivity(open))) {
            if (uri != null) AndroidRemoteViews(
                RemoteViews(context.packageName, R.layout.widget_image).apply { setImageViewUri(R.id.image, uri) },
                GlanceModifier.fillMaxSize(),
            )
            Taps(d, k)
        }
    }

    /** The widget's own tap areas, over the picture. */
    @Composable open fun Taps(d: WidgetSnapshot, k: Float) {}
}

class PrayersWidget : AlzkrWidget(WidgetKind.Prayers)
class NextWidget : AlzkrWidget(WidgetKind.Next)
class RowWidget : AlzkrWidget(WidgetKind.Row)
class DateTimesWidget : AlzkrWidget(WidgetKind.DateTimes)
class TableWidget : AlzkrWidget(WidgetKind.Table)
class ProgressWidget : AlzkrWidget(WidgetKind.Progress)
class BannerWidget : AlzkrWidget(WidgetKind.Banner)
class DateWidget : AlzkrWidget(WidgetKind.Date)
class ClockWidget : AlzkrWidget(WidgetKind.Clock)
class AyahWidget : AlzkrWidget(WidgetKind.Ayah)
class OccasionsWidget : AlzkrWidget(WidgetKind.Occasions)
class CustomWidget : AlzkrWidget(WidgetKind.Custom)

/** The five cells: a tap ticks the prayer as prayed (kept with the day in «متابعتي»). */
class TrackerWidget : AlzkrWidget(WidgetKind.Tracker) {
    @Composable override fun Taps(d: WidgetSnapshot, k: Float) {
        val context = LocalContext.current
        val five = WidgetData.FivePrayers
        // the row lies out in the home screen's direction; the picture in the app's
        val systemRtl = context.resources.configuration.layoutDirection == android.view.View.LAYOUT_DIRECTION_RTL
        val flip = d.rtl != systemRtl
        Column(GlanceModifier.fillMaxSize().padding(start = (12 * k).dp, end = (12 * k).dp, bottom = (12 * k).dp, top = (WidgetRenderer.TRACKER_HEAD_DP * k).dp)) {
            Row(GlanceModifier.fillMaxSize()) {
                (0 until 5).forEach { j ->
                    val p = five[if (flip) 4 - j else j]
                    if (j > 0) Spacer(GlanceModifier.width((6 * k).dp))
                    Box(GlanceModifier.defaultWeight().fillMaxHeight().clickable(actionRunCallback<TogglePrayedAction>(actionParametersOf(PrayerKey to p.name)))) {}
                }
            }
        }
    }
}

/** «ذكر آخر» along the bottom. */
class DhikrWidget : AlzkrWidget(WidgetKind.Dhikr) {
    @Composable override fun Taps(d: WidgetSnapshot, k: Float) {
        Column(GlanceModifier.fillMaxSize()) {
            Spacer(GlanceModifier.defaultWeight())
            Box(GlanceModifier.fillMaxWidth().height((WidgetRenderer.DHIKR_FOOT_DP * k).dp).clickable(actionRunCallback<NextDhikrAction>())) {}
        }
    }
}

/** A tap anywhere counts; «تصفير» along the bottom starts again. */
class TasbihWidget : AlzkrWidget(WidgetKind.Tasbih) {
    @Composable override fun Taps(d: WidgetSnapshot, k: Float) {
        Column(GlanceModifier.fillMaxSize()) {
            Box(GlanceModifier.fillMaxWidth().defaultWeight().clickable(actionRunCallback<TasbihTapAction>())) {}
            Box(GlanceModifier.fillMaxWidth().height((WidgetRenderer.TASBIH_FOOT_DP * k).dp).clickable(actionRunCallback<TasbihResetAction>())) {}
        }
    }
}

/* ---------- taps ---------- */

val PrayerKey = ActionParameters.Key<String>("prayer")

class TogglePrayedAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val p = parameters[PrayerKey] ?: return
        val store = Prefs(context)
        val today = java.time.LocalDate.now().toString()
        val done = "prayer_$p" in store.flow.first().track[today].orEmpty()
        store.setTracked(today, "prayer_$p", !done)
        WidgetBus.bump()
        TrackerWidget().update(context, glanceId)
    }
}

class NextDhikrAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        WidgetStore.nextDhikr(context)
        WidgetBus.bump()
        DhikrWidget().update(context, glanceId)
    }
}

class TasbihTapAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        WidgetStore.tasbihTap(context)
        WidgetBus.bump()
        TasbihWidget().update(context, glanceId)
    }
}

class TasbihResetAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        WidgetStore.tasbihReset(context)
        WidgetBus.bump()
        TasbihWidget().update(context, glanceId)
    }
}

/* ---------- the receivers the phone talks to ---------- */

class PrayersWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = PrayersWidget() }
class NextWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = NextWidget() }
class RowWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = RowWidget() }
class DateTimesWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = DateTimesWidget() }
class TableWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = TableWidget() }
class ProgressWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = ProgressWidget() }
class BannerWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = BannerWidget() }
class DateWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = DateWidget() }
class ClockWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = ClockWidget() }
class TrackerWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = TrackerWidget() }
class AyahWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = AyahWidget() }
class DhikrWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = DhikrWidget() }
class TasbihWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = TasbihWidget() }
class OccasionsWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = OccasionsWidget() }
class CustomWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = CustomWidget() }

fun WidgetKind.widget(): AlzkrWidget = when (this) {
    WidgetKind.Prayers -> PrayersWidget()
    WidgetKind.Next -> NextWidget()
    WidgetKind.Row -> RowWidget()
    WidgetKind.DateTimes -> DateTimesWidget()
    WidgetKind.Table -> TableWidget()
    WidgetKind.Progress -> ProgressWidget()
    WidgetKind.Banner -> BannerWidget()
    WidgetKind.Date -> DateWidget()
    WidgetKind.Clock -> ClockWidget()
    WidgetKind.Tracker -> TrackerWidget()
    WidgetKind.Ayah -> AyahWidget()
    WidgetKind.Dhikr -> DhikrWidget()
    WidgetKind.Tasbih -> TasbihWidget()
    WidgetKind.Occasions -> OccasionsWidget()
    WidgetKind.Custom -> CustomWidget()
}

fun WidgetKind.receiver(): Class<*> = when (this) {
    WidgetKind.Prayers -> PrayersWidgetReceiver::class.java
    WidgetKind.Next -> NextWidgetReceiver::class.java
    WidgetKind.Row -> RowWidgetReceiver::class.java
    WidgetKind.DateTimes -> DateTimesWidgetReceiver::class.java
    WidgetKind.Table -> TableWidgetReceiver::class.java
    WidgetKind.Progress -> ProgressWidgetReceiver::class.java
    WidgetKind.Banner -> BannerWidgetReceiver::class.java
    WidgetKind.Date -> DateWidgetReceiver::class.java
    WidgetKind.Clock -> ClockWidgetReceiver::class.java
    WidgetKind.Tracker -> TrackerWidgetReceiver::class.java
    WidgetKind.Ayah -> AyahWidgetReceiver::class.java
    WidgetKind.Dhikr -> DhikrWidgetReceiver::class.java
    WidgetKind.Tasbih -> TasbihWidgetReceiver::class.java
    WidgetKind.Occasions -> OccasionsWidgetReceiver::class.java
    WidgetKind.Custom -> CustomWidgetReceiver::class.java
}

fun kindOfReceiver(className: String?): WidgetKind = WidgetKind.entries.firstOrNull { it.receiver().name == className } ?: WidgetKind.Prayers

/** Redraws the widgets every minute while any is on the home screen (their countdowns count minutes). */
object WidgetUpdater {
    private fun placed(context: Context): Boolean {
        val m = AppWidgetManager.getInstance(context)
        return WidgetKind.entries.any { runCatching { m.getAppWidgetIds(ComponentName(context, it.receiver())).isNotEmpty() }.getOrDefault(false) }
    }

    suspend fun updateAll(context: Context) {
        val app = context.applicationContext
        if (!placed(app)) return
        WidgetBus.bump()
        WidgetKind.entries.forEach { k -> runCatching { k.widget().updateAll(app) } }
        scheduleNext(app)
    }

    suspend fun updateOne(context: Context, kind: WidgetKind, id: GlanceId) = kind.widget().update(context, id)

    fun scheduleNext(context: Context) {
        if (!placed(context)) return
        val am = context.getSystemService(android.app.AlarmManager::class.java)
        val pi = android.app.PendingIntent.getBroadcast(
            context, 41, android.content.Intent(context, WidgetTickReceiver::class.java),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
        )
        val at = WidgetData.nextMinuteMs()
        // RTC, not _WAKEUP: while the screen is off nothing is drawn; it catches up when the phone wakes
        val exact = android.os.Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        if (exact) am.setExact(android.app.AlarmManager.RTC, at, pi) else am.set(android.app.AlarmManager.RTC, at, pi)
    }

    /** From anywhere (settings changed, app opened): redraw in the background. */
    fun refresh(context: Context) {
        val app = context.applicationContext
        CoroutineScope(Dispatchers.Default).launch {
            runCatching { publishPreviews(app) }
            runCatching { updateAll(app) }
        }
    }

    /**
     * The pictures the phone's widget list shows (Android 15 and later): each widget drawn as it
     * looks. The phone limits how often they may be sent, so only when the language or app changes.
     */
    private suspend fun publishPreviews(context: Context) {
        if (android.os.Build.VERSION.SDK_INT < 35) return
        val data = WidgetData.load(context)
        val key = "${com.kouwis.alzkr.data.I18n.lang.code}:${context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime}"
        val sp = context.getSharedPreferences("widget_state", Context.MODE_PRIVATE)
        if (sp.getString("previews", null) == key) return
        val m = AppWidgetManager.getInstance(context)
        val density = context.resources.displayMetrics.density
        var ok = true
        WidgetKind.entries.forEach { k ->
            val (w, h) = WidgetRenderer.defaultSize(k)
            // small enough for the phone to carry (about half a megabyte at most)
            val bmp = WidgetRenderer.render(context, k, WidgetStyle(bg = "midnight"), data, w, h, minOf(density, 440f / maxOf(w, h)))
            val rv = android.widget.RemoteViews(context.packageName, com.kouwis.alzkr.R.layout.widget_preview).apply {
                setImageViewBitmap(com.kouwis.alzkr.R.id.preview, bmp)
            }
            ok = ok && runCatching {
                m.setWidgetPreview(ComponentName(context, k.receiver()), android.appwidget.AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN, rv)
            }.getOrDefault(false)
        }
        if (ok) sp.edit().putString("previews", key).apply()
    }
}

class WidgetTickReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: android.content.Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try { runCatching { WidgetUpdater.updateAll(context.applicationContext) } } finally { pending.finish() }
        }
    }
}
