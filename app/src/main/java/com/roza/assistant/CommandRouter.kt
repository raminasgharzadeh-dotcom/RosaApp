package com.roza.assistant

import android.view.KeyEvent

sealed class Command {
    data class OpenApp(val app: AppEntry) : Command()
    data class SetAlarm(val hour: Int, val minute: Int) : Command()
    data class SetTimer(val seconds: Int) : Command()
    data class Media(val keyCode: Int, val app: AppEntry?) : Command()
    data class Navigate(val place: String, val app: AppEntry?) : Command()
    data class Volume(val up: Boolean, val steps: Int) : Command()
    data class Reply(val text: String) : Command()
    object ShowDate : Command()
    object ShowTime : Command()
}

// Turns a sentence into a command. Returns null when the sentence is not a command;
// then it is sent to the chat model instead.
object CommandRouter {

    private val hourWords = mapOf(
        "دوازده" to 12, "یازده" to 11, "ده" to 10, "نه" to 9, "هشت" to 8, "هفت" to 7,
        "شش" to 6, "پنج" to 5, "چهار" to 4, "سه" to 3, "دو" to 2, "یک" to 1
    )

    private val numberWords = hourWords + mapOf(
        "پانزده" to 15, "بیست" to 20, "سی" to 30, "چهل" to 40, "پنجاه" to 50, "شصت" to 60
    )

    fun parse(raw: String): Command? {
        val text = PersianUtils.normalize(raw)
        if (text.isEmpty()) return null

        parseTimer(text)?.let { return it }
        parseAlarm(text)?.let { return it }
        parseNavigate(text)?.let { return it }
        parseVolume(text)?.let { return it }
        parseMedia(text)?.let { return it }
        if (isDateQuestion(text)) return Command.ShowDate
        if (isTimeQuestion(text)) return Command.ShowTime
        parseOpenApp(text)?.let { return it }
        return null
    }

    // ---------- timer and alarm ----------

    private fun unitSeconds(unit: String): Int {
        return when (unit) {
            "ثانیه", "second", "sec" -> 1
            "دقیقه", "minute", "min" -> 60
            else -> 3600
        }
    }

    private fun parseTimer(text: String): Command? {
        val triggers = listOf("تایمر", "زمان سنج", "زمانسنج", "شمارنده", "timer")
        if (triggers.none { text.contains(it) }) return null

        var seconds = 0
        val digitRegex = Regex("(\\d+)\\s*(ثانیه|دقیقه|ساعت|second|sec|minute|min|hour)")
        for (match in digitRegex.findAll(text)) {
            val number = (match.groupValues[1].toIntOrNull() ?: 0).coerceAtMost(100000)
            seconds += number * unitSeconds(match.groupValues[2])
        }
        if (seconds == 0) {
            val words = numberWords.keys.sortedByDescending { it.length }.joinToString("|")
            val wordRegex = Regex("($words)\\s*(ثانیه|دقیقه|ساعت)")
            for (match in wordRegex.findAll(text)) {
                val number = numberWords[match.groupValues[1]] ?: 0
                seconds += number * unitSeconds(match.groupValues[2])
            }
        }
        if (seconds == 0 && text.contains("نیم ساعت")) seconds = 1800

        if (seconds <= 0 || seconds > 86400) {
            return Command.Reply("مدت تایمر را نفهمیدم. مثلاً بگو: تایمر ۵ دقیقه.")
        }
        return Command.SetTimer(seconds)
    }

    private fun parseAlarm(text: String): Command? {
        val triggers = listOf("آلارم", "الارم", "بیدارم کن", "بیدار کن", "alarm", "wake me")
        if (triggers.none { text.contains(it) }) return null
        val clock = parseClock(text)
            ?: return Command.Reply("ساعت را نفهمیدم. مثلاً بگو: آلارم ساعت ۷ صبح.")
        return Command.SetAlarm(applyPeriod(clock.first, text), clock.second)
    }

    private fun validTime(hour: Int, minute: Int): Pair<Int, Int>? {
        return if (hour in 0..23 && minute in 0..59) Pair(hour, minute) else null
    }

    private fun parseClock(text: String): Pair<Int, Int>? {
        Regex("(\\d{1,2})[:.](\\d{2})").find(text)?.let {
            return validTime(it.groupValues[1].toInt(), it.groupValues[2].toInt())
        }
        Regex("(\\d{1,2})\\s*و\\s*(\\d{1,2})\\s*دقیقه").find(text)?.let {
            return validTime(it.groupValues[1].toInt(), it.groupValues[2].toInt())
        }
        Regex("(\\d{1,2})\\s*و\\s*نیم").find(text)?.let {
            return validTime(it.groupValues[1].toInt(), 30)
        }
        Regex("(\\d{1,2})\\s*و\\s*ربع").find(text)?.let {
            return validTime(it.groupValues[1].toInt(), 15)
        }
        Regex("(?:^|\\s)(\\d{1,2})(?=\\s|$)").find(text)?.let {
            return validTime(it.groupValues[1].toInt(), 0)
        }
        val words = hourWords.keys.sortedByDescending { it.length }.joinToString("|")
        Regex("ساعت\\s+($words)(?:\\s+و\\s+(نیم|ربع))?(?=\\s|$)").find(text)?.let {
            val hour = hourWords[it.groupValues[1]] ?: return null
            val minute = when (it.groupValues[2]) {
                "نیم" -> 30
                "ربع" -> 15
                else -> 0
            }
            return validTime(hour, minute)
        }
        return null
    }

    // "صبح / ظهر / عصر / شب" decide between morning and evening hours.
    private fun applyPeriod(hour: Int, text: String): Int {
        fun has(word: String): Boolean = text.contains(word)
        return when {
            has("نیمه شب") -> if (hour == 12) 0 else hour
            has("صبح") || has(" am") || has("a.m") -> if (hour == 12) 0 else hour
            has("ظهر") && !has("بعد از ظهر") && !has("بعدازظهر") ->
                if (hour in 1..5) hour + 12 else hour
            has("بعد از ظهر") || has("بعدازظهر") || has("عصر") || has(" pm") || has("p.m") ->
                if (hour in 1..11) hour + 12 else hour
            has("شب") -> when {
                hour == 12 -> 0
                hour in 6..11 -> hour + 12
                else -> hour
            }
            else -> hour
        }
    }

    // ---------- date and time questions ----------

    private fun isDateQuestion(text: String): Boolean {
        val words = text.split(" ")
        if (words.size > 6) return false
        if (Regex("(^|\\s)(چندم|چندمه|date)(\\s|$)").containsMatchIn(text)) return true
        if (text.contains("تاریخ")) {
            return text.contains("امروز") || text.contains("الان") ||
                text.contains("میلادی") || text.contains("شمسی") || words.size <= 2
        }
        return false
    }

    private fun isTimeQuestion(text: String): Boolean {
        if (text.split(" ").size > 6) return false
        return Regex("ساعت\\s+چند").containsMatchIn(text) ||
            text.contains("what time") || text.contains("time is it")
    }

    // ---------- volume ----------

    private fun parseVolume(text: String): Command? {
        if (text.split(" ").size > 8) return null
        if (!Regex("(^|\\s)(صدا|صدای|volume)(\\s|$)").containsMatchIn(text)) return null

        val cleaned = text.replace("کمی", " ")
        val hasVerb = Regex("کن|کنید|ببر|بیار|بده|up|down|increase|decrease|raise|lower|turn|make")
            .containsMatchIn(cleaned)
        if (!hasVerb) return null

        val up = Regex("زیاد|بالا|افزایش|بلند|بیشتر|up|increase|louder|raise").containsMatchIn(cleaned)
        val down = Regex("کم|پایین|کاهش|آهسته|down|decrease|lower|quieter").containsMatchIn(cleaned)
        if (up == down) return null

        var steps = 1
        val digits = Regex("(\\d+)\\s*(بار|پله|درجه|times|time|steps|step)").find(cleaned)
        if (digits != null) {
            steps = digits.groupValues[1].toIntOrNull() ?: 1
        } else {
            val words = numberWords.keys.sortedByDescending { it.length }.joinToString("|")
            val spoken = Regex("($words)\\s*(بار|پله|درجه)").find(cleaned)
            if (spoken != null) steps = numberWords[spoken.groupValues[1]] ?: 1
        }
        return Command.Volume(up, steps.coerceIn(1, 15))
    }

    // ---------- music ----------

    // The time of the last music command. Right after one, a short "قطع کن" is understood.
    private var lastMusicAt = 0L

    private fun parseMedia(text: String): Command? {
        val words = text.split(" ")
        val stopRegex = Regex(
            "توقف|مکث|وایسا|بس کن|بسه|متوقف|قطع|خاموش|ساکت|ببند|استپ|استاپ|pause|stop"
        )
        val aboutMusic = Regex("آهنگ|اهنگ|ترانه|موزیک|موسیقی|music|song|track|اسپاتیفای|spotify")
            .containsMatchIn(text) || (text.contains("پخش") && stopRegex.containsMatchIn(text))
        val recentMusic = System.currentTimeMillis() - lastMusicAt < 10 * 60 * 1000L
        val shortStop = recentMusic && words.size <= 3 && stopRegex.containsMatchIn(text)
        if (!aboutMusic && words.size != 1 && !shortStop) return null

        val key = when {
            Regex("بعدی|next|skip|عوض").containsMatchIn(text) -> KeyEvent.KEYCODE_MEDIA_NEXT
            Regex("قبلی|previous|prev|back|برگرد").containsMatchIn(text) ->
                KeyEvent.KEYCODE_MEDIA_PREVIOUS
            stopRegex.containsMatchIn(text) -> KeyEvent.KEYCODE_MEDIA_PAUSE
            Regex("پخش|پلی|ادامه|بذار|بزن|شروع|play|resume").containsMatchIn(text) ->
                KeyEvent.KEYCODE_MEDIA_PLAY
            else -> return null
        }
        lastMusicAt = System.currentTimeMillis()
        val app = AllowedApps.mentioned(text, listOf("spotify", "music"))
        return Command.Media(key, app)
    }

    // ---------- navigation ----------

    private fun parseNavigate(text: String): Command? {
        val app = AllowedApps.mentioned(text, listOf("neshan", "balad", "waze"))
        val strong = listOf(
            "مسیریابی", "برو به", "ببر به", "ببرم به", "navigate to", "directions to", "take me to"
        )
        val strongTrigger = strong.firstOrNull { text.contains(it) }
        val trigger: String = strongTrigger
            ?: (if (app != null && text.contains("مسیر")) "مسیر" else null)
            ?: return null

        var place = text.substring(text.indexOf(trigger) + trigger.length).trim()
        for (prefix in listOf("به ", "تا ", "to ")) {
            if (place.startsWith(prefix)) place = place.removePrefix(prefix).trim()
        }
        if (app != null) {
            for (alias in app.aliases) {
                val name = Regex.escape(PersianUtils.normalize(alias))
                place = place.replace(
                    Regex("(^| )((با|توی|تو|در|از طریق|with|in|on) )?" + name + "(?= |$)"),
                    " "
                )
            }
        }
        place = place.replace(Regex("\\s+"), " ").trim().replace(Regex(" (رو|را)$"), "").trim()
        if (place.length < 2) return null
        return Command.Navigate(place, app)
    }

    // ---------- opening apps ----------

    private fun parseOpenApp(text: String): Command? {
        val verbs = Regex("باز کن|باز کردن|بازش کن|اجرا کن|بیار|open|launch")
        if (!verbs.containsMatchIn(text)) return null
        val app = AllowedApps.mentioned(text, null) ?: return null
        return Command.OpenApp(app)
    }
}
