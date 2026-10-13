package com.example.notificationannouncer

import java.util.Locale

/** Tries the detected language, then explicit fallbacks; never reuses the previous message's voice. */
internal object MessageLanguage {
    fun select(tag: String?, device: Locale, apply: (Locale) -> Boolean): Locale? {
        val detected = tag?.takeIf { it.isNotBlank() && it != "und" }
            ?.let { Locale.forLanguageTag(it) }?.takeIf { it.language.isNotEmpty() }
        return listOfNotNull(detected, device, Locale.ENGLISH).distinct().firstOrNull(apply)
    }
}
