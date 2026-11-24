package com.kouwis.alzkr.widget

import android.content.Context
import com.kouwis.alzkr.data.Adhkar
import com.kouwis.alzkr.data.Hijri
import com.kouwis.alzkr.data.I18n
import com.kouwis.alzkr.data.Lang
import com.kouwis.alzkr.data.Prayer
import com.kouwis.alzkr.data.PrayerTimes
import com.kouwis.alzkr.data.Prefs
import com.kouwis.alzkr.data.SurahLatin
import com.kouwis.alzkr.data.t
import com.kouwis.alzkr.ui.Occasions
import com.kouwis.alzkr.ui.nextOf
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoField
import java.time.temporal.ChronoUnit

/*
 * The widgets write numbers in Western digits, as the phone's own clock does, so a widget never
 * mixes two kinds of digits.
 */

/** One prayer's line: its name, adhan and iqama times (minutes of the day, and written). */
data class TimeRow(val prayer: Prayer, val name: String, val time: String, val minute: Int, val iqama: String?, val iqamaMinute: Int?)

/** Everything a widget shows, worked out once per update (every minute while widgets are placed). */
data class WidgetSnapshot(
    val place: String,
    val rows: List<TimeRow>,
    /** the prayer the widgets point at: the next adhan, or the iqama of the one just called */
    val next: TimeRow,
    val headline: String,
    val targetTime: String,
    val targetMs: Long,
    val remaining: String,
    /** «أذان الظهر قبل قليل · 11:44» between the adhan and the iqama */
    val sub: String,
    /** how far from the last moment to the next (0…1) */
    val progress: Float,
    val fromLabel: String,
    val fromTime: String,
    val iqamaPhase: Boolean,
    val weekday: String,
    val hijriDay: Int,
    val hijriMonth: String,
    val hijriYear: Int,
    val gregorian: String,
    val gregorianShort: String,
    val week: List<Pair<String, Int>>,
    val todayIndex: Int,
    val dhikr: String,
    val ayah: String,
    val ayahRef: String,
    val tasbihText: String,
    val tasbihCount: Int,
    val occasions: List<Pair<String, Long>>,
    val prayed: Set<String>,
    val rtl: Boolean,
) {
    val hijri: String get() = "$hijriDay $hijriMonth $hijriYear"
}

@Serializable
private data class WidgetAyah(val s: Int, val a: Int, val t: String, val n: String)

object WidgetData {
    private val json = Json { ignoreUnknownKeys = true }
    @Volatile private var ayat: List<WidgetAyah>? = null
    @Volatile private var adhkar: List<String>? = null

    val TasbihPhrases = listOf("سُبْحَانَ اللَّهِ", "الْحَمْدُ لِلَّهِ", "اللَّهُ أَكْبَرُ", "لَا إِلَٰهَ إِلَّا اللَّهُ", "أَسْتَغْفِرُ اللَّهَ")
    const val TASBIH_TARGET = 33
    val FivePrayers = listOf(Prayer.Fajr, Prayer.Dhuhr, Prayer.Asr, Prayer.Maghrib, Prayer.Isha)

    fun clock(min: Int): String {
        val m = Math.floorMod(min, 1440)
        val h = m / 60
        return "%d:%02d".format(java.util.Locale.US, if (h % 12 == 0) 12 else h % 12, m % 60)
    }

    /** «بعد 6 د», «بعد 1 س 24 د» — in Western digits. */
    fun remaining(minutes: Long): String = when {
        minutes <= 0 -> t("الآن")
        minutes < 60 -> t("بعد {0} د", minutes.toString())
        else -> t("بعد {0} س {1} د", (minutes / 60).toString(), (minutes % 60).toString())
    }

    private suspend fun adhkarPool(context: Context): List<String> = adhkar ?: run {
        val all = runCatching { Adhkar.load(context) }.getOrDefault(emptyMap())
        val list = listOf("morning", "evening", "after_prayer", "sleep", "waking", "prophetic")
            .flatMap { all[it]?.items.orEmpty() }
            .filter { d -> d.parts.none { it.q != null } }
            .map { d -> d.parts.mapNotNull { it.t }.joinToString(" ").trim() }
            .filter { it.length in 12..170 }
            .distinct()
        list.ifEmpty { TasbihPhrases }.also { adhkar = it }
    }

    private fun ayatPool(context: Context): List<WidgetAyah> = ayat ?: runCatching {
        json.decodeFromString<List<WidgetAyah>>(context.assets.open("data/widget_ayat.json").bufferedReader().use { it.readText() })
    }.getOrDefault(emptyList()).also { ayat = it }

    suspend fun load(context: Context): WidgetSnapshot {
        val prefs = Prefs(context).flow.first()
        I18n.set(context, Lang.of(prefs.lang))
        Hijri.offset = prefs.hijriOffset; Hijri.hijriFormat = prefs.hijriFormat; Hijri.gregFormat = prefs.gregFormat
        val lat = prefs.lat ?: 30.0444
        val lng = prefs.lng ?: 31.2357
        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.now()
        val today = now.toLocalDate()
        val nowMin = now.hour * 60 + now.minute
        val nowMs = System.currentTimeMillis()
        fun ms(day: LocalDate, min: Int) = day.atStartOfDay(zone).plusMinutes(min.toLong()).toInstant().toEpochMilli()

        val times = PrayerTimes.forDay(today, lat, lng, prefs.prayer)
        val rows = listOf(Prayer.Fajr, Prayer.Sunrise, Prayer.Dhuhr, Prayer.Asr, Prayer.Maghrib, Prayer.Isha).map { p ->
            val m = times.getValue(p)
            val iq = prefs.prayer.iqama[p]?.takeIf { it > 0 && p != Prayer.Sunrise }
            TimeRow(p, p.ar, clock(m), m, iq?.let { clock(m + it) }, iq?.let { m + it })
        }
        val five = rows.filter { it.prayer != Prayer.Sunrise }

        // between an adhan and its iqama, the widgets count down to the iqama
        val inIqama = five.lastOrNull { it.iqamaMinute != null && nowMin >= it.minute && nowMin < it.iqamaMinute }
        val upcoming = five.firstOrNull { it.minute > nowMin }
        val tomorrowFajr = PrayerTimes.forDay(today.plusDays(1), lat, lng, prefs.prayer).getValue(Prayer.Fajr)
        val next: TimeRow; val headline: String; val targetMin: Int; val targetDay: LocalDate; val sub: String
        val fromMin: Int; val fromLabel: String
        if (inIqama != null) {
            next = inIqama; headline = t("إقامة {0}", inIqama.name); targetMin = inIqama.iqamaMinute!!; targetDay = today
            sub = t("أذان {0} قبل قليل · {1}", inIqama.name, inIqama.time)
            fromMin = inIqama.minute; fromLabel = inIqama.name
        } else if (upcoming != null) {
            next = upcoming; headline = upcoming.name; targetMin = upcoming.minute; targetDay = today
            val prev = rows.lastOrNull { it.minute <= nowMin }
            sub = prev?.let { t("{0} · {1}", it.name, it.time) }.orEmpty()
            fromMin = prev?.minute ?: (times.getValue(Prayer.Isha) - 1440); fromLabel = prev?.name ?: Prayer.Isha.ar
        } else {
            next = TimeRow(Prayer.Fajr, Prayer.Fajr.ar, clock(tomorrowFajr), tomorrowFajr, null, null)
            headline = Prayer.Fajr.ar; targetMin = tomorrowFajr; targetDay = today.plusDays(1)
            sub = t("{0} · {1}", Prayer.Isha.ar, clock(times.getValue(Prayer.Isha)))
            fromMin = times.getValue(Prayer.Isha); fromLabel = Prayer.Isha.ar
        }
        val targetMs = ms(targetDay, targetMin)
        val fromMs = ms(today, fromMin)
        val left = ((targetMs - nowMs + 59_999L) / 60_000L).coerceAtLeast(0)
        val progress = ((nowMs - fromMs).toFloat() / (targetMs - fromMs).coerceAtLeast(1)).coerceIn(0f, 1f)

        // the Islamic day starts at Maghrib
        val day = Hijri.of(today, afterMaghrib = nowMin >= times.getValue(Prayer.Maghrib))
        val gMonths = Hijri.gMonths
        val weekStart = today.minusDays(((today.dayOfWeek.value + 1) % 7).toLong()) // Saturday
        val week = (0..6).map { i ->
            val d = weekStart.plusDays(i.toLong())
            val name = when (d.dayOfWeek.value) { 6 -> t("سب"); 7 -> t("أح"); 1 -> t("إث"); 2 -> t("ثل"); 3 -> t("أر"); 4 -> t("خم"); else -> t("جم") }
            name to Hijri.hijrah(d).get(ChronoField.DAY_OF_MONTH)
        }
        val doy = today.dayOfYear + today.year * 366
        val pool = adhkarPool(context)
        val ap = ayatPool(context)
        val ayah = ap.getOrNull(Math.floorMod(doy, ap.size.coerceAtLeast(1)))
        val (tIdx, tCount) = WidgetStore.tasbih(context)
        val occ = listOf("رمضان", "عيد الفطر", "عيد الأضحى").mapNotNull { name ->
            val o = Occasions.firstOrNull { it.arTitle == name } ?: return@mapNotNull null
            val (date, happening) = nextOf(o, today)
            o.title to if (happening) 0L else ChronoUnit.DAYS.between(today, date)
        }
        return WidgetSnapshot(
            place = prefs.place.ifBlank { t("القاهرة") },
            rows = rows,
            next = next,
            headline = headline,
            targetTime = clock(targetMin),
            targetMs = targetMs,
            remaining = remaining(left),
            sub = sub,
            progress = progress,
            fromLabel = fromLabel,
            fromTime = clock(fromMin),
            iqamaPhase = inIqama != null,
            weekday = day.weekday,
            hijriDay = day.day,
            hijriMonth = day.month,
            hijriYear = day.year,
            gregorian = "${today.dayOfMonth} ${gMonths[today.monthValue - 1]} ${today.year}",
            gregorianShort = "${today.dayOfMonth} ${gMonths[today.monthValue - 1]}",
            week = week,
            todayIndex = (today.dayOfWeek.value + 1) % 7,
            dhikr = pool[Math.floorMod(doy * 7 + WidgetStore.dhikrShift(context), pool.size)],
            ayah = ayah?.t.orEmpty(),
            ayahRef = ayah?.let { (if (I18n.lang.rtl) it.n else SurahLatin[it.s - 1]) + " · " + it.a }.orEmpty(),
            tasbihText = TasbihPhrases[tIdx % TasbihPhrases.size],
            tasbihCount = tCount,
            occasions = occ,
            prayed = prefs.track[today.toString()].orEmpty().filter { it.startsWith("prayer_") }.map { it.removePrefix("prayer_") }.toSet(),
            rtl = I18n.lang.rtl,
        )
    }

    /** The next minute: the widgets' countdowns count minutes. */
    fun nextMinuteMs(): Long {
        val now = System.currentTimeMillis()
        return now - now % 60_000L + 60_000L + 500L
    }
}

/** Small state the widgets keep: which dhikr is shown, and the tasbih count. */
object WidgetStore {
    private fun sp(context: Context) = context.getSharedPreferences("widget_state", Context.MODE_PRIVATE)
    fun dhikrShift(context: Context) = sp(context).getInt("dhikr_shift", 0)
    fun nextDhikr(context: Context) = sp(context).edit().putInt("dhikr_shift", dhikrShift(context) + 1).apply()
    fun tasbih(context: Context) = sp(context).getInt("tasbih_i", 0) to sp(context).getInt("tasbih_n", 0)
    fun tasbihTap(context: Context) {
        val (i, n) = tasbih(context)
        if (n >= WidgetData.TASBIH_TARGET) sp(context).edit().putInt("tasbih_n", 1).putInt("tasbih_i", i + 1).apply()
        else sp(context).edit().putInt("tasbih_n", n + 1).apply()
    }
    fun tasbihReset(context: Context) = sp(context).edit().putInt("tasbih_n", 0).apply()
}
