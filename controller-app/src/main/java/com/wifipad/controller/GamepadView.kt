package com.wifipad.controller

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.google.android.material.color.MaterialColors
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.customview.widget.ExploreByTouchHelper
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
    private val stickClickButtons = mutableListOf<Rect2>()
    private val activePointerToRect = mutableMapOf<Int, Rect2>()
    private val settings = mutableMapOf<ControlGroup, ControlSettings>()
    private data class AccessibilityTarget(val id: Int, val bounds: RectF, val label: String, val bit: Int, val group: ControlGroup)
    private val accessibilityHandler = Handler(Looper.getMainLooper())
    private val accessibilityHelper = object : ExploreByTouchHelper(this) {
        override fun getVirtualViewAt(x: Float, y: Float): Int = accessibilityTargets()
            .firstOrNull { it.bounds.contains(x, y) }?.id ?: INVALID_ID

        override fun getVisibleVirtualViews(virtualViewIds: MutableList<Int>) {
            accessibilityTargets().forEach { virtualViewIds.add(it.id) }
        }

        override fun onPopulateNodeForVirtualView(virtualViewId: Int, node: AccessibilityNodeInfoCompat) {
            val target = accessibilityTargets().firstOrNull { it.id == virtualViewId }
            if (target == null) {
                node.contentDescription = "Controller control"
                node.setBoundsInParent(Rect(0, 0, 1, 1))
                return
            }
            node.className = android.widget.Button::class.java.name
            node.text = target.label
            node.contentDescription = when (target.group) {
                ControlGroup.STICK -> context.getString(R.string.accessibility_left_stick)
                ControlGroup.RIGHT_STICK -> context.getString(R.string.accessibility_right_stick)
                ControlGroup.DPAD -> context.getString(R.string.accessibility_dpad, target.label)
                else -> context.getString(R.string.accessibility_button, target.label)
            }
            node.setBoundsInParent(Rect(
                target.bounds.left.toInt(), target.bounds.top.toInt(),
                target.bounds.right.toInt(), target.bounds.bottom.toInt()
            ))
            node.isClickable = true
            node.addAction(AccessibilityNodeInfoCompat.ACTION_CLICK)
        }

        override fun onPerformActionForVirtualView(virtualViewId: Int, action: Int, arguments: android.os.Bundle?): Boolean {
            if (action != AccessibilityNodeInfoCompat.ACTION_CLICK) return false
            val target = accessibilityTargets().firstOrNull { it.id == virtualViewId } ?: return false
            when (target.group) {
                ControlGroup.DPAD -> {
                    val mask = when (target.bit) { 1 -> 1; 2 -> 2; 4 -> 4; 8 -> 8; else -> 0 }
                    state.dpad = DpadEncoding.fromCardinalMask(mask)
                    accessibilityHandler.postDelayed({ state.dpad = 0; invalidate() }, 160L)
                }
                else -> {
                    state.setButton(target.bit, true)
                    accessibilityHandler.postDelayed({ state.setButton(target.bit, false); invalidate() }, 160L)
                }
            }
            invalidate()
            return true
        }
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
        isClickable = true
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        ViewCompat.setAccessibilityDelegate(this, accessibilityHelper)
        loadSettings()
    }

    fun reloadSettings() {
        loadSettings()
        resetInputState()
        if (width > 0 && height > 0) {
            recalculateLayout(width, height)
        }
        invalidate()
        accessibilityHelper.invalidateRoot()
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
        val system = settings.getValue(ControlGroup.SYSTEM)
        val left = settings.getValue(ControlGroup.LEFT_SHOULDER)
        val right = settings.getValue(ControlGroup.RIGHT_SHOULDER)

        val stickRadius = s * 0.19f * stick.scale
        stickBase = Circle(w * stick.x, h * stick.y, stickRadius)
        stickKnob = PointF(stickBase.cx, stickBase.cy)
        val rightStickRadius = s * 0.19f * rightStick.scale
        rightStickBase = Circle(w * rightStick.x, h * rightStick.y, rightStickRadius)
        rightStickKnob = PointF(rightStickBase.cx, rightStickBase.cy)

        stickClickButtons.clear()
        val leftClickRadius = s * 0.035f * stick.scale
        stickClickButtons += Rect2(
            sq(stickBase.cx + stickBase.r * 0.55f, stickBase.cy + stickBase.r * 0.55f, leftClickRadius),
            ButtonMappingStore.bit(context, "L3"), "L3", true, ControlGroup.STICK
        )
        val rightClickRadius = s * 0.035f * rightStick.scale
        stickClickButtons += Rect2(
            sq(rightStickBase.cx + rightStickBase.r * 0.55f, rightStickBase.cy + rightStickBase.r * 0.55f, rightClickRadius),
            ButtonMappingStore.bit(context, "R3"), "R3", true, ControlGroup.RIGHT_STICK
        )

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
        faceButtons += Rect2(sq(faceCx, faceCy - faceSpacing, faceButton), ButtonMappingStore.bit(context, "Y"), "Y", group = ControlGroup.FACE)
        faceButtons += Rect2(sq(faceCx + faceSpacing, faceCy, faceButton), ButtonMappingStore.bit(context, "B"), "B", group = ControlGroup.FACE)
        faceButtons += Rect2(sq(faceCx, faceCy + faceSpacing, faceButton), ButtonMappingStore.bit(context, "A"), "A", group = ControlGroup.FACE)
        faceButtons += Rect2(sq(faceCx - faceSpacing, faceCy, faceButton), ButtonMappingStore.bit(context, "X"), "X", group = ControlGroup.FACE)

        systemButtons.clear()
        val systemButtonWidth = s * 0.145f * system.scale
        val systemButtonHeight = s * 0.07f * system.scale
        val systemGap = s * 0.025f * system.scale
        val systemCx = w * system.x
        val systemCy = h * system.y
        listOf(
            ButtonMappingStore.bit(context, "SELECT") to "SELECT",
            ButtonMappingStore.bit(context, "MODE") to "MODE",
            ButtonMappingStore.bit(context, "START") to "START"
        ).forEachIndexed { index, (bit, label) ->
            val cx = systemCx + (index - 1) * (systemButtonWidth + systemGap)
            systemButtons += Rect2(
                RectF(cx - systemButtonWidth / 2f, systemCy - systemButtonHeight / 2f,
                    cx + systemButtonWidth / 2f, systemCy + systemButtonHeight / 2f),
                bit, label, group = ControlGroup.SYSTEM
            )
        }

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
            ButtonMappingStore.bit(context, "L1"),
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
            ButtonMappingStore.bit(context, "R1"),
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
        accessibilityHelper.invalidateRoot()
    }

    private fun accessibilityTargets(): List<AccessibilityTarget> {
        val targets = mutableListOf<AccessibilityTarget>()
        var id = 1
        fun append(rect: Rect2) {
            if (settings[rect.group]?.visible == true) {
                targets += AccessibilityTarget(id++, RectF(rect.r), rect.label, rect.bit, rect.group)
            }
        }
        if (settings[ControlGroup.STICK]?.visible == true) {
            val click = stickClickButtons.firstOrNull { it.group == ControlGroup.STICK }
            if (click != null) append(click)
            val r = stickBase.r * 0.65f
            targets += AccessibilityTarget(id++, RectF(stickBase.cx-r, stickBase.cy-r, stickBase.cx+r, stickBase.cy+r), "Left stick", ButtonBit.L3, ControlGroup.STICK)
        }
        if (settings[ControlGroup.RIGHT_STICK]?.visible == true) {
            val click = stickClickButtons.firstOrNull { it.group == ControlGroup.RIGHT_STICK }
            if (click != null) append(click)
            val r = rightStickBase.r * 0.65f
            targets += AccessibilityTarget(id++, RectF(rightStickBase.cx-r, rightStickBase.cy-r, rightStickBase.cx+r, rightStickBase.cy+r), "Right stick", ButtonBit.R3, ControlGroup.RIGHT_STICK)
        }
        dpadButtons.forEach(::append)
        faceButtons.forEach(::append)
        shoulderButtons.forEach(::append)
        systemButtons.forEach(::append)
        return targets
    }

    override fun dispatchHoverEvent(event: MotionEvent): Boolean {
        return accessibilityHelper.dispatchHoverEvent(event) || super.dispatchHoverEvent(event)
    }

    private fun sq(cx: Float, cy: Float, half: Float): RectF =
        RectF(cx - half, cy - half, cx + half, cy + half)

    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        if (settings.getValue(ControlGroup.SYSTEM).visible) {
            systemButtons.forEach { drawButton(canvas, it) }
        }

        if (settings.getValue(ControlGroup.STICK).visible) {
            drawStick(canvas, stickBase, stickKnob)
            stickClickButtons.filter { it.group == ControlGroup.STICK }.forEach { drawButton(canvas, it) }
        }
        if (settings.getValue(ControlGroup.RIGHT_STICK).visible) {
            drawStick(canvas, rightStickBase, rightStickKnob)
            stickClickButtons.filter { it.group == ControlGroup.RIGHT_STICK }.forEach { drawButton(canvas, it) }
        }
        if (settings.getValue(ControlGroup.DPAD).visible) {
            dpadButtons.forEach { drawButton(canvas, it) }
        }
        if (settings.getValue(ControlGroup.FACE).visible) {
            faceButtons.forEach { drawButton(canvas, it) }
        }

        shoulderButtons.forEach {
            if (settings.getValue(it.group).visible) drawButton(canvas, it)
        }
        triggerButtons.forEach {
            if (settings.getValue(it.group).visible) drawButton(canvas, it)
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

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    fun cancelActiveInputs() {
        resetInputState()
        invalidate()
        accessibilityHelper.invalidateRoot()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_POINTER_DOWN -> handleDown(event, event.actionIndex)
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) handleMove(event, i)
            }
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_POINTER_UP -> {
                if (handleUp(event, event.actionIndex)) performClick()
            }
            MotionEvent.ACTION_CANCEL -> resetInputState()
        }

        invalidate()
        return true
    }

    private fun handleDown(event: MotionEvent, index: Int) {
        val id = event.getPointerId(index)
        val x = event.getX(index)
        val y = event.getY(index)

        val rect = findRect(x, y)
        if (rect != null) {
            activePointerToRect[id] = rect
            applyRect(rect, true, y)
            return
        }

        if (settings.getValue(ControlGroup.STICK).visible &&
            inCircle(x, y, stickBase) && stickPointer == -1
        ) {
            stickPointer = id
            updateStick(x, y, stickBase, stickKnob, settings.getValue(ControlGroup.STICK)) { dx, dy ->
                state.leftX = dx
                state.leftY = dy
            }
            return
        }

        if (settings.getValue(ControlGroup.RIGHT_STICK).visible &&
            inCircle(x, y, rightStickBase) && rightStickPointer == -1
        ) {
            rightStickPointer = id
            updateStick(x, y, rightStickBase, rightStickKnob, settings.getValue(ControlGroup.RIGHT_STICK)) { dx, dy ->
                state.rightX = dx
                state.rightY = dy
            }
        }
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

    private fun handleUp(event: MotionEvent, index: Int): Boolean {
        val id = event.getPointerId(index)

        var handled = false
        if (id == stickPointer) {
            stickPointer = -1
            stickKnob = PointF(stickBase.cx, stickBase.cy)
            state.leftX = 0
            state.leftY = 0
            handled = true
        }
        if (id == rightStickPointer) {
            rightStickPointer = -1
            rightStickKnob = PointF(rightStickBase.cx, rightStickBase.cy)
            state.rightX = 0
            state.rightY = 0
            handled = true
        }

        val rect = activePointerToRect.remove(id)
        if (rect != null) {
            applyRect(rect, false)
            return true
        }
        return handled
    }

    private fun applyRect(r: Rect2, pressed: Boolean, touchY: Float = Float.NaN) {
        when {
            r.bit == -1 -> state.leftTrigger = if (pressed) triggerValue(r, touchY) else 0
            r.bit == -2 -> state.rightTrigger = if (pressed) triggerValue(r, touchY) else 0
            r.bit == ButtonBit.L1 || r.bit == ButtonBit.R1 ->
                state.setButton(r.bit, pressed || activePointerToRect.values.any { it.bit == r.bit })
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
        stickClickButtons.firstOrNull {
            settings.getValue(it.group).visible && it.r.contains(x, y)
        }?.let { return it }
        if (settings.getValue(ControlGroup.SYSTEM).visible) {
            systemButtons.firstOrNull { it.r.contains(x, y) }?.let { return it }
        }

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

    override fun onDetachedFromWindow() {
        resetInputState()
        super.onDetachedFromWindow()
    }
}
