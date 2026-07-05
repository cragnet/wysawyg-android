package com.cragnet.wysawyg

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONTokener
import org.json.JSONObject
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
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
            val json = JSONObject()
            json.put(MainActivity.PREF_ALARMA_URL, prefs.getString(MainActivity.PREF_ALARMA_URL, "") ?: "")
            json.put(MainActivity.PREF_API_KEY, prefs.getString(MainActivity.PREF_API_KEY, "") ?: "")
            json.put(MainActivity.PREF_MODEL, prefs.getString(MainActivity.PREF_MODEL, "") ?: "")
            json.put(MainActivity.PREF_SYSTEM_PROMPT, prefs.getString(MainActivity.PREF_SYSTEM_PROMPT, "") ?: "")

            val out: OutputStream? = activity.contentResolver.openOutputStream(uri)
            if (out != null) {
                OutputStreamWriter(out).use { writer ->
                    writer.write(json.toString(2))
                }
            }
            WysawygLogger.i("Settings exported to $uri")
            Toast.makeText(activity, "Settings exported", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            WysawygLogger.e("Failed to export settings", e)
            Toast.makeText(activity, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun importFromUri(uri: Uri) {
        try {
            val ins: InputStream? = activity.contentResolver.openInputStream(uri)
            val jsonString: String = if (ins != null) {
                InputStreamReader(ins).use { reader ->
                    reader.readText()
                }
            } else {
                throw RuntimeException("Empty file")
            }

            val json: JSONObject = JSONTokener(jsonString).nextValue() as JSONObject
            activity.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE).edit().apply {
                putString(MainActivity.PREF_ALARMA_URL, json.optString(MainActivity.PREF_ALARMA_URL, "http://alarma.local:11434/api/chat"))
                putString(MainActivity.PREF_API_KEY, json.optString(MainActivity.PREF_API_KEY, ""))
                putString(MainActivity.PREF_MODEL, json.optString(MainActivity.PREF_MODEL, "gemma4:12b"))
                putString(MainActivity.PREF_SYSTEM_PROMPT, json.optString(MainActivity.PREF_SYSTEM_PROMPT, "Transcribe the audio exactly. Output only the spoken words, no commentary."))
                apply()
            }

            WysawygLogger.i("Settings imported from $uri")
            Toast.makeText(activity, "Settings imported — restart app to apply", Toast.LENGTH_LONG).show()
            activity.recreate()
        } catch (e: Exception) {
            WysawygLogger.e("Failed to import settings", e)
            Toast.makeText(activity, "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
