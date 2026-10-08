package com.venuecast.setup

import android.Manifest
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.venuecast.R
import com.venuecast.core.MirrorGateway
import com.venuecast.input.VenueAccessibilityService
import com.venuecast.mirror.MirrorService

class MainActivity : ComponentActivity() {
    private lateinit var status: TextView
    private lateinit var qualityGroup: RadioGroup
    private val prefs by lazy { getSharedPreferences(PREFS, MODE_PRIVATE) }
    private val gatewayListener = { runOnUiThread { updateStatus() } }

    private val projectionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val data = result.data
            if (result.resultCode == RESULT_OK && data != null) {
                MirrorGateway.setStatus(MirrorGateway.Status.WAITING_SURFACE)
                MirrorService.start(this, result.resultCode, data)
                updateStatus()
            } else {
                status.text = getString(R.string.status_permission)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        requestNotificationPermissionIfNeeded()
        MirrorGateway.observe(gatewayListener)
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    override fun onDestroy() {
        MirrorGateway.removeObserver(gatewayListener)
        super.onDestroy()
    }

    private fun buildUi() {
        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(32))
        }

        val title = TextView(this).apply {
            text = getString(R.string.app_name)
            textSize = 32f
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val subtitle = TextView(this).apply {
            text = getString(R.string.app_version)
            textSize = 14f
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, dp(6), 0, dp(20))
        }
        status = TextView(this).apply {
            textSize = 18f
            setPadding(0, dp(10), 0, dp(18))
        }

        val start = Button(this).apply {
            text = getString(R.string.start_mirror)
            setOnClickListener { requestCapture() }
        }
        val stop = Button(this).apply {
            text = getString(R.string.stop_mirror)
            setOnClickListener {
                MirrorService.stop(this@MainActivity)
                updateStatus()
            }
        }

        val qualityTitle = TextView(this).apply {
            text = getString(R.string.quality_title)
            textSize = 17f
            setPadding(0, dp(24), 0, dp(8))
        }
        qualityGroup = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL }

        val q720 = RadioButton(this).apply {
            text = getString(R.string.quality_720)
            id = 720
        }
        val q1080 = RadioButton(this).apply {
            text = getString(R.string.quality_1080)
            id = 1080
        }
        qualityGroup.addView(q720)
        qualityGroup.addView(q1080)
        qualityGroup.check(
            if (prefs.getString(MirrorService.KEY_QUALITY, MirrorService.QUALITY_720) == MirrorService.QUALITY_1080)
                1080 else 720
        )
        qualityGroup.setOnCheckedChangeListener { _, checkedId ->
            prefs.edit().putString(
                MirrorService.KEY_QUALITY,
                if (checkedId == 1080) MirrorService.QUALITY_1080 else MirrorService.QUALITY_720
            ).apply()
        }

        val touchInfo = TextView(this).apply {
            text = getString(R.string.accessibility_description)
            textSize = 14f
            setPadding(0, dp(20), 0, dp(8))
        }
        val accessibility = Button(this).apply {
            text = getString(R.string.accessibility_title)
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }

        root.addView(title)
        root.addView(subtitle)
        root.addView(status)
        root.addView(start)
        root.addView(stop)
        root.addView(qualityTitle)
        root.addView(qualityGroup)
        root.addView(touchInfo)
        root.addView(accessibility)
        scroll.addView(root)
        setContentView(scroll)
        updateStatus()
    }

    private fun requestCapture() {
        val manager = getSystemService(MediaProjectionManager::class.java)
        projectionLauncher.launch(manager.createScreenCaptureIntent())
    }

    private fun updateStatus() {
        status.text = when (MirrorGateway.status()) {
            MirrorGateway.Status.ACTIVE -> getString(R.string.status_active)
            MirrorGateway.Status.WAITING_SURFACE -> getString(R.string.status_waiting_car)
            MirrorGateway.Status.ERROR -> getString(R.string.status_error)
            MirrorGateway.Status.IDLE -> getString(R.string.status_ready)
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                2001
            )
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val PREFS = "venuecast"
    }
}