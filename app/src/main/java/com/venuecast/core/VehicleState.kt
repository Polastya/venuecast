package com.venuecast.core

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs

object VehicleState {
    private val movingRef = AtomicBoolean(false)
    private val knownRef = AtomicBoolean(false)
    private val speedMsRef = AtomicReference(0f)

    fun setSpeedMetersPerSecond(speed: Float) {
        speedMsRef.set(speed)
        movingRef.set(abs(speed) > 0.5f)
        knownRef.set(true)
    }

    fun isMoving(): Boolean = movingRef.get()
    fun safetyKnown(): Boolean = knownRef.get()
    fun visualOutputAllowed(): Boolean = safetyKnown() && !isMoving()
    fun speedMetersPerSecond(): Float = speedMsRef.get()

    fun reset() {
        speedMsRef.set(0f)
        movingRef.set(false)
        knownRef.set(false)
    }
}