package com.example.notificationannouncer

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import com.example.notificationannouncer.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnGrantPermission.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun isNotificationServiceEnabled(): Boolean {
        val cn = ComponentName(this, NotificationAnnouncerService::class.java)
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return flat?.contains(cn.flattenToString()) == true
    }

    private fun updateStatus() {
        val enabled = isNotificationServiceEnabled()
        if (enabled) {
            binding.tvStatus.text = getString(R.string.status_active)
            binding.tvStatusDetail.text = getString(R.string.status_active_detail)
            binding.statusCard.setCardBackgroundColor(getColor(R.color.status_active))
            binding.btnGrantPermission.text = getString(R.string.permission_granted)
            binding.btnGrantPermission.isEnabled = false
        } else {
            binding.tvStatus.text = getString(R.string.status_inactive)
            binding.tvStatusDetail.text = getString(R.string.status_inactive_detail)
            binding.statusCard.setCardBackgroundColor(getColor(R.color.status_inactive))
            binding.btnGrantPermission.text = getString(R.string.grant_permission)
            binding.btnGrantPermission.isEnabled = true
        }
    }
}
