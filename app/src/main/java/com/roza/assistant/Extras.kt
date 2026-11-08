package com.roza.assistant

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.BatteryManager
import android.os.Build
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Calendar
import java.util.Locale

// Small helpers that work on the phone itself, without the internet: calculator,
// date arithmetic, flashlight and battery level.
object Extras {

    // Returns the answer, or null when the sentence is not for one of these helpers.
    fun handle(context: Context, raw: String): String? {
        val text = clean(raw)
        if (text.isEmpty()) return null
        flashlight(context, text)?.let { return it }
        battery(context, text)?.let { return it }
        dateMath(text)?.let { return it }
        calculate(text)?.let { return it }
        return null
    }

    // ---------- text cleaning ----------

    private fun asciiDigits(s: String): String {
        val out = StringBuilder()
        for (c in s) {
            when (c) {
                in '۰'..'۹' -> out.append('0' + (c - '۰'))
                in '٠'..'٩' -> out.append('0' + (c - '٠'))
                else -> out.append(c)
            }
        }
        return out.toString()
    }

    private fun clean(raw: String): String {
        var t = raw
            .replace('ي', 'ی')
            .replace('ك', 'ک')
            .replace(Regex("[\\u064B-\\u065F]"), "")
        t = asciiDigits(t)
        t = t.replace('\u200c', ' ').replace('٫', '.').replace("٬", "")
        // 2,500,000 is one number.
        t = t.replace(Regex("(?<=\\d),(?=\\d{3}(\\D|$))"), "")
        t = t.replace(Regex("[؟?!،؛]"), " ")
        t = t.lowercase(Locale.ROOT)
        return t.replace(Regex("\\s+"), " ").trim()
    }

    // ---------- flashlight ----------

    private var torchOn = false
    private var torchWatching = false

    private fun flashlight(context: Context, text: String): String? {
        val words = text.split(" ").filter { it.isNotEmpty() }
        if (words.size > 8) return null
        val named = text.contains("چراغ قوه") || text.contains("چراغقوه") ||
            text.contains("flashlight") || text.contains("torch") ||
            (text.contains("فلش") && words.size <= 4 && !text.contains("مموری"))
        if (!named) return null

        val off = Regex("خاموش|قطع|ببند|(^|\\s)off(\\s|$)").containsMatchIn(text)
        val on = Regex("روشن|بزن|فعال|بیار|(^|\\s)on(\\s|$)").containsMatchIn(text)

        if (Build.VERSION.SDK_INT < 23) {
            return "در این نسخهٔ اندروید برنامه نمی‌تواند چراغ‌قوه را کنترل کند."
        }
        return try {
            val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            watchTorch(manager)
            val id = torchCamera(manager) ?: return "چراغ‌قوه‌ای روی گوشی پیدا نکردم."
            val target = when {
                off && !on -> false
                on && !off -> true
                else -> !torchOn
            }
            manager.setTorchMode(id, target)
            torchOn = target
            if (target) "چراغ‌قوه را روشن کردم." else "چراغ‌قوه را خاموش کردم."
        } catch (e: Exception) {
            "نتوانستم چراغ‌قوه را کنترل کنم."
        }
    }

    // Keeps track of the torch even when it is switched from somewhere else.
    private fun watchTorch(manager: CameraManager) {
        if (torchWatching) return
        torchWatching = true
        try {
            manager.registerTorchCallback(object : CameraManager.TorchCallback() {
                override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                    torchOn = enabled
                }
            }, null)
        } catch (e: Exception) {
            torchWatching = false
        }
    }

    private fun torchCamera(manager: CameraManager): String? {
        for (id in manager.cameraIdList) {
            val c = manager.getCameraCharacteristics(id)
            val hasFlash = c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            val back = c.get(CameraCharacteristics.LENS_FACING) ==
                CameraCharacteristics.LENS_FACING_BACK
            if (hasFlash && back) return id
        }
        return null
    }

    // ---------- battery ----------

    private fun battery(context: Context, text: String): String? {
        if (text.split(" ").size > 8) return null
        val about = text.contains("باتری") || text.contains("شارژ") || text.contains("battery")
        val asks = Regex("چقدر|چقد|چنده|چند|درصد|وضعیت|چطور|level|percent|how").containsMatchIn(text)
        if (!about || !asks) return null

        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return "نتوانستم وضعیت باتری را بخوانم."
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return "نتوانستم وضعیت باتری را بخوانم."
        val percent = level * 100 / scale
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val state = when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING -> " و در حال شارژ است."
            BatteryManager.BATTERY_STATUS_FULL -> " و کامل شارژ شده است."
            else -> " و در حال شارژ نیست."
        }
        return PersianUtils.toPersianDigits("باتری " + percent + " درصد است" + state)
    }

    // ---------- date arithmetic ----------

    private val persianMonths = arrayOf(
        "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"
    )

    // Calendar.DAY_OF_WEEK: 1 is Sunday ... 7 is Saturday.
    private val weekDays = arrayOf(
        "یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه", "جمعه", "شنبه"
    )

    // Gregorian date to Persian (Solar Hijri) date, with plain arithmetic.
    private fun toPersian(gy: Int, gm: Int, gd: Int): Triple<Int, Int, Int> {
        val before = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
        val gy2 = if (gm > 2) gy + 1 else gy
        var days = 355666 + 365 * gy + (gy2 + 3) / 4 - (gy2 + 99) / 100 +
            (gy2 + 399) / 400 + gd + before[gm - 1]
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
        return Triple(jy, jm, jd)
    }

    private fun dateMath(text: String): String? {
        if (text.split(" ").size > 9) return null
        val asks = Regex("چندم|چه روز|چه تاریخ|تاریخ|میشه|می شه").containsMatchIn(text)
        if (!asks) return null
        if (Regex("آلارم|الارم|تایمر|یادآور|بیدار").containsMatchIn(text)) return null

        val cal = Calendar.getInstance()
        val label: String
        val relative = Regex("(\\d+)\\s*(روز|هفته|ماه|سال)\\s*(دیگه|دیگر|بعد|پیش|قبل)").find(text)
        if (relative != null) {
            val n = relative.groupValues[1].toIntOrNull() ?: return null
            if (n > 36500) return "عدد خیلی بزرگ است."
            val sign = if (relative.groupValues[3] == "پیش" || relative.groupValues[3] == "قبل") -1 else 1
            when (relative.groupValues[2]) {
                "روز" -> cal.add(Calendar.DAY_OF_YEAR, sign * n)
                "هفته" -> cal.add(Calendar.DAY_OF_YEAR, sign * n * 7)
                "ماه" -> cal.add(Calendar.MONTH, sign * n)
                else -> cal.add(Calendar.YEAR, sign * n)
            }
            label = relative.value
        } else {
            val offset: Int
            when {
                text.contains("پس فردا") || text.contains("پسفردا") -> {
                    offset = 2
                    label = "پس‌فردا"
                }
                text.contains("فردا") -> {
                    offset = 1
                    label = "فردا"
                }
                text.contains("پریروز") -> {
                    offset = -2
                    label = "پریروز"
                }
                text.contains("دیروز") -> {
                    offset = -1
                    label = "دیروز"
                }
                else -> return null
            }
            cal.add(Calendar.DAY_OF_YEAR, offset)
        }

        val gy = cal.get(Calendar.YEAR)
        val gm = cal.get(Calendar.MONTH) + 1
        val gd = cal.get(Calendar.DAY_OF_MONTH)
        val persian = toPersian(gy, gm, gd)
        val dayName = weekDays[cal.get(Calendar.DAY_OF_WEEK) - 1]
        val gregorian = String.format(Locale.US, "%04d-%02d-%02d", gy, gm, gd)
        return PersianUtils.toPersianDigits(
            label + " می‌شود " + dayName + " " + persian.third + " " +
                persianMonths[persian.second - 1] + " " + persian.first +
                " (میلادی: " + gregorian + ")"
        )
    }

    // ---------- calculator ----------

    private class MathError(message: String) : Exception(message)

    // Reads an expression such as 2+3*(4-1)^2.
    private class Parser(private val s: String) {
        private var pos = 0
        var operators = 0

        fun parse(): Double {
            val value = expression()
            skipSpaces()
            if (pos != s.length) throw MathError("syntax")
            return value
        }

        private fun skipSpaces() {
            while (pos < s.length && s[pos] == ' ') pos++
        }

        private fun peek(): Char? {
            skipSpaces()
            return if (pos < s.length) s[pos] else null
        }

        private fun expression(): Double {
            var value = term()
            while (true) {
                val c = peek()
                if (c == '+') {
                    pos++; operators++; value += term()
                } else if (c == '-') {
                    pos++; operators++; value -= term()
                } else {
                    return value
                }
            }
        }

        private fun term(): Double {
            var value = power()
            while (true) {
                val c = peek()
                if (c == '*') {
                    pos++; operators++; value *= power()
                } else if (c == '/') {
                    pos++; operators++
                    val divisor = power()
                    if (divisor == 0.0) throw MathError("zero")
                    value /= divisor
                } else {
                    return value
                }
            }
        }

        private fun power(): Double {
            val base = unary()
            if (peek() == '^') {
                pos++; operators++
                return Math.pow(base, power())
            }
            return base
        }

        private fun unary(): Double {
            if (peek() == '-') {
                pos++
                return -unary()
            }
            return primary()
        }

        private fun primary(): Double {
            val c = peek() ?: throw MathError("syntax")
            if (c == '(') {
                pos++
                val value = expression()
                if (peek() != ')') throw MathError("syntax")
                pos++
                return value
            }
            val start = pos
            while (pos < s.length && (s[pos].isDigit() || s[pos] == '.')) pos++
            if (start == pos) throw MathError("syntax")
            return s.substring(start, pos).toDoubleOrNull() ?: throw MathError("syntax")
        }
    }

    private fun formatNumber(value: Double): String {
        if (value.isNaN() || value.isInfinite()) return "نامعتبر"
        if (Math.abs(value) >= 1e15) return String.format(Locale.US, "%.6e", value)
        var plain = BigDecimal(value).setScale(6, RoundingMode.HALF_UP)
            .stripTrailingZeros().toPlainString()
        if (plain == "-0") plain = "0"
        val negative = plain.startsWith("-")
        if (negative) plain = plain.substring(1)
        val parts = plain.split(".")
        val whole = parts[0].replace(Regex("(\\d)(?=(\\d{3})+$)"), "$1٬")
        val result = if (parts.size > 1) whole + "٫" + parts[1] else whole
        return (if (negative) "-" else "") + result
    }

    private fun calculate(text: String): String? {
        var t = text
            .replace("به علاوه", "+").replace("بعلاوه", "+").replace("بهعلاوه", "+")
            .replace("منهای", "-")
            .replace("ضرب در", "*").replace("ضربدر", "*").replace("ضرب", "*")
            .replace("تقسیم بر", "/").replace("تقسیم", "/")
            .replace("به توان", "^")
            .replace("÷", "/").replace("−", "-").replace("–", "-")
        t = t.replace(Regex("(?<=\\d)\\s*[xX×]\\s*(?=\\d)"), "*")

        // Filler words that only belong to the request.
        val fillers = Regex(
            "(^|\\s)(حساب کن|محاسبه کن|ماشین حساب|چنده|چند میشه|چند می شه|چقدر میشه|" +
                "چقدر می شه|چقدر|چند|میشه|می شه|مساوی|برابر|حاصل|است|هست|بگو|بده|لطفا|" +
                "لطفاً|یعنی|رو|را|بزن|=)(?=\\s|$)"
        )
        var before: String
        do {
            before = t
            t = t.replace(fillers, " ").replace(Regex("\\s+"), " ").trim()
        } while (t != before)
        t = t.trimEnd('.', '=', ' ')
        if (t.isEmpty()) return null

        // "18 percent 2500000" means 18 percent of 2500000.
        val percent = Regex("^([0-9.]+) ?درصد ?(?:از )?([0-9.]+)$").find(t)
        if (percent != null) {
            val a = percent.groupValues[1].toDoubleOrNull()
            val b = percent.groupValues[2].toDoubleOrNull()
            if (a != null && b != null) {
                return PersianUtils.toPersianDigits("نتیجه: " + formatNumber(a * b / 100.0))
            }
            return null
        }

        // Only numbers and operators may be left, otherwise it is not a calculation.
        if (!Regex("^[0-9+\\-*/^(). ]+$").matches(t)) return null
        // Dates and phone numbers such as 2026-11-08 or 021-123-456 are not calculations.
        if (Regex("^\\d+([-/]\\d+){2,}$").matches(t.replace(" ", ""))) return null

        return try {
            val parser = Parser(t)
            val value = parser.parse()
            if (parser.operators == 0) return null
            PersianUtils.toPersianDigits("نتیجه: " + formatNumber(value))
        } catch (e: MathError) {
            if (e.message == "zero") "تقسیم بر صفر ممکن نیست." else null
        } catch (e: Exception) {
            null
        }
    }
}
