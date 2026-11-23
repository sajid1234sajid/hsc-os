package com.example.data

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

object StudyDateUtils {
    const val PLAN_START = "2026-11-24"
    const val PLAN_END = "2027-05-23"
    const val TOTAL_WEEKS = 26

    private val isoFormat: SimpleDateFormat
        get() = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }

    fun todayIso(): String = isoFormat.format(Calendar.getInstance().time)

    fun parseCalendar(isoDate: String): Calendar? {
        return runCatching {
            val date = isoFormat.parse(isoDate) ?: return null
            Calendar.getInstance().apply {
                time = date
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
        }.getOrNull()
    }

    fun todayCalendar(): Calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    fun daysBetween(fromIso: String, toIso: String): Int {
        val from = parseCalendar(fromIso) ?: return 0
        val to = parseCalendar(toIso) ?: return 0
        val diffMs = to.timeInMillis - from.timeInMillis
        return (diffMs / 86_400_000.0).roundToInt()
    }

    fun currentWeekNumber(todayIso: String = todayIso()): Int {
        val daysFromStart = daysBetween(PLAN_START, todayIso)
        return when {
            daysFromStart < 0 -> 1
            else -> ((daysFromStart / 7) + 1).coerceIn(1, TOTAL_WEEKS)
        }
    }

    fun isBeforePlanStart(todayIso: String = todayIso()): Boolean =
        daysBetween(PLAN_START, todayIso) < 0

    fun daysUntilExamEnd(todayIso: String = todayIso()): Int =
        daysBetween(todayIso, PLAN_END).coerceAtLeast(0)

    fun weekDeadlineIso(weekNumber: Int): String {
        if (weekNumber >= TOTAL_WEEKS) return PLAN_END
        val cal = parseCalendar(PLAN_START) ?: return PLAN_END
        cal.add(Calendar.DAY_OF_YEAR, (weekNumber.coerceIn(1, TOTAL_WEEKS) * 7) - 1)
        return isoFormat.format(cal.time)
    }

    fun weekStartIso(weekNumber: Int): String {
        val cal = parseCalendar(PLAN_START) ?: return PLAN_START
        cal.add(Calendar.DAY_OF_YEAR, (weekNumber.coerceIn(1, TOTAL_WEEKS) - 1) * 7)
        return isoFormat.format(cal.time)
    }

    fun dayKeyForRoutine(todayIso: String = todayIso()): String {
        val cal = parseCalendar(todayIso) ?: todayCalendar()
        return when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.SATURDAY -> "SATURDAY"
            Calendar.SUNDAY -> "SUNDAY"
            Calendar.MONDAY -> "MONDAY"
            Calendar.TUESDAY -> "TUESDAY"
            Calendar.WEDNESDAY -> "WEDNESDAY"
            Calendar.THURSDAY -> "THURSDAY"
            Calendar.FRIDAY -> "FRIDAY"
            else -> "SATURDAY"
        }
    }

    fun previousDayIso(isoDate: String): String {
        val cal = parseCalendar(isoDate) ?: return isoDate
        cal.add(Calendar.DAY_OF_YEAR, -1)
        return isoFormat.format(cal.time)
    }

    fun formatReadableDate(isoDate: String, lang: AppLanguage): String {
        val cal = parseCalendar(isoDate) ?: return isoDate
        val day = cal.get(Calendar.DAY_OF_MONTH)
        val month = cal.get(Calendar.MONTH)
        val year = cal.get(Calendar.YEAR)
        val dow = cal.get(Calendar.DAY_OF_WEEK)

        return if (lang == AppLanguage.BN) {
            val monthsBn = listOf(
                "জানুয়ারি", "ফেব্রুয়ারি", "মার্চ", "এপ্রিল", "মে", "জুন",
                "জুলাই", "আগস্ট", "সেপ্টেম্বর", "অক্টোবর", "নভেম্বর", "ডিসেম্বর"
            )
            val daysBn = mapOf(
                Calendar.SATURDAY to "শনিবার",
                Calendar.SUNDAY to "রবিবার",
                Calendar.MONDAY to "সোমবার",
                Calendar.TUESDAY to "মঙ্গলবার",
                Calendar.WEDNESDAY to "বুধবার",
                Calendar.THURSDAY to "বৃহস্পতিবার",
                Calendar.FRIDAY to "শুক্রবার"
            )
            "${daysBn[dow] ?: ""}, ${toBnDigits(day)} ${monthsBn[month]} ${toBnDigits(year)}"
        } else {
            val fmt = SimpleDateFormat("EEE, dd MMM yyyy", Locale.ENGLISH)
            fmt.format(cal.time)
        }
    }

    fun formatShortDate(isoDate: String, lang: AppLanguage): String {
        val cal = parseCalendar(isoDate) ?: return isoDate
        val day = cal.get(Calendar.DAY_OF_MONTH)
        val month = cal.get(Calendar.MONTH)
        val year = cal.get(Calendar.YEAR)
        return if (lang == AppLanguage.BN) {
            val monthsBn = listOf(
                "জানু", "ফেব্রু", "মার্চ", "এপ্রিল", "মে", "জুন",
                "জুলাই", "আগস্ট", "সেপ্টে", "অক্টো", "নভে", "ডিসে"
            )
            "${toBnDigits(day)} ${monthsBn[month]} ${toBnDigits(year)}"
        } else {
            val fmt = SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH)
            fmt.format(cal.time)
        }
    }

    fun phaseDurationsMinutes(dailyHours: Int): Triple<Int, Int, Int> {
        // 5 hours -> 4 x 75m (50 new + 20 practice + 5 recall)
        // 3 hours -> 4 x 45m (30 new + 12 practice + 3 recall)
        return if (dailyHours <= 3) Triple(30, 12, 3) else Triple(50, 20, 5)
    }

    fun sessionTotalMinutes(dailyHours: Int): Int {
        val (a, b, c) = phaseDurationsMinutes(dailyHours)
        return a + b + c
    }

    fun formatTimerMmSs(seconds: Int, lang: AppLanguage): String {
        val mins = (seconds.coerceAtLeast(0)) / 60
        val secs = (seconds.coerceAtLeast(0)) % 60
        val raw = String.format(Locale.US, "%02d:%02d", mins, secs)
        return if (lang == AppLanguage.BN) toBnDigits(raw) else raw
    }

    fun toBnDigits(input: Int): String = toBnDigits(input.toString())

    fun toBnDigits(input: String): String {
        val bnDigits = charArrayOf('০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯')
        return buildString(input.length) {
            for (ch in input) {
                if (ch in '0'..'9') append(bnDigits[ch - '0']) else append(ch)
            }
        }
    }
}
