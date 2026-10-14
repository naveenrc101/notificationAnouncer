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
import androidx.core.app.NotificationCompat
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentifier
import java.util.ArrayDeque
import java.util.Locale

class NotificationAnnouncerService : NotificationListenerService() {
    private var tts: TextToSpeech? = null
    private var languageIdentifier: LanguageIdentifier? = null
    private var detectionTimeout: Runnable? = null
    private val handler = Handler(Looper.getMainLooper())
    private val announcements = AnnouncementQueue()
    private var destroyed = false
    private var busy = false
        set(value) {
            field = value
            handlingMessage = value
        }
    private val retryPending = Runnable { flushPending() }
    private var interaction = 0
    private var utteranceId: String? = null
    private var afterSpeech: (() -> Unit)? = null
    private val speechChunks = ArrayDeque<String>()
    private val speechTimeout = Runnable { failSpeech() }

    companion object {
        private const val TAG = "NotifAnnouncer"
        var languageStatus = "Message language: automatic."
            private set
        var handlingMessage = false
            private set
        @Volatile var connected = false
            private set
        @Volatile var speechState = SpeechState.STARTING
            private set

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
        val notification = sbn.notification
        val extras = notification.extras
        // A summary bundles notifications; it is not the same as a group chat.
        // Use the explicit conversation flag. Missing metadata is not proof of a
        // conversation type, so skip it instead of guessing from a sender/title.
        val groupConversation = if (extras.containsKey(NotificationCompat.EXTRA_IS_GROUP_CONVERSATION)) {
            extras.getBoolean(NotificationCompat.EXTRA_IS_GROUP_CONVERSATION)
        } else null
        if (!WhatsAppNotificationFilter.shouldAnnounce(
                packageName = sbn.packageName,
                ongoing = sbn.isOngoing,
                summary = notification.flags and Notification.FLAG_GROUP_SUMMARY != 0,
                messageNotification = notification.category == Notification.CATEGORY_MESSAGE ||
                    extras.containsKey(Notification.EXTRA_MESSAGES),
                groupConversation = groupConversation,
                announceGroups = AnnouncementPreferences.announceGroups(this)
            )) return
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
        val announcement = "Mister Naveen, you have a message from " + sender +
            ". Would you like me to read it?"
        val identity = title.length.toString() + ":" + title + message +
            (latest?.timestamp?.let { ":" + it } ?: "")
        announcements.offer(sbn.key, announcement, identity, message)
        flushPending()
    }

    private fun flushPending() {
        if (destroyed || !connected || speechState != SpeechState.READY || busy) return
        // A user-initiated microphone test must finish before any TTS starts.
        if (VoiceCommandService.instance?.isListening == true) {
            handler.removeCallbacks(retryPending)
            handler.postDelayed(retryPending, 200)
            return
        }
        announcements.drainOne { announcement, message ->
            busy = true
            val session = ++interaction
            if (!useGreetingLanguage()) {
                failSpeech()
                return@drainOne false
            }
            speakText(announcement) {
                if (session == interaction && !destroyed && connected) {
                    val voice = VoiceCommandService.instance
                    Log.i(TAG, "Announcement finished; voice mode available=" + (voice != null))
                    if (voice == null) {
                        VoiceCommandService.reportNotRunning()
                        finishInteraction()
                    } else {
                        voice.listenForReadIt { read ->
                            if (session == interaction && !destroyed && connected) {
                                if (read) {
                                    readMessage(message, session)
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

    private fun useGreetingLanguage(): Boolean {
        val engine = tts ?: return false
        val device = Locale.getDefault()
        val english = if (device.language == "en") device else Locale.ENGLISH
        return MessageLanguage.select("en", english) {
            engine.setLanguage(it) >= TextToSpeech.LANG_AVAILABLE
        } != null || engine.setLanguage(device) >= TextToSpeech.LANG_AVAILABLE
    }

    private fun readMessage(message: String, session: Int) {
        if (message.isBlank()) {
            speakText("This notification has no readable message.") { finishInteraction() }
            return
        }
        var completed = false
        fun complete(tag: String?) {
            if (completed || destroyed || !connected || session != interaction) return
            completed = true
            detectionTimeout?.let { handler.removeCallbacks(it) }
            detectionTimeout = null
            val engine = tts ?: return
            val selected = MessageLanguage.select(tag, Locale.getDefault()) {
                engine.setLanguage(it) >= TextToSpeech.LANG_AVAILABLE
            }
            if (selected == null) {
                failSpeech()
                return
            }
            languageStatus = when {
                tag == null || tag == "und" ->
                    "Language uncertain; reading in " + selected.displayLanguage + "."
                selected.language != Locale.forLanguageTag(tag).language ->
                    "Voice unavailable for " + Locale.forLanguageTag(tag).displayLanguage +
                        "; using " + selected.displayLanguage + ". Install the voice in device text-to-speech settings."
                else -> "Reading messages in " + selected.displayLanguage + "."
            }
            speakText(message) { finishInteraction() }
        }
        languageStatus = "Detecting message language..."
        val timeout = Runnable { complete(null) }
        detectionTimeout = timeout
        handler.postDelayed(timeout, 3_000L)
        try {
            val identifier = languageIdentifier ?: LanguageIdentification.getClient().also {
                languageIdentifier = it
            }
            identifier.identifyLanguage(message)
                .addOnSuccessListener { complete(it) }
                .addOnFailureListener { complete(null) }
        } catch (error: RuntimeException) {
            complete(null)
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
        detectionTimeout?.let { handler.removeCallbacks(it) }
        detectionTimeout = null
        busy = false
        utteranceId = null
        afterSpeech = null
        speechChunks.clear()
        handler.removeCallbacks(speechTimeout)
        handler.removeCallbacks(retryPending)
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
        languageIdentifier?.close()
        languageIdentifier = null
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }
}
