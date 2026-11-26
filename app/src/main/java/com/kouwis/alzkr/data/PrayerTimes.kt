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
enum class Prayer(val arName: String) {
    Fajr("الفجر"), Sunrise("الشروق"), Dhuhr("الظهر"), Asr("العصر"), Maghrib("المغرب"), Isha("العشاء");

    /** The name in the interface language. */
    val ar: String get() = t(arName)
}

/** A way of working out Fajr and Isha: the sun's angle below the horizon, or Isha a set time after Maghrib. */
enum class CalcMethod(val arName: String, val fajr: Double, val isha: Double, val ishaMinutes: Int = 0) {
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

    val ar: String get() = t(arName)

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
enum class HighLat(val arName: String) { Middle("منتصف الليل"), Seventh("سُبع الليل"), Angle("نسبة الزاوية"), None("بدون تعديل"); val ar: String get() = t(arName) }

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
    /** in Greater Cairo, Cairo's own times (the mosques' unified adhan), not the exact spot's */
    val cairoUnified: Boolean = true,
) {
    fun methodFor(lat: Double, lng: Double) = method ?: CalcMethod.forPlace(lat, lng)

    /** The place the times are worked out for: Cairo's, for anywhere in Greater Cairo when [cairoUnified] is on. */
    fun placeFor(lat: Double, lng: Double): Pair<Double, Double> =
        if (cairoUnified && inGreaterCairo(lat, lng)) CAIRO else lat to lng

    fun encode(): String = listOf(
        method?.name ?: "auto", if (asrHanafi) "hanafi" else "shafii", highLat.name, shift.toString(),
        Prayer.entries.joinToString(",") { (adjust[it] ?: 0).toString() },
        Prayer.entries.joinToString(",") { (iqama[it] ?: -1).toString() },
        if (cairoUnified) "unified" else "own",
    ).joinToString("|")

    companion object {
        /** The usual time from the adhan to the iqama in Egypt's mosques, in minutes. */
        /**
         * Cairo as Dar al-Ifta's table reckons it: from these coordinates the times match its November 2026
         * table to the minute (175 of 180; the rest a minute's rounding). Greater Cairo's mosques (Cairo,
         * Giza, Qalyubia) call the unified adhan at these times, broadcast from one place.
         */
        val CAIRO = 30.06 to 31.27
        /** Within about 45 km of Cairo: Cairo, Giza (6th of October, Sheikh Zayed), Qalyubia, New Cairo. */
        fun inGreaterCairo(lat: Double, lng: Double) = Locator.km(lat, lng, CAIRO.first, CAIRO.second) <= 45.0

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
                cairoUnified = f.getOrNull(6) != "own",
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
    fun forDay(date: LocalDate, atLat: Double, atLng: Double, cfg: PrayerConfig = PrayerConfig(), zone: ZoneId = ZoneId.systemDefault()): Map<Prayer, Int> {
        val (lat, lng) = cfg.placeFor(atLat, atLng)
        val m = cfg.methodFor(lat, lng)
        val raw = compute(date, lat, lng, zone, m.fajr, m.isha, if (cfg.asrHanafi) 2.0 else 1.0)
        val t = raw.toMutableMap()
        // Isha a set time after Maghrib (Umm al-Qura: 90 minutes, 120 in Ramadan)
        if (m.ishaMinutes > 0) {
            val ramadan = Hijri.hijrah(date).get(ChronoField.MONTH_OF_YEAR) == 9
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
        // the unified adhan of Greater Cairo's mosques is called a minute after the reckoned time (as heard
        // in the mosques: Isha at 6:18 where the table says 6:17); sunrise is not an adhan and stays
        val unified = if (cfg.cairoUnified && PrayerConfig.inGreaterCairo(atLat, atLng)) 1 else 0
        return t.mapValues { (p, v) -> ((v + cfg.shift + (cfg.adjust[p] ?: 0) + if (p == Prayer.Sunrise) 0 else unified) % 1440 + 1440) % 1440 }
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

    /**
     * The sun's declination (degrees) and the equation of time (hours), from Meeus's «Astronomical
     * Algorithms» (as NOAA's solar calculator): apparent longitude with nutation and the true
     * obliquity. Closer than the almanac's short formulas: checked against Dar al-Ifta's Cairo table.
     */
    private fun sunPosition(jd: Double): Pair<Double, Double> {
        val tc = (jd - 2451545.0) / 36525.0
        val l0 = fixAngle(280.46646 + tc * (36000.76983 + tc * 0.0003032))
        val m = 357.52911 + tc * (35999.05029 - 0.0001537 * tc)
        val e = 0.016708634 - tc * (0.000042037 + 0.0000001267 * tc)
        val c = dsin(m) * (1.914602 - tc * (0.004817 + 0.000014 * tc)) + dsin(2 * m) * (0.019993 - 0.000101 * tc) + dsin(3 * m) * 0.000289
        val omega = 125.04 - 1934.136 * tc
        val lambda = l0 + c - 0.00569 - 0.00478 * dsin(omega)
        val eps0 = 23.0 + (26.0 + (21.448 - tc * (46.815 + tc * (0.00059 - tc * 0.001813))) / 60.0) / 60.0
        val eps = eps0 + 0.00256 * dcos(omega)
        val decl = darcsin(dsin(eps) * dsin(lambda))
        val y = tan(Math.toRadians(eps / 2)).let { it * it }
        val l0r = Math.toRadians(l0); val mr = Math.toRadians(m)
        val eqtMin = 4 * Math.toDegrees(
            y * sin(2 * l0r) - 2 * e * sin(mr) + 4 * e * y * sin(mr) * cos(2 * l0r) - 0.5 * y * y * sin(4 * l0r) - 1.25 * e * e * sin(2 * mr),
        )
        return decl to eqtMin / 60.0
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
    private val monthsAr = listOf(
        "محرم", "صفر", "ربيع الأول", "ربيع الآخر", "جمادى الأولى", "جمادى الآخرة",
        "رجب", "شعبان", "رمضان", "شوال", "ذو القعدة", "ذو الحجة",
    )
    private val weekdaysAr = listOf("الاثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجُمُعة", "السبت", "الأحد")
    private val gMonthsAr = listOf("يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو", "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر")

    /** Month and day names in the interface language. */
    val months: List<String> get() = monthsAr.map { t(it) }
    val gMonths: List<String> get() = gMonthsAr.map { t(it) }
    private val weekdays: List<String> get() = weekdaysAr.map { t(it) }

    data class Day(val weekday: String, val day: Int, val month: String, val year: Int, val gregorian: String, val friday: Boolean, val monthNo: Int = 0, val date: LocalDate = LocalDate.now())

    /** «التاريخ» settings, set from the preferences: days added to the Hijri date, and the formats. */
    @Volatile var offset: Int = 0
    @Volatile var hijriFormat: Int = 0
    @Volatile var gregFormat: Int = 0

    /** The Hijri date of a day, with the user's adjustment. */
    fun hijrah(date: LocalDate): HijrahDate = HijrahDate.from(date.plusDays(offset.toLong()))

    /** The Gregorian day of a Hijri date, with the user's adjustment. */
    fun gregorianOf(h: HijrahDate): LocalDate = LocalDate.from(h).minusDays(offset.toLong())

    /** The Hijri date written in the chosen format. */
    fun hijriText(d: Day, format: Int = hijriFormat): String {
        val n = { x: Int -> ArabicText.num(x) }
        return when (format) {
            1 -> t("{0} {1} {2} هـ", n(d.day), d.month, n(d.year))
            2 -> t("{0} هـ", ArabicText.digitsOf("%02d/%02d/%d".format(java.util.Locale.US, d.day, d.monthNo, d.year)))
            3 -> "${d.weekday} ${n(d.day)} ${d.month}"
            else -> "${n(d.day)} ${d.month} ${n(d.year)}"
        }
    }

    /** The Gregorian date written in the chosen format. */
    fun gregorianText(date: LocalDate, format: Int = gregFormat): String {
        val n = { x: Int -> ArabicText.num(x) }
        return when (format) {
            1 -> ArabicText.digitsOf("%02d/%02d/%d".format(java.util.Locale.US, date.dayOfMonth, date.monthValue, date.year))
            2 -> "${weekdays[date.dayOfWeek.value - 1]} ${n(date.dayOfMonth)} ${gMonths[date.monthValue - 1]}"
            3 -> t("{0} {1} {2} م", n(date.dayOfMonth), gMonths[date.monthValue - 1], n(date.year))
            else -> "${n(date.dayOfMonth)} ${gMonths[date.monthValue - 1]} ${n(date.year)}"
        }
    }

    /** [afterMaghrib]: the Islamic day starts at Maghrib, so from then on the Hijri date is tomorrow's (the weekday stays). */
    fun of(date: LocalDate, afterMaghrib: Boolean = false): Day {
        val h = hijrah(if (afterMaghrib) date.plusDays(1) else date)
        return Day(
            weekday = weekdays[date.dayOfWeek.value - 1],
            day = h.get(ChronoField.DAY_OF_MONTH),
            month = months[h.get(ChronoField.MONTH_OF_YEAR) - 1],
            year = h.get(ChronoField.YEAR),
            gregorian = "${date.dayOfMonth} ${gMonths[date.monthValue - 1]} ${date.year}",
            friday = date.dayOfWeek.value == 5,
            monthNo = h.get(ChronoField.MONTH_OF_YEAR),
            date = date,
        )
    }
}
