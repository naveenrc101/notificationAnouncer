package com.example.notificationannouncer

import android.app.Notification
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.ArrayDeque
import java.util.Locale

class NotificationAnnouncerService : NotificationListenerService() {
    private var tts: TextToSpeech? = null
    private val handler = Handler(Looper.getMainLooper())
    private val announcements = AnnouncementQueue()
    private var destroyed = false
    private var busy = false
    private var interaction = 0
    private var utteranceId: String? = null
    private var afterSpeech: (() -> Unit)? = null
    private val speechChunks = ArrayDeque<String>()
    private val speechTimeout = Runnable { failSpeech() }

    companion object {
        private const val TAG = "NotifAnnouncer"
        @Volatile var connected = false
            private set
        @Volatile var speechState = SpeechState.STARTING
            private set
        private val ALLOWED_PACKAGES = setOf(
            "com.whatsapp", "com.whatsapp.w4b", "com.android.mms",
            "com.google.android.apps.messaging", "com.samsung.android.messaging",
            "com.android.messaging", "com.verizon.messaging.vzmsgs", "com.att.messages"
        )
    }

    enum class SpeechState { STARTING, READY, ERROR }

    override fun onCreate() {
        super.onCreate()
        speechState = SpeechState.STARTING
        tts = TextToSpeech(this) { status ->
            handler.post { if (!destroyed) initializeSpeech(status) }
        }
    }

    private fun initializeSpeech(status: Int) {
        val engine = tts ?: return
        if (status != TextToSpeech.SUCCESS) {
            speechState = SpeechState.ERROR
            Log.e(TAG, "TTS initialization failed: " + status)
            return
        }
        var result = engine.setLanguage(Locale.getDefault())
        if (result < TextToSpeech.LANG_AVAILABLE) result = engine.setLanguage(Locale.ENGLISH)
        speechState = if (result >= TextToSpeech.LANG_AVAILABLE) SpeechState.READY else SpeechState.ERROR
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) {
                handler.post {
                    if (destroyed || id != utteranceId) return@post
                    handler.removeCallbacks(speechTimeout)
                    utteranceId = null
                    if (speechChunks.isNotEmpty()) {
                        speakNextChunk()
                    } else {
                        val completion = afterSpeech
                        afterSpeech = null
                        completion?.invoke()
                    }
                }
            }
            @Deprecated("Required by UtteranceProgressListener")
            override fun onError(id: String?) {
                handler.post {
                    if (!destroyed && id == utteranceId) failSpeech()
                }
            }
            override fun onStop(id: String?, interrupted: Boolean) {
                handler.post {
                    if (!destroyed && id == utteranceId) failSpeech()
                }
            }
        })
        flushPending()
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        connected = true
        flushPending()
    }

    override fun onListenerDisconnected() {
        connected = false
        cancelInteraction()
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName !in ALLOWED_PACKAGES || sbn.isOngoing) return
        val notification = sbn.notification
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val extras = notification.extras
        // MessagingStyle provides the newest message and sender without reading
        // a whole expanded conversation when the user says "read it".
        val messages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            extras.getParcelableArray(Notification.EXTRA_MESSAGES)
                ?.let { Notification.MessagingStyle.Message.getMessagesFromBundleArray(it) }
        } else null
        val latest = messages?.lastOrNull()
        val title = latest?.sender?.toString()?.trim()?.takeIf { it.isNotEmpty() }
            ?: extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim()
        val message = latest?.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }
            ?: bigText?.takeIf { it.isNotEmpty() } ?: text
        if (title.isEmpty() && message.isEmpty()) return
        val sender = title.ifEmpty { "an unknown sender" }
        val announcement = "Mr. Naveen, you have a message from " + sender + "."
        val identity = title.length.toString() + ":" + title + message +
            (latest?.timestamp?.let { ":" + it } ?: "")
        announcements.offer(sbn.key, announcement, identity, message)
        flushPending()
    }

    private fun flushPending() {
        if (destroyed || !connected || speechState != SpeechState.READY || busy) return
        announcements.drainOne { announcement, message ->
            busy = true
            val session = ++interaction
            speakText(announcement) {
                if (session == interaction && !destroyed && connected) {
                    val voice = VoiceCommandService.instance
                    if (voice == null) {
                        finishInteraction()
                    } else {
                        voice.listenForReadIt { read ->
                            if (session == interaction && !destroyed && connected) {
                                if (read) {
                                    speakText(message.ifBlank { "This notification has no readable message." }) {
                                        finishInteraction()
                                    }
                                } else {
                                    finishInteraction()
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun speakText(text: String, onComplete: () -> Unit): Boolean {
        afterSpeech = onComplete
        speechChunks.clear()
        // Each chunk stays below the TTS input limit; only the final chunk
        // completion opens the mic or advances the notification queue.
        text.chunked(TextToSpeech.getMaxSpeechInputLength() - 1).forEach { speechChunks.add(it) }
        return speakNextChunk()
    }

    private fun speakNextChunk(): Boolean {
        val engine = tts ?: return false
        val id = "notif_" + System.nanoTime()
        utteranceId = id
        val accepted = engine.speak(speechChunks.removeFirst(), TextToSpeech.QUEUE_ADD, null, id) ==
            TextToSpeech.SUCCESS
        if (accepted) {
            handler.postDelayed(speechTimeout, 180_000L)
        } else {
            failSpeech()
        }
        return accepted
    }

    private fun finishInteraction() {
        busy = false
        handler.post { flushPending() }
    }

    private fun failSpeech() {
        speechState = SpeechState.ERROR
        Log.e(TAG, "TTS failed or timed out")
        cancelInteraction()
    }

    private fun cancelInteraction() {
        ++interaction
        busy = false
        utteranceId = null
        afterSpeech = null
        speechChunks.clear()
        handler.removeCallbacks(speechTimeout)
        VoiceCommandService.instance?.cancelListening()
        tts?.stop()
    }

    override fun onDestroy() {
        destroyed = true
        connected = false
        cancelInteraction()
        speechState = SpeechState.STARTING
        handler.removeCallbacksAndMessages(null)
        announcements.clear()
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }
}
