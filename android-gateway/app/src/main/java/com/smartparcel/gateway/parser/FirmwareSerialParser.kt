package com.smartparcel.gateway.parser

import com.smartparcel.gateway.data.FirmwareMessage

/**
 * Resilient streaming serial parser for STM32F401 ASCII messages.
 * Handles partial line accumulation, fragmented RFCOMM packets, and corrupted tokens.
 */
class FirmwareSerialParser {

    private val lineBuffer = StringBuilder()

    companion object {
        private val VALID_SHAKE_LEVELS = setOf("LOW", "MEDIUM", "INTENSE")
        private val VALID_REED_STATES = setOf("OPEN", "CLOSED")
    }

    /**
     * Accumulates incoming byte chunks and parses any completed CRLF/LF-terminated lines.
     */
    @Synchronized
    fun feed(data: ByteArray, length: Int): List<FirmwareMessage> {
        val messages = mutableListOf<FirmwareMessage>()
        val text = String(data, 0, length, Charsets.US_ASCII)

        for (ch in text) {
            if (ch == '\n') {
                val completeLine = lineBuffer.toString().trim()
                lineBuffer.setLength(0) // Reset buffer

                if (completeLine.isNotEmpty()) {
                    val parsed = parseLine(completeLine)
                    messages.add(parsed)
                }
            } else if (ch != '\r') {
                // Safeguard against unbounded buffer growth from garbage data
                if (lineBuffer.length < 512) {
                    lineBuffer.append(ch)
                } else {
                    lineBuffer.setLength(0) // Discard overflow noise
                }
            }
        }

        return messages
    }

    /**
     * Parses a single completed message line into a strongly typed FirmwareMessage.
     */
    fun parseLine(line: String): FirmwareMessage {
        val tokens = line.split(",")
        val map = mutableMapOf<String, String>()

        for (token in tokens) {
            val parts = token.split("=", limit = 2)
            if (parts.size == 2) {
                map[parts[0].trim().uppercase()] = parts[1].trim()
            }
        }

        return when {
            // Discrete Transition Events: EVENT=PARCEL_OPENED, EVENT=PARCEL_CLOSED, EVENT=SHAKE_LEVEL_CHANGED, EVENT=STARTUP
            map.containsKey("EVENT") -> {
                val eventName = map["EVENT"]?.uppercase() ?: "UNKNOWN"
                if (eventName == "STARTUP") {
                    FirmwareMessage.StartupMessage(
                        status = map["STATUS"] ?: "INITIALIZING",
                        rawLine = line
                    )
                } else {
                    val rawShake = map["SHAKE"]?.uppercase()
                    val validatedShake = if (rawShake in VALID_SHAKE_LEVELS) rawShake else null

                    FirmwareMessage.EventMessage(
                        eventType = eventName,
                        openCount = map["OPEN_COUNT"]?.toLongOrNull(),
                        shakeLevel = validatedShake,
                        rawLine = line
                    )
                }
            }

            // Status Telemetry: Either periodic with AX/AY/AZ or startup broadcast with REED
            // Matches both STATUS=SECURE,REED=CLOSED,OPEN_COUNT=0,SHAKE=LOW,AX=140,AY=-280,AZ=16380,MPU=OK
            // and STATUS=READY,REED=CLOSED,OPEN_COUNT=0,SHAKE=LOW,MPU=OK
            map.containsKey("STATUS") && (map.containsKey("AX") || map.containsKey("REED")) -> {
                val rawReed = map["REED"]?.uppercase()
                val validatedReed = if (rawReed in VALID_REED_STATES) rawReed!! else "UNKNOWN"

                val rawShake = map["SHAKE"]?.uppercase()
                val validatedShake = if (rawShake in VALID_SHAKE_LEVELS) rawShake else null

                FirmwareMessage.StatusTelemetry(
                    status = map["STATUS"] ?: "SECURE",
                    reedState = validatedReed,
                    openCount = map["OPEN_COUNT"]?.toLongOrNull(),
                    shakeLevel = validatedShake,
                    ax = map["AX"]?.toIntOrNull(),
                    ay = map["AY"]?.toIntOrNull(),
                    az = map["AZ"]?.toIntOrNull(),
                    mpuStatus = map["MPU"]?.uppercase() ?: "OK",
                    rawLine = line
                )
            }

            // Startup or Recovery Status without sensor fields: STATUS=MPU6050_RECOVERED, STATUS=READY
            map.containsKey("STATUS") -> {
                FirmwareMessage.StartupMessage(
                    status = map["STATUS"] ?: "READY",
                    rawLine = line
                )
            }

            // Firmware Error Codes: ERROR=MPU6050_INIT_FAILED, ERROR=MPU6050_READ_FAILED
            map.containsKey("ERROR") -> {
                FirmwareMessage.ErrorMessage(
                    errorCode = map["ERROR"] ?: "UNKNOWN_ERROR",
                    rawLine = line
                )
            }

            else -> {
                FirmwareMessage.UnknownMessage(rawLine = line)
            }
        }
    }

    @Synchronized
    fun reset() {
        lineBuffer.setLength(0)
    }
}

