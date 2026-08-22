package io.github.mhmdwaelanwr.eventcheckin.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckInRulesTest {
    @Test
    fun normalizeRegistrationId_trimsValidValue() {
        assertEquals("EVENT-42", CheckInRules.normalizeRegistrationId("  EVENT-42  "))
    }

    @Test
    fun normalizeRegistrationId_rejectsBlankControlCharactersAndOversizedValues() {
        assertNull(CheckInRules.normalizeRegistrationId("   "))
        assertNull(CheckInRules.normalizeRegistrationId("EVENT\n42"))
        assertNull(CheckInRules.normalizeRegistrationId("A".repeat(161)))
    }

    @Test
    fun duplicateMessages_supportExpectedServerVocabulary() {
        assertTrue(CheckInRules.isDuplicateMessage("Already checked in"))
        assertTrue(CheckInRules.isDuplicateMessage("Duplicate registration"))
        assertTrue(CheckInRules.isDuplicateMessage("مسجل مسبقا"))
        assertFalse(CheckInRules.isDuplicateMessage("Registration not found"))
    }

    @Test
    fun addPending_isIdempotentAndRejectsOverflow() {
        val current = setOf("EVENT-1")
        assertSame(current, CheckInRules.addPending(current, "EVENT-1"))
        assertEquals(setOf("EVENT-1", "EVENT-2"), CheckInRules.addPending(current, "EVENT-2"))

        val full = (1..CheckInRules.MAX_PENDING_CHECK_INS).map { "EVENT-$it" }.toSet()
        assertNull(CheckInRules.addPending(full, "EVENT-101"))
    }

    @Test
    fun scanDebounce_blocksOnlyTheSameRecentCode() {
        assertTrue(CheckInRules.shouldDebounceScan("EVENT-1", "EVENT-1", 2_999))
        assertFalse(CheckInRules.shouldDebounceScan("EVENT-2", "EVENT-1", 500))
        assertFalse(CheckInRules.shouldDebounceScan("EVENT-1", "EVENT-1", 3_000))
    }

    @Test
    fun pendingItem_isRemovedOnlyAfterAcceptanceOrDuplicate() {
        assertTrue(CheckInRules.shouldRemovePending(200, true, null))
        assertTrue(CheckInRules.shouldRemovePending(409, false, null))
        assertTrue(CheckInRules.shouldRemovePending(200, false, "Already registered"))
        assertFalse(CheckInRules.shouldRemovePending(500, false, "Server error"))
    }
}
