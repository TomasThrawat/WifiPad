package com.wifipad.controller

import android.os.Bundle
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
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

    override fun onDestroy() {
        if (::sender.isInitialized) {
            sender.stop()
        }
        super.onDestroy()
    }
}
