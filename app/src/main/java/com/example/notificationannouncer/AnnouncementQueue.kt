package com.example.notificationannouncer

/** Bounded, in-memory queue. Each announcement retains its own message snapshot. */
internal class AnnouncementQueue(private val capacity: Int = 256) {
    init { require(capacity > 0) }
    private data class Pending(val text: String, val identity: String, val message: String)
    private val pending = linkedMapOf<String, Pending>()
    private val submitted = linkedMapOf<String, String>()

    fun offer(key: String, text: String, identity: String = text, message: String = "") {
        if (submitted[key] == identity) {
            pending.remove(key)
            return
        }
        pending[key] = Pending(text, identity, message)
        trim(pending)
    }

    // Commit only one item after TTS accepts it. The caller waits for the whole
    // announce/listen/read interaction before requesting the next item.
    fun drainOne(speak: (String, String) -> Boolean): Boolean {
        val entry = pending.entries.firstOrNull() ?: return false
        val key = entry.key
        val announcement = entry.value
        if (!speak(announcement.text, announcement.message)) return false
        submitted.remove(key)
        submitted[key] = announcement.identity
        trim(submitted)
        pending.remove(key)
        return true
    }

    fun drain(speak: (String) -> Boolean) {
        while (drainOne { text, _ -> speak(text) }) { /* drain accepted entries */ }
    }

    fun clear() {
        pending.clear()
        submitted.clear()
    }

    private fun <T> trim(entries: MutableMap<String, T>) {
        if (entries.size > capacity) {
            val oldest = entries.entries.iterator()
            oldest.next()
            oldest.remove()
        }
    }
}
