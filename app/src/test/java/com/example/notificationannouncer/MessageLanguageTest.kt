package com.example.notificationannouncer

import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class MessageLanguageTest {
    @Test fun supportedDetectedLanguageWinsOverDeviceLanguage() {
        assertEquals("kn", MessageLanguage.select("kn", Locale.US) { true }?.language)
    }
    @Test fun unknownLanguageResetsToDeviceInsteadOfPreviousVoice() {
        assertEquals(Locale.US, MessageLanguage.select("und", Locale.US) { true })
        assertEquals(Locale.US, MessageLanguage.select(null, Locale.US) { true })
    }
    @Test fun missingDetectedVoiceFallsBackToDeviceThenEnglish() {
        val attempts = mutableListOf<String>()
        val selected = MessageLanguage.select("kn", Locale.forLanguageTag("hi-IN")) {
            attempts.add(it.language)
            it.language == "en"
        }
        assertEquals(listOf("kn", "hi", "en"), attempts)
        assertEquals(Locale.ENGLISH, selected)
    }
    @Test fun noUsableVoiceReturnsFailure() {
        assertNull(MessageLanguage.select("hi", Locale.US) { false })
    }
    @Test fun preservesDetectedRegionAndAvoidsDuplicateAttempts() {
        assertEquals(Locale.forLanguageTag("pt-BR"),
            MessageLanguage.select("pt-BR", Locale.US) { true })
        var attempts = 0
        MessageLanguage.select("en", Locale.ENGLISH) { attempts++; false }
        assertEquals(1, attempts)
    }
}
