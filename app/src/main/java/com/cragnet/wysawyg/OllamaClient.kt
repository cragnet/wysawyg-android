package com.cragnet.wysawyg

import android.content.Context
import android.net.Uri
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
        val url = prefs.getString(MainActivity.PREF_ALARMA_URL, "https://ollama.com/api/chat")!!
        val apiKey = prefs.getString(MainActivity.PREF_API_KEY, "") ?: ""
        val model = prefs.getString(MainActivity.PREF_MODEL, "gemma4:e4b")!!
        val systemPrompt = prefs.getString(MainActivity.PREF_SYSTEM_PROMPT, "Transcribe the audio exactly. Output only the spoken words, no commentary.")!!

        val uri = Uri.parse(url)
        val modelFromUrl = uri.getQueryParameter("model")
        val effectiveModel = modelFromUrl ?: model

        // Ollama Cloud / public endpoints work best with native /api/chat for multimodal audio.
        // OpenAI-compatible /v1/chat/completions does not reliably pass audio on Ollama Cloud.
        val base = url.trimEnd('/').removeSuffix("/api/chat").removeSuffix("/v1")
        val isOllama = url.contains("ollama.com") || url.contains("/api/chat")
        val endpoint = if (isOllama) "$base/api/chat" else "$base/v1/chat/completions"

        WysawygLogger.i("OllamaClient transcribe: endpoint=$endpoint model=$effectiveModel audio=${wavBytes.size} bytes apiKeyPresent=${apiKey.isNotBlank()}")

        return@withContext transcribeAudio(endpoint, effectiveModel, systemPrompt, wavBytes, apiKey, isOllama)
    }

    private fun transcribeAudio(endpoint: String, model: String, systemPrompt: String, wavBytes: ByteArray, apiKey: String, nativeOllama: Boolean): String {
        WysawygLogger.i("Audio transcription POST $endpoint model=$model")

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

        if (nativeOllama) {
            // Ollama native uses "think" to disable thinking mode on newer Gemma models.
            json.put("think", false)
        }

        val body = json.toString().toRequestBody("application/json".toMediaType())
        WysawygLogger.d("Transcription request body length: ${body.contentLength()} bytes")

        val requestBuilder = Request.Builder()
            .url(endpoint)
            .post(body)

        if (apiKey.isNotBlank()) {
            requestBuilder.header("Authorization", "Bearer $apiKey")
        }

        val request = requestBuilder.build()
        val response = client.newCall(request).execute()
        val responseBody = response.body?.string() ?: throw RuntimeException("Empty response")

        WysawygLogger.i("Transcription response code: ${response.code}")

        if (!response.isSuccessful) {
            WysawygLogger.e("Transcription error ${response.code}: $responseBody")
            throw RuntimeException("Transcription error ${response.code}: $responseBody")
        }

        val parsed = JSONObject(responseBody)
        WysawygLogger.d("Transcription response body: $responseBody")

        if (nativeOllama) {
            return parsed.getJSONObject("message").optString("content", "").trim()
        }

        val choices = parsed.optJSONArray("choices")
        if (choices != null && choices.length() > 0) {
            return choices.getJSONObject(0).getJSONObject("message").optString("content", "").trim()
        }

        return parsed.optJSONObject("message")?.optString("content", "")?.trim()
            ?: parsed.optString("response", "").trim()
    }
}
