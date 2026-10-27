package com.smartparcel.gateway

import com.smartparcel.gateway.location.LocationFix
import org.junit.Assert.*
import org.junit.Test

class LocationValidatorTest {

    @Test
    fun testValidCoordinatesPass() {
        val fix = LocationFix(
            latitude = 12.971598,
            longitude = 77.594562,
            accuracy = 8.5f,
            altitude = 920.0,
            speed = 1.2f,
            timestamp = System.currentTimeMillis()
        )
        assertTrue(fix.isValid())
    }

    @Test
    fun testNullIslandRejected() {
        val fix = LocationFix(
            latitude = 0.0,
            longitude = 0.0,
            accuracy = 10.0f,
            altitude = null,
            speed = null,
            timestamp = System.currentTimeMillis()
        )
        assertFalse("Coordinates (0.0, 0.0) must be rejected as invalid sentinel", fix.isValid())
    }

    @Test
    fun testOutOfBoundsLatitudeRejected() {
        val fix = LocationFix(
            latitude = 95.123,
            longitude = 77.0,
            accuracy = 10.0f,
            altitude = null,
            speed = null,
            timestamp = System.currentTimeMillis()
        )
        assertFalse(fix.isValid())
    }

    @Test
    fun testOutOfBoundsLongitudeRejected() {
        val fix = LocationFix(
            latitude = 12.0,
            longitude = 185.0,
            accuracy = 10.0f,
            altitude = null,
            speed = null,
            timestamp = System.currentTimeMillis()
        )
        assertFalse(fix.isValid())
    }

    @Test
    fun testStaleLocationDetection() {
        val oldTimestamp = System.currentTimeMillis() - 70_000L // 70s ago
        val fix = LocationFix(
            latitude = 12.97,
            longitude = 77.59,
            accuracy = 8.0f,
            altitude = null,
            speed = null,
            timestamp = oldTimestamp
        )
        assertTrue("Fix older than 60s must be flagged as stale", fix.isStale(60_000L))

        val freshFix = LocationFix(
            latitude = 12.97,
            longitude = 77.59,
            accuracy = 8.0f,
            altitude = null,
            speed = null,
            timestamp = System.currentTimeMillis() - 5_000L // 5s ago
        )
        assertFalse("Fix within 60s should not be stale", freshFix.isStale(60_000L))
    }
}
