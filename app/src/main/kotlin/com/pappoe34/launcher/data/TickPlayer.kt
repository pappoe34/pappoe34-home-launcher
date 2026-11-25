package com.pappoe34.launcher.data

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Plays a short mechanical "click" as a wheel passes a detent.
 * The sample is synthesised once into the cache dir so no audio asset is needed.
 */
class TickPlayer(context: Context) {

    private val appContext = context.applicationContext
    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    @Volatile private var soundId: Int = 0
    @Volatile private var loaded = false

    init {
        pool.setOnLoadCompleteListener { _, id, status -> if (status == 0 && id == soundId) loaded = true }
        soundId = pool.load(ensureSample().absolutePath, 1)
    }

    fun play(volume: Float = 0.6f) {
        if (loaded) pool.play(soundId, volume, volume, 1, 0, 1f)
    }

    fun release() = pool.release()

    private fun ensureSample(): File {
        val file = File(appContext.cacheDir, "tick-v1.wav")
        if (file.length() > 44) return file
        file.writeBytes(synthesiseClick())
        return file
    }

    /** 16-bit mono PCM WAV: a 1.8 kHz burst with fast exponential decay plus a touch of noise. */
    private fun synthesiseClick(sampleRate: Int = 44_100, durationMs: Int = 28): ByteArray {
        val frames = sampleRate * durationMs / 1000
        val pcm = ByteBuffer.allocate(frames * 2).order(ByteOrder.LITTLE_ENDIAN)
        val rng = java.util.Random(7)
        for (i in 0 until frames) {
            val t = i.toDouble() / sampleRate
            val env = exp(-t * 180.0)
            val tone = sin(2 * PI * 1800.0 * t) * 0.8 + sin(2 * PI * 3600.0 * t) * 0.2
            val noise = (rng.nextDouble() * 2 - 1) * 0.15
            val sample = ((tone + noise) * env * Short.MAX_VALUE * 0.9).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            pcm.putShort(sample.toShort())
        }
        val data = pcm.array()
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray(Charsets.US_ASCII))
        header.putInt(36 + data.size)
        header.put("WAVE".toByteArray(Charsets.US_ASCII))
        header.put("fmt ".toByteArray(Charsets.US_ASCII))
        header.putInt(16)
        header.putShort(1) // PCM
        header.putShort(1) // mono
        header.putInt(sampleRate)
        header.putInt(sampleRate * 2)
        header.putShort(2)
        header.putShort(16)
        header.put("data".toByteArray(Charsets.US_ASCII))
        header.putInt(data.size)
        return header.array() + data
    }
}
