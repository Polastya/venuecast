package com.venuecast.car

import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.car.app.hardware.CarHardwareManager
import androidx.car.app.hardware.common.CarValue
import androidx.car.app.hardware.common.OnCarDataAvailableListener
import androidx.car.app.hardware.info.Speed
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.ParkedOnlyOnClickListener
import androidx.car.app.model.Template
import com.venuecast.R
import com.venuecast.core.MirrorGateway
import com.venuecast.core.VehicleState
import com.venuecast.mirror.MirrorService

class VenueScreen(carContext: CarContext) : Screen(carContext) {
    private val handler = Handler(Looper.getMainLooper())
    private var speedPermissionGranted = false
    private var hardwareStarted = false

    private val speedListener = OnCarDataAvailableListener<Speed> { speedData ->
        val speed = speedData.rawSpeedMetersPerSecond
        if (speed.status == CarValue.STATUS_SUCCESS && speed.value != null) {
            VehicleState.setSpeedMetersPerSecond(speed.value!!)
        }
        invalidate()
    }

    private val surfaceCallback = object : SurfaceCallback {
        override fun onSurfaceAvailable(container: SurfaceContainer) {
            MirrorGateway.attachSurface(
                MirrorGateway.SurfaceTarget(
                    surface = container.surface,
                    width = container.width,
                    height = container.height,
                    dpi = container.dpi
                )
            )
            invalidate()
        }

        override fun onSurfaceDestroyed(container: SurfaceContainer) {
            MirrorGateway.detachSurface(container.surface)
        }
    }

    init {
        carContext.getCarService(AppManager::class.java)
            .setSurfaceCallback(surfaceCallback)

        speedPermissionGranted =
            carContext.checkSelfPermission(CAR_SPEED_PERMISSION) == PackageManager.PERMISSION_GRANTED

        if (speedPermissionGranted) startHardware()
        handler.post(refreshRunnable)
    }

    private val refreshRunnable = object : Runnable {
        override fun run() {
            invalidate()
            handler.postDelayed(this, 1000L)
        }
    }

    private fun requestSpeedPermissionIfNeeded() {
        runCatching {
            carContext.requestPermissions(
                mutableListOf(CAR_SPEED_PERMISSION),
                object : androidx.car.app.OnRequestPermissionsListener {
                    override fun onRequestPermissionsResult(
                        grantedPermissions: List<String>,
                        rejectedPermissions: List<String>
                    ) {
                        speedPermissionGranted =
                            grantedPermissions.contains(CAR_SPEED_PERMISSION)
                        if (speedPermissionGranted) startHardware()
                        invalidate()
                    }
                }
            )
        }
    }

    private fun startHardware() {
        if (hardwareStarted) return
        hardwareStarted = true
        runCatching {
            val hardware = carContext.getCarService(CarHardwareManager::class.java)
            hardware.carInfo.addSpeedListener(carContext.mainExecutor, speedListener)
        }.onFailure {
            hardwareStarted = false
        }
    }

    private fun stopHardware() {
        if (!hardwareStarted) return
        runCatching {
            val hardware = carContext.getCarService(CarHardwareManager::class.java)
            hardware.carInfo.removeSpeedListener(speedListener)
        }
        hardwareStarted = false
        VehicleState.reset()
    }

    override fun onGetTemplate(): Template {
        val statusText = when {
            !speedPermissionGranted -> getString(R.string.car_ready)
            VehicleState.isMoving() -> getString(R.string.car_moving)
            MirrorGateway.status() == MirrorGateway.Status.ACTIVE -> getString(R.string.car_active)
            else -> getString(R.string.car_ready)
        }

        val strip = ActionStrip.Builder()

        strip.addAction(
            Action.Builder()
                .setTitle(
                    if (MirrorGateway.status() == MirrorGateway.Status.ACTIVE)
                        getString(R.string.car_stop)
                    else
                        getString(R.string.car_ready_action)
                )
                .setOnClickListener {
                    if (MirrorGateway.status() == MirrorGateway.Status.ACTIVE) {
                        MirrorService.stop(carContext)
                    }
                }
                .build()
        )

        if (!speedPermissionGranted) {
            strip.addAction(
                Action.Builder()
                    .setTitle(getString(R.string.enable_safety))
                    .setOnClickListener(
                        ParkedOnlyOnClickListener.create {
                            requestSpeedPermissionIfNeeded()
                        }
                    )
                    .build()
            )
        }

        return MessageTemplate.Builder(statusText)
            .setTitle(getString(R.string.car_status))
            .setActionStrip(strip.build())
            .build()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        stopHardware()
        MirrorGateway.detachSurface()
        super.onDestroy()
    }

    companion object {
        private const val CAR_SPEED_PERMISSION = "com.google.android.gms.permission.CAR_SPEED"
    }
}