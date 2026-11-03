package com.wifipad.controller

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.textview.MaterialTextView

class MainActivity : AppCompatActivity() {
    companion object {
        private const val PREFS = "wifipad"
        private const val PREF_HOST = "tv_ip"
        private const val BLACK = Color.BLACK
        private const val PANEL = 0xFF101010.toInt()
        private const val RAISED = 0xFF171717.toInt()
        private const val LINE = 0xFF303030.toInt()
        private const val MUTED = 0xFF9B9B9B.toInt()
        private const val WHITE = Color.WHITE
        private const val ERROR = 0xFFFF7777.toInt()
    }

    private lateinit var hostInput: TextInputEditText
    private lateinit var connectButton: MaterialButton
    private lateinit var messageView: MaterialTextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val screen = createScreen()
        setContentView(screen)
        screen.applySystemBarInsets()
        hostInput.setText(getSharedPreferences(PREFS, MODE_PRIVATE).getString(PREF_HOST, "").orEmpty())
    }

    private fun createScreen(): android.view.View {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(BLACK)
            isFillViewport = true
            clipToPadding = false
        }
        val width = (resources.displayMetrics.widthPixels - dp(48)).coerceAtMost(dp(520)).coerceAtLeast(dp(280))
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(24), 0, dp(32))
            layoutParams = FrameLayout.LayoutParams(width, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }
        }

        content.addView(createBrandHeader(
            "PHONE CONTROLLER",
            "Use your phone as a wireless game controller.",
            "⌘"
        ))
        addSpace(content, 44)
        content.addView(label("Connect to your TV", 25f, WHITE, true))
        addSpace(content, 8)
        content.addView(label("Enter the receiver address shown on your TV.", 14f, MUTED))
        addSpace(content, 22)

        val panel = card()
        val panelContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
        }
        val inputLayout = TextInputLayout(this).apply {
            hint = "TV IP address"
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_FILLED
            boxBackgroundColor = RAISED
            setBoxCornerRadii(dp(16).toFloat(), dp(16).toFloat(), dp(16).toFloat(), dp(16).toFloat())
            setBoxStrokeColor(WHITE)
            setDefaultHintTextColor(ColorStateList.valueOf(MUTED))
        }
        hostInput = TextInputEditText(this).apply {
            setTextColor(WHITE)
            setHintTextColor(MUTED)
            hint = "192.168.1.20"
            textSize = 16f
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            imeOptions = EditorInfo.IME_ACTION_GO
            setSingleLine(true)
            setPadding(dp(16), dp(16), dp(16), dp(16))
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE) {
                    connect()
                    true
                } else false
            }
        }
        inputLayout.addView(hostInput, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        panelContent.addView(inputLayout)
        addSpace(panelContent, 12)
        messageView = label("", 13f, ERROR).apply { visibility = android.view.View.GONE }
        panelContent.addView(messageView)
        addSpace(panelContent, 18)

        connectButton = MaterialButton(this).apply {
            text = getString(R.string.open_controller)
            isAllCaps = false
            textSize = 15f
            setTextColor(BLACK)
            typeface = Typeface.DEFAULT_BOLD
            cornerRadius = dp(16)
            backgroundTintList = ColorStateList.valueOf(WHITE)
            minimumHeight = dp(56)
            setOnClickListener { connect() }
        }
        panelContent.addView(connectButton, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(56)
        ))
        panel.addView(panelContent)
        content.addView(panel)

        addSpace(content, 18)
        val info = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
        }
        info.addView(label("ⓘ", 18f, MUTED))
        addSpaceHorizontal(info, 10)
        info.addView(label("Connect both devices to the same Wi-Fi network.", 12f, MUTED))
        content.addView(info)

        scroll.addView(content)
        return scroll
    }

    private fun connect() {
        val host = hostInput.text?.toString()?.trim().orEmpty()
        if (host.isBlank()) {
            messageView.text = getString(R.string.enter_tv_ip)
            messageView.visibility = android.view.View.VISIBLE
            return
        }
        getSharedPreferences(PREFS, MODE_PRIVATE).edit { putString(PREF_HOST, host) }
        connectButton.isEnabled = false
        connectButton.text = getString(R.string.opening_controls)
        startActivity(Intent(this, GamepadActivity::class.java).putExtra(GamepadActivity.EXTRA_HOST, host))
        connectButton.isEnabled = true
        connectButton.text = getString(R.string.open_controller)
    }

    private fun createBrandHeader(eyebrow: String, subtitle: String, symbol: String): LinearLayout {
        val header = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val iconCard = MaterialCardView(this).apply {
            radius = dp(18).toFloat()
            cardElevation = 0f
            strokeWidth = dp(1)
            strokeColor = LINE
            setCardBackgroundColor(RAISED)
        }
        iconCard.addView(MaterialTextView(this).apply {
            text = symbol
            gravity = Gravity.CENTER
            setTextColor(WHITE)
            textSize = 29f
        }, FrameLayout.LayoutParams(dp(58), dp(58)))
        header.addView(iconCard, LinearLayout.LayoutParams(dp(58), dp(58)))
        addSpace(header, 22)
        header.addView(label(eyebrow, 11f, MUTED, true).apply { letterSpacing = 0.16f })
        addSpace(header, 7)
        header.addView(label("WifiPad", 36f, WHITE, true).apply { letterSpacing = -0.035f })
        addSpace(header, 7)
        header.addView(label(subtitle, 14f, MUTED))
        return header
    }

    private fun card(): MaterialCardView = MaterialCardView(this).apply {
        radius = dp(22).toFloat()
        cardElevation = 0f
        strokeWidth = dp(1)
        strokeColor = LINE
        setCardBackgroundColor(PANEL)
    }

    private fun label(value: String, size: Float, color: Int, bold: Boolean = false) =
        MaterialTextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
            includeFontPadding = true
        }

    private fun addSpace(parent: LinearLayout, height: Int) {
        parent.addView(android.view.View(this), LinearLayout.LayoutParams(1, dp(height)))
    }

    private fun addSpaceHorizontal(parent: LinearLayout, width: Int) {
        parent.addView(android.view.View(this), LinearLayout.LayoutParams(dp(width), 1))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
