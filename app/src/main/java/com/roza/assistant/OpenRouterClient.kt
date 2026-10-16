package com.roza.assistant

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class ApiException(val status: Int) : Exception("HTTP $status")

class OpenRouterClient(private val apiKey: String) {

    private val systemPrompt =
        "تو «رزا» هستی، یک دستیار صوتی فارسی‌زبان، صمیمی و مفید. " +
            "کوتاه جواب بده (حداکثر سه جمله)، محاوره‌ای و بدون مارک‌داون و فهرست."

    // Returns up to 6 free model ids, preferring well-known families.
    fun listFreeModels(): List<String> {
        val text = request("https://openrouter.ai/api/v1/models", null, false)
        val data = JSONObject(text).getJSONArray("data")
        val preferred = Regex(
            "llama|gemma|qwen|mistral|gpt-oss|deepseek|nemotron|glm",
            RegexOption.IGNORE_CASE
        )
        val free = ArrayList<JSONObject>()
        for (i in 0 until data.length()) {
            val model = data.getJSONObject(i)
            if (model.optString("id").endsWith(":free")) {
                free.add(model)
            }
        }
        free.sortWith(
            compareByDescending<JSONObject> { preferred.containsMatchIn(it.optString("id")) }
                .thenByDescending { it.optInt("context_length", 0) }
        )
        return free.take(6).map { it.getString("id") }
    }

    // history holds pairs of (role, text); role is "user" or "assistant".
    fun chat(models: List<String>, history: List<Pair<String, String>>, question: String): String {
        val messages = JSONArray()
        messages.put(JSONObject().put("role", "system").put("content", systemPrompt))
        for (item in history.takeLast(8)) {
            messages.put(JSONObject().put("role", item.first).put("content", item.second))
        }
        messages.put(JSONObject().put("role", "user").put("content", question))

        var lastStatus = 0
        for (model in models) {
            try {
                val body = JSONObject()
                    .put("model", model)
                    .put("messages", messages)
                    .put("max_tokens", 400)
                    .toString()
                val text = request("https://openrouter.ai/api/v1/chat/completions", body, true)
                val reply = JSONObject(text)
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .optString("content", "")
                val cleaned = reply.replace(Regex("<think>[\\s\\S]*?</think>"), "").trim()
                if (cleaned.isNotEmpty()) {
                    return cleaned
                }
            } catch (e: ApiException) {
                if (e.status == 401) throw e
                lastStatus = e.status
            } catch (e: Exception) {
                lastStatus = -1
            }
        }
        throw ApiException(if (lastStatus == 0) -1 else lastStatus)
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
                throw ApiException(status)
            }
            return connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
