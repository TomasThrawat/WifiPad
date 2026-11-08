package com.wifipad.controller

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import com.google.android.material.color.MaterialColors
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

class GamepadView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    val state = GamepadState()

    private data class Circle(val cx: Float, val cy: Float, val r: Float)
    private data class Rect2(
        val r: RectF,
        val bit: Int,
        val label: String,
        val circle: Boolean = false,
        val group: ControlGroup
    )

    private var stickBase = Circle(0f, 0f, 0f)
    private var stickPointer = -1
    private var stickKnob = PointF(0f, 0f)
    private var rightStickBase = Circle(0f, 0f, 0f)
    private var rightStickPointer = -1
    private var rightStickKnob = PointF(0f, 0f)

    private val faceButtons = mutableListOf<Rect2>()
    private val dpadButtons = mutableListOf<Rect2>()
    private val shoulderButtons = mutableListOf<Rect2>()
    private val triggerButtons = mutableListOf<Rect2>()
    private val systemButtons = mutableListOf<Rect2>()
    private val activePointerToRect = mutableMapOf<Int, Rect2>()
    private val settings = mutableMapOf<ControlGroup, ControlSettings>()

    /** In edit mode the pad stops sending input and controls can be dragged/resized in place. */
    var editMode = false
        private set
    var onSelectionChanged: ((ControlGroup?) -> Unit)? = null
    private var selectedGroup: ControlGroup? = null
    private var dragPointer = -1
    private var dragOffsetX = 0f
    private var dragOffsetY = 0f
    private val density = resources.displayMetrics.density
    private val selectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        pathEffect = DashPathEffect(floatArrayOf(8f * density, 6f * density), 0f)
    }

    private companion object {
        const val HIDDEN_ALPHA = 70
    }

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stickBasePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stickKnobPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val buttonPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val buttonActivePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stickIndicatorPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }

    private val surfaceColor = android.graphics.Color.BLACK
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

    private class PointF(var x: Float, var y: Float)

    init {
        loadSettings()
    }

    fun reloadSettings() {
        loadSettings()
        resetInputState()
        if (width > 0 && height > 0) {
            recalculateLayout(width, height)
        }
        invalidate()
    }

    private fun loadSettings() {
        for (group in ControlGroup.values()) {
            settings[group] = ControlSettingsStore.load(context, group)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        recalculateLayout(w, h)
    }

    private fun recalculateLayout(w: Int, h: Int) {
        val s = min(w, h).toFloat()
        val stick = settings.getValue(ControlGroup.STICK)
        val rightStick = settings.getValue(ControlGroup.RIGHT_STICK)
        val dpad = settings.getValue(ControlGroup.DPAD)
        val face = settings.getValue(ControlGroup.FACE)
        val left = settings.getValue(ControlGroup.LEFT_SHOULDER)
        val right = settings.getValue(ControlGroup.RIGHT_SHOULDER)

        val stickRadius = s * 0.19f * stick.scale
        stickBase = Circle(w * stick.x, h * stick.y, stickRadius)
        stickKnob = PointF(stickBase.cx, stickBase.cy)

        val rightStickRadius = s * 0.19f * rightStick.scale
        rightStickBase = Circle(w * rightStick.x, h * rightStick.y, rightStickRadius)
        rightStickKnob = PointF(rightStickBase.cx, rightStickBase.cy)

        val dpadButton = s * 0.072f * dpad.scale
        val dpadGap = s * 0.020f * dpad.scale
        val dpadSpacing = 2f * dpadButton + dpadGap
        val dpadCx = w * dpad.x
        val dpadCy = h * dpad.y
        dpadButtons.clear()
        dpadButtons += Rect2(sq(dpadCx, dpadCy - dpadSpacing, dpadButton), 1, "↑", group = ControlGroup.DPAD)
        dpadButtons += Rect2(sq(dpadCx + dpadSpacing, dpadCy, dpadButton), 2, "→", group = ControlGroup.DPAD)
        dpadButtons += Rect2(sq(dpadCx, dpadCy + dpadSpacing, dpadButton), 4, "↓", group = ControlGroup.DPAD)
        dpadButtons += Rect2(sq(dpadCx - dpadSpacing, dpadCy, dpadButton), 8, "←", group = ControlGroup.DPAD)

        val faceButton = s * 0.075f * face.scale
        val faceGap = s * 0.020f * face.scale
        val faceSpacing = 2f * faceButton + faceGap
        val faceCx = w * face.x
        val faceCy = h * face.y
        faceButtons.clear()
        faceButtons += Rect2(sq(faceCx, faceCy - faceSpacing, faceButton), ButtonBit.Y, "Y", group = ControlGroup.FACE)
        faceButtons += Rect2(sq(faceCx + faceSpacing, faceCy, faceButton), ButtonBit.B, "B", group = ControlGroup.FACE)
        faceButtons += Rect2(sq(faceCx, faceCy + faceSpacing, faceButton), ButtonBit.A, "A", group = ControlGroup.FACE)
        faceButtons += Rect2(sq(faceCx - faceSpacing, faceCy, faceButton), ButtonBit.X, "X", group = ControlGroup.FACE)

        systemButtons.clear()
        val startWidth = s * 0.16f
        val startHeight = s * 0.07f
        val startCx = w / 2f
        val startCy = h * 0.22f
        systemButtons += Rect2(
            RectF(
                startCx - startWidth / 2f,
                startCy - startHeight / 2f,
                startCx + startWidth / 2f,
                startCy + startHeight / 2f
            ),
            ButtonBit.START,
            "START",
            group = ControlGroup.FACE
        )

        shoulderButtons.clear()
        triggerButtons.clear()

        val shoulderHalfLeft = s * 0.060f * left.scale
        val shoulderHalfRight = s * 0.060f * right.scale
        val shoulderGapLeft = s * 0.18f * left.scale
        val shoulderGapRight = s * 0.18f * right.scale

        val leftX = w * left.x
        val leftCenterY = h * left.y
        val leftY1 = (leftCenterY - shoulderGapLeft / 2f).coerceIn(shoulderHalfLeft, h - shoulderHalfLeft)
        val leftY2 = (leftCenterY + shoulderGapLeft / 2f).coerceIn(shoulderHalfLeft, h - shoulderHalfLeft)

        shoulderButtons += Rect2(
            sq(leftX, leftY1, shoulderHalfLeft),
            ButtonBit.L1,
            "L1",
            true,
            ControlGroup.LEFT_SHOULDER
        )
        val leftTriggerHalfWidth = shoulderHalfLeft * 0.85f
        val leftTriggerHalfHeight = shoulderHalfLeft * 1.25f
        triggerButtons += Rect2(
            RectF(leftX - leftTriggerHalfWidth, leftY2 - leftTriggerHalfHeight, leftX + leftTriggerHalfWidth, leftY2 + leftTriggerHalfHeight),
            -1,
            "L2",
            group = ControlGroup.LEFT_SHOULDER
        )

        val rightX = w * right.x
        val rightCenterY = h * right.y
        val rightY1 = (rightCenterY - shoulderGapRight / 2f).coerceIn(shoulderHalfRight, h - shoulderHalfRight)
        val rightY2 = (rightCenterY + shoulderGapRight / 2f).coerceIn(shoulderHalfRight, h - shoulderHalfRight)

        shoulderButtons += Rect2(
            sq(rightX, rightY1, shoulderHalfRight),
            ButtonBit.R1,
            "R1",
            true,
            ControlGroup.RIGHT_SHOULDER
        )
        val rightTriggerHalfWidth = shoulderHalfRight * 0.85f
        val rightTriggerHalfHeight = shoulderHalfRight * 1.25f
        triggerButtons += Rect2(
            RectF(rightX - rightTriggerHalfWidth, rightY2 - rightTriggerHalfHeight, rightX + rightTriggerHalfWidth, rightY2 + rightTriggerHalfHeight),
            -2,
            "R2",
            group = ControlGroup.RIGHT_SHOULDER
        )

        bgPaint.color = surfaceColor
        stickBasePaint.color = surfaceContainerColor
        stickKnobPaint.color = primaryContainerColor
        buttonPaint.color = surfaceContainerColor
        buttonActivePaint.color = primaryContainerColor
        textPaint.color = onSurfaceColor
        textPaint.textSize = (s * 0.040f).coerceIn(24f, 44f)
        stickIndicatorPaint.color = onPrimaryContainerColor
        stickIndicatorPaint.alpha = 90
    }

    private fun sq(cx: Float, cy: Float, half: Float): RectF =
        RectF(cx - half, cy - half, cx + half, cy + half)

    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        systemButtons.forEach { drawButton(canvas, it) }

        ControlGroup.values().forEach { group ->
            val visible = settings.getValue(group).visible
            if (!visible && !editMode) return@forEach
            // Hidden controls stay visible (dimmed) while editing so they can be shown again.
            val saved = if (visible) -1 else
                canvas.saveLayerAlpha(0f, 0f, width.toFloat(), height.toFloat(), HIDDEN_ALPHA)
            drawGroup(canvas, group)
            if (saved >= 0) canvas.restoreToCount(saved)
        }

        if (editMode) selectedGroup?.let { drawSelection(canvas, it) }
    }

    private fun drawGroup(canvas: Canvas, group: ControlGroup) {
        when (group) {
            ControlGroup.STICK -> drawStick(canvas, stickBase, stickKnob)
            ControlGroup.RIGHT_STICK -> drawStick(canvas, rightStickBase, rightStickKnob)
            ControlGroup.DPAD -> dpadButtons.forEach { drawButton(canvas, it) }
            ControlGroup.FACE -> faceButtons.forEach { drawButton(canvas, it) }
            ControlGroup.LEFT_SHOULDER, ControlGroup.RIGHT_SHOULDER -> {
                shoulderButtons.filter { it.group == group }.forEach { drawButton(canvas, it) }
                triggerButtons.filter { it.group == group }.forEach { drawButton(canvas, it) }
            }
        }
    }

    private fun drawStick(canvas: Canvas, base: Circle, knob: PointF) {
        canvas.drawCircle(base.cx, base.cy, base.r, stickBasePaint)
        canvas.drawCircle(knob.x, knob.y, base.r * 0.48f, stickKnobPaint)

        canvas.drawCircle(knob.x, knob.y, base.r * 0.20f, stickIndicatorPaint)
    }

    private fun drawButton(canvas: Canvas, r: Rect2) {
        val pressed = activePointerToRect.containsValue(r)
        val paint = if (pressed) buttonActivePaint else buttonPaint

        if (r.circle) {
            val radius = min(r.r.width(), r.r.height()) / 2f
            canvas.drawCircle(r.r.centerX(), r.r.centerY(), radius, paint)
        } else {
            val cornerRadius = min(r.r.width(), r.r.height()) * 0.32f
            canvas.drawRoundRect(r.r, cornerRadius, cornerRadius, paint)
        }

        val labelPaint = if (pressed) {
            Paint(textPaint).apply { color = onPrimaryContainerColor }
        } else {
            textPaint
        }

        canvas.drawText(
            r.label,
            r.r.centerX(),
            r.r.centerY() - (labelPaint.ascent() + labelPaint.descent()) / 2f,
            labelPaint
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (editMode) {
            handleEditTouch(event)
            invalidate()
            return true
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_POINTER_DOWN -> handleDown(event, event.actionIndex)
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) handleMove(event, i)
            }
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_POINTER_UP -> handleUp(event, event.actionIndex)
            MotionEvent.ACTION_CANCEL -> resetInputState()
        }

        invalidate()
        return true
    }

    private fun handleDown(event: MotionEvent, index: Int) {
        val id = event.getPointerId(index)
        val x = event.getX(index)
        val y = event.getY(index)

        if (settings.getValue(ControlGroup.STICK).visible &&
            inCircle(x, y, stickBase) &&
            stickPointer == -1
        ) {
            stickPointer = id
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            updateStick(x, y, stickBase, stickKnob, settings.getValue(ControlGroup.STICK)) { dx, dy ->
                state.leftX = dx
                state.leftY = dy
            }
            return
        }

        if (settings.getValue(ControlGroup.RIGHT_STICK).visible &&
            inCircle(x, y, rightStickBase) &&
            rightStickPointer == -1
        ) {
            rightStickPointer = id
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            updateStick(x, y, rightStickBase, rightStickKnob, settings.getValue(ControlGroup.RIGHT_STICK)) { dx, dy ->
                state.rightX = dx
                state.rightY = dy
            }
            return
        }

        val rect = findRect(x, y) ?: return
        activePointerToRect[id] = rect
        applyRect(rect, true, y)
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    private fun handleMove(event: MotionEvent, index: Int) {
        val id = event.getPointerId(index)
        val x = event.getX(index)
        val y = event.getY(index)

        when (id) {
            stickPointer -> updateStick(x, y, stickBase, stickKnob, settings.getValue(ControlGroup.STICK)) { dx, dy ->
                state.leftX = dx
                state.leftY = dy
            }
            rightStickPointer -> updateStick(x, y, rightStickBase, rightStickKnob, settings.getValue(ControlGroup.RIGHT_STICK)) { dx, dy ->
                state.rightX = dx
                state.rightY = dy
            }
        }
        activePointerToRect[id]?.takeIf { it.bit == -1 || it.bit == -2 }?.let {
            applyTrigger(it, y)
        }
    }

    private fun handleUp(event: MotionEvent, index: Int) {
        val id = event.getPointerId(index)

        if (id == stickPointer) {
            stickPointer = -1
            stickKnob = PointF(stickBase.cx, stickBase.cy)
            state.leftX = 0
            state.leftY = 0
        }
        if (id == rightStickPointer) {
            rightStickPointer = -1
            rightStickKnob = PointF(rightStickBase.cx, rightStickBase.cy)
            state.rightX = 0
            state.rightY = 0
        }

        activePointerToRect.remove(id)?.let { applyRect(it, false) }
    }

    private fun applyRect(r: Rect2, pressed: Boolean, touchY: Float = Float.NaN) {
        when {
            r.bit == -1 -> state.leftTrigger = if (pressed) triggerValue(r, touchY) else 0
            r.bit == -2 -> state.rightTrigger = if (pressed) triggerValue(r, touchY) else 0
            r.bit == ButtonBit.L1 || r.bit == ButtonBit.R1 ->
                state.setButton(r.bit, pressed)
            dpadButtons.contains(r) -> syncDpadState()
            else -> state.setButton(
                r.bit,
                pressed || activePointerToRect.values.any { it.bit == r.bit }
            )
        }
    }

    private fun triggerValue(rect: Rect2, y: Float): Int {
        if (!y.isFinite()) return 0
        return (((y - rect.r.top) / rect.r.height()).coerceIn(0f, 1f) * 255f).roundToInt()
    }

    private fun applyTrigger(rect: Rect2, y: Float) {
        val value = triggerValue(rect, y)
        if (rect.bit == -1) state.leftTrigger = value else if (rect.bit == -2) state.rightTrigger = value
    }

    private fun syncDpadState() {
        val mask = activePointerToRect.values
            .filter { dpadButtons.contains(it) }
            .fold(0) { value, rect -> value or rect.bit }
        state.dpad = DpadEncoding.fromCardinalMask(mask)
    }

    private fun findRect(x: Float, y: Float): Rect2? {
        systemButtons.firstOrNull { it.r.contains(x, y) }?.let { return it }

        val allLists = listOf(dpadButtons, faceButtons, shoulderButtons, triggerButtons)
        for (list in allLists) {
            for (r in list) {
                if (settings.getValue(r.group).visible && r.r.contains(x, y)) return r
            }
        }
        return null
    }

    private fun inCircle(x: Float, y: Float, c: Circle): Boolean =
        hypot((x - c.cx).toDouble(), (y - c.cy).toDouble()) <= c.r * 1.25

    private fun updateStick(
        x: Float,
        y: Float,
        base: Circle,
        knob: PointF,
        inputSettings: ControlSettings,
        apply: (Byte, Byte) -> Unit
    ) {
        var dx = x - base.cx
        var dy = y - base.cy
        val dist = hypot(dx.toDouble(), dy.toDouble()).toFloat()

        if (dist > base.r) {
            val scale = base.r / dist
            dx *= scale
            dy *= scale
        }

        val output = AnalogStickMapping.map(
            normalizedX = dx / base.r,
            normalizedY = dy / base.r,
            deadZone = inputSettings.deadZone,
            sensitivity = inputSettings.sensitivity
        )
        knob.x = base.cx + dx
        knob.y = base.cy + dy
        apply(output.x, output.y)
    }

    private fun resetInputState() {
        stickPointer = -1
        rightStickPointer = -1
        activePointerToRect.clear()
        state.dpad = 0
        state.buttons = 0
        state.leftTrigger = 0
        state.rightTrigger = 0
        state.leftX = 0
        state.leftY = 0
        state.rightX = 0
        state.rightY = 0
        stickKnob = PointF(stickBase.cx, stickBase.cy)
        rightStickKnob = PointF(rightStickBase.cx, rightStickBase.cy)
    }

    // ---------------------------------------------------------------------
    // In-place layout editing (single layout, stored in ControlSettingsStore)
    // ---------------------------------------------------------------------

    fun enterEditMode() {
        resetInputState()
        editMode = true
        dragPointer = -1
        select(null)
    }

    fun saveLayout() {
        for (group in ControlGroup.values()) {
            ControlSettingsStore.save(context, group, settings.getValue(group))
        }
        exitEditMode()
    }

    fun cancelEdit() {
        loadSettings()
        exitEditMode()
    }

    fun resetLayoutToDefaults() {
        for (group in ControlGroup.values()) {
            settings[group] = ControlSettings(
                visible = group.defaultVisible,
                scale = group.defaultScale,
                x = group.defaultX,
                y = group.defaultY
            )
        }
        refreshLayout()
        invalidate()
        selectedGroup?.let { onSelectionChanged?.invoke(it) }
    }

    fun selectedSettings(): ControlSettings? = selectedGroup?.let { settings[it] }

    fun updateSelected(transform: (ControlSettings) -> ControlSettings) {
        val group = selectedGroup ?: return
        settings[group] = transform(settings.getValue(group))
        refreshLayout()
        invalidate()
    }

    private fun exitEditMode() {
        editMode = false
        dragPointer = -1
        selectedGroup = null
        onSelectionChanged?.invoke(null)
        resetInputState()
        refreshLayout()
        invalidate()
    }

    private fun refreshLayout() {
        if (width > 0 && height > 0) recalculateLayout(width, height)
    }

    private fun select(group: ControlGroup?) {
        selectedGroup = group
        onSelectionChanged?.invoke(group)
        invalidate()
    }

    private fun handleEditTouch(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (dragPointer != -1) return
                val index = event.actionIndex
                val x = event.getX(index)
                val y = event.getY(index)
                val hit = groupAt(x, y)
                if (hit != selectedGroup) select(hit)
                if (hit != null) {
                    val current = settings.getValue(hit)
                    dragPointer = event.getPointerId(index)
                    dragOffsetX = x - width * current.x
                    dragOffsetY = y - height * current.y
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val index = event.findPointerIndex(dragPointer)
                if (dragPointer == -1 || index < 0) return
                moveSelectedTo(event.getX(index) - dragOffsetX, event.getY(index) - dragOffsetY)
            }
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_POINTER_UP -> {
                if (event.getPointerId(event.actionIndex) == dragPointer) dragPointer = -1
            }
            MotionEvent.ACTION_CANCEL -> dragPointer = -1
        }
    }

    private fun moveSelectedTo(anchorX: Float, anchorY: Float) {
        val group = selectedGroup ?: return
        val current = settings.getValue(group)
        val bounds = groupBounds(group)
        val margin = 4f * density
        val dx = clampDelta(anchorX - width * current.x, margin - bounds.left, width - margin - bounds.right)
        val dy = clampDelta(anchorY - height * current.y, margin - bounds.top, height - margin - bounds.bottom)
        settings[group] = current.copy(
            x = ((width * current.x + dx) / width).coerceIn(0.05f, 0.95f),
            y = ((height * current.y + dy) / height).coerceIn(0.08f, 0.92f)
        )
        refreshLayout()
        invalidate()
    }

    // Never force a control that already sits past the margin to jump; only limit further drift.
    private fun clampDelta(delta: Float, lo: Float, hi: Float): Float =
        delta.coerceIn(lo.coerceAtMost(0f), hi.coerceAtLeast(0f))

    private fun groupAt(x: Float, y: Float): ControlGroup? {
        val pad = 8f * density
        return ControlGroup.values().reversed().firstOrNull { group ->
            val b = groupBounds(group)
            RectF(b.left - pad, b.top - pad, b.right + pad, b.bottom + pad).contains(x, y)
        }
    }

    private fun groupBounds(group: ControlGroup): RectF = when (group) {
        ControlGroup.STICK -> circleBounds(stickBase)
        ControlGroup.RIGHT_STICK -> circleBounds(rightStickBase)
        ControlGroup.DPAD -> unionOf(dpadButtons.map { it.r })
        ControlGroup.FACE -> unionOf(faceButtons.map { it.r })
        ControlGroup.LEFT_SHOULDER, ControlGroup.RIGHT_SHOULDER ->
            unionOf((shoulderButtons + triggerButtons).filter { it.group == group }.map { it.r })
    }

    private fun circleBounds(c: Circle) = RectF(c.cx - c.r, c.cy - c.r, c.cx + c.r, c.cy + c.r)

    private fun unionOf(rects: List<RectF>): RectF {
        if (rects.isEmpty()) return RectF()
        val result = RectF(rects.first())
        for (i in 1 until rects.size) result.union(rects[i])
        return result
    }

    private fun drawSelection(canvas: Canvas, group: ControlGroup) {
        val b = groupBounds(group)
        val pad = 8f * density
        selectionPaint.color = onSurfaceColor
        canvas.drawRoundRect(
            b.left - pad, b.top - pad, b.right + pad, b.bottom + pad,
            12f * density, 12f * density, selectionPaint
        )
    }

    override fun onDetachedFromWindow() {
        resetInputState()
        super.onDetachedFromWindow()
    }
}
