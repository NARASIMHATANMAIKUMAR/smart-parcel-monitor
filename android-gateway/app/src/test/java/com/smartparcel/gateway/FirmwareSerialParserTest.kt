package com.smartparcel.gateway

import com.smartparcel.gateway.data.FirmwareMessage
import com.smartparcel.gateway.parser.FirmwareSerialParser
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class FirmwareSerialParserTest {

    private lateinit var parser: FirmwareSerialParser

    @Before
    fun setUp() {
        parser = FirmwareSerialParser()
    }

    @Test
    fun testParseParcelOpenedEvent() {
        val line = "EVENT=PARCEL_OPENED,OPEN_COUNT=3,SHAKE=MEDIUM"
        val msg = parser.parseLine(line)

        assertTrue(msg is FirmwareMessage.EventMessage)
        val event = msg as FirmwareMessage.EventMessage
        assertEquals("PARCEL_OPENED", event.eventType)
        assertEquals(3L, event.openCount)
        assertEquals("MEDIUM", event.shakeLevel)
    }

    @Test
    fun testParseParcelClosedEvent() {
        val line = "EVENT=PARCEL_CLOSED,OPEN_COUNT=3,SHAKE=LOW"
        val msg = parser.parseLine(line)

        assertTrue(msg is FirmwareMessage.EventMessage)
        val event = msg as FirmwareMessage.EventMessage
        assertEquals("PARCEL_CLOSED", event.eventType)
        assertEquals(3L, event.openCount)
        assertEquals("LOW", event.shakeLevel)
    }

    @Test
    fun testParseShakeLevelChangedEvent() {
        val line = "EVENT=SHAKE_LEVEL_CHANGED,SHAKE=INTENSE,OPEN_COUNT=2"
        val msg = parser.parseLine(line)

        assertTrue(msg is FirmwareMessage.EventMessage)
        val event = msg as FirmwareMessage.EventMessage
        assertEquals("SHAKE_LEVEL_CHANGED", event.eventType)
        assertEquals(2L, event.openCount)
        assertEquals("INTENSE", event.shakeLevel)
    }

    @Test
    fun testParsePeriodicStatusTelemetry() {
        val line = "STATUS=SECURE,REED=CLOSED,OPEN_COUNT=0,SHAKE=LOW,AX=140,AY=-280,AZ=16380,MPU=OK"
        val msg = parser.parseLine(line)

        assertTrue(msg is FirmwareMessage.StatusTelemetry)
        val status = msg as FirmwareMessage.StatusTelemetry
        assertEquals("SECURE", status.status)
        assertEquals("CLOSED", status.reedState)
        assertEquals(0L, status.openCount)
        assertEquals("LOW", status.shakeLevel)
        assertEquals(140, status.ax)
        assertEquals(-280, status.ay)
        assertEquals(16380, status.az)
        assertEquals("OK", status.mpuStatus)
    }

    @Test
    fun testParseStartupStatusWithoutAx() {
        // Line 427 in smart_parcel_monitor.c transmits REED and OPEN_COUNT without AX
        val line = "STATUS=READY,REED=CLOSED,OPEN_COUNT=0,SHAKE=LOW,MPU=OK"
        val msg = parser.parseLine(line)

        assertTrue(msg is FirmwareMessage.StatusTelemetry)
        val status = msg as FirmwareMessage.StatusTelemetry
        assertEquals("READY", status.status)
        assertEquals("CLOSED", status.reedState)
        assertEquals(0L, status.openCount)
        assertEquals("LOW", status.shakeLevel)
        assertNull(status.ax)
        assertNull(status.ay)
        assertNull(status.az)
        assertEquals("OK", status.mpuStatus)
    }

    @Test
    fun testMissingOpenCountPreservedAsNull() {
        val line = "EVENT=PARCEL_OPENED,SHAKE=MEDIUM"
        val msg = parser.parseLine(line)

        assertTrue(msg is FirmwareMessage.EventMessage)
        val event = msg as FirmwareMessage.EventMessage
        assertEquals("PARCEL_OPENED", event.eventType)
        assertNull("Missing open count must be null to avoid resetting count to 0", event.openCount)
        assertEquals("MEDIUM", event.shakeLevel)
    }

    @Test
    fun testMalformedShakeLevelRejected() {
        val line = "EVENT=SHAKE_LEVEL_CHANGED,SHAKE=INVALID_VALUE,OPEN_COUNT=3"
        val msg = parser.parseLine(line)

        assertTrue(msg is FirmwareMessage.EventMessage)
        val event = msg as FirmwareMessage.EventMessage
        assertNull("Malformed shake level must be null to prevent forcing default LOW", event.shakeLevel)
        assertEquals(3L, event.openCount)
    }

    @Test
    fun testParseMpuErrorAndRecovery() {
        val errorLine = "ERROR=MPU6050_READ_FAILED"
        val errorMsg = parser.parseLine(errorLine)
        assertTrue(errorMsg is FirmwareMessage.ErrorMessage)
        assertEquals("MPU6050_READ_FAILED", (errorMsg as FirmwareMessage.ErrorMessage).errorCode)

        val recoverLine = "STATUS=MPU6050_RECOVERED"
        val recoverMsg = parser.parseLine(recoverLine)
        assertTrue(recoverMsg is FirmwareMessage.StartupMessage)
        assertEquals("MPU6050_RECOVERED", (recoverMsg as FirmwareMessage.StartupMessage).status)
    }

    @Test
    fun testStreamPacketFragmentation() {
        // Simulate fragmented RFCOMM reception across multiple reads
        val part1 = "EVENT=PARCEL_OP".toByteArray(Charsets.US_ASCII)
        val part2 = "ENED,OPEN_COUNT=1,SH".toByteArray(Charsets.US_ASCII)
        val part3 = "AKE=LOW\r\n".toByteArray(Charsets.US_ASCII)

        val res1 = parser.feed(part1, part1.size)
        assertTrue("No messages expected before newline", res1.isEmpty())

        val res2 = parser.feed(part2, part2.size)
        assertTrue("No messages expected before newline", res2.isEmpty())

        val res3 = parser.feed(part3, part3.size)
        assertEquals(1, res3.size)
        val event = res3[0] as FirmwareMessage.EventMessage
        assertEquals("PARCEL_OPENED", event.eventType)
        assertEquals(1L, event.openCount)
        assertEquals("LOW", event.shakeLevel)
    }

    @Test
    fun testMalformedLineDoesNotCrash() {
        val garbage = "&&*#@*$,,,NOT_A_VALID_LINE===123"
        val msg = parser.parseLine(garbage)
        assertTrue(msg is FirmwareMessage.UnknownMessage)
    }
}
