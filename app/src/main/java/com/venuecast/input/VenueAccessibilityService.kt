package com.venuecast.input

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import java.util.concurrent.atomic.AtomicReference

class VenueAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance.set(this)
    }

    override fun onDestroy() {
        instance.compareAndSet(this, null)
        super.onDestroy()
    }

    private fun tapInternal(x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, 80L))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    private fun swipeInternal(
        x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long
    ): Boolean {
        val path = Path().apply {
            moveTo(x1, y1)
            lineTo(x2, y2)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(
                GestureDescription.StrokeDescription(
                    path, 0L, durationMs.coerceIn(80L, 1500L)
                )
            )
            .build()
        return dispatchGesture(gesture, null, null)
    }

    companion object {
        private val instance = AtomicReference<VenueAccessibilityService?>(null)

        fun isEnabled(): Boolean = instance.get() != null
        fun tap(x: Float, y: Float): Boolean = instance.get()?.tapInternal(x, y) == true
        fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long = 300L): Boolean =
            instance.get()?.swipeInternal(x1, y1, x2, y2, durationMs) == true
    }
}