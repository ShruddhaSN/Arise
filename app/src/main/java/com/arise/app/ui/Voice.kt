package com.arise.app.ui

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * The System's voice: the phone's built-in text-to-speech, tuned a little lower and slower so it
 * sounds less chirpy. Used by the workout coach (counts, form tips) and System pop-ups.
 */
object Voice {
    private var tts: TextToSpeech? = null
    private var ready = false

    @Volatile
    var enabled: Boolean = true

    fun init(ctx: Context) {
        if (tts != null) return
        tts = TextToSpeech(ctx.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                tts?.language = Locale.US
                tts?.setPitch(0.85f)
                tts?.setSpeechRate(0.95f)
            }
        }
    }

    /** [now] = interrupt whatever is being said (counts must stay on the beat). */
    fun say(text: String, now: Boolean = true) {
        if (!enabled || !ready || text.isBlank()) return
        tts?.speak(text, if (now) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, "arise-${System.nanoTime()}")
    }

    fun stop() {
        tts?.stop()
    }
}
