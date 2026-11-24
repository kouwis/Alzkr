package com.kouwis.alzkr.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
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
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.kouwis.alzkr.MainActivity
import com.kouwis.alzkr.R
import com.kouwis.alzkr.data.Prefs
import com.kouwis.alzkr.data.t
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/* ---------- type and the pieces every widget is built from ---------- */

/** How much bigger or smaller than its usual size the widget is shown (the text scales with it). */
@Composable
private fun scale(kind: WidgetKind): Float {
    val s = LocalSize.current
    val baseW = kind.w * 82f; val baseH = kind.h * 80f
    return minOf(s.width.value / baseW, s.height.value / baseH).coerceIn(0.78f, 1.35f)
}

@Composable
private fun T(
    text: String, size: Float, color: Color, bold: Boolean = true, align: TextAlign = TextAlign.Start,
    lines: Int = 1, modifier: GlanceModifier = GlanceModifier,
) = Text(
    text,
    style = TextStyle(color = ColorProvider(color), fontSize = size.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium, textAlign = align),
    maxLines = lines, modifier = modifier,
)

/** The widget's frame: its background picture (glass, gradient, colour, photo or nothing); a tap opens the app. */
@Composable
private fun Frame(s: WidgetStyle, pad: Dp = 16.dp, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val size = LocalSize.current
    val bmp = WidgetStyles.background(s, size.width.value, size.height.value, context.resources.displayMetrics.density)
    Box(GlanceModifier.fillMaxSize().clickable(actionStartActivity<MainActivity>())) {
        if (bmp != null) Image(ImageProvider(bmp), contentDescription = null, contentScale = ContentScale.FillBounds, modifier = GlanceModifier.fillMaxSize())
        Box(GlanceModifier.fillMaxSize().padding(horizontal = pad, vertical = pad * 0.8f), contentAlignment = Alignment.Center) { content() }
    }
}

/** A thin bar of how far from one moment to the next; it fills from the reading side. */
@Composable
private fun Bar(fraction: Float, width: Dp, s: WidgetStyle) {
    Box(GlanceModifier.width(width).height(8.dp).cornerRadius(4.dp).background(s.faint)) {
        Box(GlanceModifier.width((width.value * fraction.coerceIn(0.03f, 1f)).dp).height(8.dp).cornerRadius(4.dp).background(s.accentColor)) {}
    }
}

@Composable
private fun Pill(label: String, s: WidgetStyle, action: Action) {
    Box(
        GlanceModifier.cornerRadius(16.dp).background(s.faint).padding(horizontal = 14.dp, vertical = 6.dp).clickable(action),
        contentAlignment = Alignment.Center,
    ) { T(label, 12f, s.ink) }
}

/** The phone's own ticking clock (it needs no redraws). */
@Composable
private fun LiveClock(color: Color, sizeSp: Float) {
    val context = LocalContext.current
    val rv = RemoteViews(context.packageName, R.layout.widget_clock).apply {
        setTextColor(R.id.clock, color.toArgb())
        setTextViewTextSize(R.id.clock, TypedValue.COMPLEX_UNIT_SP, sizeSp)
    }
    AndroidRemoteViews(rv)
}

@Composable
private fun TimesRow(d: WidgetSnapshot, s: WidgetStyle, k: Float) {
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        d.rows.filter { it.prayer.name != "Sunrise" }.forEach { r ->
            val on = r.prayer == d.next.prayer
            Column(GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                T(r.name, 12.5f * k, if (on) s.accentColor else s.soft, bold = on, align = TextAlign.Center)
                T(r.time, 16f * k, if (on) s.accentColor else s.ink, align = TextAlign.Center)
            }
        }
    }
}

/* ---------- the widgets ---------- */

abstract class AlzkrWidget(val kind: WidgetKind) : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = WidgetData.load(context)
        val appWidgetId = runCatching { GlanceAppWidgetManager(context).getAppWidgetId(id) }.getOrDefault(-1)
        val s = WidgetStyles.get(context, appWidgetId, kind)
        provideContent { Body(data, s) }
    }

    @Composable abstract fun Body(d: WidgetSnapshot, s: WidgetStyle)
}

/** The next prayer in large type, the time left, and all the times beside it. */
class PrayersWidget : AlzkrWidget(WidgetKind.Prayers) {
    @Composable override fun Body(d: WidgetSnapshot, s: WidgetStyle) = Frame(s) {
        val k = scale(kind)
        Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Column(GlanceModifier.defaultWeight().fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
                T(d.place, 12f * k, s.soft, bold = false)
                T(d.headline, 26f * k, s.ink)
                T(d.targetTime, 36f * k, s.ink)
                T(d.remaining, 14f * k, s.accentColor)
            }
            Spacer(GlanceModifier.width(12.dp))
            Column(GlanceModifier.defaultWeight().fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
                d.rows.forEach { r ->
                    val on = r.prayer == d.next.prayer
                    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        T(r.name, 13.5f * k, if (on) s.ink else s.soft, bold = on, modifier = GlanceModifier.defaultWeight())
                        T(r.time, 13.5f * k, if (on) s.ink else s.soft, bold = on)
                    }
                }
            }
        }
    }
}

/** The next prayer, its time and the time left — for a small square. */
class NextWidget : AlzkrWidget(WidgetKind.Next) {
    @Composable override fun Body(d: WidgetSnapshot, s: WidgetStyle) = Frame(s, pad = 14.dp) {
        val k = scale(kind)
        Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            T(d.place, 11f * k, s.soft, bold = false)
            T(d.headline, 21f * k, s.ink)
            T(d.targetTime, 32f * k, s.ink)
            T(d.remaining, 13f * k, s.accentColor)
        }
    }
}

/** The five prayers in one line; the next one in the accent colour. */
class RowWidget : AlzkrWidget(WidgetKind.Row) {
    @Composable override fun Body(d: WidgetSnapshot, s: WidgetStyle) = Frame(s, pad = 12.dp) {
        val k = scale(kind).coerceAtMost(1.15f)
        Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            if (LocalSize.current.height >= 110.dp) {
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    T(d.headline, 15f, s.ink, modifier = GlanceModifier.defaultWeight())
                    T(d.remaining, 13f, s.accentColor)
                }
                Spacer(GlanceModifier.height(10.dp))
            }
            TimesRow(d, s, k)
        }
    }
}

/** The day and Hijri date, the five times, and the iqama or prayer to come. */
class DateTimesWidget : AlzkrWidget(WidgetKind.DateTimes) {
    @Composable override fun Body(d: WidgetSnapshot, s: WidgetStyle) = Frame(s) {
        val k = scale(kind)
        Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                T(d.weekday, 16f * k, s.ink)
                Spacer(GlanceModifier.width(8.dp))
                T("${d.hijriDay} ${d.hijriMonth}", 16f * k, s.soft, modifier = GlanceModifier.defaultWeight())
                T(d.place, 12f * k, s.soft, bold = false)
            }
            Spacer(GlanceModifier.height(10.dp))
            TimesRow(d, s, k)
            Spacer(GlanceModifier.height(10.dp))
            Box(GlanceModifier.fillMaxWidth().height(1.dp).background(s.faint)) {}
            Spacer(GlanceModifier.height(8.dp))
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                T("${d.headline}  ${d.targetTime}", 14f * k, s.accentColor, modifier = GlanceModifier.defaultWeight())
                T(d.remaining, 14f * k, s.accentColor)
            }
        }
    }
}

/** Each prayer's adhan and iqama in a table, under the moment to come. */
class TableWidget : AlzkrWidget(WidgetKind.Table) {
    @Composable override fun Body(d: WidgetSnapshot, s: WidgetStyle) = Frame(s, pad = 14.dp) {
        val k = scale(kind).coerceAtMost(1.2f)
        Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Row(
                GlanceModifier.fillMaxWidth().cornerRadius(14.dp).background(s.faint).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                T(d.headline, 14f * k, s.accentColor, modifier = GlanceModifier.defaultWeight())
                T(d.remaining, 14f * k, s.accentColor)
            }
            Spacer(GlanceModifier.height(6.dp))
            Row(GlanceModifier.fillMaxWidth().padding(vertical = 4.dp)) {
                T(t("الصلاة"), 12f * k, s.soft, align = TextAlign.Center, modifier = GlanceModifier.defaultWeight())
                T(t("الأذان"), 12f * k, s.soft, align = TextAlign.Center, modifier = GlanceModifier.defaultWeight())
                T(t("الإقامة"), 12f * k, s.soft, align = TextAlign.Center, modifier = GlanceModifier.defaultWeight())
            }
            d.rows.filter { it.prayer.name != "Sunrise" }.forEach { r ->
                val on = r.prayer == d.next.prayer
                val c = if (on) s.accentColor else s.ink
                Row(
                    GlanceModifier.fillMaxWidth().cornerRadius(10.dp).background(if (on) s.faint else Color.Transparent).padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    T(r.name, 15f * k, c, align = TextAlign.Center, modifier = GlanceModifier.defaultWeight())
                    T(r.time, 15f * k, c, align = TextAlign.Center, modifier = GlanceModifier.defaultWeight())
                    T(r.iqama ?: "—", 15f * k, c, align = TextAlign.Center, modifier = GlanceModifier.defaultWeight())
                }
            }
        }
    }
}

/** From the last moment to the next: a bar of how far, with both times and the time left. */
class ProgressWidget : AlzkrWidget(WidgetKind.Progress) {
    @Composable override fun Body(d: WidgetSnapshot, s: WidgetStyle) = Frame(s) {
        val k = scale(kind)
        val width = (LocalSize.current.width.value - 32f).coerceAtLeast(40f).dp
        Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Row(GlanceModifier.fillMaxWidth()) {
                T(d.weekday, 12f * k, s.soft, bold = false, modifier = GlanceModifier.defaultWeight())
                T(d.place, 12f * k, s.soft, bold = false)
            }
            Spacer(GlanceModifier.height(8.dp))
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                T("✓ " + d.fromLabel, 19f * k, s.ink, modifier = GlanceModifier.defaultWeight())
                T(d.headline, 19f * k, s.ink)
            }
            Spacer(GlanceModifier.height(8.dp))
            Bar(d.progress, width, s)
            Spacer(GlanceModifier.height(8.dp))
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                T(d.fromTime, 16f * k, s.ink)
                T(d.remaining, 14f * k, s.accentColor, align = TextAlign.Center, modifier = GlanceModifier.defaultWeight())
                T(d.targetTime, 16f * k, s.ink)
            }
            Spacer(GlanceModifier.height(6.dp))
            Row(GlanceModifier.fillMaxWidth()) {
                T(d.gregorian, 11.5f * k, s.soft, bold = false, modifier = GlanceModifier.defaultWeight())
                T(d.hijri, 11.5f * k, s.soft, bold = false)
            }
        }
    }
}

/** One big line: «إقامة الظهر · بعد 6 د». */
class BannerWidget : AlzkrWidget(WidgetKind.Banner) {
    @Composable override fun Body(d: WidgetSnapshot, s: WidgetStyle) = Frame(s, pad = 14.dp) {
        val k = scale(kind).coerceAtMost(1.2f)
        Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            if (d.sub.isNotBlank()) T(d.sub, 11.5f * k, s.soft, bold = false)
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                T(d.headline, 22f * k, s.ink, modifier = GlanceModifier.defaultWeight())
                T(d.remaining, 18f * k, s.accentColor)
            }
        }
    }
}

/** The Hijri day in large type, the month, the weekday and the Gregorian date. */
class DateWidget : AlzkrWidget(WidgetKind.Date) {
    @Composable override fun Body(d: WidgetSnapshot, s: WidgetStyle) = Frame(s) {
        val k = scale(kind)
        Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) {
            T(d.hijriDay.toString(), 66f * k, s.ink)
            Spacer(GlanceModifier.width(14.dp))
            Column(verticalAlignment = Alignment.CenterVertically) {
                T(d.hijriMonth, 24f * k, s.ink)
                T(d.weekday, 22f * k, s.accentColor)
                T(d.gregorianShort, 15f * k, s.soft)
            }
        }
    }
}

/** The time, the date and the prayer to come. */
class ClockWidget : AlzkrWidget(WidgetKind.Clock) {
    @Composable override fun Body(d: WidgetSnapshot, s: WidgetStyle) = Frame(s) {
        val k = scale(kind)
        Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) {
            LiveClock(s.ink, 52f * k)
            T("${d.weekday}، ${d.gregorianShort}", 15f * k, s.ink, align = TextAlign.Center)
            Spacer(GlanceModifier.height(4.dp))
            T("${d.headline}  ${d.targetTime}  ·  ${d.remaining}", 14f * k, s.accentColor, align = TextAlign.Center)
        }
    }
}

/** The five prayers as cells to tick once prayed (kept with the day in «متابعتي»). */
class TrackerWidget : AlzkrWidget(WidgetKind.Tracker) {
    @Composable override fun Body(d: WidgetSnapshot, s: WidgetStyle) = Frame(s, pad = 12.dp) {
        val k = scale(kind).coerceAtMost(1.15f)
        val five = d.rows.filter { it.prayer.name != "Sunrise" }
        Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Row(GlanceModifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                T(t("متابعة الصلوات"), 14f * k, s.accentColor, modifier = GlanceModifier.defaultWeight())
                T("${five.count { it.prayer.name in d.prayed }}/5", 14f * k, s.soft)
            }
            Spacer(GlanceModifier.height(8.dp))
            Row(GlanceModifier.fillMaxWidth()) {
                five.forEachIndexed { i, r ->
                    if (i > 0) Spacer(GlanceModifier.width(6.dp))
                    val done = r.prayer.name in d.prayed
                    Column(
                        GlanceModifier.defaultWeight().cornerRadius(16.dp).background(s.faint).padding(vertical = 8.dp)
                            .clickable(actionRunCallback<TogglePrayedAction>(actionParametersOf(PrayerKey to r.prayer.name))),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            GlanceModifier.size((26 * k).dp).cornerRadius((13 * k).dp).background(if (done) s.accentColor else s.faint),
                            contentAlignment = Alignment.Center,
                        ) { T(if (done) "✓" else "", 14f * k, if (s.lightBg) Color.White else Color(0xFF14171C), align = TextAlign.Center) }
                        Spacer(GlanceModifier.height(4.dp))
                        T(r.name, 13f * k, if (done) s.ink else s.soft, align = TextAlign.Center)
                        T(r.time, 12f * k, s.soft, bold = false, align = TextAlign.Center)
                    }
                }
            }
        }
    }
}

/** A verse each day. */
class AyahWidget : AlzkrWidget(WidgetKind.Ayah) {
    @Composable override fun Body(d: WidgetSnapshot, s: WidgetStyle) = Frame(s) {
        val k = scale(kind)
        val lines = if (LocalSize.current.height >= 170.dp) 6 else 3
        Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) {
            T(t("آية اليوم"), 12f * k, s.accentColor, align = TextAlign.Center)
            Spacer(GlanceModifier.height(4.dp))
            T("﴿ ${d.ayah} ﴾", 19f * k, s.ink, align = TextAlign.Center, lines = lines, modifier = GlanceModifier.fillMaxWidth())
            Spacer(GlanceModifier.height(4.dp))
            T(d.ayahRef, 12f * k, s.soft, align = TextAlign.Center)
        }
    }
}

/** A dhikr, with «ذكر آخر». */
class DhikrWidget : AlzkrWidget(WidgetKind.Dhikr) {
    @Composable override fun Body(d: WidgetSnapshot, s: WidgetStyle) = Frame(s) {
        val k = scale(kind)
        val lines = if (LocalSize.current.height >= 170.dp) 6 else 3
        Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) {
            Box(GlanceModifier.defaultWeight().fillMaxWidth(), contentAlignment = Alignment.Center) {
                T(d.dhikr, 18f * k, s.ink, align = TextAlign.Center, lines = lines, modifier = GlanceModifier.fillMaxWidth())
            }
            Pill(t("ذكر آخر"), s, actionRunCallback<NextDhikrAction>())
        }
    }
}

/** A tasbih counter: tap to count. */
class TasbihWidget : AlzkrWidget(WidgetKind.Tasbih) {
    @Composable override fun Body(d: WidgetSnapshot, s: WidgetStyle) = Frame(s, pad = 12.dp) {
        val k = scale(kind)
        Column(
            GlanceModifier.fillMaxSize().clickable(actionRunCallback<TasbihTapAction>()),
            verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            T(d.tasbihText, 15f * k, s.ink, align = TextAlign.Center)
            T(d.tasbihCount.toString(), 46f * k, s.ink, align = TextAlign.Center)
            T(t("من {0}", WidgetData.TASBIH_TARGET.toString()), 12f * k, s.soft, align = TextAlign.Center)
            Spacer(GlanceModifier.height(6.dp))
            Pill(t("تصفير"), s, actionRunCallback<TasbihResetAction>())
        }
    }
}

/** Days left to Ramadan and the two Eids. */
class OccasionsWidget : AlzkrWidget(WidgetKind.Occasions) {
    @Composable override fun Body(d: WidgetSnapshot, s: WidgetStyle) = Frame(s, pad = 14.dp) {
        val k = scale(kind).coerceAtMost(1.2f)
        Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            T(t("الأيام"), 12f * k, s.accentColor)
            d.occasions.forEachIndexed { i, (name, days) ->
                if (i > 0) Box(GlanceModifier.fillMaxWidth().height(1.dp).background(s.faint)) {}
                Row(GlanceModifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    T(name, 15f * k, s.ink, modifier = GlanceModifier.defaultWeight())
                    T(if (days == 0L) t("الآن") else days.toString(), 22f * k, s.ink)
                }
            }
        }
    }
}

/** The user's own mix of pieces. */
class CustomWidget : AlzkrWidget(WidgetKind.Custom) {
    @Composable override fun Body(d: WidgetSnapshot, s: WidgetStyle) = Frame(s) {
        val k = scale(kind).coerceAtMost(1.15f)
        Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            s.blocks.ifEmpty { listOf("next") }.forEachIndexed { i, b ->
                if (i > 0) Spacer(GlanceModifier.height(10.dp))
                when (b) {
                    "next" -> Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        T("${d.headline}  ${d.targetTime}", 20f * k, s.ink, modifier = GlanceModifier.defaultWeight())
                        T(d.remaining, 15f * k, s.accentColor)
                    }
                    "times" -> TimesRow(d, s, k)
                    "date" -> Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        T("${d.weekday} · ${d.hijriDay} ${d.hijriMonth}", 14f * k, s.ink, modifier = GlanceModifier.defaultWeight())
                        T(d.gregorianShort, 13f * k, s.soft)
                    }
                    "dhikr" -> T(d.dhikr, 15f * k, s.ink, align = TextAlign.Center, lines = 2, modifier = GlanceModifier.fillMaxWidth())
                    "ayah" -> T("﴿ ${d.ayah} ﴾", 15f * k, s.ink, align = TextAlign.Center, lines = 2, modifier = GlanceModifier.fillMaxWidth())
                }
            }
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
        TrackerWidget().update(context, glanceId)
    }
}

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
