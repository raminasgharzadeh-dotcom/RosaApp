package com.roza.assistant

import java.util.Calendar
import java.util.Locale

object PersianUtils {

    private val persianMonths = arrayOf(
        "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"
    )

    private val gregorianMonths = arrayOf(
        "ژانویه", "فوریه", "مارس", "آوریل", "مه", "ژوئن",
        "ژوئیه", "اوت", "سپتامبر", "اکتبر", "نوامبر", "دسامبر"
    )

    // Calendar.DAY_OF_WEEK starts with Sunday = 1.
    private val weekdays = arrayOf(
        "یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنج‌شنبه", "جمعه", "شنبه"
    )

    // Unifies digits and Arabic letters so that spoken commands are easy to match.
    fun normalize(text: String): String {
        val builder = StringBuilder()
        for (ch in text) {
            when (ch) {
                in '۰'..'۹' -> builder.append('0' + (ch - '۰'))
                in '٠'..'٩' -> builder.append('0' + (ch - '٠'))
                'ي' -> builder.append('ی')
                'ك' -> builder.append('ک')
                '\u200C' -> builder.append(' ')
                else -> builder.append(ch)
            }
        }
        return builder.toString()
            .replace(Regex("[\\u064B-\\u065F]"), "")
            .replace(Regex("[،,!?؟]"), " ")
            .replace(Regex("(?<!\\d)\\.|\\.(?!\\d)"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .lowercase()
    }

    fun toPersianDigits(text: String): String {
        val builder = StringBuilder()
        for (ch in text) {
            if (ch in '0'..'9') {
                builder.append('۰' + (ch - '0'))
            } else {
                builder.append(ch)
            }
        }
        return builder.toString()
    }

    // Converts a Gregorian date to the Persian (Jalali) calendar: [year, month, day].
    fun gregorianToJalali(gy: Int, gm: Int, gd: Int): IntArray {
        val cumulative = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
        val gy2 = if (gm > 2) gy + 1 else gy
        var days = 355666 + 365 * gy + (gy2 + 3) / 4 - (gy2 + 99) / 100 +
            (gy2 + 399) / 400 + gd + cumulative[gm - 1]
        var jy = -1595 + 33 * (days / 12053)
        days %= 12053
        jy += 4 * (days / 1461)
        days %= 1461
        if (days > 365) {
            jy += (days - 1) / 365
            days = (days - 1) % 365
        }
        val jm: Int
        val jd: Int
        if (days < 186) {
            jm = 1 + days / 31
            jd = 1 + days % 31
        } else {
            jm = 7 + (days - 186) / 30
            jd = 1 + (days - 186) % 30
        }
        return intArrayOf(jy, jm, jd)
    }

    fun dateReply(now: Calendar): String {
        val gy = now.get(Calendar.YEAR)
        val gm = now.get(Calendar.MONTH) + 1
        val gd = now.get(Calendar.DAY_OF_MONTH)
        val j = gregorianToJalali(gy, gm, gd)
        val weekday = weekdays[now.get(Calendar.DAY_OF_WEEK) - 1]
        val shamsi = j[2].toString() + " " + persianMonths[j[1] - 1] + " " + j[0].toString()
        val miladi = gd.toString() + " " + gregorianMonths[gm - 1] + " " + gy.toString()
        return toPersianDigits(
            "امروز " + weekday + " است.\nتاریخ شمسی: " + shamsi + "\nتاریخ میلادی: " + miladi
        )
    }

    fun timeReply(now: Calendar): String {
        val clock = String.format(
            Locale.US, "%02d:%02d",
            now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE)
        )
        return toPersianDigits("ساعت " + clock + " است.")
    }
}
