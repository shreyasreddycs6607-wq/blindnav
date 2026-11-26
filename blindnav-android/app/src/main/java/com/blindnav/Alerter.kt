package com.blindnav

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import com.blindnav.core.Alert
import com.blindnav.core.Level
import java.util.Locale

/** Higher priority interrupts lower. A lower priority never cuts off a higher one that is still speaking. */
enum class Priority(val rank: Int) { AMBIENT(0), NAV(1), USER(2), HAZARD(3), SOS(4) }

/**
 * Urgent hazards: tone + vibration first (instant), then a short phrase.
 * Speech alone is too slow for an urgent warning.
 * Hazard phrases are spoken in English (COCO labels are English). Everything else follows the chosen language.
 */
class Alerter(context: Context, private val settings: Settings) : AutoCloseable {
    private val vibrator = context.getSystemService(VibratorManager::class.java).defaultVibrator
    private val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 90)
    private var ready = false
    private val pending = mutableListOf<Triple<String, Priority, String?>>()
    private var curRank = -1
    private var curUntil = 0L
    private var lang = ""
    private var last = ""
    private var warnedMissing = ""

    private val tts: TextToSpeech = TextToSpeech(context) { status ->
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            tts.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            pending.toList().also { pending.clear() }.forEach { say(it.first, it.second, it.third) }
        }
    }

    fun alert(a: Alert) {
        if (a.level == Level.URGENT) {
            tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 150)
            vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 200, 100, 200), -1))
            say(a.text, Priority.HAZARD, "en-IN")
        } else {
            vibrator.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE))
            say(a.text, Priority.NAV, "en-IN")
        }
    }

    fun say(text: String, priority: Priority = Priority.USER, tag: String? = null) {
        if (!ready) { pending += Triple(text, priority, tag); return }
        val now = SystemClock.elapsedRealtime()
        if (priority.rank < curRank && now < curUntil) return
        val interrupt = priority.rank >= Priority.USER.rank
        if (!interrupt && tts.isSpeaking) return // ambient/nav chatter is dropped, not queued behind speech
        setLanguage(tag ?: settings.language)
        tts.setSpeechRate(settings.speechRate)
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "u")
        curRank = priority.rank
        curUntil = now + text.length * 80L + 300
        last = text
    }

    /** For reading pages aloud: chunks the text and queues it. */
    fun speakLong(text: String, tag: String) {
        val max = (TextToSpeech.getMaxSpeechInputLength() - 100).coerceAtLeast(500)
        val chunks = text.chunked(max)
        chunks.forEachIndexed { i, c ->
            if (i == 0) say(c, Priority.USER, tag)
            else if (ready) tts.speak(c, TextToSpeech.QUEUE_ADD, null, "l$i")
        }
    }

    private fun setLanguage(tag: String) {
        if (tag == lang) return
        val r = tts.setLanguage(Locale.forLanguageTag(tag))
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
            tts.setLanguage(Locale.forLanguageTag("en-IN"))
            lang = "en-IN"
            if (warnedMissing != tag) {
                warnedMissing = tag
                tts.speak("The voice for ${Settings.nameOf(tag)} is not installed. Install it in the phone's text to speech settings.", TextToSpeech.QUEUE_ADD, null, "w")
            }
        } else lang = tag
    }

    fun repeatLast() { if (last.isNotEmpty()) say(last) }

    fun stop() {
        if (ready) tts.stop()
        curRank = -1
    }

    fun beep() = tone.startTone(ToneGenerator.TONE_PROP_BEEP, 120)

    override fun close() {
        tts.shutdown()
        tone.release()
    }
}
