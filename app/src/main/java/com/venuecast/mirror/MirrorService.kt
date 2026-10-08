package com.venuecast.mirror

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Surface
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.venuecast.R
import com.venuecast.core.MirrorGateway
import com.venuecast.core.VehicleState
import com.venuecast.setup.MainActivity

class MirrorService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var lastSurface: Surface? = null
    private var lastWidth = 0
    private var lastHeight = 0
    private var projectionStarted = false

    private val surfacePoller = object : Runnable {
        override fun run() {
            if (projection != null) attachSurface(MirrorGateway.surface())
            handler.postDelayed(this, SURFACE_POLL_MS)
        }
    }

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            projectionStarted = false
            releaseVirtualDisplay()
            projection = null
            lastSurface = null
            MirrorGateway.setStatus(MirrorGateway.Status.IDLE)
            stopSelf()
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        handler.post(surfacePoller)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, -1)
                val data = readProjectionIntent(intent)
                if (resultCode == RESULT_OK && data != null) {
                    startAsForeground()
                    startProjection(resultCode, data)
                } else {
                    MirrorGateway.setStatus(MirrorGateway.Status.ERROR)
                    stopSelf()
                }
            }
            ACTION_STOP -> stopMirroring()
        }
        return START_NOT_STICKY
    }

    private fun readProjectionIntent(intent: Intent): Intent? =
        if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_RESULT_DATA)
        }

    private fun startProjection(resultCode: Int, data: Intent) {
        if (projectionStarted) return

        val manager = getSystemService(MediaProjectionManager::class.java)
        projection = runCatching { manager.getMediaProjection(resultCode, data) }
            .onFailure { Log.e(TAG, "getMediaProjection failed", it) }
            .getOrNull()

        val p = projection ?: run {
            MirrorGateway.setStatus(MirrorGateway.Status.ERROR)
            stopSelf()
            return
        }

        projectionStarted = true
        p.registerCallback(projectionCallback, handler)
        attachSurface(MirrorGateway.surface())
    }

    private fun attachSurface(target: MirrorGateway.SurfaceTarget?) {
        val p = projection ?: return

        if (!VehicleState.visualOutputAllowed()) {
            virtualDisplay?.setSurface(null)
            lastSurface = null
            MirrorGateway.setStatus(MirrorGateway.Status.WAITING_SURFACE)
            return
        }

        if (target == null) {
            virtualDisplay?.setSurface(null)
            lastSurface = null
            MirrorGateway.setStatus(MirrorGateway.Status.WAITING_SURFACE)
            return
        }

        val quality = getSharedPreferences(PREFS, MODE_PRIVATE)
            .getString(KEY_QUALITY, QUALITY_720) ?: QUALITY_720

        val (targetWidth, targetHeight) =
            chooseOutputSize(target.width, target.height, quality)

        val targetChanged = target.surface !== lastSurface ||
            targetWidth != lastWidth ||
            targetHeight != lastHeight

        if (!targetChanged) {
            MirrorGateway.setStatus(MirrorGateway.Status.ACTIVE)
            return
        }

        try {
            if (virtualDisplay == null) {
                virtualDisplay = p.createVirtualDisplay(
                    DISPLAY_NAME,
                    targetWidth,
                    targetHeight,
                    target.dpi.coerceAtLeast(160),
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    target.surface,
                    null,
                    handler
                )
            } else {
                virtualDisplay?.resize(
                    targetWidth, targetHeight, target.dpi.coerceAtLeast(160)
                )
                virtualDisplay?.setSurface(target.surface)
            }

            lastSurface = target.surface
            lastWidth = targetWidth
            lastHeight = targetHeight
            MirrorGateway.setStatus(MirrorGateway.Status.ACTIVE)
        } catch (t: Throwable) {
            Log.e(TAG, "Virtual display attach failed", t)
            MirrorGateway.setStatus(MirrorGateway.Status.ERROR)
        }
    }

    private fun chooseOutputSize(
        hostWidth: Int, hostHeight: Int, quality: String
    ): Pair<Int, Int> {
        val longSide = if (quality == QUALITY_1080) 1920 else 1280
        val w = hostWidth.coerceAtLeast(640)
        val h = hostHeight.coerceAtLeast(360)
        val aspect = w.toFloat() / h.toFloat()

        return if (w >= h) {
            val outW = minOf(w, longSide)
            outW to (outW / aspect).toInt().coerceAtLeast(360)
        } else {
            val outH = minOf(h, longSide)
            (outH * aspect).toInt().coerceAtLeast(360) to outH
        }
    }

    private fun startAsForeground() {
        val stopIntent = Intent(this, MirrorService::class.java).setAction(ACTION_STOP)
        val stopPi = PendingIntent.getService(
            this, 42, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val openPi = PendingIntent.getActivity(
            this, 43,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_app)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notification_mirroring))
            .setOngoing(true)
            .setContentIntent(openPi)
            .addAction(
                NotificationCompat.Action.Builder(
                    R.drawable.ic_app,
                    getString(R.string.notification_stop),
                    stopPi
                ).build()
            )
            .build()

        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun stopMirroring() {
        runCatching { projection?.unregisterCallback(projectionCallback) }
        releaseVirtualDisplay()
        runCatching { projection?.stop() }
        projection = null
        projectionStarted = false
        lastSurface = null
        MirrorGateway.setStatus(MirrorGateway.Status.IDLE)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun releaseVirtualDisplay() {
        runCatching { virtualDisplay?.release() }
        virtualDisplay = null
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        runCatching { projection?.unregisterCallback(projectionCallback) }
        releaseVirtualDisplay()
        runCatching { projection?.stop() }
        projection = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel),
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    companion object {
        private const val TAG = "VenueCast"
        private const val CHANNEL_ID = "venuecast_mirror"
        private const val NOTIFICATION_ID = 7004
        private const val SURFACE_POLL_MS = 250L
        private const val PREFS = "venuecast"

        const val KEY_QUALITY = "quality"
        const val QUALITY_720 = "720"
        const val QUALITY_1080 = "1080"

        const val ACTION_START = "com.venuecast.START"
        const val ACTION_STOP = "com.venuecast.STOP"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"

        private const val DISPLAY_NAME = "VenueCast-Mirror"
        private const val RESULT_OK = -1

        fun start(context: Context, resultCode: Int, data: Intent) {
            val intent = Intent(context, MirrorService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, data)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, MirrorService::class.java).setAction(ACTION_STOP)
            context.startService(intent)
        }
    }
}