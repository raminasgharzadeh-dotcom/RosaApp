package com.roza.assistant

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

// A reply from OpenRouter that is not a success. detail is a short reason.
class ApiException(val status: Int, val detail: String = "") : Exception("HTTP $status $detail")

class OpenRouterClient(private val apiKey: String) {

    companion object {
        // At most this many models are tried for one question, so a bad moment does not
        // use up the free daily allowance.
        private const val MAX_TRIES = 4

        // Remembered while the app is running.
        private var lastGood: String? = null
        private val pausedUntil = HashMap<String, Long>()

        private fun pause(model: String, minutes: Int) {
            pausedUntil[model] = System.currentTimeMillis() + minutes * 60L * 1000L
        }
    }

    // Some free models refuse a separate "system" message, so these instructions
    // are put in front of the question instead.
    private val instructions =
        "تو «رزا» هستی، یک دستیار صوتی فارسی‌زبان، صمیمی و مفید. " +
            "کوتاه جواب بده (حداکثر سه جمله)، محاوره‌ای و بدون مارک‌داون و فهرست."

    // Returns up to 8 free model ids. Models that "think" first are put last, because
    // they often use up all their words thinking and return an empty answer.
    fun listFreeModels(): List<String> {
        val text = request("https://openrouter.ai/api/v1/models", null, false)
        val data = JSONObject(text).getJSONArray("data")
        val preferred = Regex("llama|gemma|mistral|qwen", RegexOption.IGNORE_CASE)
        val avoid = Regex("r1|think|reason|nemotron|gpt-oss|deepseek", RegexOption.IGNORE_CASE)
        val free = ArrayList<JSONObject>()
        for (i in 0 until data.length()) {
            val model = data.getJSONObject(i)
            if (model.optString("id").endsWith(":free")) {
                free.add(model)
            }
        }
        free.sortWith(
            compareByDescending<JSONObject> { preferred.containsMatchIn(it.optString("id")) }
                .thenBy { avoid.containsMatchIn(it.optString("id")) }
                .thenByDescending { it.optInt("context_length", 0) }
        )
        return free.take(8).map { it.getString("id") }
    }

    // history holds pairs of (role, text); role is "user" or "assistant".
    fun chat(models: List<String>, history: List<Pair<String, String>>, question: String): String {
        val messages = JSONArray()
        for (item in history.takeLast(8)) {
            messages.put(JSONObject().put("role", item.first).put("content", item.second))
        }
        messages.put(
            JSONObject().put("role", "user").put("content", instructions + "\n\n" + question)
        )

        var lastStatus = 0
        var lastDetail = ""
        var network: IOException? = null
        var sawOtherError = false

        // Try the model that worked last first, and skip models that failed a moment ago.
        val now = System.currentTimeMillis()
        val good = lastGood
        val usable = models.filter { (pausedUntil[it] ?: 0L) <= now }
        val ordered = ArrayList<String>()
        if (good != null && usable.contains(good)) ordered.add(good)
        for (model in usable) {
            if (model != good) ordered.add(model)
        }
        val candidates = if (ordered.isEmpty()) models.take(MAX_TRIES) else ordered.take(MAX_TRIES)

        for (model in candidates) {
            try {
                val body = JSONObject()
                    .put("model", model)
                    .put("messages", messages)
                    .put("max_tokens", 700)
                    .toString()
                val text = request("https://openrouter.ai/api/v1/chat/completions", body, true)
                val json = JSONObject(text)
                if (json.has("error")) {
                    // OpenRouter sometimes reports a model error inside a normal reply.
                    val error = json.optJSONObject("error")
                    lastStatus = error?.optInt("code", -1) ?: -1
                    lastDetail = model + ": " + (error?.optString("message", "") ?: "")
                    sawOtherError = true
                    pause(model, 5)
                    continue
                }
                val message = json.getJSONArray("choices").getJSONObject(0).getJSONObject("message")
                // An answer that is JSON null must not turn into the word "null".
                val reply = if (message.isNull("content")) "" else message.optString("content", "")
                val cleaned = reply.replace(Regex("<think>[\\s\\S]*?</think>"), "").trim()
                if (cleaned.isNotEmpty()) {
                    lastGood = model
                    return cleaned
                }
                lastStatus = -2
                lastDetail = model + ": empty answer"
                sawOtherError = true
                pause(model, 10)
            } catch (e: ApiException) {
                if (e.status == 401) throw e
                lastStatus = e.status
                lastDetail = model + ": " + e.detail
                sawOtherError = true
                // A refused or missing model is skipped for a while, a busy one for a short time.
                pause(model, if (e.status == 429) 3 else 30)
            } catch (e: IOException) {
                network = e
            } catch (e: Exception) {
                lastStatus = -1
                lastDetail = model + ": " + e.javaClass.simpleName
                sawOtherError = true
                pause(model, 5)
            }
        }

        val connectionProblem = network
        if (!sawOtherError && connectionProblem != null) {
            throw connectionProblem
        }
        throw ApiException(
            if (lastStatus == 0) -1 else lastStatus,
            lastDetail + " [" + candidates.size + " models tried]"
        )
    }

    private fun request(url: String, body: String?, withKey: Boolean): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15000
            connection.readTimeout = 60000
            if (withKey) {
                connection.setRequestProperty("Authorization", "Bearer $apiKey")
            }
            if (body != null) {
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            if (status !in 200..299) {
                throw ApiException(status, shortError(readText(connection.errorStream)))
            }
            return readText(connection.inputStream)
        } finally {
            connection.disconnect()
        }
    }

    private fun readText(stream: InputStream?): String {
        if (stream == null) return ""
        return try {
            stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (e: Exception) {
            ""
        }
    }

    // A short, one-line version of the error that OpenRouter sent back.
    private fun shortError(text: String): String {
        if (text.isBlank()) return ""
        val message = try {
            JSONObject(text).optJSONObject("error")?.optString("message", "") ?: ""
        } catch (e: Exception) {
            ""
        }
        val shown = if (message.isNotBlank()) message else text
        return shown.replace("\n", " ").take(120)
    }
}
