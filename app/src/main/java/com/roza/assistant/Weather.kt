package com.roza.assistant

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

// Answers "what is the weather in <city>" with live data from Open-Meteo (no key needed).
object Weather {

    private val triggerRegex = Regex(
        "(^|\\s)(هوا|هوای|هواشناسی|دمای|دما|weather|temperature|forecast)(\\s|$)"
    )
    private val openRegex = Regex("باز کن|باز کردن|بازش کن|اجرا کن|open|launch")

    // Words that belong to the question and are not part of the city name.
    private val stopWords = setOf(
        "هوا", "هوای", "هواشناسی", "دمای", "دما", "آب", "و", "چطوره", "چطور", "چطوری",
        "چگونه", "چقدره", "چقدر", "چنده", "چیه", "چی", "چه", "است", "هست", "الان", "اکنون",
        "امروز", "فعلا", "فعلاً", "کنونی", "وضعیت", "در", "توی", "تو", "برای", "رو", "را",
        "بگو", "بده", "به", "من", "لطفا", "لطفاً", "جوریه", "چجوریه", "طوره",
        "weather", "temperature", "forecast", "in", "of", "the", "what", "whats", "what's",
        "is", "how", "today", "now", "like", "tell", "me", "current", "right", "for", "at"
    )

    // Common cities by their spoken name, so the search does not depend on Persian spelling.
    private val knownCities = mapOf(
        "تهران" to "Tehran", "مشهد" to "Mashhad", "اصفهان" to "Isfahan", "شیراز" to "Shiraz",
        "تبریز" to "Tabriz", "کرج" to "Karaj", "اهواز" to "Ahvaz", "قم" to "Qom",
        "کرمانشاه" to "Kermanshah", "ارومیه" to "Urmia", "رشت" to "Rasht",
        "زاهدان" to "Zahedan", "کرمان" to "Kerman", "همدان" to "Hamadan", "یزد" to "Yazd",
        "اردبیل" to "Ardabil", "بندرعباس" to "Bandar Abbas", "اراک" to "Arak",
        "زنجان" to "Zanjan", "سنندج" to "Sanandaj", "قزوین" to "Qazvin", "گرگان" to "Gorgan",
        "ساری" to "Sari", "خرم آباد" to "Khorramabad", "خرم‌آباد" to "Khorramabad",
        "بوشهر" to "Bushehr", "بجنورد" to "Bojnord", "بیرجند" to "Birjand", "ایلام" to "Ilam",
        "یاسوج" to "Yasuj", "شهرکرد" to "Shahrekord", "شهر کرد" to "Shahrekord",
        "سمنان" to "Semnan", "کیش" to "Kish", "لندن" to "London", "پاریس" to "Paris",
        "استانبول" to "Istanbul", "دبی" to "Dubai", "برلین" to "Berlin", "رم" to "Rome",
        "مادرید" to "Madrid", "تورنتو" to "Toronto", "نیویورک" to "New York",
        "توکیو" to "Tokyo", "مسکو" to "Moscow", "آنکارا" to "Ankara"
    )

    // Returns null when the sentence is not a weather question, an empty text when it is
    // one but no city was said, otherwise the city as it was said.
    fun cityFrom(raw: String): String? {
        val text = PersianUtils.normalize(raw).replace(Regex("[؟?!.,،:;]"), " ")
        val words = text.split(" ").filter { it.isNotEmpty() }
        if (words.isEmpty() || words.size > 8) return null
        if (!triggerRegex.containsMatchIn(text)) return null
        if (openRegex.containsMatchIn(text)) return null
        return words.filter { it.lowercase() !in stopWords }.joinToString(" ").trim()
    }

    // Does the network work, so call this only from a background thread.
    fun reply(city: String): String {
        if (city.isEmpty()) return "برای کدام شهر؟ مثلاً بگو: هوای مشهد چطوره؟"
        return try {
            val place = findPlace(city) ?: return "شهری با نام «" + city + "» پیدا نکردم."
            val url = "https://api.open-meteo.com/v1/forecast?latitude=" + place.first +
                "&longitude=" + place.second +
                "&current=temperature_2m,apparent_temperature,relative_humidity_2m," +
                "weather_code,wind_speed_10m&timezone=auto"
            val current = JSONObject(get(url)).getJSONObject("current")
            val temp = Math.round(current.getDouble("temperature_2m")).toInt()
            val feels = Math.round(current.getDouble("apparent_temperature")).toInt()
            val humidity = Math.round(current.getDouble("relative_humidity_2m")).toInt()
            val wind = Math.round(current.getDouble("wind_speed_10m")).toInt()
            val description = describe(current.getInt("weather_code"))
            PersianUtils.toPersianDigits(
                "الان در " + city + " حدود " + degrees(temp) + " است و هوا " + description +
                    ". دمای حس‌شده " + degrees(feels) + "، رطوبت " + humidity +
                    " درصد و سرعت باد " + wind + " کیلومتر بر ساعت است."
            )
        } catch (e: Exception) {
            "نتوانستم اطلاعات هوا را بگیرم. اینترنت یا VPN را بررسی کن."
        }
    }

    private fun degrees(value: Int): String {
        return if (value < 0) "منفی " + (-value) + " درجه" else value.toString() + " درجه"
    }

    private fun findPlace(city: String): Pair<Double, Double>? {
        val query = knownCities[city] ?: city
        val url = "https://geocoding-api.open-meteo.com/v1/search?name=" +
            URLEncoder.encode(query, "UTF-8") + "&count=1&language=fa&format=json"
        val results = JSONObject(get(url)).optJSONArray("results") ?: return null
        if (results.length() == 0) return null
        val first = results.getJSONObject(0)
        return Pair(first.getDouble("latitude"), first.getDouble("longitude"))
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

    // The weather codes of the World Meteorological Organization, in simple Persian.
    private fun describe(code: Int): String {
        return when (code) {
            0 -> "صاف است"
            1 -> "بیشتر صاف است"
            2 -> "کمی ابری است"
            3 -> "ابری است"
            45, 48 -> "مه‌آلود است"
            51, 53, 55, 56, 57 -> "نم‌نم باران می‌بارد"
            61, 63, 65, 66, 67 -> "بارانی است"
            71, 73, 75, 77 -> "برفی است"
            80, 81, 82 -> "رگبار است"
            85, 86 -> "رگبار برف است"
            95 -> "رعدوبرق است"
            96, 99 -> "رعدوبرق همراه با تگرگ است"
            else -> "وضعیت مشخصی ندارد"
        }
    }
}
