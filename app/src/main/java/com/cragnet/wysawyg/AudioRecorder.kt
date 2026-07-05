package com.cragnet.wysawyg

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AudioRecorder(private val context: Context) {

    companion object {
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val TAG = "AudioRecorder"
    }

    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null
    private var isRecording = false

    fun start() {
        val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        val bufferSize = minBufferSize.coerceAtLeast(4096)

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufferSize
        )

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            throw IllegalStateException("AudioRecord not initialized")
        }

        isRecording = true
        audioRecord?.startRecording()

        val buffer = ByteArray(bufferSize)
        val outputStream = ByteArrayOutputStream()

        recordingThread = Thread {
            while (isRecording) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (read > 0) {
                    outputStream.write(buffer, 0, read)
                }
            }

            val pcmBytes = outputStream.toByteArray()
            outputStream.close()
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null

            val wavFile = File(context.cacheDir, "recording.wav")
            writeWav(wavFile, pcmBytes)
            lastRecording = wavFile.readBytes()
        }.apply { start() }
    }

    fun stop(): ByteArray {
        isRecording = false
        recordingThread?.join(1000)
        recordingThread = null
        return lastRecording ?: throw IllegalStateException("No recording captured")
    }

    private fun writeWav(file: File, pcmBytes: ByteArray) {
        FileOutputStream(file).use { out ->
            val totalDataLen = pcmBytes.size + 36
            val longSampleRate = SAMPLE_RATE.toLong()
            val byteRate = (16 * SAMPLE_RATE * 1 / 8).toLong()

            out.write("RIFF".toByteArray())
            out.write(intToByteArray(totalDataLen))
            out.write("WAVE".toByteArray())
            out.write("fmt ".toByteArray())
            out.write(intToByteArray(16))
            out.write(shortToByteArray(1))
            out.write(shortToByteArray(1))
            out.write(intToByteArray(longSampleRate.toInt()))
            out.write(intToByteArray(byteRate.toInt()))
            out.write(shortToByteArray((16 * 1 / 8).toShort()))
            out.write(shortToByteArray(16))
            out.write("data".toByteArray())
            out.write(intToByteArray(pcmBytes.size))
            out.write(pcmBytes)
        }
    }

    private fun intToByteArray(value: Int): ByteArray {
        return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()
    }

    private fun shortToByteArray(value: Short): ByteArray {
        return ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(value).array()
    }

    private var lastRecording: ByteArray? = null
}
