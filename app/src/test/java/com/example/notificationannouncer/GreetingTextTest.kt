package com.example.notificationannouncer

import org.junit.Assert.assertEquals
import org.junit.Test

class GreetingTextTest {
    @Test fun noNameUsesNeutralGreeting() {
        assertEquals(
            "You have a message from Alex. Would you like me to read it?",
            GreetingText.announcement(null, "Alex")
        )
    }

    @Test fun titlesAreSpokenNaturally() {
        val expected = mapOf(
            Honorific.MR to "Mister",
            Honorific.MRS to "Missus",
            Honorific.MS to "Miz"
        )
        expected.forEach { (title, spoken) ->
            assertEquals(
                "$spoken Taylor, you have a message from Alex. Would you like me to read it?",
                GreetingText.announcement(GreetingAddress(title, "Taylor"), "Alex")
            )
        }
    }

    @Test fun whitespaceOnlyNameDoesNotGetSpoken() {
        assertEquals(
            "You have a message from Alex. Would you like me to read it?",
            GreetingText.announcement(GreetingAddress(Honorific.MR, "  "), "Alex")
        )
        assertEquals("A B", GreetingText.cleanedName("  A\n  B  "))
    }
}
