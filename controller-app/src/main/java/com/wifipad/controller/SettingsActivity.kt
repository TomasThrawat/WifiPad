package com.wifipad.controller

import android.os.Bundle
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.MaterialColors
import com.google.android.material.slider.Slider
import com.google.android.material.textview.MaterialTextView
import kotlin.math.roundToInt

class SettingsActivity : AppCompatActivity() {

    private lateinit var preview: ControlEditorView
    private lateinit var selectedTitle: MaterialTextView
    private lateinit var positionLabel: MaterialTextView
    private lateinit var showCheckBox: MaterialCheckBox
    private lateinit var sizeSlider: Slider
    private lateinit var sizeValue: MaterialTextView
    private var selectedGroup: ControlGroup = ControlGroup.STICK

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
            text = getString(R.string.settings_editor_hint)
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
                bottomMargin = dp(14)
            }
        )

        val actionRow = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
        }

        val resetButton = MaterialButton(this).apply {
            text = getString(R.string.settings_reset)
            isAllCaps = false
            cornerRadius = dp(18)
            setOnClickListener {
                preview.resetToDefaults()
                selectGroup(selectedGroup)
                Toast.makeText(
                    context,
                    R.string.settings_reset_done,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        val applyButton = MaterialButton(this).apply {
            text = getString(R.string.settings_apply)
            isAllCaps = false
            cornerRadius = dp(18)
            setOnClickListener {
                preview.applyChanges()
                Toast.makeText(
                    context,
                    R.string.settings_apply_done,
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }

        actionRow.addView(
            resetButton,
            LinearLayout.LayoutParams(
                0,
                dp(52),
                1f
            ).apply {
                marginEnd = dp(6)
            }
        )
        actionRow.addView(
            applyButton,
            LinearLayout.LayoutParams(
                0,
                dp(52),
                1f
            ).apply {
                marginStart = dp(6)
            }
        )
        content.addView(
            actionRow,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(14)
            }
        )

        val previewCard = MaterialCardView(this).apply {
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

        preview = ControlEditorView(this).apply {
            setOnSelectionChangedListener { group ->
                selectGroup(group)
            }
        }
        previewCard.addView(
            preview,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dp(260)
            )
        )
        content.addView(
            previewCard,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(260)
            ).apply {
                bottomMargin = dp(14)
            }
        )

        val editorCard = MaterialCardView(this).apply {
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

        val editor = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
        }

        selectedTitle = MaterialTextView(this).apply {
            setTextAppearance(
                com.google.android.material.R.style.TextAppearance_Material3_TitleMedium
            )
        }
        editor.addView(
            selectedTitle,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        positionLabel = MaterialTextView(this).apply {
            setTextAppearance(
                com.google.android.material.R.style.TextAppearance_Material3_BodyMedium
            )
            setTextColor(
                MaterialColors.getColor(
                    this,
                    com.google.android.material.R.attr.colorOnSurfaceVariant
                )
            )
        }
        editor.addView(
            positionLabel,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(4)
                bottomMargin = dp(12)
            }
        )

        val showRow = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
        }

        showCheckBox = MaterialCheckBox(this).apply {
            text = getString(R.string.settings_show)
            isAllCaps = false
            setOnCheckedChangeListener { _, checked ->
                if (::preview.isInitialized) {
                    preview.setSelectedVisible(checked)
                }
            }
        }

        showRow.addView(
            showCheckBox,
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        )
        editor.addView(
            showRow,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(2)
            }
        )

        val sizeHeader = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
        }

        val sizeTitle = MaterialTextView(this).apply {
            text = getString(R.string.settings_size)
            setTextAppearance(
                com.google.android.material.R.style.TextAppearance_Material3_BodyLarge
            )
        }

        sizeValue = MaterialTextView(this).apply {
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

        sizeHeader.addView(
            sizeTitle,
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        )
        sizeHeader.addView(
            sizeValue,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        editor.addView(
            sizeHeader,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(6)
            }
        )

        sizeSlider = Slider(this).apply {
            valueFrom = 60f
            valueTo = 160f
            stepSize = 5f
            addOnChangeListener { _, value, fromUser ->
                if (!fromUser || !::preview.isInitialized) return@addOnChangeListener
                preview.setSelectedScale(value / 100f)
                updateEditorLabels()
            }
        }
        editor.addView(
            sizeSlider,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(44)
            )
        )

        editorCard.addView(editor)
        editorCard.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        content.addView(editorCard)

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
        selectGroup(ControlGroup.STICK)
    }

    private fun selectGroup(group: ControlGroup) {
        selectedGroup = group
        val current = preview.getSelectedSettings(group)

        selectedTitle.text = groupLabel(group)
        showCheckBox.setOnCheckedChangeListener(null)
        showCheckBox.isChecked = current.visible
        showCheckBox.setOnCheckedChangeListener { _, checked ->
            preview.setSelectedVisible(checked)
        }

        sizeSlider.value = current.scale * 100f

        updateEditorLabels()
    }

    private fun updateEditorLabels() {
        val current = preview.getSelectedSettings(selectedGroup)
        val scalePercent = (current.scale * 100f).roundToInt()
        val xPercent = (current.x * 100f).roundToInt()
        val yPercent = (current.y * 100f).roundToInt()

        sizeValue.text = getString(R.string.settings_size_value, scalePercent)
        positionLabel.text = getString(
            R.string.settings_position_value,
            xPercent,
            yPercent
        )
    }

    private fun groupLabel(group: ControlGroup): String = when (group) {
        ControlGroup.STICK -> getString(R.string.control_stick)
        ControlGroup.DPAD -> getString(R.string.control_dpad)
        ControlGroup.FACE -> getString(R.string.control_face)
        ControlGroup.LEFT_SHOULDER -> getString(R.string.control_left_shoulder)
        ControlGroup.RIGHT_SHOULDER -> getString(R.string.control_right_shoulder)
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
