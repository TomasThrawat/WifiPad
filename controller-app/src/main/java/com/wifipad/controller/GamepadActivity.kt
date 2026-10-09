package com.wifipad.controller

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
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

        val layoutButton = actionButton(layoutButtonText()).apply {
            setOnClickListener { showLayoutChooser(this) }
        }
        val sensitivityButton = actionButton(sensitivityButtonText()).apply {
            setOnClickListener { showSensitivityChooser(this) }
        }
        val settingsButton = actionButton(getString(R.string.settings)).apply {
            setOnClickListener {
                startActivity(
                    android.content.Intent(
                        this@GamepadActivity,
                        SettingsActivity::class.java
                    )
                )
            }
        }

        val toolbarActions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            addView(layoutButton, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(46)
            ).apply { marginEnd = dp(4) })
            addView(sensitivityButton, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(46)
            ).apply {
                marginStart = dp(4)
                marginEnd = dp(4)
            })
            addView(settingsButton, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(46)
            ).apply { marginStart = dp(4) })
        }

        root.addView(
            toolbarActions,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                dp(46),
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

    private fun showLayoutChooser(button: MaterialButton) {
        val layouts = ControllerLayout.values()
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.layout_title)
            .setSingleChoiceItems(
                layouts.map { getString(it.titleResId) }.toTypedArray(),
                layouts.indexOf(ControllerLayoutStore.load(this))
            ) { dialog, which ->
                ControllerLayoutStore.save(this, layouts[which])
                padView.reloadSettings()
                button.text = layoutButtonText()
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showSensitivityChooser(button: MaterialButton) {
        val levels = StickSensitivity.values()
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.sensitivity_title)
            .setSingleChoiceItems(
                levels.map { getString(it.titleResId) }.toTypedArray(),
                levels.indexOf(ControllerLayoutStore.loadSensitivity(this))
            ) { dialog, which ->
                ControllerLayoutStore.saveSensitivity(this, levels[which])
                padView.reloadSettings()
                button.text = sensitivityButtonText()
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun layoutButtonText(): String =
        getString(
            R.string.layout_button,
            getString(ControllerLayoutStore.load(this).titleResId)
        )

    private fun sensitivityButtonText(): String =
        getString(
            R.string.sensitivity_button,
            getString(ControllerLayoutStore.loadSensitivity(this).titleResId)
        )

    private fun actionButton(label: String) = MaterialButton(this).apply {
        text = label
        isAllCaps = false
        minHeight = 0
        minimumHeight = 0
        cornerRadius = dp(18)
        setTextSize(13f)
        setPadding(dp(10), 0, dp(10), 0)
        backgroundTintList = ColorStateList.valueOf(
            MaterialColors.getColor(
                this,
                com.google.android.material.R.attr.colorSurfaceContainerHigh
            )
        )
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
