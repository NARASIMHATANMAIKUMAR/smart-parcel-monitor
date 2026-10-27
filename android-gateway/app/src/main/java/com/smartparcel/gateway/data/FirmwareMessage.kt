package com.smartparcel.gateway.data

/**
 * Sealed hierarchy representing messages received from the STM32F401 firmware over USART2 (9600 Baud).
 * Fields are nullable to prevent the parser from forcing default 0 or LOW values when firmware
 * messages omit fields or send malformed data.
 */
sealed class FirmwareMessage {
    abstract val rawLine: String

    data class EventMessage(
        val eventType: String,
        val openCount: Long?,
        val shakeLevel: String?,
        override val rawLine: String
    ) : FirmwareMessage()

    data class StatusTelemetry(
        val status: String,
        val reedState: String,
        val openCount: Long?,
        val shakeLevel: String?,
        val ax: Int?,
        val ay: Int?,
        val az: Int?,
        val mpuStatus: String,
        override val rawLine: String
    ) : FirmwareMessage()

    data class ErrorMessage(
        val errorCode: String,
        override val rawLine: String
    ) : FirmwareMessage()

    data class StartupMessage(
        val status: String,
        override val rawLine: String
    ) : FirmwareMessage()

    data class UnknownMessage(
        override val rawLine: String
    ) : FirmwareMessage()
}

