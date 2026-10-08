package com.venuecast.core

import android.view.Surface
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference

object MirrorGateway {
    data class SurfaceTarget(
        val surface: Surface,
        val width: Int,
        val height: Int,
        val dpi: Int
    )

    enum class Status { IDLE, WAITING_SURFACE, ACTIVE, ERROR }

    private val surfaceRef = AtomicReference<SurfaceTarget?>(null)
    private val statusRef = AtomicReference(Status.IDLE)
    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    fun surface(): SurfaceTarget? = surfaceRef.get()
    fun status(): Status = statusRef.get()

    fun observe(listener: () -> Unit) { listeners += listener }
    fun removeObserver(listener: () -> Unit) { listeners -= listener }

    fun attachSurface(target: SurfaceTarget) {
        surfaceRef.set(target)
        if (statusRef.get() != Status.ACTIVE) statusRef.set(Status.WAITING_SURFACE)
        notifyChanged()
    }

    fun detachSurface(surface: Surface? = null) {
        val current = surfaceRef.get()
        if (surface == null || current?.surface === surface) {
            surfaceRef.set(null)
            if (statusRef.get() != Status.ACTIVE && statusRef.get() != Status.ERROR) {
                statusRef.set(Status.IDLE)
            }
            notifyChanged()
        }
    }

    fun setStatus(status: Status) {
        statusRef.set(status)
        notifyChanged()
    }

    private fun notifyChanged() {
        listeners.forEach { runCatching { it() } }
    }
}