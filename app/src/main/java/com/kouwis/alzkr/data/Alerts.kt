package com.kouwis.alzkr.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField

/*
 * «الأذان والتنبيهات»: what sounds at each prayer, the alarms set around the prayers, and the daily
 * reminders. Everything is worked out from the prayer times on the phone, so it follows the times
 * as they change from day to day.
 *
 * A sound is named by a short string:
 *   "full:<id>"  the whole adhan by muezzin <id> (from the sounds catalog)
 *   "short:<id>" only its opening takbirs
 *   "sys"        the phone's notification sound
 *   "alarm"      the phone's alarm sound (for alarms)
 *   "vib"        vibration only
 */

/** The alerts of one prayer. [pre]: minutes before the adhan for a heads-up (0 = none). */
@Serializable
data class PrayerAlert(
    val on: Boolean = true,
    val sound: String = "",
    val pre: Int = 0,
    val preSound: String = "sys",
    val iqama: Boolean = false,
    /** minutes before the iqama (0 = at the iqama itself) */
    val iqamaBefore: Int = 0,
    val iqamaSound: String = "sys",
)

/** An alarm that rings like the phone's alarm clock, [offset] minutes from a prayer's adhan or iqama (negative = before). */
@Serializable
data class PrayerAlarm(
    val id: Int,
    val prayer: String = Prayer.Fajr.name,
    val iqama: Boolean = false,
    val offset: Int = -15,
    /** ISO day numbers (1 = Monday … 7 = Sunday); empty = every day */
    val days: Set<Int> = emptySet(),
    val sound: String = "alarm",
    val on: Boolean = true,
    val label: String = "",
) {
    val prayerEnum: Prayer get() = Prayer.entries.firstOrNull { it.name == prayer } ?: Prayer.Fajr
}

/**
 * «الصمت أثناء الصلاة»: the phone goes fully silent (Do Not Disturb, total silence) from the end of
 * the adhan until [after] minutes past the iqama, then back to how it was. Off by default.
 * [off]: prayers left out.
 */
@Serializable
data class SilentConfig(val on: Boolean = false, val after: Int = 10, val off: Set<String> = emptySet())

/** A daily reminder: on/off and minutes from its anchor time. */
@Serializable
data class ReminderSetting(val on: Boolean, val offset: Int)

@Serializable
data class AlertsConfig(
    val prayers: Map<String, PrayerAlert> = emptyMap(),
    val alarms: List<PrayerAlarm> = emptyList(),
    val reminders: Map<String, ReminderSetting> = emptyMap(),
    /** play the adhan even when the phone is on silent or vibrate */
    val playOnSilent: Boolean = false,
    /** nothing sounds until the user has opened «الأذان والتنبيهات» once (no surprise adhan after an update) */
    val setUp: Boolean = false,
    /** the full-screen-alarm permission was asked for once: never ask again (some phones always report it off) */
    val fullScreenAsked: Boolean = false,
    /** «الصمت أثناء الصلاة» */
    val silent: SilentConfig = SilentConfig(),
) {
    fun prayer(p: Prayer): PrayerAlert = prayers[p.name] ?: PrayerAlert(on = p != Prayer.Sunrise, sound = if (p == Prayer.Sunrise) "sys" else "")
    fun reminder(r: Reminder): ReminderSetting = reminders[r.id] ?: ReminderSetting(r.defaultOn, r.defaultOffset)
    fun with(p: Prayer, a: PrayerAlert) = copy(prayers = prayers + (p.name to a))
    fun with(r: Reminder, s: ReminderSetting) = copy(reminders = reminders + (r.id to s))

    fun encode(): String = json.encodeToString(serializer(), this)

    companion object {
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
        fun decode(raw: String?): AlertsConfig = raw?.let { runCatching { json.decodeFromString(serializer(), it) }.getOrNull() } ?: AlertsConfig()
    }
}

/** What a reminder is anchored to. */
enum class Anchor { Fajr, Sunrise, Asr, Maghrib, Isha, LastThird }

/**
 * The daily reminders. [days]: which weekdays (ISO numbers; empty = every day); [hijriDays]: only on
 * these days of the Hijri month. [open]: the adhkar section opened from the notification.
 */
enum class Reminder(
    val id: String, val arTitle: String, val arText: String, val anchor: Anchor, val defaultOffset: Int,
    val defaultOn: Boolean = false, val open: String? = null, val days: Set<Int> = emptySet(), val hijriDays: Set<Int> = emptySet(),
) {
    Morning("morning", "أذكار الصباح", "حان وقت أذكار الصباح", Anchor.Fajr, 30, true, "morning"),
    Evening("evening", "أذكار المساء", "حان وقت أذكار المساء", Anchor.Asr, 30, true, "evening"),
    Duha("duha", "صلاة الضحى", "دخل وقت صلاة الضحى", Anchor.Sunrise, 20, open = "duha"),
    Sleep("sleep", "أذكار النوم", "لا تنسَ أذكار النوم", Anchor.Isha, 120, open = "sleep"),
    Qiyam("qiyam", "قيام الليل", "دخل ثلث الليل الآخر، وقت النزول الإلهي", Anchor.LastThird, 0, open = "qiyam"),
    Kahf("kahf", "سورة الكهف", "اليوم الجمعة: سورة الكهف والصلاة على النبي ﷺ", Anchor.Sunrise, 60, open = "kahf", days = setOf(5)),
    FridayHour("friday_hour", "ساعة الإجابة", "آخر ساعة من يوم الجمعة، فأكثِر من الدعاء", Anchor.Maghrib, -60, open = "friday_hour", days = setOf(5)),
    MonThu("mon_thu", "صيام الاثنين والخميس", "غدًا يُستحب الصيام، فلا تنسَ نية الصيام والسحور", Anchor.Isha, 30, days = setOf(7, 3)),
    WhiteDays("white_days", "صيام الأيام البيض", "غدًا من الأيام البيض (١٣ و١٤ و١٥ من الشهر الهجري)", Anchor.Isha, 30, hijriDays = setOf(12, 13, 14)),
    ;
    val title: String get() = t(arTitle)
    val text: String get() = t(arText)
}

/** One thing to sound or show at a moment. */
@Serializable
data class AlertEvent(
    val at: Long,
    val kind: String, // adhan, pre, iqama, alarm, reminder
    val key: String,
    val sound: String,
    val title: String,
    val text: String,
    val open: String? = null,
    val prayer: String? = null,
) {
    companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}

object AlertEvents {
    private const val CAIRO_LAT = 30.0444
    private const val CAIRO_LNG = 31.2357
    val AlertPrayers = listOf(Prayer.Fajr, Prayer.Sunrise, Prayer.Dhuhr, Prayer.Asr, Prayer.Maghrib, Prayer.Isha)

    /** Every event from [fromMs] up to [days] days on, in time order. */
    fun upcoming(prefs: AppPrefs, catalog: List<SoundEntry>, fromMs: Long, days: Int = 2): List<AlertEvent> {
        val zone = ZoneId.systemDefault()
        val lat = prefs.lat ?: CAIRO_LAT
        val lng = prefs.lng ?: CAIRO_LNG
        val cfg = prefs.alerts
        val alerts = cfg.setUp
        val silence = cfg.silent.on
        if (!alerts && !silence) return emptyList()
        val start = java.time.Instant.ofEpochMilli(fromMs).atZone(zone).toLocalDate().minusDays(1)
        val out = mutableListOf<AlertEvent>()
        for (k in 0..days + 1) {
            val d = start.plusDays(k.toLong())
            val times = PrayerTimes.forDay(d, lat, lng, prefs.prayer)
            fun at(min: Int): Long = d.atStartOfDay(zone).plusMinutes(min.toLong()).toInstant().toEpochMilli()
            if (silence) for (p in SilentPrayers) {
                val w = silenceWindow(p, times, prefs, catalog) ?: continue
                out += AlertEvent(at(w.first), "silent_on", "silent_on:${p.name}", "", t("الصمت أثناء الصلاة"), p.ar, prayer = p.name)
                out += AlertEvent(at(w.second), "silent_off", "silent_off:${p.name}", "", t("الصمت أثناء الصلاة"), p.ar, prayer = p.name)
            }
            if (!alerts) continue
            for (p in AlertPrayers) {
                val a = cfg.prayer(p)
                val adhan = times.getValue(p)
                val iq = prefs.prayer.iqama[p] ?: 0
                if (a.on) {
                    val sound = a.sound.ifBlank { Sounds.defaultFor(p, catalog) }
                    out += AlertEvent(
                        at(adhan), "adhan", "adhan:${p.name}", sound,
                        if (p == Prayer.Sunrise) t("موعد الشروق") else t("أذان صلاة {0}", p.ar),
                        "",
                        prayer = p.name,
                    )
                }
                if (a.pre > 0) out += AlertEvent(
                    at(adhan - a.pre), "pre", "pre:${p.name}", a.preSound,
                    if (p == Prayer.Sunrise) t("بقي {0} دقيقة على الشروق", num(a.pre)) else t("بقي {0} دقيقة على أذان {1}", num(a.pre), p.ar),
                    "", prayer = p.name,
                )
                if (a.iqama && p != Prayer.Sunrise && iq > 0) {
                    val before = a.iqamaBefore.coerceIn(0, iq)
                    out += AlertEvent(
                        at(adhan + iq - before), "iqama", "iqama:${p.name}", a.iqamaSound,
                        if (before == 0) t("إقامة صلاة {0}", p.ar) else t("بقي {0} دقيقة على إقامة صلاة {1}", num(before), p.ar),
                        "", prayer = p.name,
                    )
                }
            }
            for (al in cfg.alarms) {
                if (!al.on || (al.days.isNotEmpty() && d.dayOfWeek.value !in al.days)) continue
                val p = al.prayerEnum
                val base = times.getValue(p) + if (al.iqama) (prefs.prayer.iqama[p] ?: 0) else 0
                out += AlertEvent(at(base + al.offset), "alarm", "alarm:${al.id}", al.sound, al.label.ifBlank { alarmTitle(al) }, alarmTitle(al), prayer = p.name)
            }
            val hijriDay = Hijri.hijrah(d).get(ChronoField.DAY_OF_MONTH)
            for (r in Reminder.entries) {
                val s = cfg.reminder(r)
                if (!s.on) continue
                if (r.days.isNotEmpty() && d.dayOfWeek.value !in r.days) continue
                if (r.hijriDays.isNotEmpty() && hijriDay !in r.hijriDays) continue
                val base = when (r.anchor) {
                    Anchor.Fajr -> times.getValue(Prayer.Fajr)
                    Anchor.Sunrise -> times.getValue(Prayer.Sunrise)
                    Anchor.Asr -> times.getValue(Prayer.Asr)
                    Anchor.Maghrib -> times.getValue(Prayer.Maghrib)
                    Anchor.Isha -> times.getValue(Prayer.Isha)
                    // the last third of tonight: from Maghrib to tomorrow's Fajr
                    Anchor.LastThird -> {
                        val m = times.getValue(Prayer.Maghrib)
                        val f = PrayerTimes.forDay(d.plusDays(1), lat, lng, prefs.prayer).getValue(Prayer.Fajr) + 1440
                        m + (f - m) * 2 / 3
                    }
                }
                out += if (r.anchor == Anchor.LastThird)
                    AlertEvent(at(base + s.offset), "reminder", "reminder:${r.id}", "sys", t("موعد الثلث الأخير"), "", open = r.open)
                else AlertEvent(at(base + s.offset), "reminder", "reminder:${r.id}", "sys", r.title, r.text, open = r.open)
            }
        }
        return out.filter { it.at > fromMs }.sortedBy { it.at }
    }

    val SilentPrayers = listOf(Prayer.Fajr, Prayer.Dhuhr, Prayer.Asr, Prayer.Maghrib, Prayer.Isha)

    /**
     * The minutes of the day the phone is silent around a prayer: from when the adhan ends (the
     * length of the adhan the app plays, if it plays one) to [SilentConfig.after] minutes past the iqama.
     */
    fun silenceWindow(p: Prayer, times: Map<Prayer, Int>, prefs: AppPrefs, catalog: List<SoundEntry>): Pair<Int, Int>? {
        val cfg = prefs.alerts
        if (p.name in cfg.silent.off) return null
        val adhan = times[p] ?: return null
        val a = cfg.prayer(p)
        val sound = a.sound.ifBlank { Sounds.defaultFor(p, catalog) }
        val playing = cfg.setUp && a.on && (sound.startsWith("full:") || sound.startsWith("short:"))
        val secs = if (!playing) 0 else Sounds.entry(catalog, sound)?.let { if (sound.startsWith("short:")) 40 else it.seconds } ?: 0
        val start = adhan + (secs + 59) / 60
        val end = adhan + (prefs.prayer.iqama[p] ?: 0) + cfg.silent.after
        return if (end > start) start to end else null
    }

    /** «قبل أذان الفجر بـ ١٥ دقيقة» and the like. */
    fun alarmTitle(a: PrayerAlarm): String {
        val p = a.prayerEnum.ar
        val m = num(kotlin.math.abs(a.offset))
        return when {
            a.offset == 0 && !a.iqama -> t("عند أذان {0}", p)
            a.offset == 0 -> t("عند إقامة {0}", p)
            a.offset < 0 && !a.iqama -> t("قبل أذان {0} بـ {1} دقيقة", p, m)
            a.offset < 0 -> t("قبل إقامة {0} بـ {1} دقيقة", p, m)
            !a.iqama -> t("بعد أذان {0} بـ {1} دقيقة", p, m)
            else -> t("بعد إقامة {0} بـ {1} دقيقة", p, m)
        }
    }

    fun daysLabel(days: Set<Int>): String = when {
        days.isEmpty() || days.size == 7 -> t("كل يوم")
        else -> DayOfWeek.entries.sortedBy { (it.value + 1) % 7 }.filter { it.value in days }.joinToString("، ") { dayShort(it.value) }
    }

    fun dayShort(iso: Int): String = when (iso) {
        6 -> t("سبت"); 7 -> t("أحد"); 1 -> t("اثنين"); 2 -> t("ثلاثاء"); 3 -> t("أربعاء"); 4 -> t("خميس"); else -> t("جمعة")
    }

    private fun num(n: Int) = ArabicText.num(n)
}

/** One muezzin's recordings, from assets/audio/catalog.json (built by the audio workflow). */
@Serializable
data class SoundEntry(
    val id: String,
    val name: String,
    val place: String = "",
    val fajr: Boolean = false,
    val full: String,
    val short: String? = null,
    val fullSize: Long = 0,
    val shortSize: Long = 0,
    val bundled: Boolean = false,
    val seconds: Int = 0,
)

object Sounds {
    private const val RELEASE = "https://github.com/kouwis/Alzkr/releases/download/audio-v1/"
    @Volatile private var cache: List<SoundEntry>? = null

    fun catalog(context: Context): List<SoundEntry> = cache ?: runCatching {
        val text = context.assets.open("audio/catalog.json").bufferedReader().use { it.readText() }
        Json { ignoreUnknownKeys = true }.decodeFromString<List<SoundEntry>>(text)
    }.getOrDefault(emptyList()).also { cache = it }

    /** The first bundled recording of the right kind (a Fajr adhan for Fajr). */
    fun defaultFor(p: Prayer, catalog: List<SoundEntry>): String {
        if (p == Prayer.Sunrise) return "sys"
        val e = catalog.firstOrNull { it.bundled && it.fajr == (p == Prayer.Fajr) } ?: catalog.firstOrNull { it.bundled } ?: return "sys"
        return "full:${e.id}"
    }

    fun entry(catalog: List<SoundEntry>, sound: String): SoundEntry? =
        sound.substringAfter(':', "").takeIf { it.isNotEmpty() }?.let { id -> catalog.firstOrNull { it.id == id } }

    /** The file of a "full:"/"short:" sound. */
    fun fileName(catalog: List<SoundEntry>, sound: String): String? {
        val e = entry(catalog, sound) ?: return null
        return if (sound.startsWith("short:")) e.short else e.full
    }

    fun remoteUrl(catalog: List<SoundEntry>, sound: String): String = RELEASE + (fileName(catalog, sound) ?: "")

    fun isBundled(catalog: List<SoundEntry>, sound: String) = entry(catalog, sound)?.bundled == true

    private fun dir(context: Context) = File(context.filesDir, "audio").apply { mkdirs() }

    fun localFile(context: Context, name: String) = File(dir(context), name)

    /** Ready to play now: bundled, downloaded, or one of the phone's own sounds. */
    fun ready(context: Context, catalog: List<SoundEntry>, sound: String): Boolean {
        if (!sound.startsWith("full:") && !sound.startsWith("short:")) return true
        if (isBundled(catalog, sound)) return true
        val f = fileName(catalog, sound) ?: return false
        return localFile(context, f).let { it.exists() && it.length() > 1000 }
    }

    /** Downloads a recording from the app's GitHub release; [progress] gets 0..1. */
    fun download(context: Context, catalog: List<SoundEntry>, sound: String, progress: (Float) -> Unit = {}): Boolean = runCatching {
        val name = fileName(catalog, sound) ?: return false
        val target = localFile(context, name)
        val tmp = File(target.path + ".part")
        val con = URL(RELEASE + name).openConnection() as HttpURLConnection
        con.instanceFollowRedirects = true
        con.connectTimeout = 15_000; con.readTimeout = 30_000
        val total = con.contentLengthLong.takeIf { it > 0 } ?: 1L
        con.inputStream.use { input ->
            tmp.outputStream().use { out ->
                val buf = ByteArray(32 * 1024); var done = 0L
                while (true) {
                    val n = input.read(buf); if (n < 0) break
                    out.write(buf, 0, n); done += n
                    progress((done.toFloat() / total).coerceIn(0f, 1f))
                }
            }
        }
        tmp.renameTo(target) && target.length() > 1000
    }.getOrDefault(false)

    /** A sound's name for the lists: «علي أحمد ملا · أذان كامل». */
    fun label(catalog: List<SoundEntry>, sound: String): String = when {
        sound == "sys" -> t("صوت الإشعار")
        sound == "alarm" -> t("صوت المنبّه")
        sound == "vib" -> t("اهتزاز فقط")
        else -> entry(catalog, sound)?.let { e ->
            val n = t(e.name)
            if (sound.startsWith("short:")) t("{0} · تكبيرات", n) else if (e.fajr) t("{0} · أذان الفجر", n) else n
        } ?: t("صوت الإشعار")
    }
}
