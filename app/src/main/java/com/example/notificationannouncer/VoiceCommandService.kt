package com.example.notificationannouncer

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.app.ServiceCompat

/**
 * Started explicitly from the visible activity so microphone access remains
 * eligible when the user leaves the app. It records only during command windows.
 * All service, recognizer and callback state is confined to the main thread.
 */
class VoiceCommandService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var completion: ((Boolean) -> Unit)? = null
    private var generation = 0
    private var ready = false
    private var captureEnded = false

    companion object {
        private const val CHANNEL = "voice_commands"
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_STOP = "com.example.notificationannouncer.STOP_VOICE"
        var instance: VoiceCommandService? = null
            private set
        var status = "Voice commands are off."
            private set
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            if (instance === this) instance = null
            finish(false)
            stopSelf()
            return START_NOT_STICKY
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            status = "Microphone permission is needed. Enable voice commands again."
            stopSelf()
            return START_NOT_STICKY
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            status = "No speech recognition service is available on this device."
            stopSelf()
            return START_NOT_STICKY
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(
            CHANNEL, "Voice commands", NotificationManager.IMPORTANCE_LOW))
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0)
            instance = this
            status = "Enabled. Say â€œread itâ€ within two seconds after an announcement."
        } catch (error: RuntimeException) {
            status = "Could not enable microphone mode. Open the app and try again."
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun notification(): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1,
            Intent(this, VoiceCommandService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_mic)
            .setContentTitle("Voice commands enabled")
            .setContentText("Listens for two seconds after each announcement.")
            .setContentIntent(open)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .addAction(Notification.Action.Builder(null, "Stop voice commands", stop).build())
            .build()
    }

    fun listenForReadIt(onComplete: (Boolean) -> Unit) {
        if (instance !== this || completion != null) {
            handler.post { onComplete(false) }
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            status = "Microphone permission was removed. Enable voice commands again."
            handler.post { onComplete(false) }
            stopSelf()
            return
        }
        completion = onComplete
        ready = false
        captureEnded = false
        val session = ++generation
        status = "Preparing the microphoneâ€¦"
        try {
            recognizer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(this)
            } else {
                SpeechRecognizer.createSpeechRecognizer(this)
            }
            recognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    if (session != generation || ready) return
                    ready = true
                    handler.removeCallbacksAndMessages(null)
                    status = "Listening for â€œread itâ€â€¦"
                    // A timer enforces the capture window; recognizer silence
                    // extras are only hints and can be ignored by providers.
                    handler.postDelayed({
                        if (session == generation) endCapture(session)
                    }, ReadCommand.LISTEN_MILLIS)
                }
                override fun onResults(results: Bundle?) {
                    if (session != generation) return
                    val best = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                    finish(ready && ReadCommand.matches(best))
                }
                override fun onError(error: Int) {
                    if (session != generation) return
                    val detail = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> null
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                            "Microphone access denied. Check the app permission and microphone privacy switch."
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                            "Microphone or speech recognizer is busy. Will retry after the next announcement."
                        else -> "Speech recognition failed (code " + error +
                            "). Check speech recognition language/downloads and connectivity."
                    }
                    finish(false, detail)
                }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    if (session != generation) return
                    captureEnded = true
                    // Providers may finish early. Bound the wait for their final result.
                    handler.removeCallbacksAndMessages(null)
                    handler.postDelayed({
                        if (session == generation) finish(false)
                    }, ReadCommand.RESULT_TIMEOUT_MILLIS)
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            handler.postDelayed({
                if (session == generation) finish(false, "Speech recognizer did not become ready.")
            }, ReadCommand.STARTUP_TIMEOUT_MILLIS)
            recognizer?.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            })
        } catch (error: RuntimeException) {
            finish(false, "Could not open speech recognition. Check microphone and speech settings.")
        }
    }

    private fun endCapture(session: Int) {
        if (captureEnded) return
        captureEnded = true
        status = "Checking the voice commandâ€¦"
        try {
            recognizer?.stopListening()
            handler.postDelayed({
                if (session == generation) finish(false)
            }, ReadCommand.RESULT_TIMEOUT_MILLIS)
        } catch (error: RuntimeException) {
            finish(false, "Speech recognition could not finish.")
        }
    }

    fun cancelListening() {
        finish(false)
    }

    private fun finish(read: Boolean, detail: String? = null) {
        ++generation // Invalidate callbacks from the old recognizer before destroying it.
        handler.removeCallbacksAndMessages(null)
        val callback = completion
        completion = null
        val oldRecognizer = recognizer
        recognizer = null
        runCatching { oldRecognizer?.cancel() }
        runCatching { oldRecognizer?.destroy() }
        status = detail ?: if (instance === this)
            "Enabled. Say â€œread itâ€ within two seconds after an announcement."
            else "Voice commands are off."
        // Return asynchronously so the microphone has been closed before TTS resumes.
        if (callback != null) handler.post { callback(read) }
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        val previousStatus = status
        finish(false)
        if (previousStatus.startsWith("Could not") || previousStatus.startsWith("No speech") ||
            previousStatus.startsWith("Microphone permission")) status = previousStatus
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
}
