package com.cragnet.wysawyg

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    companion object {
        private const val PERMISSION_REQUEST_CODE = 1001
        private const val OVERLAY_REQUEST_CODE = 1002
        const val PREFS_NAME = "wysawyg"
        const val PREF_ALARMA_URL = "alarma_url"
        const val PREF_API_KEY = "api_key"
        const val PREF_MODEL = "model"
        const val PREF_SYSTEM_PROMPT = "system_prompt"
    }

    private lateinit var alarmaUrlInput: EditText
    private lateinit var apiKeyInput: EditText
    private lateinit var modelInput: EditText
    private lateinit var promptInput: EditText

    private lateinit var settingsExporter: SettingsExporter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        WysawygLogger.init(this)
        settingsExporter = SettingsExporter(this)

        alarmaUrlInput = findViewById(R.id.alarmaUrl)
        apiKeyInput = findViewById(R.id.apiKey)
        modelInput = findViewById(R.id.modelName)
        promptInput = findViewById(R.id.systemPrompt)

        WysawygLogger.i("MainActivity started")

        findViewById<Button>(R.id.startOverlayButton).setOnClickListener {
            saveSettings()
            if (hasPermissions()) {
                startOverlayService()
            } else {
                requestPermissions()
            }
        }

        findViewById<Button>(R.id.stopOverlayButton).setOnClickListener {
            stopService(Intent(this, OverlayService::class.java))
        }

        findViewById<Button>(R.id.enableKeyboardButton).setOnClickListener {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }

        findViewById<Button>(R.id.viewLogButton).setOnClickListener {
            startActivity(Intent(this, LogActivity::class.java))
        }

        findViewById<Button>(R.id.shareLogButton).setOnClickListener {
            shareLog()
        }

        findViewById<Button>(R.id.clearLogButton).setOnClickListener {
            WysawygLogger.clear(this)
        }

        findViewById<Button>(R.id.exportSettingsButton).setOnClickListener {
            saveSettings()
            settingsExporter.promptExport()
        }

        findViewById<Button>(R.id.importSettingsButton).setOnClickListener {
            settingsExporter.promptImport()
        }

        alarmaUrlInput.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val currentModel = modelInput.text.toString()
                if (currentModel.isBlank()) {
                    modelInput.setText(defaultModel(alarmaUrlInput.text.toString()))
                }
            }
        }

        loadSettings()
    }

    private fun hasPermissions(): Boolean {
        val audio = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val overlay = Settings.canDrawOverlays(this)
        return audio && overlay
    }

    private fun requestPermissions() {
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            permissions.add(Manifest.permission.FOREGROUND_SERVICE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            permissions.add(Manifest.permission.FOREGROUND_SERVICE_MICROPHONE)
        }
        ActivityCompat.requestPermissions(this, permissions.toTypedArray(), PERMISSION_REQUEST_CODE)

        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivityForResult(intent, OVERLAY_REQUEST_CODE)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED } && Settings.canDrawOverlays(this)) {
                startOverlayService()
            } else {
                Toast.makeText(this, "Permissions required", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == OVERLAY_REQUEST_CODE && Settings.canDrawOverlays(this) && hasPermissions()) {
            startOverlayService()
        }
    }

    private fun startOverlayService() {
        val intent = Intent(this, OverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(this, intent)
        } else {
            startService(intent)
        }
    }

    private fun shareLog() {
        val logText = WysawygLogger.getLogText(this)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "WYSAWYG log")
            putExtra(Intent.EXTRA_TEXT, logText)
        }
        startActivity(Intent.createChooser(shareIntent, "Share log"))
    }

    private fun saveSettings() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().apply {
            putString(PREF_ALARMA_URL, alarmaUrlInput.text.toString().ifBlank { "http://alarma.local:11434/api/chat" })
            putString(PREF_API_KEY, apiKeyInput.text.toString())
            putString(PREF_MODEL, modelInput.text.toString().ifBlank { defaultModel(alarmaUrlInput.text.toString()) })
            putString(PREF_SYSTEM_PROMPT, promptInput.text.toString())
            apply()
        }
        WysawygLogger.i("Settings saved")
    }

    private fun loadSettings() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val url = prefs.getString(PREF_ALARMA_URL, "http://alarma.local:11434/api/chat") ?: "http://alarma.local:11434/api/chat"
        alarmaUrlInput.setText(url)
        apiKeyInput.setText(prefs.getString(PREF_API_KEY, ""))
        modelInput.setText(prefs.getString(PREF_MODEL, defaultModel(url)))
        promptInput.setText(prefs.getString(PREF_SYSTEM_PROMPT, "Transcribe the audio exactly. Output only the spoken words, no commentary."))
    }

    private fun defaultModel(url: String): String {
        return if (url.trimEnd('/').contains("/v1")) "whisper-1" else "gemma4:12b"
    }
}
