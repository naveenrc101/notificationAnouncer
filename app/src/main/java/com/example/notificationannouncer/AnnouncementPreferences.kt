package com.example.notificationannouncer

import android.content.Context

internal object AnnouncementPreferences {
    private const val PREFS_NAME = "announcement_settings"
    private const val KEY_ANNOUNCE_GROUPS = "announce_groups"
    private const val KEY_ADDRESS_NAME = "address_name"
    private const val KEY_ADDRESS_TITLE = "address_title"

    fun announceGroups(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ANNOUNCE_GROUPS, false)

    fun setAnnounceGroups(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ANNOUNCE_GROUPS, enabled).apply()
    }

    fun address(context: Context): GreetingAddress? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val name = prefs.getString(KEY_ADDRESS_NAME, null)?.let(GreetingText::cleanedName)
            ?.takeIf { it.isNotBlank() } ?: return null
        val title = Honorific.values().firstOrNull {
            it.name == prefs.getString(KEY_ADDRESS_TITLE, null)
        } ?: return null
        return GreetingAddress(title, name)
    }

    fun setAddress(context: Context, address: GreetingAddress?) {
        val editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        if (address == null) {
            editor.remove(KEY_ADDRESS_NAME).remove(KEY_ADDRESS_TITLE)
        } else {
            val name = GreetingText.cleanedName(address.name)
            require(name.isNotBlank()) { "Name is required when saving an address" }
            editor.putString(KEY_ADDRESS_NAME, name)
                .putString(KEY_ADDRESS_TITLE, address.title.name)
        }
        editor.apply()
    }
}
