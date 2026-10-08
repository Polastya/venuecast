package com.venuecast.car

import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.Session

class VenueSession : Session() {
    override fun onCreateScreen(intent: Intent): Screen =
        VenueScreen(carContext)
}