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
 * AudioTrack принадлежит рабочему потоку: создаётся, играет и освобождается только в нём.
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

    init {
        thread(name = "alignment-tone", isDaemon = true) { loop() }
    }

    private fun newTrack(): AudioTrack = AudioTrack.Builder()
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
        .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
        .setTransferMode(AudioTrack.MODE_STREAM)
        .setBufferSizeInBytes((beepSamples + gapSamples) * 2 * 2)
        .build()

    private fun loop() {
        var track: AudioTrack? = null
        while (running) {
            try {
                val s = signal
                if (!enabled || s == null) {
                    track?.let { if (it.playState == AudioTrack.PLAYSTATE_PLAYING) { it.pause(); it.flush() } }
                    Thread.sleep(100); continue
                }
                val t = track ?: newTrack().also { track = it }
                if (t.playState != AudioTrack.PLAYSTATE_PLAYING) t.play()
                val written = t.write(beep(freqFor(s)), 0, beepSamples + gapSamples)
                if (written < 0) { // аудиовыход сменился/умер — пересоздать трек
                    runCatching { t.release() }; track = null; Thread.sleep(200)
                }
            } catch (e: Exception) {
                runCatching { track?.release() }; track = null
                runCatching { Thread.sleep(500) }
            }
        }
        runCatching { track?.stop() }
        runCatching { track?.release() }
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

    fun setEnabled(on: Boolean) { enabled = on }

    /** Не блокирует главный поток: рабочий поток сам остановит и освободит AudioTrack. */
    fun release() { enabled = false; running = false }
}
