package com.wifipad.controller

import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.LinearLayout
import android.widget.LinearLayout
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.textview.MaterialTextView
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private lateinit var padView: GamepadView
    private lateinit var statusView: TextView
    private lateinit var ipField: EditText
    private lateinit var sender: UdpSender

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val prefs = getSharedPreferences("wifipad", MODE_PRIVATE)
        val surface = color(com.google.android.material.R.attr.colorSurface)
        val surfaceContainer = color(com.google.android.material.R.attr.colorSurfaceContainer)
        val outline = color(com.google.android.material.R.attr.colorOutlineVariant)
        val onSurface = color(com.google.android.material.R.attr.colorOnSurface)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(surface)
        }

        val toolbar = MaterialToolbar(this).apply {
            title = "WiFiPad Controller"
            setTitleTextColor(onSurface)
            setBackgroundColor(surfaceContainer)
            elevation = 0f
            contentInsetStartWithNavigation = dp(16)
        }
        root.addView(toolbar, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(64)
        ))

        val card = MaterialCardView(this).apply {
            radius = dp(28).toFloat()
            cardElevation = 0f
            strokeWidth = dp(1)
            strokeColor = outline
            setCardBackgroundColor(surfaceContainer)
        }

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        ipField = TextInputEditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine(true)
            setText(prefs.getString("tv_ip", ""))
        }

        val inputLayout = TextInputLayout(this).apply {
            hint = "TV IP address"
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            endIconMode = TextInputLayout.END_ICON_CLEAR_TEXT
        }
        inputLayout.addView(ipField, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        val connectBtn = MaterialButton(this).apply {
            text = "Connect"
            minHeight = dp(56)
            insetTop = 0
            insetBottom = 0
            cornerRadius = dp(18)
        }

        row.addView(inputLayout, LinearLayout.LayoutParams(0, dp(56), 1f))
        row.addView(connectBtn, LinearLayout.LayoutParams(dp(132), dp(56)).apply {
            marginStart = dp(12)
        })

        statusView = MaterialTextView(this).apply {
            text = "Ready. Enter the TV IP and connect."
            setTextColor(color(com.google.android.material.R.attr.colorOnSurfaceVariant))
            textSize = 14f
            setPadding(dp(4), dp(10), dp(4), 0)
        }

        panel.addView(row)
        panel.addView(statusView)
        card.addView(panel)

        root.addView(card, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            marginStart = dp(12)
            marginEnd = dp(12)
            topMargin = dp(10)
            bottomMargin = dp(10)
        })

        padView = GamepadView(this, null)
        root.addView(padView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f
        ))

        setContentView(root)

        sender = UdpSender(padView.state)
        sender.onError = { msg ->
            runOnUiThread {
                statusView.text = "Error: " + msg
                statusView.setTextColor(color(com.google.android.material.R.attr.colorError))
            }
        }

        connectBtn.setOnClickListener {
            val host = ipField.text?.toString()?.trim().orEmpty()
            if (host.isEmpty()) {
                statusView.text = "Enter the TV's IP"
                statusView.setTextColor(color(com.google.android.material.R.attr.colorError))
                ipField.requestFocus()
                return@setOnClickListener
            }

            prefs.edit().putString("tv_ip", host).apply()
            sender.start(host, Protocol.DEFAULT_PORT)
            statusView.text = "Sending to " + host + ":" + Protocol.DEFAULT_PORT
            statusView.setTextColor(color(com.google.android.material.R.attr.colorOnSurfaceVariant))
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).roundToInt()

    private fun color(attr: Int): Int =
        MaterialColors.getColor(this, attr, 0)

    override fun onDestroy() {
        sender.stop()
        super.onDestroy()
    }
}
