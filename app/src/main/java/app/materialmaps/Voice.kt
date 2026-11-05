package app.materialmaps

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * Speaks navigation instructions with the phone's text-to-speech, turning music down
 * (not off) while it talks, the way navigation apps do.
 */
class Voice(context: Context) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attributes)
        .build()
    private var ready = false
    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        if (status != TextToSpeech.SUCCESS) return@TextToSpeech
        // The instructions come in the phone's language; fall back to English if there's no voice for it.
        if (tts.setLanguage(Locale.getDefault()) < TextToSpeech.LANG_AVAILABLE) tts.setLanguage(Locale.ENGLISH)
        tts.setAudioAttributes(attributes)
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) = release()
            @Suppress("OVERRIDE_DEPRECATION")
            override fun onError(utteranceId: String?) = release()
            override fun onError(utteranceId: String?, errorCode: Int) = release()
        })
        ready = true
    }

    fun say(text: String) {
        if (!ready || text.isBlank()) return
        audio.requestAudioFocus(focus)
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "navigation")
    }

    fun stop() {
        if (ready) tts.stop()
        release()
    }

    fun shutdown() {
        stop()
        tts.shutdown()
    }

    private fun release() {
        audio.abandonAudioFocusRequest(focus)
    }
}
