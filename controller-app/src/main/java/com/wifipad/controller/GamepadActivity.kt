package com.wifipad.controller

import android.content.res.ColorStateList
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
    }

    private lateinit var padView: GamepadView
    private lateinit var sender: UdpSender

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
            setBackgroundColor(
                MaterialColors.getColor(
                    this,
                    com.google.android.material.R.attr.colorSurface
                )
            )
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

        sender = UdpSender(padView.state)
        sender.onError = { message ->
            runOnUiThread {
                Toast.makeText(
                    this,
                    getString(R.string.status_error, message),
                    Toast.LENGTH_LONG
                ).show()
                finish()
            }
        }
        sender.start(host, Protocol.DEFAULT_PORT)
    }

    override fun onResume() {
        super.onResume()
        if (::padView.isInitialized) {
            padView.reloadSettings()
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
