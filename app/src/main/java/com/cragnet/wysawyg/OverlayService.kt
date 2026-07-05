package com.cragnet.wysawyg

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
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
    private var miniTriggerView: View? = null
    private lateinit var overlayParams: WindowManager.LayoutParams
    private lateinit var miniParams: WindowManager.LayoutParams
    private lateinit var audioRecorder: AudioRecorder
    private lateinit var ollamaClient: OllamaClient
    private var isRecording = false

    private lateinit var cancelButton: ImageButton
    private lateinit var acceptButton: ImageButton
    private lateinit var waveformView: WaveformView

    companion object {
        private const val CHANNEL_ID = "wysawyg_overlay"
        private const val NOTIFICATION_ID = 1
        private var instance: OverlayService? = null

        fun isRunning(): Boolean = instance != null
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        WysawygLogger.init(this)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        audioRecorder = AudioRecorder(this)
        ollamaClient = OllamaClient(this)
        createNotificationChannel()
        WysawygLogger.i("Overlay service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification()
        startForeground(NOTIFICATION_ID, notification)
        WysawygLogger.i("Overlay service started by user")
        showOverlay()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        hideAll()
        instance = null
        super.onDestroy()
    }

    private fun runOnMain(block: () -> Unit) {
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            block()
        } else {
            android.os.Handler(android.os.Looper.getMainLooper()).post(block)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Wysawyg Overlay",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Wysawyg")
            .setContentText("Dictation overlay active")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()
    }

    private fun hideAll() {
        overlayView?.let {
            try { windowManager.removeView(it) } catch (e: Exception) { WysawygLogger.e("Error removing overlay", e) }
        }
        overlayView = null
        miniTriggerView?.let {
            try { windowManager.removeView(it) } catch (e: Exception) { WysawygLogger.e("Error removing mini trigger", e) }
        }
        miniTriggerView = null
    }

    private fun showOverlay() {
        try {
            hideMiniTrigger()
            if (overlayView != null) {
                overlayView?.visibility = View.VISIBLE
                return
            }

            overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_button, null)
            cancelButton = overlayView!!.findViewById(R.id.cancelButton)
            acceptButton = overlayView!!.findViewById(R.id.acceptButton)
            waveformView = overlayView!!.findViewById(R.id.waveformView)

            overlayParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 50
                y = 200
            }

            windowManager.addView(overlayView, overlayParams)

            setIdleState()
            makeDraggable(overlayView!!, overlayParams)

            cancelButton.setOnClickListener {
                if (isRecording) {
                    cancelRecording()
                } else {
                    hideOverlay()
                    showMiniTrigger()
                }
            }

            acceptButton.setOnClickListener {
                if (isRecording) {
                    stopAndTranscribe()
                }
            }

            waveformView.setOnClickListener {
                if (!isRecording) {
                    startRecording()
                } else {
                    stopAndTranscribe()
                }
            }
            WysawygLogger.i("Overlay shown")
        } catch (e: Exception) {
            WysawygLogger.e("Failed to show overlay", e)
            Toast.makeText(this, "Overlay failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun hideOverlay() {
        overlayView?.let {
            try { windowManager.removeView(it) } catch (e: Exception) { WysawygLogger.e("Error hiding overlay", e) }
        }
        overlayView = null
    }

    private fun showMiniTrigger() {
        try {
            if (miniTriggerView != null) return

            miniTriggerView = LayoutInflater.from(this).inflate(R.layout.mini_trigger, null)
            val button = miniTriggerView!!.findViewById<ImageButton>(R.id.miniRecordButton)

            miniParams = WindowManager.LayoutParams(
                64, 64,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 50
                y = 200
            }

            windowManager.addView(miniTriggerView, miniParams)
            makeDraggable(miniTriggerView!!, miniParams)

            button.setOnClickListener {
                showOverlay()
            }
            WysawygLogger.i("Mini trigger shown")
        } catch (e: Exception) {
            WysawygLogger.e("Failed to show mini trigger", e)
        }
    }

    private fun hideMiniTrigger() {
        miniTriggerView?.let {
            try { windowManager.removeView(it) } catch (e: Exception) { WysawygLogger.e("Error hiding mini trigger", e) }
        }
        miniTriggerView = null
    }

    private fun setIdleState() {
        isRecording = false
        waveformView.stopAnimation()
        acceptButton.setImageResource(android.R.drawable.ic_btn_speak_now)
        acceptButton.contentDescription = "Record"
        waveformView.alpha = 0.5f
    }

    private fun setRecordingState() {
        isRecording = true
        waveformView.startAnimation()
        acceptButton.setImageResource(android.R.drawable.ic_menu_save)
        acceptButton.contentDescription = "Accept"
        waveformView.alpha = 1.0f
    }

    private fun startRecording() {
        try {
            audioRecorder.start()
            setRecordingState()
            WysawygLogger.i("Overlay recording started")
            Toast.makeText(this, "Recording...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            WysawygLogger.e("Failed to start overlay recording", e)
            setIdleState()
            Toast.makeText(this, "Recording failed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun cancelRecording() {
        try {
            audioRecorder.stop()
            WysawygLogger.i("Overlay recording cancelled")
        } catch (e: Exception) {
            WysawygLogger.e("Error cancelling recording", e)
        }
        setIdleState()
        Toast.makeText(this, "Cancelled", Toast.LENGTH_SHORT).show()
    }

    private fun stopAndTranscribe() {
        if (!isRecording) return
        isRecording = false
        waveformView.stopAnimation()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val wavBytes = audioRecorder.stop()
                WysawygLogger.i("Overlay audio captured: ${wavBytes.size} bytes")
                val text = ollamaClient.transcribe(wavBytes)
                WysawygLogger.i("Overlay transcription: $text")
                withContext(Dispatchers.Main) {
                    if (text.isNotBlank()) {
                        TextInjectorService.inject(this@OverlayService, text)
                        Toast.makeText(this@OverlayService, "Inserted: $text", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this@OverlayService, "No transcription", Toast.LENGTH_SHORT).show()
                    }
                    setIdleState()
                }
            } catch (e: Exception) {
                WysawygLogger.e("Overlay transcription failed", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@OverlayService, "Transcription failed: ${e.message}", Toast.LENGTH_LONG).show()
                    setIdleState()
                }
            }
        }
    }

    private fun makeDraggable(view: View, layoutParams: WindowManager.LayoutParams) {
        var initialX = 0
        var initialY = 0
        var touchX = 0f
        var touchY = 0f
        var dragging = false

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    touchX = event.rawX
                    touchY = event.rawY
                    dragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - touchX
                    val dy = event.rawY - touchY
                    if (kotlin.math.abs(dx) > 10 || kotlin.math.abs(dy) > 10) {
                        dragging = true
                        layoutParams.x = initialX + dx.toInt()
                        layoutParams.y = initialY + dy.toInt()
                        windowManager.updateViewLayout(view, layoutParams)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!dragging) {
                        view.performClick()
                    }
                    true
                }
                else -> false
            }
        }
    }
}
