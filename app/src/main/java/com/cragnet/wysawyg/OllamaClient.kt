package com.cragnet.wysawyg

import android.content.Context
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

    companion object {
        private const val TAG = "OllamaClient"
    }

    suspend fun transcribe(wavBytes: ByteArray): String = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences("wysawyg", Context.MODE_PRIVATE)
        val url = prefs.getString("ollama_url", "http://alarma.local:11434/api/chat")!!
        val model = prefs.getString("model", "gemma4:12b")!!
        val systemPrompt = prefs.getString("system_prompt", "Transcribe the audio exactly. Output only the spoken words, no commentary.")!!

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
        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        Log.d(TAG, "Sending ${wavBytes.size} bytes to $url")
        val response = client.newCall(request).execute()
        val responseBody = response.body?.string() ?: throw RuntimeException("Empty response")

        if (!response.isSuccessful) {
            throw RuntimeException("Ollama error ${response.code}: $responseBody")
        }

        val parsed = JSONObject(responseBody)
        Log.d(TAG, "Response: $responseBody")
        return@withContext parsed.getJSONObject("message").optString("content", "").trim()
    }
}
