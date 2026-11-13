package com.airmaxfield.ui.status

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.sin

/**
 * Пищалка юстировки: тон 300 Гц при −90 dBm … 1500 Гц при −45 dBm, писк каждые 200 мс.
 * Вибрация 30 мс, когда сигнал улучшился на ≥ 2 dB.
 */
class AlignmentTone(ctx: Context) {
    private val rate = 22050
    private val beepSamples = rate * 80 / 1000
    private val gapSamples = rate * 120 / 1000
    @Volatile private var enabled = false
    @Volatile private var signal: Int? = null
    @Volatile private var running = true
    private var lastForVibe: Int? = null

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
        ctx.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION") ctx.getSystemService(Vibrator::class.java)
    }

    private val track = AudioTrack.Builder()
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
        .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
        .setTransferMode(AudioTrack.MODE_STREAM)
        .setBufferSizeInBytes((beepSamples + gapSamples) * 2 * 2)
        .build()

    private val worker = thread(name = "alignment-tone", isDaemon = true) {
        val silence = ShortArray(beepSamples + gapSamples)
        while (running) {
            val s = signal
            if (!enabled || s == null) { Thread.sleep(100); continue }
            if (track.playState != AudioTrack.PLAYSTATE_PLAYING) track.play()
            track.write(beep(freqFor(s)), 0, beepSamples + gapSamples)
        }
        track.write(silence, 0, silence.size)
    }

    fun freqFor(dbm: Int): Double {
        val t = ((dbm + 90).coerceIn(0, 45)) / 45.0
        return 300.0 + t * 1200.0
    }

    private fun beep(freq: Double): ShortArray {
        val out = ShortArray(beepSamples + gapSamples)
        for (i in 0 until beepSamples) {
            val env = minOf(1.0, i / 200.0, (beepSamples - i) / 200.0)
            out[i] = (sin(2 * PI * freq * i / rate) * 12000 * env).toInt().toShort()
        }
        return out
    }

    fun update(signalDbm: Int?) {
        signal = signalDbm
        if (!enabled || signalDbm == null) { lastForVibe = signalDbm; return }
        val prev = lastForVibe
        if (prev == null || signalDbm < prev) lastForVibe = signalDbm
        else if (signalDbm - prev >= 2) {
            lastForVibe = signalDbm
            runCatching { vibrator?.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE)) }
        }
    }

    fun setEnabled(on: Boolean) {
        enabled = on
        if (!on) runCatching { track.pause(); track.flush() }
    }

    fun release() {
        running = false
        enabled = false
        runCatching { worker.join(500) }
        runCatching { track.stop() }
        track.release()
    }
}
