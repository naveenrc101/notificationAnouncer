package com.example.notificationannouncer

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WhatsAppNotificationFilterTest {
    private fun allowed(
        pkg: String = "com.whatsapp",
        ongoing: Boolean = false,
        summary: Boolean = false,
        message: Boolean = true,
        group: Boolean? = false
    ) = WhatsAppNotificationFilter.shouldAnnounce(pkg, ongoing, summary, message, group)

    @Test fun permitsIndividualWhatsAppAndBusinessMessages() {
        assertTrue(allowed())
        assertTrue(allowed(pkg = "com.whatsapp.w4b"))
    }

    @Test fun excludesGroupMessagesEvenWhenTheyAreNotSummaries() {
        assertFalse(allowed(group = true))
        assertFalse(allowed(pkg = "com.whatsapp.w4b", group = true))
    }

    @Test fun excludesUnknownConversationTypesRatherThanRiskAnnouncingGroups() {
        assertFalse(allowed(group = null))
    }

    @Test fun excludesSmsAndOtherAppsEvenWhenTheyAreIndividualMessages() {
        listOf("com.google.android.apps.messaging", "com.samsung.android.messaging",
            "com.android.mms", "com.android.messaging", "org.telegram.messenger").forEach {
            assertFalse(allowed(pkg = it))
        }
    }

    @Test fun excludesSummariesOngoingNotificationsAndNonMessages() {
        assertFalse(allowed(summary = true))
        assertFalse(allowed(ongoing = true))
        assertFalse(allowed(message = false))
    }
}
