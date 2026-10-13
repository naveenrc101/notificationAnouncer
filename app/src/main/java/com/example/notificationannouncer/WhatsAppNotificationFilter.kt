package com.example.notificationannouncer

/** Only positively identified one-to-one WhatsApp message notifications are spoken. */
internal object WhatsAppNotificationFilter {
    fun shouldAnnounce(
        packageName: String,
        ongoing: Boolean,
        summary: Boolean,
        messageNotification: Boolean,
        groupConversation: Boolean?
    ): Boolean {
        return packageName in setOf("com.whatsapp", "com.whatsapp.w4b") &&
            !ongoing && !summary && messageNotification && groupConversation == false
    }
}
