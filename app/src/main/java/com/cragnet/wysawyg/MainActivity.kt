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
    }

    private lateinit var urlInput: EditText
    private lateinit var modelInput: EditText
    private lateinit var promptInput: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        WysawygLogger.init(this)

        urlInput = findViewById(R.id.ollamaUrl)
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

        findViewById<Button>(R.id.shareLogButton).setOnClickListener {
            shareLog()
        }

        findViewById<Button>(R.id.clearLogButton).setOnClickListener {
            WysawygLogger.clear(this)
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
        getSharedPreferences("wysawyg", MODE_PRIVATE).edit().apply {
            putString("ollama_url", urlInput.text.toString().ifBlank { "http://alarma.local:11434/api/chat" })
            putString("model", modelInput.text.toString().ifBlank { "gemma4:12b" })
            putString("system_prompt", promptInput.text.toString())
            apply()
        }
    }

    private fun loadSettings() {
        val prefs = getSharedPreferences("wysawyg", MODE_PRIVATE)
        urlInput.setText(prefs.getString("ollama_url", "http://alarma.local:11434/api/chat"))
        modelInput.setText(prefs.getString("model", "gemma4:12b"))
        promptInput.setText(prefs.getString("system_prompt", "Transcribe the audio exactly. Output only the spoken words, no commentary."))
    }
}
