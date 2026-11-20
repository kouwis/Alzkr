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

    /** Minutes after local midnight for each [Prayer], for [date] at [lat]/[lng] in [zone]. */
    fun forDay(date: LocalDate, lat: Double, lng: Double, zone: ZoneId = ZoneId.systemDefault()): Map<Prayer, Int> {
        val tz = zone.rules.getOffset(date.atTime(12, 0)).totalSeconds / 3600.0
        val jd = julian(date.year, date.monthValue, date.dayOfMonth) - lng / (15.0 * 24.0)
        // two passes: the second uses the first pass's times to place the sun more exactly
        var t = doubleArrayOf(5.0, 6.0, 12.0, 13.0, 18.0, 18.0)
        repeat(2) {
            val p = t.map { it / 24.0 }
            t = doubleArrayOf(
                sunAngleTime(jd, FAJR_ANGLE, p[0], lat, ccw = true),
                sunAngleTime(jd, SUN_ANGLE, p[1], lat, ccw = true),
                midDay(jd, p[2]),
                asrTime(jd, 1.0, p[3], lat),
                sunAngleTime(jd, SUN_ANGLE, p[4], lat, ccw = false),
                sunAngleTime(jd, ISHA_ANGLE, p[5], lat, ccw = false),
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
    private val months = listOf(
        "محرم", "صفر", "ربيع الأول", "ربيع الآخر", "جمادى الأولى", "جمادى الآخرة",
        "رجب", "شعبان", "رمضان", "شوال", "ذو القعدة", "ذو الحجة",
    )
    private val weekdays = listOf("الاثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجُمُعة", "السبت", "الأحد")
    private val gMonths = listOf("يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو", "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر")

    data class Day(val weekday: String, val day: Int, val month: String, val year: Int, val gregorian: String, val friday: Boolean)

    fun of(date: LocalDate): Day {
        val h = HijrahDate.from(date)
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
