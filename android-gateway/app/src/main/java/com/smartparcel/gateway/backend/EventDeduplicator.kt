package com.smartparcel.gateway.backend

import java.util.concurrent.ConcurrentHashMap

/**
 * Time-windowed event deduplicator to prevent repeated uploads from serial retransmissions,
 * network retry loops, or sensor mechanical contact bounce.
 */
class EventDeduplicator(
    private val suppressionWindowMillis: Long = 3_000L
) {
    // Map from signature string to epoch millisecond timestamp
    private val recentSignatures = ConcurrentHashMap<String, Long>()

    /**
     * Evaluates whether an event with the given characteristics is a duplicate within the time window.
     * If not duplicate, records the signature and returns false.
     * If duplicate, returns true.
     */
    @Synchronized
    fun isDuplicate(
        parcelId: String,
        eventType: String,
        openCount: Long,
        shakeLevel: String
    ): Boolean {
        pruneExpired()

        val signature = "$parcelId:$eventType:$openCount:$shakeLevel"
        val now = System.currentTimeMillis()
        val lastSeen = recentSignatures[signature]

        if (lastSeen != null && (now - lastSeen) < suppressionWindowMillis) {
            return true
        }

        recentSignatures[signature] = now
        return false
    }

    private fun pruneExpired() {
        val now = System.currentTimeMillis()
        val iterator = recentSignatures.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (now - entry.value > suppressionWindowMillis * 2) {
                iterator.remove()
            }
        }
    }

    @Synchronized
    fun clear() {
        recentSignatures.clear()
    }
}
