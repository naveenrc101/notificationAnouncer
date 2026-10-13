package com.example.notificationannouncer

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadCommandTest {
    @Test fun acceptsExactCommandWithCaseSpacingAndPunctuation() {
        assertTrue(ReadCommand.matches("read it"))
        assertTrue(ReadCommand.matches("  READ   IT! "))
        assertTrue(ReadCommand.matches("Read it."))
    }

    @Test fun rejectsNegationsUnrelatedSpeechAndMissingResults() {
        listOf(null, "", "read", "don't read it", "do not read it",
            "please read it later", "credit", "read it now").forEach {
            assertFalse(ReadCommand.matches(it))
        }
    }
}
