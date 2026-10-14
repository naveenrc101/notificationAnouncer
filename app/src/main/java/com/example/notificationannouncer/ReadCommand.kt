package com.example.notificationannouncer

import java.util.Locale

internal object ReadCommand {
    const val LISTEN_MILLIS = 4_000L
    const val STARTUP_TIMEOUT_MILLIS = 10_000L
    const val RESULT_TIMEOUT_MILLIS = 8_000L

    // Match the complete, best-ranked final transcript, not a substring or a
    // provisional partial result (which might later become "do not read it").
    fun matches(transcript: String?): Boolean {
        val normalized = transcript.orEmpty().lowercase(Locale.ENGLISH)
            .replace(Regex("[.,!?]"), "")
            .trim().replace(Regex("\\s+"), " ")
        return normalized == "read it"
    }
}
