package com.venuecast.car

import android.view.Surface
import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.Header
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template
import com.venuecast.R
import com.venuecast.core.MirrorGateway
import com.venuecast.mirror.MirrorService

class VenueScreen(carContext: CarContext) : Screen(carContext) {

    private val surfaceCallback = object : SurfaceCallback {
        override fun onSurfaceAvailable(container: SurfaceContainer) {
            val surface: Surface = container.surface ?: return
            MirrorGateway.attachSurface(
                MirrorGateway.SurfaceTarget(
                    surface = surface,
                    width = container.width,
                    height = container.height,
                    dpi = container.dpi
                )
            )
            invalidate()
        }

        override fun onSurfaceDestroyed(container: SurfaceContainer) {
            MirrorGateway.detachSurface()
            invalidate()
        }
    }

    init {
        carContext.getCarService(AppManager::class.java)
            .setSurfaceCallback(surfaceCallback)
    }

    override fun onGetTemplate(): Template {
        val active = MirrorGateway.status() == MirrorGateway.Status.ACTIVE

        val stopAction = Action.Builder()
            .setTitle(carContext.getString(R.string.car_stop))
            .setOnClickListener {
                if (active) MirrorService.stop(carContext)
            }
            .build()

        return MessageTemplate.Builder(
            carContext.getString(
                if (active) R.string.car_active else R.string.car_ready
            )
        )
            .setHeader(
                Header.Builder()
                    .setTitle(carContext.getString(R.string.car_status))
                    .build()
            )
            .setActionStrip(
                ActionStrip.Builder()
                    .addAction(stopAction)
                    .build()
            )
            .build()
    }
}
