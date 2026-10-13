package com.example.notificationannouncer

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

class NotificationAnnouncerService : NotificationListenerService() {

    private lateinit var tts: TextToSpeech
    private var isTtsReady = false

    companion object {
        private const val TAG = "NotifAnnouncer"

        private val WHATSAPP_PACKAGES = setOf(
            "com.whatsapp",       // WhatsApp
            "com.whatsapp.w4b"    // WhatsApp Business
        )

        private val SMS_PACKAGES = setOf(
            "com.android.mms",
            "com.google.android.apps.messaging",   // Google Messages
            "com.samsung.android.messaging",        // Samsung Messages
            "com.android.messaging",                // AOSP Messaging
            "com.verizon.messaging.vzmsgs",
            "com.att.messages"
        )

        private val ALLOWED_PACKAGES = WHATSAPP_PACKAGES + SMS_PACKAGES
    }

    override fun onCreate() {
        super.onCreate()
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts.setLanguage(Locale.getDefault())
                isTtsReady = result != TextToSpeech.LANG_MISSING_DATA &&
                        result != TextToSpeech.LANG_NOT_SUPPORTED
                if (!isTtsReady) {
                    val fallback = tts.setLanguage(Locale.ENGLISH)
                    isTtsReady = fallback != TextToSpeech.LANG_MISSING_DATA &&
                            fallback != TextToSpeech.LANG_NOT_SUPPORTED
                }
            } else {
                Log.e(TAG, "TTS init failed: $status")
            }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName

        if (packageName !in ALLOWED_PACKAGES) return
        if (sbn.isOngoing) return

        val notification = sbn.notification ?: return

        // Skip group summary notifications (they bundle multiple messages)
        val isGroupSummary = notification.flags and Notification.FLAG_GROUP_SUMMARY != 0
        if (isGroupSummary) return

        val extras = notification.extras
        val title = extras.getString(Notification.EXTRA_TITLE)?.trim() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim() ?: ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim()

        // Prefer big text (expanded), fall back to regular text
        val messageText = bigText?.takeIf { it.isNotEmpty() } ?: text

        if (title.isEmpty() && messageText.isEmpty()) return

        val announcement = buildAnnouncement(packageName, title, messageText)
        Log.d(TAG, "Announcing: $announcement")
        speak(announcement)
    }

    private fun buildAnnouncement(packageName: String, title: String, text: String): String {
        val source = when (packageName) {
            "com.whatsapp" -> "WhatsApp"
            "com.whatsapp.w4b" -> "WhatsApp Business"
            else -> "Message"
        }
        return buildString {
            append("$source. ")
            if (title.isNotEmpty()) append("$title. ")
            if (text.isNotEmpty()) append(text)
        }.trim()
    }

    private fun speak(text: String) {
        if (!isTtsReady) {
            Log.w(TAG, "TTS not ready, skipping")
            return
        }
        tts.speak(text, TextToSpeech.QUEUE_ADD, null, "notif_${System.currentTimeMillis()}")
    }

    override fun onDestroy() {
        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
        super.onDestroy()
    }
}
