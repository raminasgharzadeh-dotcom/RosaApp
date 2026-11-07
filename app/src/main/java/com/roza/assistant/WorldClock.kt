package com.roza.assistant

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// Answers "what time is it in <city>". Common cities are worked out on the phone itself;
// other cities are looked up on Open-Meteo, which tells the time zone of the place.
object WorldClock {

    private val askRegex = Regex("(^|\\s)(چنده|چند|چندمه)(\\s|$)")
    private val englishRegex = Regex("what time|time in|current time")

    // Words that belong to the question and are not part of the place name.
    private val stopWords = setOf(
        "ساعت", "در", "توی", "تو", "الان", "الآن", "اکنون", "حالا", "چنده", "چند", "چندمه",
        "است", "هست", "الانه", "چیه", "چی", "بگو", "بده", "لطفا", "لطفاً", "برای", "کشور",
        "شهر", "و", "به", "رو", "را", "من",
        "what", "whats", "what's", "time", "is", "it", "in", "the", "now", "current", "at",
        "tell", "me", "of", "local", "right"
    )

    // Countries with several time zones: a city must be named instead.
    private val multiZone = setOf(
        "آمریکا", "امریکا", "روسیه", "کانادا", "استرالیا", "برزیل", "مکزیک", "چین و روسیه"
    )

    private val zoneIds: Map<String, String> = buildMap {
        // Iran
        for (name in listOf(
            "ایران", "تهران", "مشهد", "اصفهان", "شیراز", "تبریز", "کرج", "اهواز", "قم",
            "کرمانشاه", "ارومیه", "رشت", "زاهدان", "کرمان", "همدان", "یزد", "اردبیل",
            "بندرعباس", "اراک", "زنجان", "سنندج", "قزوین", "گرگان", "ساری", "خرم آباد",
            "خرم‌آباد", "بوشهر", "بجنورد", "بیرجند", "ایلام", "یاسوج", "شهرکرد", "شهر کرد",
            "سمنان", "کیش"
        )) put(name, "Asia/Tehran")

        // Europe
        put("لندن", "Europe/London"); put("انگلیس", "Europe/London"); put("بریتانیا", "Europe/London")
        put("پاریس", "Europe/Paris"); put("فرانسه", "Europe/Paris")
        put("برلین", "Europe/Berlin"); put("آلمان", "Europe/Berlin")
        put("رم", "Europe/Rome"); put("ایتالیا", "Europe/Rome")
        put("مادرید", "Europe/Madrid"); put("اسپانیا", "Europe/Madrid")
        put("آمستردام", "Europe/Amsterdam"); put("هلند", "Europe/Amsterdam")
        put("بروکسل", "Europe/Brussels")
        put("وین", "Europe/Vienna"); put("اتریش", "Europe/Vienna")
        put("زوریخ", "Europe/Zurich"); put("سوئیس", "Europe/Zurich")
        put("استکهلم", "Europe/Stockholm"); put("سوئد", "Europe/Stockholm")
        put("آتن", "Europe/Athens"); put("یونان", "Europe/Athens")
        put("ورشو", "Europe/Warsaw"); put("لهستان", "Europe/Warsaw")
        put("مسکو", "Europe/Moscow")
        put("کیف", "Europe/Kiev")
        put("استانبول", "Europe/Istanbul"); put("آنکارا", "Europe/Istanbul")
        put("ترکیه", "Europe/Istanbul")

        // Middle East and nearby
        put("دبی", "Asia/Dubai"); put("ابوظبی", "Asia/Dubai"); put("امارات", "Asia/Dubai")
        put("دوحه", "Asia/Qatar"); put("قطر", "Asia/Qatar")
        put("ریاض", "Asia/Riyadh"); put("عربستان", "Asia/Riyadh")
        put("کویت", "Asia/Kuwait")
        put("بغداد", "Asia/Baghdad"); put("عراق", "Asia/Baghdad")
        put("مسقط", "Asia/Muscat")
        put("دمشق", "Asia/Damascus")
        put("بیروت", "Asia/Beirut")
        put("قاهره", "Africa/Cairo"); put("مصر", "Africa/Cairo")
        put("ایروان", "Asia/Yerevan"); put("ارمنستان", "Asia/Yerevan")
        put("باکو", "Asia/Baku"); put("آذربایجان", "Asia/Baku")
        put("تفلیس", "Asia/Tbilisi")
        put("کابل", "Asia/Kabul"); put("افغانستان", "Asia/Kabul")
        put("اسلام آباد", "Asia/Karachi"); put("اسلام‌آباد", "Asia/Karachi")
        put("کراچی", "Asia/Karachi"); put("پاکستان", "Asia/Karachi")
        put("تاشکند", "Asia/Tashkent"); put("ازبکستان", "Asia/Tashkent")

        // Asia and Australia
        put("دهلی", "Asia/Kolkata"); put("دهلی نو", "Asia/Kolkata")
        put("مومبای", "Asia/Kolkata"); put("بمبئی", "Asia/Kolkata"); put("هند", "Asia/Kolkata")
        put("پکن", "Asia/Shanghai"); put("شانگهای", "Asia/Shanghai"); put("چین", "Asia/Shanghai")
        put("توکیو", "Asia/Tokyo"); put("ژاپن", "Asia/Tokyo")
        put("سئول", "Asia/Seoul"); put("کره جنوبی", "Asia/Seoul")
        put("سنگاپور", "Asia/Singapore")
        put("بانکوک", "Asia/Bangkok"); put("تایلند", "Asia/Bangkok")
        put("جاکارتا", "Asia/Jakarta"); put("اندونزی", "Asia/Jakarta")
        put("سیدنی", "Australia/Sydney"); put("ملبورن", "Australia/Melbourne")

        // Americas
        put("نیویورک", "America/New_York"); put("واشنگتن", "America/New_York")
        put("شیکاگو", "America/Chicago")
        put("لس آنجلس", "America/Los_Angeles"); put("لس‌آنجلس", "America/Los_Angeles")
        put("تورنتو", "America/Toronto"); put("ونکوور", "America/Vancouver")
        put("مکزیکوسیتی", "America/Mexico_City")
    }

    // Returns null when the sentence is not a time question about a place, otherwise the
    // place as it was said. A plain "ساعت چنده" has no place, so it stays a local-time question.
    fun cityFrom(raw: String): String? {
        val text = PersianUtils.normalize(raw).replace(Regex("[؟?!.,،:;]"), " ")
        val words = text.split(" ").filter { it.isNotEmpty() }
        if (words.isEmpty() || words.size > 8) return null
        val persianAsk = text.contains("ساعت") && askRegex.containsMatchIn(text)
        val englishAsk = englishRegex.containsMatchIn(text)
        if (!persianAsk && !englishAsk) return null
        // "چند ساعت ..." asks for a length of time, not for the clock.
        if (text.contains("چند ساعت")) return null
        val city = words.filter { it.lowercase() !in stopWords }.joinToString(" ").trim()
        return if (city.isEmpty()) null else city
    }

    // May use the network for places that are not in the built-in list, so call this
    // only from a background thread.
    fun reply(city: String): String {
        if (city in multiZone) {
            return "«" + city + "» چند ساعت محلی دارد. اسم یک شهر را بگو، مثلاً: ساعت نیویورک چنده؟"
        }
        var zoneId: String? = zoneIds[city]
        if (zoneId == null) {
            try {
                zoneId = lookupZone(city)
            } catch (e: Exception) {
                return "نتوانستم ساعت «" + city + "» را پیدا کنم. اینترنت یا VPN را بررسی کن."
            }
        }
        val id: String = zoneId ?: return "جایی با نام «" + city + "» پیدا نکردم."
        if (TimeZone.getAvailableIDs().none { it == id }) {
            return "منطقهٔ زمانی «" + city + "» را نشناختم."
        }
        val zone = TimeZone.getTimeZone(id)
        val now = Date()

        val clockFormat = SimpleDateFormat("HH:mm", Locale.US)
        clockFormat.timeZone = zone
        val clock = clockFormat.format(now)

        // Is it already tomorrow, or still yesterday, over there?
        val dayFormat = SimpleDateFormat("yyyyMMdd", Locale.US)
        dayFormat.timeZone = zone
        val there = dayFormat.format(now).toInt()
        dayFormat.timeZone = TimeZone.getDefault()
        val here = dayFormat.format(now).toInt()
        val dayNote = when {
            there > here -> " (فردا)"
            there < here -> " (دیروز)"
            else -> ""
        }
        return PersianUtils.toPersianDigits("الان ساعت " + clock + " در " + city + " است" + dayNote + ".")
    }

    private fun lookupZone(city: String): String? {
        val url = "https://geocoding-api.open-meteo.com/v1/search?name=" +
            URLEncoder.encode(city, "UTF-8") + "&count=1&language=fa&format=json"
        val results = JSONObject(get(url)).optJSONArray("results") ?: return null
        if (results.length() == 0) return null
        val zone = results.getJSONObject(0).optString("timezone", "")
        return if (zone.isEmpty()) null else zone
    }

    private fun get(address: String): String {
        val connection = URL(address).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10000
            connection.readTimeout = 15000
            connection.setRequestProperty("User-Agent", "RosaApp/1.0 (Android)")
            connection.setRequestProperty("Accept", "application/json")
            val status = connection.responseCode
            if (status !in 200..299) throw Exception("HTTP " + status)
            return connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
