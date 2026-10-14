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
        group: Boolean? = false,
        announceGroups: Boolean = false
    ) = WhatsAppNotificationFilter.shouldAnnounce(pkg, ongoing, summary, message, group, announceGroups)

    @Test fun permitsIndividualWhatsAppAndBusinessMessages() {
        assertTrue(allowed())
        assertTrue(allowed(pkg = "com.whatsapp.w4b"))
        assertTrue(allowed(announceGroups = true))
    }

    @Test fun groupsAreOffByDefaultAndCanBeEnabledForBothWhatsAppApps() {
        assertFalse(allowed(group = true))
        assertFalse(allowed(pkg = "com.whatsapp.w4b", group = true))
        assertTrue(allowed(group = true, announceGroups = true))
        assertTrue(allowed(pkg = "com.whatsapp.w4b", group = true, announceGroups = true))
    }

    @Test fun excludesUnknownConversationTypesRatherThanRiskAnnouncingGroups() {
        assertFalse(allowed(group = null))
        assertFalse(allowed(group = null, announceGroups = true))
    }

    @Test fun excludesSmsAndOtherAppsEvenWhenTheyAreIndividualMessages() {
        listOf("com.google.android.apps.messaging", "com.samsung.android.messaging",
            "com.android.mms", "com.android.messaging", "org.telegram.messenger").forEach {
            assertFalse(allowed(pkg = it))
            assertFalse(allowed(pkg = it, group = true, announceGroups = true))
        }
    }

    @Test fun excludesSummariesOngoingNotificationsAndNonMessages() {
        assertFalse(allowed(summary = true))
        assertFalse(allowed(ongoing = true))
        assertFalse(allowed(message = false))
        assertFalse(allowed(summary = true, group = true, announceGroups = true))
        assertFalse(allowed(ongoing = true, group = true, announceGroups = true))
        assertFalse(allowed(message = false, group = true, announceGroups = true))
    }
}
