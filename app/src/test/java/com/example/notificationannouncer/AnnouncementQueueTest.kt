package com.example.notificationannouncer

import org.junit.Assert.assertEquals
import org.junit.Test

class AnnouncementQueueTest {
    @Test fun buffersAndDeduplicatesWhilePreservingUpdatesAndOtherKeys() {
        val queue = AnnouncementQueue()
        val spoken = mutableListOf<String>()
        queue.offer("a", "old")
        queue.offer("a", "latest")
        queue.offer("a", "latest")
        queue.drain { spoken.add(it); true }
        queue.offer("a", "latest")
        queue.offer("b", "latest")
        queue.offer("a", "new")
        queue.drain { spoken.add(it); true }
        assertEquals(listOf("latest", "latest", "new"), spoken)
    }

    @Test fun rejectedSpeechRemainsPending() {
        val queue = AnnouncementQueue()
        queue.offer("a", "message")
        queue.drain { false }
        val spoken = mutableListOf<String>()
        queue.drain { spoken.add(it); true }
        queue.drain { spoken.add(it); true }
        assertEquals(listOf("message"), spoken)
    }

    @Test fun boundedQueueRetainsNewestKeys() {
        val queue = AnnouncementQueue(2)
        queue.offer("a", "one")
        queue.offer("b", "two")
        queue.offer("c", "three")
        val spoken = mutableListOf<String>()
        queue.drain { spoken.add(it); true }
        assertEquals(listOf("two", "three"), spoken)
    }

    @Test fun revertingToSubmittedContentCancelsPendingUpdate() {
        val queue = AnnouncementQueue()
        queue.offer("a", "original")
        queue.drain { true }
        queue.offer("a", "changed")
        queue.offer("a", "original")
        val spoken = mutableListOf<String>()
        queue.drain { spoken.add(it); true }
        assertEquals(emptyList<String>(), spoken)
    }
    @Test fun differentMessagesFromSameSenderStillAnnounce() {
        val queue = AnnouncementQueue()
        val spoken = mutableListOf<String>()
        val announcement = "Mr. Naveen, you have a message from Alex."
        queue.offer("a", announcement, "Alex:hello")
        queue.drain { spoken.add(it); true }
        queue.offer("a", announcement, "Alex:hello")
        queue.drain { spoken.add(it); true }
        queue.offer("a", announcement, "Alex:goodbye")
        queue.drain { spoken.add(it); true }
        assertEquals(listOf(announcement, announcement), spoken)
    }
    @Test fun serialProcessingKeepsEachMessageSnapshot() {
        val queue = AnnouncementQueue()
        queue.offer("a", "Alex", "first", "first message")
        queue.offer("b", "Blair", "second", "second message")
        val messages = mutableListOf<String>()
        queue.drainOne { sender, message -> messages.add(sender + ":" + message); true }
        assertEquals(listOf("Alex:first message"), messages)
        queue.offer("a", "Alex", "third", "third message")
        queue.drainOne { sender, message -> messages.add(sender + ":" + message); true }
        queue.drainOne { sender, message -> messages.add(sender + ":" + message); true }
        assertEquals(listOf("Alex:first message", "Blair:second message", "Alex:third message"), messages)
    }

    @Test fun rejectedAnnouncementRetainsItsMessage() {
        val queue = AnnouncementQueue()
        queue.offer("a", "Alex", "one", "hello")
        queue.drainOne { _, _ -> false }
        val messages = mutableListOf<String>()
        queue.drainOne { _, message -> messages.add(message); true }
        assertEquals(listOf("hello"), messages)
    }
}
