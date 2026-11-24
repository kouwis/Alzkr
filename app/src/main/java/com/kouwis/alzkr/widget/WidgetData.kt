package com.kouwis.alzkr.widget

import android.content.Context
import com.kouwis.alzkr.data.Adhkar
import com.kouwis.alzkr.data.ArabicText
import com.kouwis.alzkr.data.Hijri
import com.kouwis.alzkr.data.I18n
import com.kouwis.alzkr.data.Lang
import com.kouwis.alzkr.data.Prayer
import com.kouwis.alzkr.data.PrayerTimes
import com.kouwis.alzkr.data.Prefs
import com.kouwis.alzkr.data.SurahLatin
import com.kouwis.alzkr.data.t
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** One line of the prayer times. */
data class TimeRow(val prayer: Prayer, val name: String, val time: String, val minute: Int)

/** Everything a widget shows, worked out once per update. */
data class WidgetSnapshot(
    val place: String,
    val rows: List<TimeRow>,
    val next: TimeRow,
    val nextAtMs: Long,
    val weekday: String,
    val hijri: String,
    val gregorian: String,
    val dhikr: String,
    val ayah: String,
    val ayahRef: String,
    val tasbihText: String,
    val tasbihCount: Int,
    val rtl: Boolean,
) {
    fun digits(s: String) = ArabicText.digitsOf(s)
}

@Serializable
private data class WidgetAyah(val s: Int, val a: Int, val t: String, val n: String)

object WidgetData {
    private val json = Json { ignoreUnknownKeys = true }
    @Volatile private var ayat: List<WidgetAyah>? = null
    @Volatile private var adhkar: List<String>? = null

    val TasbihPhrases = listOf("سُبْحَانَ اللَّهِ", "الْحَمْدُ لِلَّهِ", "اللَّهُ أَكْبَرُ", "لَا إِلَٰهَ إِلَّا اللَّهُ", "أَسْتَغْفِرُ اللَّهَ")
    const val TASBIH_TARGET = 33

    private fun clock(min: Int): String {
        val h = (min / 60) % 24
        val h12 = if (h % 12 == 0) 12 else h % 12
        return ArabicText.digitsOf("%d:%02d".format(h12, min % 60))
    }

    /** Short, self-contained adhkar (no Quran passages) from the morning, evening and after-prayer sections. */
    private suspend fun adhkarPool(context: Context): List<String> = adhkar ?: run {
        val all = runCatching { Adhkar.load(context) }.getOrDefault(emptyMap())
        val ids = listOf("morning", "evening", "after_prayer", "sleep", "waking")
        val list = ids.flatMap { all[it]?.items.orEmpty() }
            .filter { d -> d.parts.none { it.q != null } }
            .map { d -> d.parts.mapNotNull { it.t }.joinToString(" ").trim() }
            .filter { it.length in 12..190 }
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
        val times = PrayerTimes.forDay(today, lat, lng, prefs.prayer)
        val rows = listOf(Prayer.Fajr, Prayer.Sunrise, Prayer.Dhuhr, Prayer.Asr, Prayer.Maghrib, Prayer.Isha)
            .map { p -> TimeRow(p, p.ar, clock(times.getValue(p)), times.getValue(p)) }
        val nowMin = now.hour * 60 + now.minute
        val nextToday = rows.firstOrNull { it.prayer != Prayer.Sunrise && it.minute > nowMin }
        val (next, nextAt) = if (nextToday != null) {
            nextToday to today.atStartOfDay(zone).plusMinutes(nextToday.minute.toLong())
        } else {
            val f = PrayerTimes.forDay(today.plusDays(1), lat, lng, prefs.prayer).getValue(Prayer.Fajr)
            TimeRow(Prayer.Fajr, Prayer.Fajr.ar, clock(f), f) to today.plusDays(1).atStartOfDay(zone).plusMinutes(f.toLong())
        }
        // the Islamic day starts at Maghrib
        val day = Hijri.of(today, afterMaghrib = nowMin >= times.getValue(Prayer.Maghrib))
        val doy = today.dayOfYear + today.year * 366
        val pool = adhkarPool(context)
        val shift = WidgetStore.dhikrShift(context)
        val ap = ayatPool(context)
        val ayah = ap.getOrNull(Math.floorMod(doy, ap.size.coerceAtLeast(1)))
        val (tIdx, tCount) = WidgetStore.tasbih(context)
        return WidgetSnapshot(
            place = prefs.place.ifBlank { t("القاهرة") },
            rows = rows,
            next = next,
            nextAtMs = nextAt.toInstant().toEpochMilli(),
            weekday = day.weekday,
            hijri = Hijri.hijriText(day),
            gregorian = Hijri.gregorianText(today),
            dhikr = pool[Math.floorMod(doy * 7 + shift, pool.size)],
            ayah = ayah?.t.orEmpty(),
            ayahRef = ayah?.let { t("{0} · {1}", if (I18n.lang.rtl) t("سورة {0}", it.n) else SurahLatin[it.s - 1], ArabicText.num(it.a)) }.orEmpty(),
            tasbihText = TasbihPhrases[tIdx % TasbihPhrases.size],
            tasbihCount = tCount,
            rtl = I18n.lang.rtl,
        )
    }

    /** When the widgets next need redrawing: the next prayer or sunrise, Maghrib (new Hijri day), or midnight. */
    suspend fun nextChangeMs(context: Context): Long {
        val prefs = Prefs(context).flow.first()
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        val times = PrayerTimes.forDay(today, prefs.lat ?: 30.0444, prefs.lng ?: 31.2357, prefs.prayer)
        val nowMs = System.currentTimeMillis()
        val candidates = times.values.map { today.atStartOfDay(zone).plusMinutes(it.toLong()).toInstant().toEpochMilli() } +
            today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return (candidates.filter { it > nowMs + 1_000 }.minOrNull() ?: (nowMs + 3_600_000)) + 2_000
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
        // after the round, on to the next phrase
        if (n >= WidgetData.TASBIH_TARGET) sp(context).edit().putInt("tasbih_n", 1).putInt("tasbih_i", i + 1).apply()
        else sp(context).edit().putInt("tasbih_n", n + 1).apply()
    }
    fun tasbihReset(context: Context) = sp(context).edit().putInt("tasbih_n", 0).apply()
}
