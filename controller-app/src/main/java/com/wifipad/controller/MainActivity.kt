package com.wifipad.controller

import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.MaterialColors
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.textview.MaterialTextView

class MainActivity : AppCompatActivity() {

    private lateinit var ipField: TextInputEditText
    private lateinit var connectButton: MaterialButton
    private lateinit var statusView: MaterialTextView

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
            setPadding(dp(20), dp(12), dp(20), dp(24))
        }

        val toolbar = MaterialToolbar(this).apply {
            title = getString(R.string.app_name)
            elevation = 0f
            setTitleTextAppearance(
                context,
                com.google.android.material.R.style.TextAppearance_Material3_TitleLarge
            )
        }
        root.addView(
            toolbar,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(64)
            )
        )

        val title = MaterialTextView(this).apply {
            text = getString(R.string.connection_title)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_HeadlineSmall)
        }
        root.addView(
            title,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(28)
            }
        )

        val subtitle = MaterialTextView(this).apply {
            text = getString(R.string.connection_subtitle)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge)
            setTextColor(
                MaterialColors.getColor(
                    this,
                    com.google.android.material.R.attr.colorOnSurfaceVariant
                )
            )
        }
        root.addView(
            subtitle,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(8)
            }
        )

        val card = MaterialCardView(this).apply {
            radius = dp(28).toFloat()
            cardElevation = 0f
            strokeWidth = dp(1)
            strokeColor = MaterialColors.getColor(
                this,
                com.google.android.material.R.attr.colorOutlineVariant
            )
            setCardBackgroundColor(
                MaterialColors.getColor(
                    this,
                    com.google.android.material.R.attr.colorSurfaceContainer
                )
            )
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(22), dp(20), dp(22))
        }

        val ipLayout = TextInputLayout(this).apply {
            hint = getString(R.string.tv_ip_hint)
            setBoxCornerRadii(
                dp(18).toFloat(),
                dp(18).toFloat(),
                dp(18).toFloat(),
                dp(18).toFloat()
            )
            boxStrokeWidth = dp(1)
            boxStrokeWidthFocused = dp(2)
        }

        ipField = TextInputEditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            isSingleLine = true
            setText(prefs.getString("tv_ip", ""))
            setTextSize(18f)
            setPadding(dp(16), 0, dp(16), 0)
        }
        ipLayout.addView(
            ipField,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(64)
            )
        )

        connectButton = MaterialButton(this).apply {
            text = getString(R.string.connect)
            minHeight = dp(58)
            isAllCaps = false
            setTextSize(16f)
            cornerRadius = dp(18)
            setOnClickListener {
                connectToReceiver(prefs)
            }
        }

        statusView = MaterialTextView(this).apply {
            text = getString(R.string.connection_help)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
            setTextColor(
                MaterialColors.getColor(
                    this,
                    com.google.android.material.R.attr.colorOnSurfaceVariant
                )
            )
            setPadding(dp(4), dp(14), dp(4), 0)
        }

        content.addView(ipLayout)
        content.addView(
            connectButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(58)
            ).apply {
                topMargin = dp(16)
            }
        )
        content.addView(
            statusView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        card.addView(content)

        root.addView(
            card,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(24)
            }
        )

        val bottom = MaterialTextView(this).apply {
            text = getString(R.string.connection_flow_hint)
            gravity = Gravity.CENTER
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
            setTextColor(
                MaterialColors.getColor(
                    this,
                    com.google.android.material.R.attr.colorOnSurfaceVariant
                )
            )
            setPadding(dp(8), dp(24), dp(8), 0)
        }
        root.addView(
            bottom,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        setContentView(root)
    }

    private fun connectToReceiver(prefs: android.content.SharedPreferences) {
        val host = ipField.text?.toString()?.trim().orEmpty()
        if (host.isEmpty()) {
            ipField.error = getString(R.string.enter_tv_ip)
            return
        }

        prefs.edit().putString("tv_ip", host).apply()
        connectButton.isEnabled = false
        statusView.text = getString(R.string.opening_controls)

        startActivity(
            android.content.Intent(this, GamepadActivity::class.java).apply {
                putExtra(GamepadActivity.EXTRA_HOST, host)
            }
        )
        connectButton.isEnabled = true
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
