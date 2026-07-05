package com.cragnet.wysawyg

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class LogActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log)

        val logView = findViewById<EditText>(R.id.logView)
        logView.setText(WysawygLogger.getLogText(this))

        findViewById<Button>(R.id.copyLogButton).setOnClickListener {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("wysawyg-log", logView.text.toString()))
            Toast.makeText(this, "Log copied", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.shareLogButton).setOnClickListener {
            val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(android.content.Intent.EXTRA_SUBJECT, "WYSAWYG log")
                putExtra(android.content.Intent.EXTRA_TEXT, logView.text.toString())
            }
            startActivity(android.content.Intent.createChooser(shareIntent, "Share log"))
        }

        findViewById<Button>(R.id.clearLogButton).setOnClickListener {
            WysawygLogger.clear(this)
            logView.setText("")
            Toast.makeText(this, "Log cleared", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.refreshLogButton).setOnClickListener {
            logView.setText(WysawygLogger.getLogText(this))
            Toast.makeText(this, "Log refreshed", Toast.LENGTH_SHORT).show()
        }
    }
}
