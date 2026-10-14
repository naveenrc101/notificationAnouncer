package com.example.notificationannouncer

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.app.ServiceCompat
import java.util.Locale

/** User-enabled microphone foreground service. All session state stays on the main thread. */
class VoiceCommandService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val callbacks = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var completion: ((Boolean) -> Unit)? = null
    private var generation = 0
    private var ready = false
    private var captureEnded = false
    private var readyTone: ToneGenerator? = null
    val isListening: Boolean get() = completion != null

    companion object {
        private const val TAG = "VoiceCommands"
        private const val CHANNEL = "voice_commands"
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_STOP = "com.example.notificationannouncer.STOP_VOICE"
        var instance: VoiceCommandService? = null
            private set
        var status = "Voice commands are off."
            private set
        var lastResult = ""
            private set

        fun wanted(context: Context): Boolean {
            // Existing installations already granted this feature microphone
            // permission before the preference existed. Preserve that opt-in.
            val previouslyGranted = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
            return context.getSharedPreferences("voice_mode", Context.MODE_PRIVATE)
                .getBoolean("enabled", previouslyGranted)
        }

        fun setWanted(context: Context, enabled: Boolean) {
            context.getSharedPreferences("voice_mode", Context.MODE_PRIVATE)
                .edit().putBoolean("enabled", enabled).apply()
        }

        fun reportNotRunning() {
            lastResult = "No microphone window: open the app and enable voice commands."
            status = lastResult
            Log.w(TAG, "Announcement completed while voice mode was not running")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            setWanted(this, false)
            if (instance === this) instance = null
            finish(false, "Voice commands stopped.")
            stopSelf()
            return START_NOT_STICKY
        }
        if (instance === this) return START_NOT_STICKY
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            status = "Microphone permission is needed. Enable voice commands again."
            stopSelf()
            return START_NOT_STICKY
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            status = "No system speech recognizer. Enable or update your speech recognition app."
            stopSelf()
            return START_NOT_STICKY
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Voice commands", NotificationManager.IMPORTANCE_LOW))
        try {
            status = "Enabled. Wait for the beep, then say read it."
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0)
            instance = this
            setWanted(this, true)
            Log.i(TAG, "Microphone foreground mode started")
        } catch (error: RuntimeException) {
            status = "Could not enable microphone mode. Keep the app open and try again."
            Log.e(TAG, "Foreground mode could not start", error)
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
            .setContentTitle("Voice commands")
            .setContentText(status)
            .setStyle(Notification.BigTextStyle().bigText(status))
            .setContentIntent(open)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .addAction(Notification.Action.Builder(null, "Stop voice commands", stop).build())
            .build()
    }

    private fun showStatus(value: String) {
        status = value
        if (instance === this && (Build.VERSION.SDK_INT < 33 ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)) {
            runCatching { getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification()) }
        }
    }

    fun listenForReadIt(onComplete: (Boolean) -> Unit) {
        if (instance !== this || completion != null) {
            lastResult = "Microphone test skipped: voice mode is off or another message is being handled."
            callbacks.post { onComplete(false) }
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            lastResult = "Microphone permission was removed. Enable voice commands again."
            showStatus(lastResult)
            callbacks.post { onComplete(false) }
            stopSelf()
            return
        }
        completion = onComplete
        ready = false
        captureEnded = false
        val session = ++generation
        showStatus("Preparing microphone. Wait for the beep.")
        Log.i(TAG, "Starting recognition session " + session)
        try {
            // Let the configured system recognizer select an available engine.
            // On-device service availability does NOT imply an en-IN model exists.
            recognizer = SpeechRecognizer.createSpeechRecognizer(this)
            recognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    if (session != generation || ready) return
                    ready = true
                    handler.removeCallbacksAndMessages(null)
                    showStatus("Listening now: say read it within four seconds.")
                    Log.i(TAG, "Microphone ready for session " + session)
                    runCatching {
                        readyTone = ToneGenerator(AudioManager.STREAM_MUSIC, 60)
                        readyTone?.startTone(ToneGenerator.TONE_PROP_BEEP, 80)
                    }
                    handler.postDelayed({ readyTone?.release(); readyTone = null }, 100)
                    handler.postDelayed({
                        if (session == generation) endCapture(session)
                    }, ReadCommand.LISTEN_MILLIS)
                }
                override fun onResults(results: Bundle?) {
                    if (session != generation) return
                    val best = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    val matched = ReadCommand.matches(best)
                    // A final result is valid even if a provider omitted onReady.
                    // Generation prevents accepting results from cancelled sessions.
                    finish(matched, if (matched) "Recognized read it."
                        else "Did not recognize read it. Try Test microphone and speak after the beep.")
                }
                override fun onError(error: Int) {
                    if (session != generation) return
                    Log.w(TAG, "Recognition failed: " + error)
                    val detail = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                            "No command heard. Say read it after the beep."
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                            "Microphone denied. Check app permission, the microphone privacy switch and speech app permissions."
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                            "Speech recognition is busy. Close other microphone apps and try Test microphone."
                        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
                            "English speech recognition is unavailable. Enable English in your system speech recognition settings."
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                            "Speech recognition needs a connection or an installed offline English model."
                        SpeechRecognizer.ERROR_AUDIO ->
                            "Could not record audio. Check the microphone privacy switch and other microphone apps."
                        else -> "Speech recognition failed (code " + error + "). Try Test microphone."
                    }
                    finish(false, detail)
                }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    if (session != generation || captureEnded) return
                    captureEnded = true
                    waitForResults(session)
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            handler.postDelayed({
                if (session == generation) finish(false,
                    "Speech recognizer did not become ready. Check the system speech app and try Test microphone.")
            }, ReadCommand.STARTUP_TIMEOUT_MILLIS)
            recognizer?.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                // Do not pin every Pixel to en-IN; use its configured English
                // locale, or English (US) when the device language is not English.
                val locale = Locale.getDefault()
                putExtra(RecognizerIntent.EXTRA_LANGUAGE,
                    if (locale.language == "en") locale.toLanguageTag() else "en-US")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                // Do not force offline-only recognition: missing local models
                // must not prevent the provider from using its normal engine.
            })
        } catch (error: RuntimeException) {
            Log.e(TAG, "Could not start recognition", error)
            finish(false, "Could not open speech recognition. Check microphone and system speech settings.")
        }
    }

    private fun waitForResults(session: Int) {
        handler.removeCallbacksAndMessages(null)
        readyTone?.release()
        readyTone = null
        showStatus("Microphone closed. Checking the command...")
        handler.postDelayed({
            if (session == generation) finish(false,
                "Speech recognition timed out after recording. Check connectivity and try Test microphone.")
        }, ReadCommand.RESULT_TIMEOUT_MILLIS)
    }

    private fun endCapture(session: Int) {
        if (captureEnded) return
        captureEnded = true
        // Arm the result timeout before stopListening, so synchronous provider
        // callbacks cannot be overwritten by a stale timer.
        waitForResults(session)
        try {
            recognizer?.stopListening()
        } catch (error: RuntimeException) {
            finish(false, "Speech recognition could not finish.")
        }
    }

    fun cancelListening() {
        if (completion != null) finish(false, "Listening cancelled.")
    }

    private fun finish(read: Boolean, detail: String) {
        ++generation
        handler.removeCallbacksAndMessages(null)
        val callback = completion
        completion = null
        val oldRecognizer = recognizer
        recognizer = null
        runCatching { oldRecognizer?.cancel() }
        runCatching { oldRecognizer?.destroy() }
        readyTone?.release()
        readyTone = null
        lastResult = detail
        showStatus(if (instance === this) "Enabled. " + detail else "Voice commands are off.")
        Log.i(TAG, "Recognition ended; read command=" + read)
        if (callback != null) callbacks.post { callback(read && instance === this) }
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        if (completion != null) finish(false, "Voice mode stopped during listening. Reopen the app to restart.")
        else {
            runCatching { recognizer?.destroy() }
            recognizer = null
            readyTone?.release()
            readyTone = null
            handler.removeCallbacksAndMessages(null)
            if (status.startsWith("Enabled") || status.startsWith("Listening")) status = "Voice commands are off."
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        Log.i(TAG, "Microphone foreground mode stopped")
        super.onDestroy()
    }
}
