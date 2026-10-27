package com.example.util

import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object DateUtils {
    private val isoDateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val isoMonthFormatter = SimpleDateFormat("yyyy-MM", Locale.US)
    private val displayDateFormatter = SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault())
    private val fullDateAndDayFormatter = SimpleDateFormat("EEEE, d MMM yyyy", Locale.getDefault())
    private val dayOfWeekFormatter = SimpleDateFormat("EEEE", Locale.getDefault())
    private val displayMonthFormatter = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
    private val timeFormatter = SimpleDateFormat("h:mm a", Locale.getDefault())

    private val numberFormat = DecimalFormat("#,##0.00")
    private val litreFormat = DecimalFormat("#,##0.##")

    const val DEFAULT_CURRENCY = "Rs."

    fun getTodayDate(): String = isoDateFormatter.format(Date())

    fun getCurrentMonth(): String = isoMonthFormatter.format(Date())

    fun getCurrentBsMonth(): String = BikramSambatCalendar.getTodayBs().yearMonthKey

    fun getTodayBs(): BsDate = BikramSambatCalendar.getTodayBs()

    fun getBsDate(dateStr: String): BsDate = BikramSambatCalendar.adToBs(dateStr)

    fun formatDisplayDate(dateStr: String, useBs: Boolean = true): String {
        return if (useBs) {
            val bs = BikramSambatCalendar.adToBs(dateStr)
            "${bs.day} ${bs.monthNameEn} ${bs.year} BS"
        } else {
            try {
                val parsed = isoDateFormatter.parse(dateStr)
                if (parsed != null) displayDateFormatter.format(parsed) else dateStr
            } catch (_: Exception) {
                dateStr
            }
        }
    }

    fun formatFullDateAndDay(dateStr: String, useBs: Boolean = true): String {
        return if (useBs) {
            val bs = BikramSambatCalendar.adToBs(dateStr)
            "${bs.dayOfWeek}, ${bs.day} ${bs.monthNameEn} ${bs.year} BS"
        } else {
            try {
                val parsed = isoDateFormatter.parse(dateStr)
                if (parsed != null) fullDateAndDayFormatter.format(parsed) else dateStr
            } catch (_: Exception) {
                dateStr
            }
        }
    }

    fun formatDayOfWeek(dateStr: String): String {
        val bs = BikramSambatCalendar.adToBs(dateStr)
        return bs.dayOfWeek
    }

    fun formatDayOfWeekNepali(dateStr: String): String {
        val bs = BikramSambatCalendar.adToBs(dateStr)
        return bs.dayOfWeekNp
    }

    fun formatDisplayMonth(monthStr: String, useBs: Boolean = true): String {
        return if (useBs) {
            BikramSambatCalendar.formatBsMonthDisplay(monthStr)
        } else {
            try {
                val parsed = isoMonthFormatter.parse(monthStr)
                if (parsed != null) displayMonthFormatter.format(parsed) else monthStr
            } catch (_: Exception) {
                monthStr
            }
        }
    }

    fun formatTime(timestamp: Long): String {
        return timeFormatter.format(Date(timestamp))
    }

    fun shiftMonth(currentMonthStr: String, deltaMonths: Int, useBs: Boolean = true): String {
        return if (useBs) {
            BikramSambatCalendar.shiftBsMonth(currentMonthStr, deltaMonths)
        } else {
            try {
                val parsed = isoMonthFormatter.parse(currentMonthStr) ?: Date()
                val cal = Calendar.getInstance()
                cal.time = parsed
                cal.add(Calendar.MONTH, deltaMonths)
                isoMonthFormatter.format(cal.time)
            } catch (_: Exception) {
                currentMonthStr
            }
        }
    }

    fun formatAmount(amount: Double, symbol: String = DEFAULT_CURRENCY): String {
        val cleanSymbol = symbol.trim()
        val spacing = if (cleanSymbol.endsWith(".") || cleanSymbol.matches(Regex("[a-zA-Z]+"))) " " else " "
        return "$cleanSymbol$spacing${numberFormat.format(amount)}"
    }

    fun formatLitres(litres: Double): String {
        return "${litreFormat.format(litres)} L"
    }

    fun roundToTwoDecimals(value: Double): Double {
        return kotlin.math.round(value * 100.0) / 100.0
    }
}
