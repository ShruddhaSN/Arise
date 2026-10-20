package com.arise.app.ui

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Vibration + beeps, so timers work with the phone on the treadmill or the floor. */
object Cues {
    fun go(ctx: Context) {
        vibrate(ctx, longArrayOf(0, 400, 150, 400))
        beep(2)
    }

    fun rest(ctx: Context) {
        vibrate(ctx, longArrayOf(0, 700))
        beep(1)
    }

    fun finish(ctx: Context) {
        vibrate(ctx, longArrayOf(0, 300, 100, 300, 100, 600))
        beep(3)
    }

    fun levelUp(ctx: Context) {
        vibrate(ctx, longArrayOf(0, 60, 80, 60, 80, 400))
        chime()
    }

    fun tap(ctx: Context) = vibrate(ctx, longArrayOf(0, 25))

    @Suppress("DEPRECATION")
    private fun vibrate(ctx: Context, pattern: LongArray) {
        runCatching {
            val v: Vibrator = if (Build.VERSION.SDK_INT >= 31) {
                ctx.getSystemService(VibratorManager::class.java).defaultVibrator
            } else {
                ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            v.vibrate(VibrationEffect.createWaveform(pattern, -1))
        }
    }

    private fun beep(times: Int) {
        runCatching {
            val tg = ToneGenerator(AudioManager.STREAM_MUSIC, 90)
            val h = Handler(Looper.getMainLooper())
            repeat(times) { i -> h.postDelayed({ tg.startTone(ToneGenerator.TONE_PROP_BEEP, 200) }, i * 350L) }
            h.postDelayed({ tg.release() }, times * 350L + 400L)
        }
    }

    /** Three rising tones. */
    private fun chime() {
        runCatching {
            val tg = ToneGenerator(AudioManager.STREAM_MUSIC, 70)
            val h = Handler(Looper.getMainLooper())
            listOf(ToneGenerator.TONE_DTMF_4, ToneGenerator.TONE_DTMF_8, ToneGenerator.TONE_DTMF_D).forEachIndexed { i, tone ->
                h.postDelayed({ tg.startTone(tone, 160) }, i * 180L)
            }
            h.postDelayed({ tg.release() }, 900L)
        }
    }
}
