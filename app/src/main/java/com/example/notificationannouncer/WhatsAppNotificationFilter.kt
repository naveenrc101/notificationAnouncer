package com.example.notificationannouncer

/** Only positively identified WhatsApp conversations selected by the user are spoken. */
internal object WhatsAppNotificationFilter {
    fun shouldAnnounce(
        packageName: String,
        ongoing: Boolean,
        summary: Boolean,
        messageNotification: Boolean,
        groupConversation: Boolean?,
        announceGroups: Boolean
    ): Boolean {
        return packageName in setOf("com.whatsapp", "com.whatsapp.w4b") &&
            !ongoing && !summary && messageNotification &&
            (groupConversation == false || (groupConversation == true && announceGroups))
    }
}
