package com.saffri.caloriecoach

import android.graphics.Bitmap
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class VisionItem(val name: String, val portion: String, val kcal: Int)
data class VisionResult(val items: List<VisionItem>, val totalKcal: Int)

/**
 * Sends the meal photo (plus the user's note) to the Claude API and asks it to identify the
 * foods and estimate calories. Must be called off the main thread.
 */
object FoodVision {
    private const val ENDPOINT = "https://api.anthropic.com/v1/messages"
    private const val MODEL = "claude-haiku-5-5"

    fun analyze(apiKey: String, photo: Bitmap, note: String): VisionResult {
        val bytes = ByteArrayOutputStream().also { photo.compress(Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray()
        val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)

        val prompt = """
            You are a nutrition assistant. Identify every food and drink visible in this photo and estimate
            the calories of the portion shown. The person added this note about what they ate (it may give
            quantities or items not visible; trust it for quantities): "${note.replace("\"", "'")}"
            Reply with ONLY a JSON object, no other text, in this exact shape:
            {"items":[{"name":"...","portion":"...","kcal":0}],"total_kcal":0}
            If the photo contains no food, reply {"items":[],"total_kcal":0}.
        """.trimIndent()

        val body = JSONObject()
            .put("model", MODEL)
            .put("max_tokens", 1024)
            .put(
                "messages", JSONArray().put(
                    JSONObject().put("role", "user").put(
                        "content", JSONArray()
                            .put(
                                JSONObject().put("type", "image").put(
                                    "source", JSONObject().put("type", "base64")
                                        .put("media_type", "image/jpeg").put("data", b64)
                                )
                            )
                            .put(JSONObject().put("type", "text").put("text", prompt))
                    )
                )
            )

        val conn = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 60_000
            doOutput = true
            setRequestProperty("content-type", "application/json")
            setRequestProperty("x-api-key", apiKey.trim())
            setRequestProperty("anthropic-version", "2023-06-01")
        }
        try {
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) {
                val msg = runCatching { JSONObject(text).getJSONObject("error").getString("message") }.getOrNull()
                throw Exception(
                    when (code) {
                        401 -> "Invalid API key — check it in the Profile tab."
                        429 -> "Too many requests — wait a moment and try again."
                        else -> "AI service error ($code): ${msg ?: "unknown"}"
                    }
                )
            }
            val content = JSONObject(text).getJSONArray("content")
            val reply = (0 until content.length())
                .map { content.getJSONObject(it) }
                .filter { it.optString("type") == "text" }
                .joinToString("") { it.getString("text") }
            return parse(reply)
        } finally {
            conn.disconnect()
        }
    }

    private fun parse(reply: String): VisionResult {
        val start = reply.indexOf('{')
        val end = reply.lastIndexOf('}')
        if (start < 0 || end <= start) throw Exception("Couldn't read the AI's answer. Try again.")
        val o = JSONObject(reply.substring(start, end + 1))
        val arr = o.optJSONArray("items") ?: JSONArray()
        val items = (0 until arr.length()).map {
            val x = arr.getJSONObject(it)
            VisionItem(x.optString("name"), x.optString("portion"), x.optDouble("kcal", 0.0).toInt())
        }
        val total = o.optDouble("total_kcal", -1.0).toInt().takeIf { it >= 0 } ?: items.sumOf { it.kcal }
        return VisionResult(items, total)
    }
}
