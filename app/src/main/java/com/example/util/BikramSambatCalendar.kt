package com.example.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class BsDate(
    val year: Int,
    val month: Int, // 1 = Baishakh ... 12 = Chaitra
    val day: Int,
    val dayOfWeek: String, // e.g. "Tuesday"
    val dayOfWeekNp: String // e.g. "मंगलबार"
) {
    val monthNameEn: String get() = BikramSambatCalendar.MONTH_NAMES_EN.getOrElse(month - 1) { "" }
    val monthNameNp: String get() = BikramSambatCalendar.MONTH_NAMES_NP.getOrElse(month - 1) { "" }

    val formattedBsDate: String
        get() = "$day $monthNameEn $year BS"

    val formattedFull: String
        get() = "$dayOfWeek, $day $monthNameEn $year BS"

    val formattedBilingual: String
        get() = "$dayOfWeek, $day $monthNameEn $year BS ($monthNameNp $day)"

    val yearMonthKey: String
        get() = "%04d-%02d".format(Locale.US, year, month)

    val isoBsDate: String
        get() = "%04d-%02d-%02d".format(Locale.US, year, month, day)

    val nepaliNumeralDate: String
        get() = "${BikramSambatCalendar.toNepaliDigits(day)} $monthNameNp ${BikramSambatCalendar.toNepaliDigits(year)}"
}

object BikramSambatCalendar {

    val MONTH_NAMES_EN = listOf(
        "Baishakh", "Jestha", "Ashadh", "Shrawan", "Bhadra", "Ashwin",
        "Kartik", "Mangsir", "Poush", "Magh", "Falgun", "Chaitra"
    )

    val MONTH_NAMES_NP = listOf(
        "बैशाख", "जेठ", "असार", "साउन", "भदौ", "असोज",
        "कात्तिक", "मंसिर", "पुष", "माघ", "फागुन", "चैत"
    )

    val DAY_NAMES_EN = listOf(
        "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"
    )

    val DAY_NAMES_NP = listOf(
        "आइतबार", "सोमबार", "मंगलबार", "बुधबार", "बिहीबार", "शुक्रबार", "शनिबार"
    )

    // Exact days in each month (Baishakh to Chaitra) for each BS Year from 2070 to 2090
    private val MONTH_DAYS = mapOf(
        2070 to intArrayOf(31, 32, 31, 32, 31, 30, 30, 30, 29, 30, 29, 31),
        2071 to intArrayOf(31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30),
        2072 to intArrayOf(31, 31, 32, 32, 31, 30, 30, 29, 30, 29, 30, 30),
        2073 to intArrayOf(31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 31),
        2074 to intArrayOf(30, 32, 31, 32, 31, 30, 30, 30, 29, 30, 29, 31),
        2075 to intArrayOf(31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30),
        2076 to intArrayOf(31, 31, 32, 32, 31, 30, 30, 29, 30, 29, 30, 30),
        2077 to intArrayOf(31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 31),
        2078 to intArrayOf(30, 32, 31, 32, 31, 30, 30, 30, 29, 30, 29, 31),
        2079 to intArrayOf(31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30),
        2080 to intArrayOf(31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 30),
        2081 to intArrayOf(31, 32, 31, 32, 31, 30, 30, 30, 29, 30, 29, 31),
        2082 to intArrayOf(31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30),
        2083 to intArrayOf(31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30),
        2084 to intArrayOf(31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 31),
        2085 to intArrayOf(30, 32, 31, 32, 31, 30, 30, 30, 29, 30, 29, 31),
        2086 to intArrayOf(31, 32, 31, 32, 31, 30, 30, 29, 30, 29, 30, 30),
        2087 to intArrayOf(31, 31, 32, 31, 31, 30, 30, 30, 29, 29, 30, 30),
        2088 to intArrayOf(31, 31, 32, 32, 31, 30, 30, 29, 30, 29, 30, 30),
        2089 to intArrayOf(31, 31, 32, 31, 31, 30, 30, 29, 30, 29, 30, 31),
        2090 to intArrayOf(30, 32, 31, 32, 31, 30, 30, 29, 30, 29, 30, 30)
    )

    // Start of 1 Baishakh in Gregorian AD (Year, Month 1-based, Day)
    private val BS_YEAR_START_AD = mapOf(
        2070 to Triple(2013, 4, 14),
        2071 to Triple(2014, 4, 14),
        2072 to Triple(2015, 4, 14),
        2073 to Triple(2016, 4, 13),
        2074 to Triple(2017, 4, 14),
        2075 to Triple(2018, 4, 14),
        2076 to Triple(2019, 4, 14),
        2077 to Triple(2020, 4, 13),
        2078 to Triple(2021, 4, 14),
        2079 to Triple(2022, 4, 14),
        2080 to Triple(2023, 4, 14),
        2081 to Triple(2024, 4, 13),
        2082 to Triple(2025, 4, 14),
        2083 to Triple(2026, 4, 14),
        2084 to Triple(2027, 4, 14),
        2085 to Triple(2028, 4, 13),
        2086 to Triple(2029, 4, 14),
        2087 to Triple(2030, 4, 14),
        2088 to Triple(2031, 4, 14),
        2089 to Triple(2032, 4, 13),
        2090 to Triple(2033, 4, 14)
    )

    private val defaultMonthDays = intArrayOf(31, 31, 31, 32, 31, 30, 30, 29, 30, 29, 30, 30)

    fun toNepaliDigits(number: Int): String {
        val nepaliDigits = arrayOf('०', '१', '२', '३', '४', '५', '६', '७', '८', '९')
        return number.toString().map { char ->
            if (char in '0'..'9') nepaliDigits[char - '0'] else char
        }.joinToString("")
    }

    fun getDaysInBsMonth(bsYear: Int, bsMonth: Int): Int {
        val daysArray = MONTH_DAYS[bsYear] ?: defaultMonthDays
        return if (bsMonth in 1..12) daysArray[bsMonth - 1] else 30
    }

    /**
     * Converts a Gregorian Date to Bikram Sambat (BsDate)
     */
    fun adToBs(adDate: Date): BsDate {
        val targetCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            time = adDate
        }
        val adY = targetCal.get(Calendar.YEAR)
        val adM = targetCal.get(Calendar.MONTH) + 1 // 1-based
        val adD = targetCal.get(Calendar.DAY_OF_MONTH)

        val dayOfWeekIdx = (targetCal.get(Calendar.DAY_OF_WEEK) - 1 + 7) % 7 // 0 = Sunday
        val dayOfWeekEn = DAY_NAMES_EN.getOrElse(dayOfWeekIdx) { "Sunday" }
        val dayOfWeekNp = DAY_NAMES_NP.getOrElse(dayOfWeekIdx) { "आइतबार" }

        return adToBs(adY, adM, adD, dayOfWeekEn, dayOfWeekNp)
    }

    fun adToBs(adYear: Int, adMonth: Int, adDay: Int, dayOfWeekEn: String, dayOfWeekNp: String): BsDate {
        // Determine estimated BS year
        var bsYear = adYear + 57
        val currentYearStart = BS_YEAR_START_AD[bsYear] ?: Triple(adYear, 4, 14)

        // Check if the date is before 1 Baishakh of currentYear
        if (adMonth < currentYearStart.second || (adMonth == currentYearStart.second && adDay < currentYearStart.third)) {
            bsYear = adYear + 56
        }

        val start = BS_YEAR_START_AD[bsYear] ?: Triple(bsYear - 57, 4, 14)

        val startCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(start.first, start.second - 1, start.third)
        }
        val targetCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(adYear, adMonth - 1, adDay)
        }

        val diffMillis = targetCal.timeInMillis - startCal.timeInMillis
        var daysElapsed = (diffMillis / 86400000L).toInt()

        val months = MONTH_DAYS[bsYear] ?: defaultMonthDays
        var bsMonth = 1
        for (m in months.indices) {
            val daysInM = months[m]
            if (daysElapsed >= daysInM) {
                daysElapsed -= daysInM
                bsMonth++
            } else {
                break
            }
        }
        val bsDay = daysElapsed + 1

        return BsDate(
            year = bsYear,
            month = bsMonth,
            day = bsDay,
            dayOfWeek = dayOfWeekEn,
            dayOfWeekNp = dayOfWeekNp
        )
    }

    fun adToBs(isoDateString: String): BsDate {
        return try {
            val parts = isoDateString.split("-")
            val y = parts[0].toInt()
            val m = parts[1].toInt()
            val d = parts[2].toInt()

            val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                clear()
                set(y, m - 1, d)
            }
            val dayOfWeekIdx = (cal.get(Calendar.DAY_OF_WEEK) - 1 + 7) % 7
            val dayOfWeekEn = DAY_NAMES_EN.getOrElse(dayOfWeekIdx) { "Sunday" }
            val dayOfWeekNp = DAY_NAMES_NP.getOrElse(dayOfWeekIdx) { "आइतबार" }

            adToBs(y, m, d, dayOfWeekEn, dayOfWeekNp)
        } catch (_: Exception) {
            adToBs(Date())
        }
    }

    fun getTodayBs(): BsDate = adToBs(Date())

    fun shiftBsMonth(currentBsYearMonth: String, deltaMonths: Int): String {
        val parts = currentBsYearMonth.split("-")
        var y = parts.getOrNull(0)?.toIntOrNull() ?: getTodayBs().year
        var m = parts.getOrNull(1)?.toIntOrNull() ?: getTodayBs().month

        m += deltaMonths
        while (m > 12) {
            m -= 12
            y++
        }
        while (m < 1) {
            m += 12
            y--
        }

        return "%04d-%02d".format(Locale.US, y, m)
    }

    fun formatBsMonthDisplay(bsYearMonth: String): String {
        val parts = bsYearMonth.split("-")
        val y = parts.getOrNull(0)?.toIntOrNull() ?: getTodayBs().year
        val m = parts.getOrNull(1)?.toIntOrNull() ?: getTodayBs().month
        val monthEn = MONTH_NAMES_EN.getOrElse(m - 1) { "" }
        val monthNp = MONTH_NAMES_NP.getOrElse(m - 1) { "" }
        return "$monthEn $y BS ($monthNp)"
    }
}
