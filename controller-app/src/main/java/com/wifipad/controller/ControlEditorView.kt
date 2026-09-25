package com.wifipad.controller

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import com.google.android.material.color.MaterialColors
import kotlin.math.hypot
import kotlin.math.min

class ControlEditorView(context: Context) : View(context) {

    private val values = mutableMapOf<ControlGroup, ControlSettings>()
    private var selectedGroup = ControlGroup.STICK
    private var activePointerId = -1
    private var dragOffsetX = 0f
    private var dragOffsetY = 0f
    private var moved = false
    private var selectionListener: ((ControlGroup) -> Unit)? = null

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val controlPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hiddenPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }
    private val selectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2)
    }

    private val surfaceColor by lazy {
        MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface)
    }
    private val surfaceContainerColor by lazy {
        MaterialColors.getColor(
            this,
            com.google.android.material.R.attr.colorSurfaceContainerHighest
        )
    }
    private val primaryContainerColor by lazy {
        MaterialColors.getColor(
            this,
            com.google.android.material.R.attr.colorPrimaryContainer
        )
    }
    private val onSurfaceColor by lazy {
        MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface)
    }
    private val onPrimaryContainerColor by lazy {
        MaterialColors.getColor(
            this,
            com.google.android.material.R.attr.colorOnPrimaryContainer
        )
    }
    private val primaryColor by lazy {
        MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary)
    }

    init {
        resetToDefaults()
    }

    fun setOnSelectionChangedListener(listener: (ControlGroup) -> Unit) {
        selectionListener = listener
    }

    fun getSelectedSettings(group: ControlGroup): ControlSettings =
        values[group] ?: ControlSettings(
            group.defaultVisible,
            group.defaultScale,
            group.defaultX,
            group.defaultY
        )

    fun setSelectedVisible(visible: Boolean) {
        values[selectedGroup] = getSelectedSettings(selectedGroup).copy(visible = visible)
        invalidate()
    }

    fun setSelectedScale(scale: Float) {
        values[selectedGroup] = getSelectedSettings(selectedGroup).copy(
            scale = scale.coerceIn(0.60f, 1.60f)
        )
        invalidate()
    }

    fun resetToDefaults() {
        values.clear()
        ControlGroup.values().forEach { group ->
            values[group] = ControlSettings(
                visible = group.defaultVisible,
                scale = group.defaultScale,
                x = group.defaultX,
                y = group.defaultY
            )
        }
        selectedGroup = ControlGroup.STICK
        activePointerId = -1
        moved = false
        invalidate()
    }

    fun applyChanges() {
        ControlGroup.values().forEach { group ->
            ControlSettingsStore.save(
                context,
                group,
                getSelectedSettings(group)
            )
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        backgroundPaint.color = surfaceColor
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)

        val size = min(width, height).toFloat()
        controlPaint.color = surfaceContainerColor
        hiddenPaint.color = primaryContainerColor
        textPaint.textSize = (size * 0.038f).coerceIn(11f, 22f)
        textPaint.color = onSurfaceColor
        selectionPaint.color = primaryColor

        ControlGroup.values().forEach { group ->
            drawGroup(canvas, group, getSelectedSettings(group), size)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val index = event.actionIndex
                val hit = findGroupAt(event.getX(index), event.getY(index))
                if (hit != null) {
                    selectGroup(hit)
                    activePointerId = event.getPointerId(index)
                    val center = getCenter(hit, getSelectedSettings(hit))
                    dragOffsetX = event.getX(index) - center.first
                    dragOffsetY = event.getY(index) - center.second
                    moved = false
                    parent.requestDisallowInterceptTouchEvent(true)
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (activePointerId == -1) return true
                val index = event.findPointerIndex(activePointerId)
                if (index < 0) return true

                val x = event.getX(index) - dragOffsetX
                val y = event.getY(index) - dragOffsetY

                if (hypot(
                        (x - getCenter(selectedGroup, getSelectedSettings(selectedGroup)).first).toDouble(),
                        (y - getCenter(selectedGroup, getSelectedSettings(selectedGroup)).second).toDouble()
                    ) > dp(2)
                ) {
                    moved = true
                }

                moveSelectedTo(x, y, size = min(width, height).toFloat())
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                if (event.getPointerId(event.actionIndex) == activePointerId) {
                    activePointerId = -1
                    parent.requestDisallowInterceptTouchEvent(false)
                    moved = false
                }
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                activePointerId = -1
                parent.requestDisallowInterceptTouchEvent(false)
                moved = false
                return true
            }
        }

        return true
    }

    private fun selectGroup(group: ControlGroup) {
        selectedGroup = group
        invalidate()
        selectionListener?.invoke(group)
    }

    private fun moveSelectedTo(x: Float, y: Float, size: Float) {
        val current = getSelectedSettings(selectedGroup)
        val halfX = controlHalfWidth(selectedGroup, current, size)
        val halfY = controlHalfHeight(selectedGroup, current, size)

        val safeLeft = halfX + dp(6)
        val safeRight = (width - halfX - dp(6)).coerceAtLeast(safeLeft)
        val safeTop = halfY + dp(6)
        val safeBottom = (height - halfY - dp(6)).coerceAtLeast(safeTop)

        val clampedX = x.coerceIn(safeLeft, safeRight)
        val clampedY = y.coerceIn(safeTop, safeBottom)

        values[selectedGroup] = current.copy(
            x = (clampedX / width.toFloat()).coerceIn(0.05f, 0.95f),
            y = (clampedY / height.toFloat()).coerceIn(0.08f, 0.92f)
        )
        invalidate()
        selectionListener?.invoke(selectedGroup)
    }

    private fun findGroupAt(x: Float, y: Float): ControlGroup? {
        val size = min(width, height).toFloat()

        ControlGroup.values()
            .reversed()
            .forEach { group ->
                val settings = getSelectedSettings(group)
                when (group) {
                    ControlGroup.STICK -> {
                        val center = getCenter(group, settings)
                        val radius = size * 0.19f * settings.scale
                        if (hypot(
                                (x - center.first).toDouble(),
                                (y - center.second).toDouble()
                            ) <= radius * 1.30
                        ) return group
                    }

                    ControlGroup.DPAD,
                    ControlGroup.FACE -> {
                        val button = if (group == ControlGroup.DPAD) {
                            size * 0.072f * settings.scale
                        } else {
                            size * 0.075f * settings.scale
                        }
                        val gap = if (group == ControlGroup.DPAD) {
                            size * 0.020f * settings.scale
                        } else {
                            size * 0.020f * settings.scale
                        }
                        val spacing = 2f * button + gap
                        val center = getCenter(group, settings)
                        if (RectF(
                                center.first - spacing - button,
                                center.second - spacing - button,
                                center.first + spacing + button,
                                center.second + spacing + button
                            ).contains(x, y)
                        ) return group
                    }

                    ControlGroup.LEFT_SHOULDER,
                    ControlGroup.RIGHT_SHOULDER -> {
                        val half = size * 0.060f * settings.scale
                        val gap = size * 0.11f * settings.scale
                        val center = getCenter(group, settings)
                        if (RectF(
                                center.first - half,
                                center.second - gap / 2f - half,
                                center.first + half,
                                center.second + gap / 2f + half
                            ).contains(x, y)
                        ) return group
                    }
                }
            }

        return null
    }

    private fun drawGroup(
        canvas: Canvas,
        group: ControlGroup,
        settings: ControlSettings,
        size: Float
    ) {
        val alpha = if (settings.visible) 255 else 65

        when (group) {
            ControlGroup.STICK -> drawStick(canvas, settings, size, alpha)
            ControlGroup.DPAD -> drawDpad(canvas, settings, size, alpha)
            ControlGroup.FACE -> drawFace(canvas, settings, size, alpha)
            ControlGroup.LEFT_SHOULDER,
            ControlGroup.RIGHT_SHOULDER -> drawShoulders(canvas, group, settings, size, alpha)
        }

        if (group == selectedGroup) {
            drawSelection(canvas, group, settings, size)
        }
    }

    private fun drawStick(
        canvas: Canvas,
        settings: ControlSettings,
        size: Float,
        alpha: Int
    ) {
        val center = getCenter(ControlGroup.STICK, settings)
        val radius = size * 0.19f * settings.scale
        controlPaint.alpha = alpha
        canvas.drawCircle(center.first, center.second, radius, controlPaint)
        controlPaint.color = primaryContainerColor
        controlPaint.alpha = if (settings.visible) 255 else 90
        canvas.drawCircle(
            center.first,
            center.second,
            radius * 0.48f,
            controlPaint
        )
        controlPaint.color = surfaceContainerColor
        controlPaint.alpha = alpha
    }

    private fun drawDpad(
        canvas: Canvas,
        settings: ControlSettings,
        size: Float,
        alpha: Int
    ) {
        val button = size * 0.072f * settings.scale
        val gap = size * 0.020f * settings.scale
        val spacing = 2f * button + gap
        val center = getCenter(ControlGroup.DPAD, settings)
        controlPaint.alpha = alpha

        drawSquare(canvas, center.first, center.second - spacing, button, controlPaint, "↑", alpha)
        drawSquare(canvas, center.first + spacing, center.second, button, controlPaint, "→", alpha)
        drawSquare(canvas, center.first, center.second + spacing, button, controlPaint, "↓", alpha)
        drawSquare(canvas, center.first - spacing, center.second, button, controlPaint, "←", alpha)
    }

    private fun drawFace(
        canvas: Canvas,
        settings: ControlSettings,
        size: Float,
        alpha: Int
    ) {
        val button = size * 0.075f * settings.scale
        val gap = size * 0.020f * settings.scale
        val spacing = 2f * button + gap
        val center = getCenter(ControlGroup.FACE, settings)
        controlPaint.alpha = alpha

        drawSquare(canvas, center.first, center.second - spacing, button, controlPaint, "Y", alpha)
        drawSquare(canvas, center.first + spacing, center.second, button, controlPaint, "B", alpha)
        drawSquare(canvas, center.first, center.second + spacing, button, controlPaint, "A", alpha)
        drawSquare(canvas, center.first - spacing, center.second, button, controlPaint, "X", alpha)
    }

    private fun drawShoulders(
        canvas: Canvas,
        group: ControlGroup,
        settings: ControlSettings,
        size: Float,
        alpha: Int
    ) {
        val half = size * 0.060f * settings.scale
        val gap = size * 0.11f * settings.scale
        val center = getCenter(group, settings)
        val topLabel = if (group == ControlGroup.LEFT_SHOULDER) "L1" else "R1"
        val bottomLabel = if (group == ControlGroup.LEFT_SHOULDER) "L2" else "R2"

        controlPaint.alpha = alpha
        drawCircleButton(
            canvas,
            center.first,
            center.second - gap / 2f,
            half,
            controlPaint,
            topLabel,
            alpha
        )
        drawCircleButton(
            canvas,
            center.first,
            center.second + gap / 2f,
            half,
            controlPaint,
            bottomLabel,
            alpha
        )
    }

    private fun drawSquare(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        half: Float,
        paint: Paint,
        label: String,
        alpha: Int
    ) {
        paint.alpha = alpha
        val rect = RectF(cx - half, cy - half, cx + half, cy + half)
        canvas.drawRoundRect(rect, half * 0.32f, half * 0.32f, paint)
        val labelPaint = Paint(textPaint).apply {
            this.alpha = alpha
            color = if (alpha < 100) onSurfaceColor else onSurfaceColor
        }
        canvas.drawText(
            label,
            cx,
            cy - (labelPaint.ascent() + labelPaint.descent()) / 2f,
            labelPaint
        )
    }

    private fun drawCircleButton(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        paint: Paint,
        label: String,
        alpha: Int
    ) {
        paint.alpha = alpha
        canvas.drawCircle(cx, cy, radius, paint)
        val labelPaint = Paint(textPaint).apply {
            this.alpha = alpha
        }
        canvas.drawText(
            label,
            cx,
            cy - (labelPaint.ascent() + labelPaint.descent()) / 2f,
            labelPaint
        )
    }

    private fun drawSelection(
        canvas: Canvas,
        group: ControlGroup,
        settings: ControlSettings,
        size: Float
    ) {
        val center = getCenter(group, settings)
        val halfX = controlHalfWidth(group, settings, size)
        val halfY = controlHalfHeight(group, settings, size)
        val padding = dp(7)

        canvas.drawRoundRect(
            RectF(
                center.first - halfX - padding,
                center.second - halfY - padding,
                center.first + halfX + padding,
                center.second + halfY + padding
            ),
            dp(12).toFloat(),
            dp(12).toFloat(),
            selectionPaint
        )
    }

    private fun getCenter(
        group: ControlGroup,
        settings: ControlSettings
    ): Pair<Float, Float> =
        Pair(width * settings.x, height * settings.y)

    private fun controlHalfWidth(
        group: ControlGroup,
        settings: ControlSettings,
        size: Float
    ): Float = when (group) {
        ControlGroup.STICK -> size * 0.19f * settings.scale
        ControlGroup.DPAD -> {
            val button = size * 0.072f * settings.scale
            val gap = size * 0.020f * settings.scale
            3f * button + gap
        }
        ControlGroup.FACE -> {
            val button = size * 0.075f * settings.scale
            val gap = size * 0.020f * settings.scale
            3f * button + gap
        }
        ControlGroup.LEFT_SHOULDER,
        ControlGroup.RIGHT_SHOULDER -> size * 0.060f * settings.scale
    }

    private fun controlHalfHeight(
        group: ControlGroup,
        settings: ControlSettings,
        size: Float
    ): Float = when (group) {
        ControlGroup.STICK -> size * 0.19f * settings.scale
        ControlGroup.DPAD -> {
            val button = size * 0.072f * settings.scale
            val gap = size * 0.020f * settings.scale
            3f * button + gap
        }
        ControlGroup.FACE -> {
            val button = size * 0.075f * settings.scale
            val gap = size * 0.020f * settings.scale
            3f * button + gap
        }
        ControlGroup.LEFT_SHOULDER,
        ControlGroup.RIGHT_SHOULDER -> {
            val half = size * 0.060f * settings.scale
            val gap = size * 0.11f * settings.scale
            half + gap / 2f
        }
    }

    private fun dp(value: Int): Float =
        value * resources.displayMetrics.density
}
