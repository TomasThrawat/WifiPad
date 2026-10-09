package com.wifipad.controller

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
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
    private var stickMoved = false
    private val uiHandler = Handler(Looper.getMainLooper())
    private val releaseLeftClick = Runnable { state.setButton(ButtonBit.L3, false) }

    private var rightStickBase = Circle(0f, 0f, 0f)
    private var rightStickPointer = -1
    private var rightStickKnob = PointF(0f, 0f)
    private var rightStickMoved = false
    private val releaseRightClick = Runnable { state.setButton(ButtonBit.R3, false) }

    private val faceButtons = mutableListOf<Rect2>()
    private val dpadButtons = mutableListOf<Rect2>()
    private val shoulderButtons = mutableListOf<Rect2>()
    private val triggerButtons = mutableListOf<Rect2>()
    private val systemButtons = mutableListOf<Rect2>()
    private val activePointerToRect = mutableMapOf<Int, Rect2>()
    private val settings = mutableMapOf<ControlGroup, ControlSettings>()

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stickBasePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stickKnobPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val buttonPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val buttonActivePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
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

        // One shared system-button cluster serves NES, PSP, PlayStation, Nintendo and retro emulators.
        systemButtons.clear()
        val systemHalf = s * 0.045f
        systemButtons += Rect2(sq(w * 0.45f, h * 0.52f, systemHalf), ButtonBit.SELECT, "SELECT", group = ControlGroup.FACE)
        systemButtons += Rect2(sq(w * 0.55f, h * 0.52f, systemHalf), ButtonBit.START, "START", group = ControlGroup.FACE)
        systemButtons += Rect2(sq(w * 0.50f, h * 0.62f, systemHalf * 0.78f), ButtonBit.MODE, "MODE", group = ControlGroup.FACE)

        shoulderButtons.clear()
        triggerButtons.clear()

        val shoulderHalfLeft = s * 0.060f * left.scale
        val shoulderHalfRight = s * 0.060f * right.scale
        val shoulderGapLeft = s * 0.11f * left.scale
        val shoulderGapRight = s * 0.11f * right.scale

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
        triggerButtons += Rect2(
            sq(leftX, leftY2, shoulderHalfLeft),
            -1,
            "L2",
            true,
            ControlGroup.LEFT_SHOULDER
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
        triggerButtons += Rect2(
            sq(rightX, rightY2, shoulderHalfRight),
            -2,
            "R2",
            true,
            ControlGroup.RIGHT_SHOULDER
        )

        bgPaint.color = surfaceColor
        stickBasePaint.color = surfaceContainerColor
        stickKnobPaint.color = primaryContainerColor
        buttonPaint.color = surfaceContainerColor
        buttonActivePaint.color = primaryContainerColor
        textPaint.color = onSurfaceColor
        textPaint.textSize = (s * 0.040f).coerceIn(24f, 44f)
    }

    private fun sq(cx: Float, cy: Float, half: Float): RectF =
        RectF(cx - half, cy - half, cx + half, cy + half)

    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        if (settings.getValue(ControlGroup.STICK).visible) {
            drawStick(canvas, stickBase, stickKnob)
        }
        if (settings.getValue(ControlGroup.RIGHT_STICK).visible) {
            drawStick(canvas, rightStickBase, rightStickKnob)
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
        systemButtons.forEach { drawButton(canvas, it) }
    }

    private fun drawStick(canvas: Canvas, base: Circle, knob: PointF) {
        canvas.drawCircle(base.cx, base.cy, base.r, stickBasePaint)
        canvas.drawCircle(knob.x, knob.y, base.r * 0.48f, stickKnobPaint)

        val indicator = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = onPrimaryContainerColor
            alpha = 90
        }
        canvas.drawCircle(knob.x, knob.y, base.r * 0.20f, indicator)
    }

    private fun drawButton(canvas: Canvas, r: Rect2) {
        val pressed = activePointerToRect.containsValue(r)
        val paint = if (pressed) buttonActivePaint else buttonPaint

        if (r.circle) {
            val radius = min(r.r.width(), r.r.height()) / 2f
            canvas.drawCircle(r.r.centerX(), r.r.centerY(), radius, paint)
        } else {
            canvas.drawRoundRect(
                r.r,
                r.r.width() * 0.32f,
                r.r.width() * 0.32f,
                paint
            )
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
            // Initial contact is analog input; only a later tap release can click L3.
            stickMoved = hypot((x - stickBase.cx).toDouble(), (y - stickBase.cy).toDouble()) > stickBase.r * 0.12f
            uiHandler.removeCallbacks(releaseLeftClick)
            state.setButton(ButtonBit.L3, false)
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            updateStick(x, y, stickBase, stickKnob) { dx, dy ->
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
            // Initial contact is analog input; only a later tap release can click R3.
            rightStickMoved = hypot((x - rightStickBase.cx).toDouble(), (y - rightStickBase.cy).toDouble()) > rightStickBase.r * 0.12f
            uiHandler.removeCallbacks(releaseRightClick)
            state.setButton(ButtonBit.R3, false)
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            updateStick(x, y, rightStickBase, rightStickKnob) { dx, dy ->
                state.rightX = dx
                state.rightY = dy
            }
            return
        }

        val rect = findRect(x, y) ?: return
        activePointerToRect[id] = rect
        applyRect(rect, true)
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    private fun handleMove(event: MotionEvent, index: Int) {
        val id = event.getPointerId(index)
        val x = event.getX(index)
        val y = event.getY(index)

        if (id == stickPointer) {
            if (!stickMoved && hypot((x - stickBase.cx).toDouble(), (y - stickBase.cy).toDouble()) > stickBase.r * 0.12f) {
                stickMoved = true
            }
            updateStick(x, y, stickBase, stickKnob) { dx, dy ->
                state.leftX = dx
                state.leftY = dy
            }
        }
        if (id == rightStickPointer) {
            if (!rightStickMoved && hypot((x - rightStickBase.cx).toDouble(), (y - rightStickBase.cy).toDouble()) > rightStickBase.r * 0.12f) {
                rightStickMoved = true
            }
            updateStick(x, y, rightStickBase, rightStickKnob) { dx, dy ->
                state.rightX = dx
                state.rightY = dy
            }
        }
    }

    private fun handleUp(event: MotionEvent, index: Int) {
        val id = event.getPointerId(index)

        if (id == stickPointer) {
            if (!stickMoved) emitStickClick(ButtonBit.L3, releaseLeftClick)
            stickPointer = -1
            stickKnob = PointF(stickBase.cx, stickBase.cy)
            state.leftX = 0
            state.leftY = 0
            stickMoved = false
        }
        if (id == rightStickPointer) {
            if (!rightStickMoved) emitStickClick(ButtonBit.R3, releaseRightClick)
            rightStickPointer = -1
            rightStickKnob = PointF(rightStickBase.cx, rightStickBase.cy)
            state.rightX = 0
            state.rightY = 0
            rightStickMoved = false
        }

        activePointerToRect.remove(id)?.let { applyRect(it, false) }
    }

    private fun emitStickClick(bit: Int, releaseAction: Runnable) {
        uiHandler.removeCallbacks(releaseAction)
        state.setButton(bit, true)
        // The UDP sender runs at 60 Hz, so keep a tap pulse long enough to be received.
        uiHandler.postDelayed(releaseAction, 100L)
    }

    private fun applyRect(r: Rect2, pressed: Boolean) {
        when {
            r.bit == -1 -> state.leftTrigger = if (pressed) 255 else 0
            r.bit == -2 -> state.rightTrigger = if (pressed) 255 else 0
            r.bit == ButtonBit.L1 || r.bit == ButtonBit.R1 ->
                state.setButton(r.bit, pressed)
            dpadButtons.contains(r) -> updateDpadState()
            else -> state.setButton(r.bit, pressed)
        }
    }

    private fun updateDpadState() {
        var mask = 0
        for (rect in activePointerToRect.values) {
            if (dpadButtons.contains(rect)) mask = mask or rect.bit
        }
        state.dpad = DpadCode.fromPressedDirections(mask)
    }

    private fun findRect(x: Float, y: Float): Rect2? {
        val allLists = listOf(dpadButtons, faceButtons, shoulderButtons, triggerButtons)
        for (list in allLists) {
            for (r in list) {
                if (settings.getValue(r.group).visible && r.r.contains(x, y)) return r
            }
        }
        for (r in systemButtons) {
            if (r.r.contains(x, y)) return r
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

        val nx = (dx / base.r * 127f).roundToInt().coerceIn(-127, 127)
        val ny = (dy / base.r * 127f).roundToInt().coerceIn(-127, 127)

        knob.x = base.cx + dx
        knob.y = base.cy + dy
        apply(nx.toByte(), ny.toByte())
    }

    private fun resetInputState() {
        uiHandler.removeCallbacks(releaseLeftClick)
        uiHandler.removeCallbacks(releaseRightClick)
        stickPointer = -1
        rightStickPointer = -1
        stickMoved = false
        rightStickMoved = false
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
