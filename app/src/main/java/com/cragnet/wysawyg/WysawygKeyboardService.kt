package com.cragnet.wysawyg

import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WysawygKeyboardService : InputMethodService() {

    private lateinit var audioRecorder: AudioRecorder
    private lateinit var ollamaClient: OllamaClient
    private var isRecording = false
    private var recordButton: ImageButton? = null

    override fun onCreate() {
        super.onCreate()
        WysawygLogger.init(this)
        audioRecorder = AudioRecorder(this)
        ollamaClient = OllamaClient(this)
        WysawygLogger.i("Keyboard service created")
    }

    override fun onCreateInputView(): View {
        WysawygLogger.i("Creating input view")
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(16, 16, 16, 16)
        }

        recordButton = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_btn_speak_now)
            setBackgroundResource(R.drawable.circle_button)
            layoutParams = LinearLayout.LayoutParams(128, 128)
            setOnClickListener { toggleRecording() }
        }

        layout.addView(recordButton)
        return layout
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        WysawygLogger.i("Input view started, inputType=${info?.inputType}")
    }

    private fun toggleRecording() {
        if (isRecording) {
            isRecording = false
            recordButton?.setImageResource(android.R.drawable.ic_btn_speak_now)
            stopAndTranscribe()
        } else {
            isRecording = true
            recordButton?.setImageResource(android.R.drawable.ic_media_pause)
            WysawygLogger.i("Starting recording from keyboard")
            try {
                audioRecorder.start()
                Toast.makeText(this, "Recording...", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                WysawygLogger.e("Failed to start recording", e)
                isRecording = false
                recordButton?.setImageResource(android.R.drawable.ic_btn_speak_now)
                Toast.makeText(this, "Recording failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun stopAndTranscribe() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                WysawygLogger.i("Stopping recording, transcribing...")
                val wavBytes = audioRecorder.stop()
                WysawygLogger.i("Audio captured: ${wavBytes.size} bytes")
                val text = ollamaClient.transcribe(wavBytes)
                WysawygLogger.i("Transcription result: $text")
                withContext(Dispatchers.Main) {
                    if (text.isNotBlank()) {
                        currentInputConnection?.commitText(text, 1)
                        Toast.makeText(this@WysawygKeyboardService, "Inserted: $text", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this@WysawygKeyboardService, "No transcription", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                WysawygLogger.e("Transcription failed", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@WysawygKeyboardService, "Transcription failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
