package com.example.data

import java.util.Calendar

enum class Shift(
    val id: String,
    val titleEn: String,
    val titleNp: String,
    val iconName: String,
    val timeRange: String
) {
    MORNING("morning", "Morning", "बिहान", "🌅", "04:00 - 11:59"),
    NOON("noon", "Noon", "दिउँसो", "☀️", "12:00 - 16:59"),
    EVENING("evening", "Evening", "बेलुका", "🌙", "17:00 - 23:59");

    val displayName: String
        get() = "$iconName $titleEn ($titleNp)"

    val shortName: String
        get() = "$titleEn / $titleNp"

    companion object {
        fun fromId(id: String?): Shift {
            return when (id?.lowercase()?.trim()) {
                "noon", "दिउँसो" -> NOON
                "evening", "बेलुका" -> EVENING
                else -> MORNING
            }
        }

        fun currentShift(): Shift {
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            return when (hour) {
                in 4..11 -> MORNING
                in 12..16 -> NOON
                else -> EVENING
            }
        }
    }
}
