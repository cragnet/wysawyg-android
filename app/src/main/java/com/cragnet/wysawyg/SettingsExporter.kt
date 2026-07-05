package com.cragnet.wysawyg

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import java.io.InputStreamReader
import java.io.OutputStreamWriter

class SettingsExporter(private val activity: AppCompatActivity) {

    private val exportLauncher = activity.registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { exportToUri(it) }
    }

    private val importLauncher = activity.registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { importFromUri(it) }
    }

    fun promptExport() {
        val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.UK).format(java.util.Date())
        exportLauncher.launch("wysawyg_settings_$timestamp.json")
    }

    fun promptImport() {
        importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
    }

    private fun exportToUri(uri: Uri) {
        try {
            val prefs = activity.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
            val json = JSONObject().apply {
                put(MainActivity.PREF_ALARMA_URL, prefs.getString(MainActivity.PREF_ALARMA_URL, "") ?: "")
                put(MainActivity.PREF_API_KEY, prefs.getString(MainActivity.PREF_API_KEY, "") ?: "")
                put(MainActivity.PREF_MODEL, prefs.getString(MainActivity.PREF_MODEL, "") ?: "")
                put(MainActivity.PREF_SYSTEM_PROMPT, prefs.getString(MainActivity.PREF_SYSTEM_PROMPT, "") ?: "")
            }

            activity.contentResolver.openOutputStream(uri)?.use { out ->
                OutputStreamWriter(out).use { writer ->
                    writer.write(json.toString(2))
                }
            }
            WysawygLogger.i("Settings exported to $uri")
            android.widget.Toast.makeText(activity, "Settings exported", android.widget.Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            WysawygLogger.e("Failed to export settings", e)
            android.widget.Toast.makeText(activity, "Export failed: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    private fun importFromUri(uri: Uri) {
        try {
            val jsonString = activity.contentResolver.openInputStream(uri)?.use { stream -
                InputStreamReader(stream).readText()
            } ?: throw RuntimeException("Empty file")

            val json = JSONObject(jsonString)
            activity.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE).edit().apply {
                putString(MainActivity.PREF_ALARMA_URL, json.optString(MainActivity.PREF_ALARMA_URL, "http://alarma.local:11434/api/chat"))
                putString(MainActivity.PREF_API_KEY, json.optString(MainActivity.PREF_API_KEY, ""))
                putString(MainActivity.PREF_MODEL, json.optString(MainActivity.PREF_MODEL, "gemma4:12b"))
                putString(MainActivity.PREF_SYSTEM_PROMPT, json.optString(MainActivity.PREF_SYSTEM_PROMPT, "Transcribe the audio exactly. Output only the spoken words, no commentary."))
                apply()
            }

            WysawygLogger.i("Settings imported from $uri")
            android.widget.Toast.makeText(activity, "Settings imported — restart app to apply", android.widget.Toast.LENGTH_LONG).show()
            activity.recreate()
        } catch (e: Exception) {
            WysawygLogger.e("Failed to import settings", e)
            android.widget.Toast.makeText(activity, "Import failed: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
        }
    }
}
