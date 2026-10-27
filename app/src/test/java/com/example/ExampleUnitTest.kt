package com.example

import com.example.util.BikramSambatCalendar
import com.example.util.DateUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun roundToTwoDecimals_isAccurate() {
        assertEquals(12.35, DateUtils.roundToTwoDecimals(12.3456), 0.001)
        assertEquals(10.0, DateUtils.roundToTwoDecimals(10.0), 0.001)
    }

    @Test
    fun formatLitres_displaysCorrectly() {
        assertEquals("2.5 L", DateUtils.formatLitres(2.5))
        assertEquals("10 L", DateUtils.formatLitres(10.0))
    }

    @Test
    fun formatAmount_displaysWithRsByDefault() {
        val formatted = DateUtils.formatAmount(125.0)
        assertTrue("Expected Rs. in amount: $formatted", formatted.contains("Rs."))
        assertEquals("Rs. 125.00", formatted.trim())
    }

    @Test
    fun formatAmount_displaysCustomCurrency() {
        assertEquals("रू 500.50", DateUtils.formatAmount(500.5, "रू"))
        assertEquals("₹ 500.50", DateUtils.formatAmount(500.5, "₹"))
    }

    @Test
    fun bikramSambat_convertsAdToBsAccurately() {
        val bsDate = BikramSambatCalendar.adToBs("2026-10-27")
        assertEquals(2083, bsDate.year)
        assertEquals(7, bsDate.month) // Kartik
        assertEquals("Kartik", bsDate.monthNameEn)
        assertEquals("कात्तिक", bsDate.monthNameNp)
        assertEquals(10, bsDate.day)
        assertEquals("Tuesday", bsDate.dayOfWeek)
        assertEquals("मंगलबार", bsDate.dayOfWeekNp)
        assertEquals("10 Kartik 2083 BS", bsDate.formattedBsDate)
    }

    @Test
    fun bikramSambat_formatsFullDateWithDay() {
        val full = DateUtils.formatFullDateAndDay("2026-10-27", useBs = true)
        assertEquals("Tuesday, 10 Kartik 2083 BS", full)
    }

    @Test
    fun bikramSambat_shiftsBsMonthCorrectly() {
        val shiftedNext = BikramSambatCalendar.shiftBsMonth("2083-07", 1)
        assertEquals("2083-08", shiftedNext) // Mangsir

        val shiftedPrev = BikramSambatCalendar.shiftBsMonth("2083-07", -1)
        assertEquals("2083-06", shiftedPrev) // Ashwin

        val display = BikramSambatCalendar.formatBsMonthDisplay("2083-07")
        assertTrue(display.contains("Kartik 2083 BS"))
    }
}
