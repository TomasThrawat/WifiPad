package com.wifipad.controller

import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.ColorUtils
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.MaterialColors
import com.google.android.material.slider.Slider
import com.google.android.material.textview.MaterialTextView
import kotlin.math.roundToInt

class GamepadActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_HOST = "host"
    }

    private lateinit var padView: GamepadView
    private lateinit var sender: UdpSender

    private lateinit var playBar: View
    private lateinit var editBar: View
    private lateinit var panel: MaterialCardView
    private lateinit var panelTitle: MaterialTextView
    private lateinit var showCheck: MaterialCheckBox
    private lateinit var sizeValue: MaterialTextView
    private lateinit var sizeSlider: Slider
    private lateinit var stickOptions: LinearLayout
    private lateinit var deadZoneValue: MaterialTextView
    private lateinit var deadZoneSlider: Slider
    private lateinit var sensitivityValue: MaterialTextView
    private lateinit var sensitivitySlider: Slider
    private var populating = false

    private val backCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            cancelEditing()
        }
    }

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

        playBar = buildEditButton()
        root.addView(
            playBar,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                dp(48),
                Gravity.TOP or Gravity.CENTER_HORIZONTAL
            ).apply { topMargin = dp(10) }
        )

        editBar = buildEditBar()
        editBar.visibility = View.GONE
        root.addView(
            editBar,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.CENTER_HORIZONTAL
            ).apply { topMargin = dp(6) }
        )

        panel = buildPanel()
        panel.visibility = View.GONE
        root.addView(panel, panelParams(Gravity.LEFT))

        padView.onSelectionChanged = { group ->
            if (group == null) {
                panel.visibility = View.GONE
            } else {
                populatePanel(group)
                positionPanel()
                panel.visibility = View.VISIBLE
            }
        }

        onBackPressedDispatcher.addCallback(this, backCallback)
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

    // ----------------------------------------------------------------- edit mode

    private fun startEditing() {
        padView.enterEditMode()
        playBar.visibility = View.GONE
        editBar.visibility = View.VISIBLE
        backCallback.isEnabled = true
    }

    private fun finishEditing() {
        editBar.visibility = View.GONE
        panel.visibility = View.GONE
        playBar.visibility = View.VISIBLE
        backCallback.isEnabled = false
    }

    private fun cancelEditing() {
        padView.cancelEdit()
        finishEditing()
    }

    private fun saveEditing() {
        padView.saveLayout()
        finishEditing()
        Toast.makeText(this, R.string.settings_apply_done, Toast.LENGTH_SHORT).show()
    }

    // ------------------------------------------------------------------ UI parts

    private fun buildEditButton(): MaterialButton = MaterialButton(this).apply {
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
        setOnClickListener { startEditing() }
    }

    private fun overlayColor(view: View): Int = ColorUtils.setAlphaComponent(
        MaterialColors.getColor(
            view,
            com.google.android.material.R.attr.colorSurfaceContainerHigh
        ),
        235
    )

    private fun smallButton(label: String, outlined: Boolean, onClick: () -> Unit): MaterialButton {
        val button = if (outlined) {
            MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle)
        } else {
            MaterialButton(this)
        }
        return button.apply {
            text = label
            isAllCaps = false
            minHeight = 0
            minimumHeight = 0
            minWidth = 0
            minimumWidth = 0
            insetTop = 0
            insetBottom = 0
            cornerRadius = dp(16)
            setTextSize(13f)
            setPadding(dp(14), 0, dp(14), 0)
            setOnClickListener { onClick() }
        }
    }

    private fun buildEditBar(): MaterialCardView {
        val card = MaterialCardView(this).apply {
            radius = dp(22).toFloat()
            cardElevation = 0f
        }
        card.setCardBackgroundColor(overlayColor(card))
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(6), dp(8), dp(6))
        }
        row.addView(
            MaterialTextView(this).apply {
                text = getString(R.string.edit_hint)
                textSize = 11f
                maxWidth = dp(230)
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(10) }
        )
        val buttonParams = {
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(36)).apply {
                marginStart = dp(4)
            }
        }
        row.addView(smallButton(getString(R.string.settings_reset), true) {
            padView.resetLayoutToDefaults()
        }, buttonParams())
        row.addView(smallButton(getString(R.string.edit_cancel), true) {
            cancelEditing()
        }, buttonParams())
        row.addView(smallButton(getString(R.string.edit_save), false) {
            saveEditing()
        }, buttonParams())
        card.addView(row)
        return card
    }

    private fun buildPanel(): MaterialCardView {
        val card = MaterialCardView(this).apply {
            radius = dp(20).toFloat()
            cardElevation = 0f
        }
        card.setCardBackgroundColor(overlayColor(card))

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(10))
        }

        panelTitle = MaterialTextView(this).apply {
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
        }
        content.addView(panelTitle)

        showCheck = MaterialCheckBox(this).apply {
            text = getString(R.string.settings_show)
            setOnCheckedChangeListener { _, checked ->
                if (!populating) padView.updateSelected { it.copy(visible = checked) }
            }
        }
        content.addView(
            showCheck,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        val size = sliderRow(content, getString(R.string.settings_size), 60f, 160f, 5f) { v ->
            padView.updateSelected { it.copy(scale = (v / 100f).coerceIn(0.60f, 1.60f)) }
            sizeValue.text = getString(R.string.settings_size_value, v.roundToInt())
        }
        sizeValue = size.first
        sizeSlider = size.second

        stickOptions = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val dead = sliderRow(stickOptions, getString(R.string.settings_dead_zone), 0f, 35f, 1f) { v ->
            padView.updateSelected { it.copy(deadZone = (v / 100f).coerceIn(0f, 0.35f)) }
            deadZoneValue.text = "${v.roundToInt()}%"
        }
        deadZoneValue = dead.first
        deadZoneSlider = dead.second
        val sens = sliderRow(stickOptions, getString(R.string.settings_sensitivity), 50f, 150f, 5f) { v ->
            padView.updateSelected { it.copy(sensitivity = (v / 100f).coerceIn(0.5f, 1.5f)) }
            sensitivityValue.text = "${v.roundToInt()}%"
        }
        sensitivityValue = sens.first
        sensitivitySlider = sens.second
        content.addView(
            stickOptions,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        val scroll = ScrollView(this)
        scroll.addView(content)
        card.addView(scroll)
        return card
    }

    private fun sliderRow(
        parent: LinearLayout,
        label: String,
        from: Float,
        to: Float,
        step: Float,
        onChange: (Float) -> Unit
    ): Pair<MaterialTextView, Slider> {
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(
            MaterialTextView(this).apply {
                text = label
                textSize = 13f
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        val value = MaterialTextView(this).apply { textSize = 13f }
        header.addView(value)
        parent.addView(
            header,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(4) }
        )
        val slider = Slider(this).apply {
            valueFrom = from
            valueTo = to
            stepSize = step
            addOnChangeListener { _, v, fromUser ->
                if (fromUser && !populating) onChange(v)
            }
        }
        parent.addView(
            slider,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(40))
        )
        return value to slider
    }

    private fun panelParams(side: Int) = FrameLayout.LayoutParams(
        dp(280),
        FrameLayout.LayoutParams.WRAP_CONTENT,
        Gravity.CENTER_VERTICAL or side
    ).apply { setMargins(dp(12), 0, dp(12), 0) }

    // Dock the panel on the half of the screen opposite to the selected control.
    private fun positionPanel() {
        val x = padView.selectedSettings()?.x ?: return
        panel.layoutParams = panelParams(if (x < 0.5f) Gravity.RIGHT else Gravity.LEFT)
    }

    private fun populatePanel(group: ControlGroup) {
        val s = padView.selectedSettings() ?: return
        populating = true
        panelTitle.text = when (group) {
            ControlGroup.STICK -> getString(R.string.control_stick)
            ControlGroup.RIGHT_STICK -> getString(R.string.control_right_stick)
            ControlGroup.DPAD -> getString(R.string.control_dpad)
            ControlGroup.FACE -> getString(R.string.control_face)
            ControlGroup.LEFT_SHOULDER -> getString(R.string.control_left_shoulder)
            ControlGroup.RIGHT_SHOULDER -> getString(R.string.control_right_shoulder)
        }
        showCheck.isChecked = s.visible

        val size = snap(s.scale * 100f, 5f, 60f, 160f)
        sizeSlider.value = size
        sizeValue.text = getString(R.string.settings_size_value, size.roundToInt())

        val isStick = group == ControlGroup.STICK || group == ControlGroup.RIGHT_STICK
        stickOptions.visibility = if (isStick) View.VISIBLE else View.GONE
        if (isStick) {
            val dead = snap(s.deadZone * 100f, 1f, 0f, 35f)
            deadZoneSlider.value = dead
            deadZoneValue.text = "${dead.roundToInt()}%"
            val sens = snap(s.sensitivity * 100f, 5f, 50f, 150f)
            sensitivitySlider.value = sens
            sensitivityValue.text = "${sens.roundToInt()}%"
        }
        populating = false
    }

    // Slider throws if its value is not valueFrom + n * stepSize, so always snap first.
    private fun snap(value: Float, step: Float, min: Float, max: Float): Float =
        ((value / step).roundToInt() * step).coerceIn(min, max)

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
