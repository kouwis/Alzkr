package com.kouwis.alzkr.data

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

/** The six times of the day, in reading order. */
enum class Prayer(val ar: String) {
    Fajr("الفجر"), Sunrise("الشروق"), Dhuhr("الظهر"), Asr("العصر"), Maghrib("المغرب"), Isha("العشاء")
}

/** A way of working out Fajr and Isha: the sun's angle below the horizon, or Isha a set time after Maghrib. */
enum class CalcMethod(val ar: String, val fajr: Double, val isha: Double, val ishaMinutes: Int = 0) {
    Egypt("الهيئة المصرية العامة للمساحة", 19.5, 17.5),
    Makkah("أم القرى (مكة المكرمة)", 18.5, 0.0, ishaMinutes = 90),
    MWL("رابطة العالم الإسلامي", 18.0, 17.0),
    Karachi("جامعة العلوم الإسلامية بكراتشي", 18.0, 18.0),
    ISNA("الجمعية الإسلامية لأمريكا الشمالية", 15.0, 15.0),
    Dubai("الإمارات (دبي)", 18.2, 18.2),
    Kuwait("الكويت", 18.0, 17.5),
    Qatar("قطر", 18.0, 0.0, ishaMinutes = 90),
    Turkey("رئاسة الشؤون الدينية التركية", 18.0, 17.0),
    ;

    companion object {
        /** The method a country uses, from where the place is (rough borders are enough for this). */
        fun forPlace(lat: Double, lng: Double): CalcMethod = when {
            lat in 24.4..26.2 && lng in 50.7..51.7 -> Qatar
            lat in 22.5..26.5 && lng in 51.5..56.5 -> Dubai
            lat in 28.5..30.2 && lng in 46.5..48.6 -> Kuwait
            lat in 22.0..31.8 && lng in 24.6..35.0 -> Egypt
            lat in 16.0..32.2 && lng in 34.5..56.0 -> Makkah
            lat in 35.8..42.2 && lng in 25.6..44.9 -> Turkey
            lat in 23.5..37.1 && lng in 60.5..77.9 -> Karachi
            lng in -170.0..-50.0 && lat in 14.0..72.0 -> ISNA
            else -> MWL
        }
    }
}

/** How to place Fajr and Isha where the night is short (high latitudes). */
enum class HighLat(val ar: String) { Middle("منتصف الليل"), Seventh("سُبع الليل"), Angle("نسبة الزاوية"), None("بدون تعديل") }

/**
 * The settings of «مواقيت الصلاة». [method] null = by country; [asrHanafi]: Asr when a shadow is twice the object;
 * [shift] minutes added to every time (summer time by hand); [adjust] and [iqama] minutes per prayer.
 */
data class PrayerConfig(
    val method: CalcMethod? = null,
    val asrHanafi: Boolean = false,
    val highLat: HighLat = HighLat.Middle,
    val shift: Int = 0,
    val adjust: Map<Prayer, Int> = emptyMap(),
    val iqama: Map<Prayer, Int> = DefaultIqama,
) {
    fun methodFor(lat: Double, lng: Double) = method ?: CalcMethod.forPlace(lat, lng)

    fun encode(): String = listOf(
        method?.name ?: "auto", if (asrHanafi) "hanafi" else "shafii", highLat.name, shift.toString(),
        Prayer.entries.joinToString(",") { (adjust[it] ?: 0).toString() },
        Prayer.entries.joinToString(",") { (iqama[it] ?: -1).toString() },
    ).joinToString("|")

    companion object {
        /** The usual time from the adhan to the iqama in Egypt's mosques, in minutes. */
        val DefaultIqama = mapOf(Prayer.Fajr to 25, Prayer.Dhuhr to 20, Prayer.Asr to 20, Prayer.Maghrib to 10, Prayer.Isha to 20)

        fun decode(raw: String?): PrayerConfig {
            val f = raw?.split('|') ?: return PrayerConfig()
            fun ints(i: Int) = f.getOrNull(i)?.split(',')?.map { it.toIntOrNull() ?: 0 }
            return PrayerConfig(
                method = CalcMethod.entries.firstOrNull { it.name == f.getOrNull(0) },
                asrHanafi = f.getOrNull(1) == "hanafi",
                highLat = HighLat.entries.firstOrNull { it.name == f.getOrNull(2) } ?: HighLat.Middle,
                shift = f.getOrNull(3)?.toIntOrNull() ?: 0,
                adjust = ints(4)?.let { v -> Prayer.entries.mapIndexedNotNull { i, p -> v.getOrNull(i)?.takeIf { it != 0 }?.let { p to it } }.toMap() } ?: emptyMap(),
                iqama = ints(5)?.let { v -> Prayer.entries.mapIndexedNotNull { i, p -> v.getOrNull(i)?.takeIf { it >= 0 }?.let { p to it } }.toMap() } ?: DefaultIqama,
            )
        }
    }
}

/**
 * Prayer times worked out on the phone (no server), with the Egyptian General Authority of
 * Survey method: Fajr at 19.5° below the horizon, Isha at 17.5°, Asr when a shadow equals the
 * object plus its noon shadow (Shafi'i), Maghrib at sunset. The astronomy is the standard
 * PrayTimes algorithm (sun position from the Astronomical Almanac's low-precision formulas).
 */
object PrayerTimes {
    private const val FAJR_ANGLE = 19.5
    private const val ISHA_ANGLE = 17.5
    private const val SUN_ANGLE = 0.833

    /** Minutes after local midnight for each [Prayer], for [date] at [lat]/[lng] in [zone], with the user's settings. */
    fun forDay(date: LocalDate, lat: Double, lng: Double, cfg: PrayerConfig = PrayerConfig(), zone: ZoneId = ZoneId.systemDefault()): Map<Prayer, Int> {
        val m = cfg.methodFor(lat, lng)
        val raw = compute(date, lat, lng, zone, m.fajr, m.isha, if (cfg.asrHanafi) 2.0 else 1.0)
        val t = raw.toMutableMap()
        // Isha a set time after Maghrib (Umm al-Qura: 90 minutes, 120 in Ramadan)
        if (m.ishaMinutes > 0) {
            val ramadan = HijrahDate.from(date).get(ChronoField.MONTH_OF_YEAR) == 9
            t[Prayer.Isha] = raw.getValue(Prayer.Maghrib) + m.ishaMinutes + if (m == CalcMethod.Makkah && ramadan) 30 else 0
        }
        // short nights: Fajr and Isha no further from sunrise and sunset than a part of the night
        if (cfg.highLat != HighLat.None) {
            val night = (raw.getValue(Prayer.Sunrise) + 1440 - raw.getValue(Prayer.Maghrib)) % 1440
            fun part(angle: Double) = when (cfg.highLat) {
                HighLat.Middle -> night / 2
                HighLat.Seventh -> night / 7
                else -> (angle / 60.0 * night).toInt()
            }
            val fajrGap = (raw.getValue(Prayer.Sunrise) - t.getValue(Prayer.Fajr) + 1440) % 1440
            if (fajrGap > part(m.fajr)) t[Prayer.Fajr] = raw.getValue(Prayer.Sunrise) - part(m.fajr)
            if (m.ishaMinutes == 0) {
                val ishaGap = (t.getValue(Prayer.Isha) - raw.getValue(Prayer.Maghrib) + 1440) % 1440
                if (ishaGap > part(m.isha)) t[Prayer.Isha] = raw.getValue(Prayer.Maghrib) + part(m.isha)
            }
        }
        return t.mapValues { (p, v) -> ((v + cfg.shift + (cfg.adjust[p] ?: 0)) % 1440 + 1440) % 1440 }
    }

    private fun compute(date: LocalDate, lat: Double, lng: Double, zone: ZoneId, fajrAngle: Double, ishaAngle: Double, asrFactor: Double): Map<Prayer, Int> {
        val tz = zone.rules.getOffset(date.atTime(12, 0)).totalSeconds / 3600.0
        val jd = julian(date.year, date.monthValue, date.dayOfMonth) - lng / (15.0 * 24.0)
        // two passes: the second uses the first pass's times to place the sun more exactly
        var t = doubleArrayOf(5.0, 6.0, 12.0, 13.0, 18.0, 18.0)
        repeat(2) {
            val p = t.map { it / 24.0 }
            t = doubleArrayOf(
                sunAngleTime(jd, fajrAngle, p[0], lat, ccw = true),
                sunAngleTime(jd, SUN_ANGLE, p[1], lat, ccw = true),
                midDay(jd, p[2]),
                asrTime(jd, asrFactor, p[3], lat),
                sunAngleTime(jd, SUN_ANGLE, p[4], lat, ccw = false),
                sunAngleTime(jd, if (ishaAngle > 0) ishaAngle else 18.0, p[5], lat, ccw = false),
            )
        }
        return Prayer.entries.associateWith { pr ->
            val h = t[pr.ordinal] + tz - lng / 15.0
            Math.round(fixHour(h) * 60.0).toInt()
        }
    }

    /** The prayer whose time has come (the last one that started), and the next one. */
    fun current(times: Map<Prayer, Int>, now: LocalDateTime): Pair<Prayer, Prayer> {
        val m = now.hour * 60 + now.minute
        val started = Prayer.entries.lastOrNull { times.getValue(it) <= m } ?: Prayer.Isha
        val next = Prayer.entries.firstOrNull { times.getValue(it) > m } ?: Prayer.Fajr
        return started to next
    }

    // ---- astronomy ----
    private fun julian(y0: Int, m0: Int, d: Int): Double {
        var y = y0; var m = m0
        if (m <= 2) { y -= 1; m += 12 }
        val a = floor(y / 100.0)
        val b = 2 - a + floor(a / 4.0)
        return floor(365.25 * (y + 4716)) + floor(30.6001 * (m + 1)) + d + b - 1524.5
    }

    private fun sunPosition(jd: Double): Pair<Double, Double> {
        val dd = jd - 2451545.0
        val g = fixAngle(357.529 + 0.98560028 * dd)
        val q = fixAngle(280.459 + 0.98564736 * dd)
        val l = fixAngle(q + 1.915 * dsin(g) + 0.020 * dsin(2 * g))
        val e = 23.439 - 0.00000036 * dd
        val ra = darctan2(dcos(e) * dsin(l), dcos(l)) / 15.0
        val eqt = q / 15.0 - fixHour(ra)
        val decl = darcsin(dsin(e) * dsin(l))
        return decl to eqt
    }

    private fun midDay(jd: Double, t: Double): Double = fixHour(12 - sunPosition(jd + t).second)

    private fun sunAngleTime(jd: Double, angle: Double, t: Double, lat: Double, ccw: Boolean): Double {
        val decl = sunPosition(jd + t).first
        val noon = midDay(jd, t)
        val x = (-dsin(angle) - dsin(decl) * dsin(lat)) / (dcos(decl) * dcos(lat))
        val tt = darccos(x.coerceIn(-1.0, 1.0)) / 15.0
        return noon + if (ccw) -tt else tt
    }

    private fun asrTime(jd: Double, factor: Double, t: Double, lat: Double): Double {
        val decl = sunPosition(jd + t).first
        val angle = -Math.toDegrees(atan(1.0 / (factor + tan(Math.toRadians(abs(lat - decl))))))
        return sunAngleTime(jd, angle, t, lat, ccw = false)
    }

    private fun dsin(d: Double) = sin(Math.toRadians(d))
    private fun dcos(d: Double) = cos(Math.toRadians(d))
    private fun darcsin(x: Double) = Math.toDegrees(asin(x))
    private fun darccos(x: Double) = Math.toDegrees(acos(x))
    private fun darctan2(y: Double, x: Double) = Math.toDegrees(atan2(y, x))
    private fun fixAngle(a: Double) = a - 360.0 * floor(a / 360.0)
    private fun fixHour(a: Double) = a - 24.0 * floor(a / 24.0)
}

/** Hijri date (Umm al-Qura calendar, built into Android). */
object Hijri {
    val months = listOf(
        "محرم", "صفر", "ربيع الأول", "ربيع الآخر", "جمادى الأولى", "جمادى الآخرة",
        "رجب", "شعبان", "رمضان", "شوال", "ذو القعدة", "ذو الحجة",
    )
    private val weekdays = listOf("الاثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجُمُعة", "السبت", "الأحد")
    val gMonths = listOf("يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو", "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر")

    data class Day(val weekday: String, val day: Int, val month: String, val year: Int, val gregorian: String, val friday: Boolean)

    /** [afterMaghrib]: the Islamic day starts at Maghrib, so from then on the Hijri date is tomorrow's (the weekday stays). */
    fun of(date: LocalDate, afterMaghrib: Boolean = false): Day {
        val h = HijrahDate.from(if (afterMaghrib) date.plusDays(1) else date)
        return Day(
            weekday = weekdays[date.dayOfWeek.value - 1],
            day = h.get(ChronoField.DAY_OF_MONTH),
            month = months[h.get(ChronoField.MONTH_OF_YEAR) - 1],
            year = h.get(ChronoField.YEAR),
            gregorian = "${date.dayOfMonth} ${gMonths[date.monthValue - 1]} ${date.year}",
            friday = date.dayOfWeek.value == 5,
        )
    }
}
