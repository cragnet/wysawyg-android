package com.cragnet.wysawyg

import android.content.Context
import android.util.Base64
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class OllamaClient(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun transcribe(wavBytes: ByteArray): String = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val prefs = context.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
        val url = prefs.getString(MainActivity.PREF_ALARMA_URL, "http://alarma.local:11434/api/chat")!!
        val apiKey = prefs.getString(MainActivity.PREF_API_KEY, "") ?: ""
        val model = prefs.getString(MainActivity.PREF_MODEL, "gemma4:12b")!!
        val systemPrompt = prefs.getString(MainActivity.PREF_SYSTEM_PROMPT, "Transcribe the audio exactly. Output only the spoken words, no commentary.")!!

        WysawygLogger.i("OllamaClient transcribe: url=$url model=$model audio=${wavBytes.size} bytes apiKeyPresent=${apiKey.isNotBlank()}")

        val base = url.trimEnd('/')
        return@withContext when {
            base.contains("/v1") -> transcribeViaChatCompletions(base, model, systemPrompt, wavBytes, apiKey)
            else -> transcribeOllamaNative(base, model, systemPrompt, wavBytes, apiKey)
        }
    }

    private fun transcribeViaChatCompletions(baseUrl: String, model: String, systemPrompt: String, wavBytes: ByteArray, apiKey: String): String {
        val endpoint = baseUrl + "/chat/completions"
        WysawygLogger.i("Chat-completions POST $endpoint model=$model")

        val base64Audio = Base64.encodeToString(wavBytes, Base64.NO_WRAP)

        val messages = JSONArray().apply {
            put(JSONObject().apply {
                put("role", "system")
                put("content", systemPrompt)
            })
            put(JSONObject().apply {
                put("role", "user")
                put("content", "Transcribe this audio.")
                put("images", JSONArray().apply { put(base64Audio) })
            })
        }

        val json = JSONObject().apply {
            put("model", model)
            put("stream", false)
            put("messages", messages)
            put("options", JSONObject().apply {
                put("temperature", 0.1)
                put("num_predict", 256)
            })
        }

        val body = json.toString().toRequestBody("application/json".toMediaType())
        WysawygLogger.d("Chat-completions request body length: ${body.contentLength()} bytes")

        val requestBuilder = Request.Builder()
            .url(endpoint)
            .post(body)

        if (apiKey.isNotBlank()) {
            requestBuilder.header("Authorization", "Bearer $apiKey")
        }

        val request = requestBuilder.build()
        val response = client.newCall(request).execute()
        val responseBody = response.body?.string() ?: throw RuntimeException("Empty response")

        WysawygLogger.i("Chat-completions response code: ${response.code}")

        if (!response.isSuccessful) {
            WysawygLogger.e("Chat-completions error ${response.code}: $responseBody")
            throw RuntimeException("Chat-completions error ${response.code}: $responseBody")
        }

        val parsed = JSONObject(responseBody)
        WysawygLogger.d("Chat-completions response body: $responseBody")

        val choices = parsed.optJSONArray("choices")
        if (choices != null && choices.length() > 0) {
            return choices.getJSONObject(0).getJSONObject("message").optString("content", "").trim()
        }

        // Fallback for native-style response
        return parsed.optJSONObject("message")?.optString("content", "")?.trim()
            ?: parsed.optString("response", "").trim()
    }

    private fun transcribeOllamaNative(baseUrl: String, model: String, systemPrompt: String, wavBytes: ByteArray, apiKey: String): String {
        val endpoint = baseUrl
        WysawygLogger.i("Ollama native POST $endpoint model=$model")

        val base64Audio = Base64.encodeToString(wavBytes, Base64.NO_WRAP)

        val messages = JSONArray().apply {
            put(JSONObject().apply {
                put("role", "system")
                put("content", systemPrompt)
            })
            put(JSONObject().apply {
                put("role", "user")
                put("content", "Transcribe this audio.")
                put("images", JSONArray().apply { put(base64Audio) })
            })
        }

        val json = JSONObject().apply {
            put("model", model)
            put("think", false)
            put("stream", false)
            put("messages", messages)
            put("options", JSONObject().apply {
                put("temperature", 0.1)
                put("num_predict", 256)
            })
        }

        val body = json.toString().toRequestBody("application/json".toMediaType())
        WysawygLogger.d("Ollama native request body length: ${body.contentLength()} bytes")

        val requestBuilder = Request.Builder()
            .url(endpoint)
            .post(body)

        if (apiKey.isNotBlank()) {
            requestBuilder.header("Authorization", "Bearer $apiKey")
        }

        val request = requestBuilder.build()
        val response = client.newCall(request).execute()
        val responseBody = response.body?.string() ?: throw RuntimeException("Empty response")

        WysawygLogger.i("Ollama native response code: ${response.code}")

        if (!response.isSuccessful) {
            WysawygLogger.e("Ollama native error ${response.code}: $responseBody")
            throw RuntimeException("Ollama native error ${response.code}: $responseBody")
        }

        val parsed = JSONObject(responseBody)
        WysawygLogger.d("Ollama native response body: $responseBody")
        return parsed.getJSONObject("message").optString("content", "").trim()
    }
}
