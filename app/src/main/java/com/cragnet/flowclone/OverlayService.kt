package com.cragnet.flowclone

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.Toast
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private lateinit var params: WindowManager.LayoutParams
    private lateinit var audioRecorder: AudioRecorder
    private lateinit var ollamaClient: OllamaClient
    private var isRecording = false

    companion object {
        private const val CHANNEL_ID = "flowclone_overlay"
        private const val NOTIFICATION_ID = 1
        private const val TAG = "OverlayService"
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        audioRecorder = AudioRecorder(this)
        ollamaClient = OllamaClient(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification()
        startForeground(NOTIFICATION_ID, notification)
        showOverlay()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "FlowClone Overlay",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("FlowClone")
            .setContentText("Dictation overlay active")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()
    }

    private fun showOverlay() {
        if (overlayView != null) return

        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_button, null)
        val button = overlayView!!.findViewById<ImageButton>(R.id.recordButton)

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 50
            y = 200
        }

        windowManager.addView(overlayView, params)

        var initialX = 0
        var initialY = 0
        var touchX = 0f
        var touchY = 0f

        button.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - touchX).toInt()
                    params.y = initialY + (event.rawY - touchY).toInt()
                    windowManager.updateViewLayout(overlayView, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (kotlin.math.abs(event.rawX - touchX) < 20 && kotlin.math.abs(event.rawY - touchY) < 20) {
                        toggleRecording(button)
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun toggleRecording(button: ImageButton) {
        if (isRecording) {
            isRecording = false
            button.setImageResource(android.R.drawable.ic_btn_speak_now)
            stopAndTranscribe()
        } else {
            isRecording = true
            button.setImageResource(android.R.drawable.ic_media_pause)
            try {
                audioRecorder.start()
                Toast.makeText(this, "Recording...", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start recording", e)
                isRecording = false
                button.setImageResource(android.R.drawable.ic_btn_speak_now)
                Toast.makeText(this, "Recording failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun stopAndTranscribe() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val wavBytes = audioRecorder.stop()
                val text = ollamaClient.transcribe(wavBytes)
                withContext(Dispatchers.Main) {
                    if (text.isNotBlank()) {
                        TextInjectorService.pendingText = text
                        TextInjectorService.inject(this@OverlayService, text)
                        Toast.makeText(this@OverlayService, "Inserted: $text", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this@OverlayService, "No transcription", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Transcription failed", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@OverlayService, "Transcription failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onDestroy() {
        overlayView?.let { windowManager.removeView(it) }
        overlayView = null
        super.onDestroy()
    }
}
