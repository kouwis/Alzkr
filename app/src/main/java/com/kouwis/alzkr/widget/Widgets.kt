package com.kouwis.alzkr.widget

import android.content.Context
import android.os.SystemClock
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
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
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.kouwis.alzkr.MainActivity
import com.kouwis.alzkr.R
import com.kouwis.alzkr.data.ArabicText
import com.kouwis.alzkr.data.t
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/* ---------- the pieces every widget is built from ---------- */

private fun style(color: Color, size: TextUnit, bold: Boolean = false, align: TextAlign = TextAlign.Start) =
    TextStyle(color = ColorProvider(color), fontSize = size, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, textAlign = align)

/** The widget's frame: the chosen background (glass, colour, picture, or nothing), rounded, opening the app when tapped. */
@Composable
private fun Frame(s: WidgetStyle, content: @Composable () -> Unit) {
    val bmp = if (s.bg == "image") WidgetStyles.loadImage(s.image) else null
    val action = actionStartActivity<MainActivity>()
    Box(GlanceModifier.fillMaxSize().cornerRadius(s.radius.dp).clickable(action)) {
        if (bmp != null) {
            Image(ImageProvider(bmp), contentDescription = null, contentScale = ContentScale.Crop, modifier = GlanceModifier.fillMaxSize())
            Box(GlanceModifier.fillMaxSize().background(Color.Black.copy(alpha = s.dim))) {}
        } else if (s.bg != "clear") {
            Box(GlanceModifier.fillMaxSize().background(s.panel)) {}
        }
        Box(GlanceModifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) { content() }
    }
}

/** A countdown that ticks by itself (the phone's own Chronometer), so the widget needn't redraw every second. */
@Composable
private fun Countdown(targetMs: Long, color: Color, sizeSp: Float) {
    val context = LocalContext.current
    val rv = RemoteViews(context.packageName, R.layout.widget_chrono).apply {
        val base = SystemClock.elapsedRealtime() + (targetMs - System.currentTimeMillis())
        setChronometer(R.id.chrono, base, t("بعد {0}").replace("{0}", "%s"), true)
        setChronometerCountDown(R.id.chrono, true)
        setTextColor(R.id.chrono, color.toArgb())
        setTextViewTextSize(R.id.chrono, TypedValue.COMPLEX_UNIT_SP, sizeSp)
    }
    AndroidRemoteViews(rv)
}

@Composable
private fun NextBlock(d: WidgetSnapshot, s: WidgetStyle, big: Boolean) {
    Column {
        Text(d.place, style = style(s.soft, 12.sp), maxLines = 1)
        Text(t("الصلاة القادمة"), style = style(s.soft, 12.sp), maxLines = 1)
        Text(d.next.name, style = style(s.ink, if (big) 30.sp else 24.sp, bold = true), maxLines = 1)
        Text(d.next.time, style = style(s.ink, if (big) 34.sp else 26.sp), maxLines = 1)
        Countdown(d.nextAtMs, s.soft, if (big) 13f else 12f)
    }
}

@Composable
private fun TimesList(d: WidgetSnapshot, s: WidgetStyle) {
    Column(GlanceModifier.fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
        d.rows.forEach { r ->
            val on = r.prayer == d.next.prayer
            Row(GlanceModifier.fillMaxWidth().padding(vertical = 1.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(r.name, style = style(if (on) s.ink else s.soft, 14.sp, bold = on), modifier = GlanceModifier.defaultWeight(), maxLines = 1)
                Text(r.time, style = style(if (on) s.ink else s.soft, 14.sp, bold = on), maxLines = 1)
            }
        }
    }
}

@Composable
private fun TimesRow(d: WidgetSnapshot, s: WidgetStyle) {
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        d.rows.filter { it.prayer.name != "Sunrise" }.forEach { r ->
            val on = r.prayer == d.next.prayer
            Column(GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(r.name, style = style(if (on) s.accent else s.soft, 13.sp, bold = on, align = TextAlign.Center), maxLines = 1)
                Text(r.time, style = style(if (on) s.accent else s.ink, 15.sp, bold = true, align = TextAlign.Center), maxLines = 1)
            }
        }
    }
}

@Composable
private fun DateBlock(d: WidgetSnapshot, s: WidgetStyle, big: Boolean) {
    Column(GlanceModifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(d.weekday, style = style(s.accent, if (big) 16.sp else 13.sp, bold = true, align = TextAlign.Center), maxLines = 1)
        Text(d.hijri, style = style(s.ink, if (big) 22.sp else 17.sp, bold = true, align = TextAlign.Center), maxLines = 1)
        Text(d.gregorian, style = style(s.soft, if (big) 14.sp else 12.sp, align = TextAlign.Center), maxLines = 1)
    }
}

@Composable
private fun DhikrBlock(d: WidgetSnapshot, s: WidgetStyle, lines: Int) {
    Text(d.dhikr, style = style(s.ink, 17.sp, bold = true, align = TextAlign.Center), maxLines = lines, modifier = GlanceModifier.fillMaxWidth())
}

@Composable
private fun AyahBlock(d: WidgetSnapshot, s: WidgetStyle, lines: Int) {
    Column(GlanceModifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("﴿ ${d.ayah} ﴾", style = style(s.ink, 18.sp, bold = true, align = TextAlign.Center), maxLines = lines, modifier = GlanceModifier.fillMaxWidth())
        Spacer(GlanceModifier.height(4.dp))
        Text(d.ayahRef, style = style(s.accent, 12.sp, align = TextAlign.Center), maxLines = 1)
    }
}

@Composable
private fun SmallButton(label: String, s: WidgetStyle, action: androidx.glance.action.Action) {
    Box(
        GlanceModifier.cornerRadius(14.dp).background(s.ink.copy(alpha = 0.14f)).padding(horizontal = 12.dp, vertical = 5.dp).clickable(action),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = style(s.ink, 12.sp, bold = true), maxLines = 1) }
}

/* ---------- the widgets ---------- */

abstract class AlzkrWidget(private val kind: WidgetKind) : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = WidgetData.load(context)
        val appWidgetId = runCatching { GlanceAppWidgetManager(context).getAppWidgetId(id) }.getOrDefault(0)
        val s = WidgetStyles.get(context, appWidgetId, kind)
        provideContent { Frame(s) { Body(data, s) } }
    }

    @Composable abstract fun Body(d: WidgetSnapshot, s: WidgetStyle)
}

class PrayersWidget : AlzkrWidget(WidgetKind.Prayers) {
    @Composable override fun Body(d: WidgetSnapshot, s: WidgetStyle) {
        val wide = LocalSize.current.width >= 220.dp
        if (!wide) { NextBlock(d, s, big = false); return }
        Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Box(GlanceModifier.defaultWeight()) { NextBlock(d, s, big = true) }
            Spacer(GlanceModifier.width(14.dp))
            Box(GlanceModifier.defaultWeight().fillMaxHeight()) { TimesList(d, s) }
        }
    }
}

class NextWidget : AlzkrWidget(WidgetKind.Next) {
    @Composable override fun Body(d: WidgetSnapshot, s: WidgetStyle) {
        Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) { NextBlock(d, s, big = LocalSize.current.height >= 150.dp) }
    }
}

class RowWidget : AlzkrWidget(WidgetKind.Row) {
    @Composable override fun Body(d: WidgetSnapshot, s: WidgetStyle) {
        Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            if (LocalSize.current.height >= 100.dp) {
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(d.hijri, style = style(s.soft, 12.sp), modifier = GlanceModifier.defaultWeight(), maxLines = 1)
                    Countdown(d.nextAtMs, s.soft, 12f)
                }
                Spacer(GlanceModifier.height(6.dp))
            }
            TimesRow(d, s)
        }
    }
}

class DateWidget : AlzkrWidget(WidgetKind.Date) {
    @Composable override fun Body(d: WidgetSnapshot, s: WidgetStyle) {
        Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) { DateBlock(d, s, big = LocalSize.current.height >= 110.dp) }
    }
}

class DhikrWidget : AlzkrWidget(WidgetKind.Dhikr) {
    @Composable override fun Body(d: WidgetSnapshot, s: WidgetStyle) {
        Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) {
            Box(GlanceModifier.defaultWeight().fillMaxWidth(), contentAlignment = Alignment.Center) {
                DhikrBlock(d, s, if (LocalSize.current.height >= 150.dp) 6 else 3)
            }
            SmallButton(t("ذكر آخر"), s, actionRunCallback<NextDhikrAction>())
        }
    }
}

class AyahWidget : AlzkrWidget(WidgetKind.Ayah) {
    @Composable override fun Body(d: WidgetSnapshot, s: WidgetStyle) {
        Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) { AyahBlock(d, s, if (LocalSize.current.height >= 150.dp) 7 else 3) }
    }
}

class TasbihWidget : AlzkrWidget(WidgetKind.Tasbih) {
    @Composable override fun Body(d: WidgetSnapshot, s: WidgetStyle) {
        Column(
            GlanceModifier.fillMaxSize().clickable(actionRunCallback<TasbihTapAction>()),
            verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(d.tasbihText, style = style(s.soft, 14.sp, bold = true, align = TextAlign.Center), maxLines = 1)
            Text(ArabicText.num(d.tasbihCount), style = style(s.ink, 40.sp, bold = true, align = TextAlign.Center), maxLines = 1)
            Text(t("من {0}", ArabicText.num(WidgetData.TASBIH_TARGET)), style = style(s.soft, 12.sp, align = TextAlign.Center), maxLines = 1)
            Spacer(GlanceModifier.height(6.dp))
            SmallButton(t("تصفير"), s, actionRunCallback<TasbihResetAction>())
        }
    }
}

class CustomWidget : AlzkrWidget(WidgetKind.Custom) {
    @Composable override fun Body(d: WidgetSnapshot, s: WidgetStyle) {
        val blocks = s.blocks.ifEmpty { listOf("next") }
        Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            blocks.forEachIndexed { i, b ->
                if (i > 0) Spacer(GlanceModifier.height(8.dp))
                when (b) {
                    "next" -> Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(GlanceModifier.defaultWeight()) {
                            Text(t("الصلاة القادمة"), style = style(s.soft, 12.sp), maxLines = 1)
                            Text(d.next.name + "  " + d.next.time, style = style(s.ink, 22.sp, bold = true), maxLines = 1)
                        }
                        Countdown(d.nextAtMs, s.accent, 14f)
                    }
                    "times" -> TimesRow(d, s)
                    "date" -> Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(d.weekday + " · " + d.hijri, style = style(s.ink, 14.sp, bold = true), modifier = GlanceModifier.defaultWeight(), maxLines = 1)
                        Text(d.gregorian, style = style(s.soft, 12.sp), maxLines = 1)
                    }
                    "dhikr" -> DhikrBlock(d, s, 3)
                    "ayah" -> AyahBlock(d, s, 3)
                }
            }
        }
    }
}

/* ---------- taps ---------- */

class NextDhikrAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        WidgetStore.nextDhikr(context)
        DhikrWidget().update(context, glanceId)
    }
}

class TasbihTapAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        WidgetStore.tasbihTap(context)
        TasbihWidget().update(context, glanceId)
    }
}

class TasbihResetAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        WidgetStore.tasbihReset(context)
        TasbihWidget().update(context, glanceId)
    }
}

/* ---------- the receivers the phone talks to ---------- */

class PrayersWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = PrayersWidget() }
class NextWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = NextWidget() }
class RowWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = RowWidget() }
class DateWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = DateWidget() }
class DhikrWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = DhikrWidget() }
class AyahWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = AyahWidget() }
class TasbihWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = TasbihWidget() }
class CustomWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = CustomWidget() }

fun WidgetKind.receiver(): Class<*> = when (this) {
    WidgetKind.Prayers -> PrayersWidgetReceiver::class.java
    WidgetKind.Next -> NextWidgetReceiver::class.java
    WidgetKind.Row -> RowWidgetReceiver::class.java
    WidgetKind.Date -> DateWidgetReceiver::class.java
    WidgetKind.Dhikr -> DhikrWidgetReceiver::class.java
    WidgetKind.Ayah -> AyahWidgetReceiver::class.java
    WidgetKind.Tasbih -> TasbihWidgetReceiver::class.java
    WidgetKind.Custom -> CustomWidgetReceiver::class.java
}

fun kindOfReceiver(className: String?): WidgetKind = WidgetKind.entries.firstOrNull { it.receiver().name == className } ?: WidgetKind.Prayers

/** Redraws every widget, then sets the next redraw (at the next prayer, Maghrib or midnight). */
object WidgetUpdater {
    suspend fun updateAll(context: Context) {
        val app = context.applicationContext
        runCatching { PrayersWidget().updateAll(app) }
        runCatching { NextWidget().updateAll(app) }
        runCatching { RowWidget().updateAll(app) }
        runCatching { DateWidget().updateAll(app) }
        runCatching { DhikrWidget().updateAll(app) }
        runCatching { AyahWidget().updateAll(app) }
        runCatching { TasbihWidget().updateAll(app) }
        runCatching { CustomWidget().updateAll(app) }
        scheduleNext(app)
    }

    suspend fun updateOne(context: Context, kind: WidgetKind, id: GlanceId) {
        when (kind) {
            WidgetKind.Prayers -> PrayersWidget().update(context, id)
            WidgetKind.Next -> NextWidget().update(context, id)
            WidgetKind.Row -> RowWidget().update(context, id)
            WidgetKind.Date -> DateWidget().update(context, id)
            WidgetKind.Dhikr -> DhikrWidget().update(context, id)
            WidgetKind.Ayah -> AyahWidget().update(context, id)
            WidgetKind.Tasbih -> TasbihWidget().update(context, id)
            WidgetKind.Custom -> CustomWidget().update(context, id)
        }
    }

    suspend fun scheduleNext(context: Context) {
        val at = WidgetData.nextChangeMs(context)
        val am = context.getSystemService(android.app.AlarmManager::class.java)
        val pi = android.app.PendingIntent.getBroadcast(
            context, 41, android.content.Intent(context, WidgetTickReceiver::class.java),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
        )
        val exact = android.os.Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        if (exact) am.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC, at, pi) else am.setAndAllowWhileIdle(android.app.AlarmManager.RTC, at, pi)
    }

    /** From anywhere (settings changed, app opened): redraw in the background. */
    fun refresh(context: Context) {
        val app = context.applicationContext
        CoroutineScope(Dispatchers.Default).launch { runCatching { updateAll(app) } }
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
