package com.smartparcel.gateway

import com.smartparcel.gateway.backend.EventDeduplicator
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class EventDeduplicatorTest {

    private lateinit var deduplicator: EventDeduplicator

    @Before
    fun setUp() {
        // Use a short 200ms window for unit test speed
        deduplicator = EventDeduplicator(suppressionWindowMillis = 200L)
    }

    @Test
    fun testFirstEventIsNotDuplicate() {
        val isDup = deduplicator.isDuplicate("PARCEL-001", "PARCEL_OPENED", 1L, "LOW")
        assertFalse("First event should not be marked as duplicate", isDup)
    }

    @Test
    fun testImmediateRepeatIsDuplicate() {
        deduplicator.isDuplicate("PARCEL-001", "PARCEL_OPENED", 1L, "LOW")
        val isDup = deduplicator.isDuplicate("PARCEL-001", "PARCEL_OPENED", 1L, "LOW")
        assertTrue("Immediate identical repeat should be suppressed", isDup)
    }

    @Test
    fun testDifferentCountIsNotDuplicate() {
        deduplicator.isDuplicate("PARCEL-001", "PARCEL_OPENED", 1L, "LOW")
        val isDup = deduplicator.isDuplicate("PARCEL-001", "PARCEL_OPENED", 2L, "LOW")
        assertFalse("Incremented open count must not be suppressed", isDup)
    }

    @Test
    fun testDifferentEventTypeIsNotDuplicate() {
        deduplicator.isDuplicate("PARCEL-001", "PARCEL_OPENED", 1L, "LOW")
        val isDup = deduplicator.isDuplicate("PARCEL-001", "PARCEL_CLOSED", 1L, "LOW")
        assertFalse("Different event type must not be suppressed", isDup)
    }

    @Test
    fun testExpiryAllowsSubsequentEvent() {
        deduplicator.isDuplicate("PARCEL-001", "PARCEL_OPENED", 1L, "LOW")
        Thread.sleep(250) // Wait past 200ms suppression window
        val isDup = deduplicator.isDuplicate("PARCEL-001", "PARCEL_OPENED", 1L, "LOW")
        assertFalse("Event after window expiration should be permitted", isDup)
    }
}
