package com.wifipad.controller

import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.MaterialColors
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import com.google.android.material.textview.MaterialTextView

class SettingsActivity : AppCompatActivity() {

    private val sliders = mutableMapOf<ControlGroup, List<Slider>>()
    private val valueLabels = mutableMapOf<ControlGroup, List<MaterialTextView>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DynamicColors.applyToActivityIfAvailable(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(
                MaterialColors.getColor(
                    this,
                    com.google.android.material.R.attr.colorSurface
                )
            )
        }

        val toolbar = MaterialToolbar(this).apply {
            title = getString(R.string.settings_title)
            elevation = 0f
            setNavigationOnClickListener { finish() }
        }
        root.addView(
            toolbar,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(64)
            )
        )

        val scroll = ScrollView(this).apply {
            isFillViewport = true
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(10), dp(20), dp(28))
        }

        val subtitle = MaterialTextView(this).apply {
            text = getString(R.string.settings_subtitle)
            setTextAppearance(
                com.google.android.material.R.style.TextAppearance_Material3_BodyLarge
            )
            setTextColor(
                MaterialColors.getColor(
                    this,
                    com.google.android.material.R.attr.colorOnSurfaceVariant
                )
            )
        }
        content.addView(
            subtitle,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(16)
            }
        )

        val resetButton = MaterialButton(this).apply {
            text = getString(R.string.settings_reset)
            isAllCaps = false
            cornerRadius = dp(18)
            setOnClickListener {
                ControlSettingsStore.reset(context)
                recreate()
                Toast.makeText(
                    context,
                    R.string.settings_reset_done,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        content.addView(
            resetButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(56)
            ).apply {
                bottomMargin = dp(18)
            }
        )

        ControlGroup.values().forEach { group ->
            content.addView(createGroupCard(group))
        }

        scroll.addView(content)
        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0
            ).apply {
                weight = 1f
            }
        )

        setContentView(root)
    }

    private fun createGroupCard(group: ControlGroup): MaterialCardView {
        val settings = ControlSettingsStore.load(this, group)

        val card = MaterialCardView(this).apply {
            radius = dp(24).toFloat()
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
            setPadding(dp(18), dp(18), dp(18), dp(18))
        }

        val header = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
        }

        val title = MaterialTextView(this).apply {
            text = groupLabel(group)
            setTextAppearance(
                com.google.android.material.R.style.TextAppearance_Material3_TitleMedium
            )
        }

        val toggle = MaterialSwitch(this).apply {
            isChecked = settings.visible
            text = getString(R.string.settings_show)
            isAllCaps = false
            setOnCheckedChangeListener { _, checked ->
                val current = ControlSettingsStore.load(context, group)
                ControlSettingsStore.save(
                    context,
                    group,
                    current.copy(visible = checked)
                )
                updateSliderState(group, checked)
            }
        }

        header.addView(
            title,
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        )
        header.addView(
            toggle,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        content.addView(
            header,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(8)
            }
        )

        val sizeSlider = createSlider(
            group,
            SliderKind.SIZE,
            settings.scale * 100f
        )
        val xSlider = createSlider(
            group,
            SliderKind.HORIZONTAL,
            settings.x * 100f
        )
        val ySlider = createSlider(
            group,
            SliderKind.VERTICAL,
            settings.y * 100f
        )
        sliders[group] = listOf(sizeSlider, xSlider, ySlider)

        val sizeLabel = createValueLabel(
            getString(R.string.settings_size_value, sizeSlider.value.roundToInt())
        )
        val xLabel = createValueLabel(
            getString(R.string.settings_horizontal_value, xSlider.value.roundToInt())
        )
        val yLabel = createValueLabel(
            getString(R.string.settings_vertical_value, ySlider.value.roundToInt())
        )
        valueLabels[group] = listOf(sizeLabel, xLabel, yLabel)

        addSettingRow(content, getString(R.string.settings_size), sizeLabel, sizeSlider)
        addSettingRow(
            content,
            getString(R.string.settings_horizontal),
            xLabel,
            xSlider
        )
        addSettingRow(
            content,
            getString(R.string.settings_vertical),
            yLabel,
            ySlider
        )

        updateSliderState(group, toggle.isChecked)

        card.addView(content)
        card.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = dp(14)
        }
        return card
    }

    private enum class SliderKind {
        SIZE, HORIZONTAL, VERTICAL
    }

    private fun createSlider(
        group: ControlGroup,
        kind: SliderKind,
        initialValue: Float
    ): Slider {
        return Slider(this).apply {
            when (kind) {
                SliderKind.SIZE -> {
                    valueFrom = 60f
                    valueTo = 160f
                    stepSize = 5f
                }
                SliderKind.HORIZONTAL -> {
                    valueFrom = 5f
                    valueTo = 95f
                    stepSize = 1f
                }
                SliderKind.VERTICAL -> {
                    valueFrom = 8f
                    valueTo = 92f
                    stepSize = 1f
                }
            }

            value = initialValue.coerceIn(valueFrom, valueTo)

            addOnChangeListener { _, value, fromUser ->
                if (!fromUser) return@addOnChangeListener

                val current = ControlSettingsStore.load(context, group)
                val updated = when (kind) {
                    SliderKind.SIZE -> current.copy(scale = value / 100f)
                    SliderKind.HORIZONTAL -> current.copy(x = value / 100f)
                    SliderKind.VERTICAL -> current.copy(y = value / 100f)
                }
                ControlSettingsStore.save(context, group, updated)
                updateValueLabel(group, kind, value.roundToInt())
            }
        }
    }

    private fun addSettingRow(
        parent: LinearLayout,
        title: String,
        value: MaterialTextView,
        slider: Slider
    ) {
        val rowTitle = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
        }

        val titleView = MaterialTextView(this).apply {
            text = title
            setTextAppearance(
                com.google.android.material.R.style.TextAppearance_Material3_BodyLarge
            )
        }

        rowTitle.addView(
            titleView,
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        )
        rowTitle.addView(
            value,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        parent.addView(
            rowTitle,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(8)
            }
        )
        parent.addView(
            slider,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(44)
            )
        )
    }

    private fun createValueLabel(text: String) =
        MaterialTextView(this).apply {
            this.text = text
            gravity = Gravity.END
            setTextAppearance(
                com.google.android.material.R.style.TextAppearance_Material3_LabelLarge
            )
            setTextColor(
                MaterialColors.getColor(
                    this,
                    com.google.android.material.R.attr.colorOnPrimaryContainer
                )
            )
        }

    private fun updateValueLabel(group: ControlGroup, kind: SliderKind, value: Int) {
        val index = kind.ordinal
        valueLabels[group]?.getOrNull(index)?.text = when (kind) {
            SliderKind.SIZE -> getString(R.string.settings_size_value, value)
            SliderKind.HORIZONTAL -> getString(R.string.settings_horizontal_value, value)
            SliderKind.VERTICAL -> getString(R.string.settings_vertical_value, value)
        }
    }

    private fun updateSliderState(group: ControlGroup, enabled: Boolean) {
        sliders[group]?.forEach { it.isEnabled = enabled }
    }

    private fun groupLabel(group: ControlGroup) = when (group) {
        ControlGroup.STICK -> getString(R.string.control_stick)
        ControlGroup.DPAD -> getString(R.string.control_dpad)
        ControlGroup.FACE -> getString(R.string.control_face)
        ControlGroup.SHOULDERS -> getString(R.string.control_shoulders)
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun Float.roundToInt(): Int =
        kotlin.math.round(this).toInt()
}
