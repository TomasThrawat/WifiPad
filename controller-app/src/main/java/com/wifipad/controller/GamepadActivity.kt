package com.wifipad.controller

import android.content.res.ColorStateList
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
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
        private const val LOCAL_NETWORK_REQUEST = 3701
        private const val LOCAL_NETWORK_PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"
    }

    private lateinit var padView: GamepadView
    private lateinit var sender: UdpSender
    private var pendingHost: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DynamicColors.applyToActivityIfAvailable(this)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val host = intent.getStringExtra(EXTRA_HOST)?.trim().orEmpty()
        if (host.isEmpty()) {
            finish()
            return
        }

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
        if (::sender.isInitialized) sender.stop()
        sender = UdpSender(padView.state)
        sender.onError = { message ->
            runOnUiThread {
                Toast.makeText(this, getString(R.string.status_error, message), Toast.LENGTH_LONG).show()
                finish()
            }
        }
        sender.start(host, Protocol.DEFAULT_PORT)
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
        if (::sender.isInitialized) {
            sender.stop()
        }
        super.onDestroy()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
