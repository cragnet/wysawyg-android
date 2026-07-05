package com.cragnet.wysawyg

import android.content.Context
import android.util.Base64
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
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

        return@withContext if (url.trimEnd('/').contains("/v1")) {
            transcribeOpenAICompatible(url, model, wavBytes, apiKey)
        } else {
            transcribeOllamaNative(url, model, systemPrompt, wavBytes, apiKey)
        }
    }

    private fun transcribeOpenAICompatible(baseUrl: String, model: String, wavBytes: ByteArray, apiKey: String): String {
        val endpoint = baseUrl.trimEnd('/') + "/audio/transcriptions"
        WysawygLogger.i("OpenAI-compatible POST $endpoint model=$model")

        val tempFile = File.createTempFile("wysawyg", ".wav", context.cacheDir)
        tempFile.writeBytes(wavBytes)

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("model", model)
            .addFormDataPart("file", "audio.wav", tempFile.asRequestBody("audio/wav".toMediaTypeOrNull()))
            .addFormDataPart("response_format", "json")
            .build()

        val requestBuilder = Request.Builder()
            .url(endpoint)
            .post(requestBody)

        if (apiKey.isNotBlank()) {
            requestBuilder.header("Authorization", "Bearer $apiKey")
        }

        val request = requestBuilder.build()
        val response = client.newCall(request).execute()
        tempFile.delete()

        val responseBody = response.body?.string() ?: throw RuntimeException("Empty response")
        WysawygLogger.i("OpenAI-compatible response code: ${response.code}")

        if (!response.isSuccessful) {
            WysawygLogger.e("OpenAI-compatible error ${response.code}: $responseBody")
            throw RuntimeException("OpenAI-compatible error ${response.code}: $responseBody")
        }

        val parsed = JSONObject(responseBody)
        WysawygLogger.d("OpenAI-compatible response body: $responseBody")
        return parsed.optString("text", "").trim()
    }

    private fun transcribeOllamaNative(baseUrl: String, model: String, systemPrompt: String, wavBytes: ByteArray, apiKey: String): String {
        val endpoint = baseUrl.trimEnd('/')
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
