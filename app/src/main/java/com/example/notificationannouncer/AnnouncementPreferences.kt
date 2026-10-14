package com.example.notificationannouncer

import android.content.Context

internal object AnnouncementPreferences {
    private const val PREFS_NAME = "announcement_settings"
    private const val KEY_ANNOUNCE_GROUPS = "announce_groups"

    fun announceGroups(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ANNOUNCE_GROUPS, false)

    fun setAnnounceGroups(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ANNOUNCE_GROUPS, enabled).apply()
    }
}
