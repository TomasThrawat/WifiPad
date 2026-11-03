package com.wifipad.controller

import android.content.res.ColorStateList
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.MaterialColors

class GamepadActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_HOST = "host"
        const val EXTRA_TV_ID = "tv_id"
        private const val LOCAL_NETWORK_REQUEST = 3701
        private const val LOCAL_NETWORK_PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"
    }

    private lateinit var padView: GamepadView
    private lateinit var sender: UdpSender
    private var pendingHost: String? = null
    private lateinit var connectionInfo: com.google.android.material.textview.MaterialTextView
    private var activePairing: TvPairing? = null
    private val reconnectHandler = Handler(Looper.getMainLooper())
    private var reconnectDiscovery: TvDiscovery? = null
    private var reconnectDiscoveryRunning = false
    private var rebuildingSender = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DynamicColors.applyToActivityIfAvailable(this)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val tvId = intent.getStringExtra(EXTRA_TV_ID).orEmpty()
        val pairing = TvPairingStore.get(this, tvId)
        if (pairing == null) {
            finish()
            return
        }
        val host = pairing.host
        activePairing = pairing

        padView = GamepadView(this, null)

        val root = FrameLayout(this).apply {
            setBackgroundColor(android.graphics.Color.BLACK)
            addView(
                padView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
        }

        connectionInfo = com.google.android.material.textview.MaterialTextView(this).apply {
            text = getString(R.string.secure_pending)
            setTextColor(android.graphics.Color.WHITE)
            textSize = 11f
            gravity = Gravity.CENTER
        }
        val settingsButton = MaterialButton(this).apply {
            text = getString(R.string.settings)
            isAllCaps = false
            minHeight = 0
            minimumHeight = 0
            cornerRadius = dp(18)
            setTextSize(14f)
            setPadding(dp(16), 0, dp(16), 0)
            backgroundTintList = ColorStateList.valueOf(
                MaterialColors.getColor(
                    this,
                    com.google.android.material.R.attr.colorSurfaceContainerHigh
                )
            )
            setOnClickListener {
                startActivity(
                    android.content.Intent(
                        this@GamepadActivity,
                        SettingsActivity::class.java
                    )
                )
            }
        }

        root.addView(connectionInfo, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, dp(42), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL))
        root.addView(
            settingsButton,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                dp(48),
                Gravity.TOP or Gravity.CENTER_HORIZONTAL
            ).apply {
                topMargin = dp(10)
            }
        )

        setContentView(root)
        root.applySystemBarInsets()

        startSenderWhenAllowed(host)
    }


    private fun startSenderWhenAllowed(host: String) {
        pendingHost = host
        if (Build.VERSION.SDK_INT < 37 ||
            checkSelfPermission(LOCAL_NETWORK_PERMISSION) == PackageManager.PERMISSION_GRANTED
        ) {
            startSender(host)
            return
        }
        if (shouldShowRequestPermissionRationale(LOCAL_NETWORK_PERMISSION)) {
            showLocalNetworkPermissionRationale()
        } else {
            requestPermissions(arrayOf(LOCAL_NETWORK_PERMISSION), LOCAL_NETWORK_REQUEST)
        }
    }

    private fun showLocalNetworkPermissionRationale() {
        android.app.AlertDialog.Builder(this)
            .setTitle(R.string.local_network_permission_title)
            .setMessage(R.string.local_network_permission_message)
            .setPositiveButton(R.string.local_network_permission_retry) { _, _ ->
                requestPermissions(arrayOf(LOCAL_NETWORK_PERMISSION), LOCAL_NETWORK_REQUEST)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun startSender(host: String) {
        rebuildingSender = true
        if (::sender.isInitialized) sender.stop()
        val pairing = activePairing ?: run { finish(); return }
        val updated = pairing.copy(host = host)
        activePairing = updated
        TvPairingStore.save(this, updated)
        sender = UdpSender(padView.state, updated)
        sender.onStatus = { stats ->
            runOnUiThread {
                connectionInfo.text = if (!stats.connected) getString(R.string.connection_disconnected) else
                    getString(R.string.connection_stats, stats.rttMs.toInt(), stats.acknowledged.toInt(), stats.sent.toInt(), stats.lost.toInt())
                if (stats.connected) {
                    stopReconnectDiscovery()
                } else if (!rebuildingSender) {
                    startReconnectDiscovery()
                }
            }
        }
        sender.onError = { message ->
            runOnUiThread {
                Toast.makeText(this, getString(R.string.status_error, message), Toast.LENGTH_LONG).show()
                finish()
            }
        }
        sender.start(host, pairing.port)
        rebuildingSender = false
    }


    private fun startReconnectDiscovery() {
        if (reconnectDiscoveryRunning || isFinishing || isDestroyed) return
        val expectedTvId = activePairing?.tvId ?: return
        reconnectDiscoveryRunning = true
        reconnectDiscovery = TvDiscovery(this) { device ->
            if (device.tvId != expectedTvId) return@TvDiscovery
            runOnUiThread {
                val pairing = activePairing ?: return@runOnUiThread
                val changed = pairing.host != device.host || pairing.port != device.port
                if (changed) {
                    val updated = pairing.copy(host = device.host, port = device.port, name = device.name)
                    TvPairingStore.save(this, updated)
                    activePairing = updated
                    stopReconnectDiscovery()
                    startSender(updated.host)
                }
            }
        }.also { it.start() }
        reconnectHandler.postDelayed({ stopReconnectDiscovery() }, 12_000L)
    }

    private fun stopReconnectDiscovery() {
        reconnectHandler.removeCallbacksAndMessages(null)
        reconnectDiscovery?.stop()
        reconnectDiscovery = null
        reconnectDiscoveryRunning = false
    }

    @Deprecated("Use the permission result callback")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != LOCAL_NETWORK_REQUEST) return
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            pendingHost?.let(::startSender)
        } else if (shouldShowRequestPermissionRationale(LOCAL_NETWORK_PERMISSION)) {
            showLocalNetworkPermissionRationale()
        } else {
            android.app.AlertDialog.Builder(this)
                .setTitle(R.string.local_network_permission_title)
                .setMessage(R.string.local_network_permission_settings_message)
                .setPositiveButton(R.string.local_network_permission_settings) { _, _ ->
                    val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = android.net.Uri.fromParts("package", packageName, null)
                    }
                    startActivity(intent)
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    override fun onResume() {
        super.onResume()
        if (::padView.isInitialized) {
            padView.reloadSettings()
        }
        if (Build.VERSION.SDK_INT >= 37 && !::sender.isInitialized &&
            checkSelfPermission(LOCAL_NETWORK_PERMISSION) == PackageManager.PERMISSION_GRANTED
        ) {
            pendingHost?.let(::startSender)
        }
    }

    override fun onDestroy() {
        stopReconnectDiscovery()
        if (::sender.isInitialized) {
            sender.stop()
        }
        super.onDestroy()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
