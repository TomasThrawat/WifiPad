package com.wifipad.controller

import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.WindowManager
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.MaterialColors
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.textview.MaterialTextView
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private lateinit var padView: GamepadView
    private lateinit var statusView: MaterialTextView
    private lateinit var sender: UdpSender

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DynamicColors.applyToActivityIfAvailable(this)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val prefs = getSharedPreferences("wifipad", MODE_PRIVATE)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(
                MaterialColors.getColor(
                    this,
                    com.google.android.material.R.attr.colorSurface
                )
            )
            setPadding(dp(12), dp(8), dp(12), dp(12))
        }

        val toolbar = MaterialToolbar(this).apply {
            title = getString(R.string.app_name)
            setTitleTextAppearance(
                context,
                com.google.android.material.R.style.TextAppearance_Material3_TitleLarge
            )
            elevation = 0f
        }

        val connectionCard = MaterialCardView(this).apply {
            radius = dp(20).toFloat()
            cardElevation = 0f
            strokeWidth = dp(1)
            strokeColor = MaterialColors.getColor(
                this,
                com.google.android.material.R.attr.colorOutlineVariant
            )
        }

        val connection = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(4), dp(12), dp(4))
        }

        val ipLayout = TextInputLayout(this).apply {
            hint = getString(R.string.tv_ip_hint)
            setBoxCornerRadii(
                dp(14).toFloat(),
                dp(14).toFloat(),
                dp(14).toFloat(),
                dp(14).toFloat()
            )
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                marginEnd = dp(8)
            }
        }

        val ipField = TextInputEditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine(true)
            setText(prefs.getString("tv_ip", ""))
        }
        ipLayout.addView(ipField)

        val connectButton = MaterialButton(this).apply {
            text = getString(R.string.connect)
            minHeight = dp(52)
            minWidth = dp(120)
            cornerRadius = dp(18)
            isAllCaps = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(52)
            )
        }

        connection.addView(ipLayout)
        connection.addView(connectButton)
        connectionCard.addView(connection)

        val statusCard = MaterialCardView(this).apply {
            radius = dp(18).toFloat()
            cardElevation = 0f
            setCardBackgroundColor(
                MaterialColors.getColor(
                    this,
                    com.google.android.material.R.attr.colorSurfaceContainer
                )
            )
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(8)
                bottomMargin = dp(8)
            }
        }

        statusView = MaterialTextView(this).apply {
            text = getString(R.string.status_not_connected)
            setTextAppearance(
                com.google.android.material.R.style.TextAppearance_Material3_BodyLarge
            )
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        statusCard.addView(statusView)

        padView = GamepadView(this, null)

        root.addView(
            toolbar,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(64)
            )
        )
        root.addView(connectionCard)
        root.addView(statusCard)
        root.addView(
            MaterialCardView(this).apply {
                radius = dp(24).toFloat()
                cardElevation = 0f
                strokeWidth = dp(1)
                strokeColor = MaterialColors.getColor(
                    this,
                    com.google.android.material.R.attr.colorOutlineVariant
                )
                addView(
                    padView,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.MATCH_PARENT
                    )
                )
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        setContentView(root)

        sender = UdpSender(padView.state)
        sender.onError = { msg ->
            runOnUiThread { statusView.text = getString(R.string.status_error, msg) }
        }

        connectButton.setOnClickListener {
            val host = ipField.text?.toString()?.trim().orEmpty()
            if (host.isEmpty()) {
                ipLayout.error = getString(R.string.enter_tv_ip)
                return@setOnClickListener
            }
            ipLayout.error = null
            prefs.edit().putString("tv_ip", host).apply()
            sender.start(host, Protocol.DEFAULT_PORT)
            statusView.text = getString(
                R.string.status_sending,
                host,
                Protocol.DEFAULT_PORT
            )
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    override fun onDestroy() {
        sender.stop()
        super.onDestroy()
    }
}
