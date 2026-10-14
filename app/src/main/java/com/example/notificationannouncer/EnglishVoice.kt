package com.example.notificationannouncer

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import java.util.Locale

internal object EnglishVoice {
    private const val PREFS = "announcement_settings"
    private const val KEY = "english_voice_name"

    fun selectedName(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)

    fun select(context: Context, name: String?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, name).apply()
    }

    fun available(engine: TextToSpeech): List<Voice> =
        engine.voices.orEmpty().filter {
            it.locale.language == "en" &&
                !it.features.orEmpty().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)
        }.sortedWith(
            compareByDescending<Voice> { it.locale.country == "GB" }
                .thenByDescending { !it.isNetworkConnectionRequired }
                .thenByDescending { it.quality }
                .thenBy { it.name }
        )

    fun preferred(context: Context, engine: TextToSpeech): Voice? {
        val voices = available(engine)
        val selected = selectedName(context)
        return voices.firstOrNull { it.name == selected }
            ?: voices.firstOrNull { it.locale.country == "GB" }
            ?: voices.firstOrNull()
    }

    fun apply(context: Context, engine: TextToSpeech): Boolean {
        val preferred = preferred(context, engine)
        if (preferred != null && engine.setVoice(preferred) == TextToSpeech.SUCCESS) return true
        return engine.setLanguage(Locale.UK) >= TextToSpeech.LANG_COUNTRY_AVAILABLE ||
            engine.setLanguage(Locale.ENGLISH) >= TextToSpeech.LANG_AVAILABLE
    }
}
