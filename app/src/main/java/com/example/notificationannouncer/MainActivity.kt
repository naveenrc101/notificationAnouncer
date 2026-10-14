package com.example.notificationannouncer

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.res.ColorStateList
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import androidx.appcompat.app.AlertDialog
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.notificationannouncer.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val handler = Handler(Looper.getMainLooper())
    private var resumed = false
    private var startWhenResumed = false
    private var voiceStartPending = false
    private var voiceStartTime = 0L
    private var voiceError: String? = null
    private var previewTts: TextToSpeech? = null
    private var englishVoices: List<Voice> = emptyList()
    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            if (resumed) startVoiceCommands() else startWhenResumed = true
        } else {
            voiceError = "Microphone permission was denied. Allow it in App info > Permissions to use voice commands."
        }
    }
    private val refreshStatus = object : Runnable {
        override fun run() {
            updateStatus()
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.btnGrantPermission.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        binding.switchGroupNotifications.isChecked = AnnouncementPreferences.announceGroups(this)
        binding.switchGroupNotifications.setOnCheckedChangeListener { _, enabled ->
            AnnouncementPreferences.setAnnounceGroups(this, enabled)
        }
        binding.btnChooseVoice.isEnabled = false
        binding.btnPreviewVoice.isEnabled = false
        previewTts = TextToSpeech(this) { status ->
            handler.post {
                if (!isFinishing && !isDestroyed) {
                    if (status == TextToSpeech.SUCCESS) loadEnglishVoices()
                    else binding.tvSelectedVoice.setText(R.string.voice_unavailable)
                }
            }
        }
        binding.btnChooseVoice.setOnClickListener {
            val labels = arrayOf(getString(R.string.voice_recommended)) +
                englishVoices.map { it.locale.displayName + " · " + it.name }
            AlertDialog.Builder(this)
                .setTitle(R.string.choose_english_voice)
                .setItems(labels) { _, index ->
                    EnglishVoice.select(
                        this, if (index == 0) null else englishVoices[index - 1].name
                    )
                    updateEnglishVoiceLabel()
                    previewEnglishVoice()
                }
                .show()
        }
        binding.btnPreviewVoice.setOnClickListener { previewEnglishVoice() }
        binding.btnTestMicrophone.setOnClickListener {
            if (!NotificationAnnouncerService.handlingMessage) {
                VoiceCommandService.instance?.listenForReadIt {
                    if (resumed) updateStatus()
                }
            }
        }
        binding.btnVoiceCommands.setOnClickListener {
            voiceError = null
            if (VoiceCommandService.instance != null) {
                VoiceCommandService.setWanted(this, false)
                startWhenResumed = false
                stopService(Intent(this, VoiceCommandService::class.java))
            } else {
                VoiceCommandService.setWanted(this, true)
                val needed = mutableListOf<String>()
                if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    needed.add(Manifest.permission.RECORD_AUDIO)
                }
                if (Build.VERSION.SDK_INT >= 33 &&
                    checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    needed.add(Manifest.permission.POST_NOTIFICATIONS)
                }
                if (needed.isEmpty()) startVoiceCommands()
                else permissions.launch(needed.toTypedArray())
            }
        }
    }

    private fun loadEnglishVoices() {
        val engine = previewTts ?: return
        englishVoices = EnglishVoice.available(engine)
        binding.btnChooseVoice.isEnabled = englishVoices.isNotEmpty()
        binding.btnPreviewVoice.isEnabled = englishVoices.isNotEmpty()
        updateEnglishVoiceLabel()
    }

    private fun updateEnglishVoiceLabel() {
        val engine = previewTts ?: return
        val voice = EnglishVoice.preferred(this, engine)
        binding.tvSelectedVoice.text = if (voice == null) {
            getString(R.string.voice_unavailable)
        } else {
            getString(R.string.voice_selected, voice.locale.displayName, voice.name)
        }
    }

    private fun previewEnglishVoice() {
        if (NotificationAnnouncerService.handlingMessage) return
        val engine = previewTts ?: return
        if (EnglishVoice.apply(this, engine)) {
            engine.speak(getString(R.string.voice_preview_text),
                TextToSpeech.QUEUE_FLUSH, null, "voice_preview")
        }
    }

    private fun startVoiceCommands() {
        if (voiceStartPending || VoiceCommandService.instance != null) return
        if (!resumed) {
            startWhenResumed = true
            return
        }
        try {
            ContextCompat.startForegroundService(this, Intent(this, VoiceCommandService::class.java))
            voiceStartPending = true
            voiceStartTime = android.os.SystemClock.elapsedRealtime()
        } catch (error: RuntimeException) {
            voiceError = "Could not start voice commands. Keep the app open and try again."
        }
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        val shouldRestore = VoiceCommandService.wanted(this) &&
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (startWhenResumed || shouldRestore) {
            startWhenResumed = false
            startVoiceCommands()
        }
        handler.post(refreshStatus)
    }

    override fun onDestroy() {
        previewTts?.stop()
        previewTts?.shutdown()
        previewTts = null
        super.onDestroy()
    }

    override fun onPause() {
        resumed = false
        handler.removeCallbacks(refreshStatus)
        super.onPause()
    }

    private fun isNotificationServiceEnabled(): Boolean {
        val component = ComponentName(this, NotificationAnnouncerService::class.java)
        val listeners = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return listeners?.split(':')?.any {
            ComponentName.unflattenFromString(it) == component
        } == true
    }

    private fun updateStatus() {
        val enabled = isNotificationServiceEnabled()
        val connected = NotificationAnnouncerService.connected
        val speech = NotificationAnnouncerService.speechState
        val ready = enabled && connected &&
            speech == NotificationAnnouncerService.SpeechState.READY
        val (title, detail) = when {
            !enabled -> R.string.status_inactive to R.string.status_inactive_detail
            !connected -> R.string.status_connecting to R.string.status_connecting_detail
            speech == NotificationAnnouncerService.SpeechState.ERROR ->
                R.string.status_tts_error to R.string.status_tts_error_detail
            !ready -> R.string.status_starting to R.string.status_starting_detail
            else -> R.string.status_active to R.string.status_active_detail
        }
        binding.tvInfo.text = getString(R.string.info_text) + "\n\n" + NotificationAnnouncerService.languageStatus
        binding.tvStatus.setText(title)
        binding.tvStatusDetail.setText(detail)
        binding.statusDot.backgroundTintList = ColorStateList.valueOf(getColor(
            if (ready) R.color.status_active else R.color.status_inactive))
        binding.btnGrantPermission.setText(
            if (enabled) R.string.permission_granted else R.string.grant_permission)
        binding.btnGrantPermission.isEnabled = true
        val voiceEnabled = VoiceCommandService.instance != null
        if (voiceEnabled || android.os.SystemClock.elapsedRealtime() - voiceStartTime > 5_000) {
            voiceStartPending = false
        }
        binding.btnVoiceCommands.setText(
            if (voiceEnabled) R.string.disable_voice_commands else R.string.enable_voice_commands)
        binding.btnVoiceCommands.isEnabled = !voiceStartPending
        binding.btnTestMicrophone.isEnabled = voiceEnabled && !voiceStartPending &&
            VoiceCommandService.instance?.isListening != true && !NotificationAnnouncerService.handlingMessage
        binding.tvVoiceStatus.text = voiceError ?: if (voiceStartPending)
            "Starting voice commands..." else VoiceCommandService.status
    }
}
