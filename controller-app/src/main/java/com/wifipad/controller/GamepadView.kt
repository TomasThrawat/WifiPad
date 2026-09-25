package com.wifipad.controller

import android.content.Context
import android.graphics.Canvas
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
        val circle: Boolean = false
    )

    private var stickBase = Circle(0f, 0f, 0f)
    private var stickPointer = -1
    private var stickKnob = PointF(0f, 0f)

    private val faceButtons = mutableListOf<Rect2>()
    private val dpadButtons = mutableListOf<Rect2>()
    private val shoulderButtons = mutableListOf<Rect2>()
    private val triggerButtons = mutableListOf<Rect2>()
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
        val dpad = settings.getValue(ControlGroup.DPAD)
        val face = settings.getValue(ControlGroup.FACE)
        val shoulders = settings.getValue(ControlGroup.SHOULDERS)

        val stickRadius = s * 0.19f * stick.scale
        stickBase = Circle(
            w * stick.x,
            h * stick.y,
            stickRadius
        )
        stickKnob = PointF(stickBase.cx, stickBase.cy)

        val dpadButton = s * 0.072f * dpad.scale
        val dpadGap = s * 0.020f * dpad.scale
        val dpadSpacing = 2f * dpadButton + dpadGap
        val dpadCx = w * dpad.x
        val dpadCy = h * dpad.y
        dpadButtons.clear()
        dpadButtons += Rect2(sq(dpadCx, dpadCy - dpadSpacing, dpadButton), 1, "↑")
        dpadButtons += Rect2(sq(dpadCx + dpadSpacing, dpadCy, dpadButton), 2, "→")
        dpadButtons += Rect2(sq(dpadCx, dpadCy + dpadSpacing, dpadButton), 4, "↓")
        dpadButtons += Rect2(sq(dpadCx - dpadSpacing, dpadCy, dpadButton), 8, "←")

        val faceButton = s * 0.075f * face.scale
        val faceGap = s * 0.020f * face.scale
        val faceSpacing = 2f * faceButton + faceGap
        val faceCx = w * face.x
        val faceCy = h * face.y
        faceButtons.clear()
        faceButtons += Rect2(
            sq(faceCx, faceCy - faceSpacing, faceButton),
            ButtonBit.Y,
            "Y"
        )
        faceButtons += Rect2(
            sq(faceCx + faceSpacing, faceCy, faceButton),
            ButtonBit.B,
            "B"
        )
        faceButtons += Rect2(
            sq(faceCx, faceCy + faceSpacing, faceButton),
            ButtonBit.A,
            "A"
        )
        faceButtons += Rect2(
            sq(faceCx - faceSpacing, faceCy, faceButton),
            ButtonBit.X,
            "X"
        )

        val shoulderHalf = s * 0.070f * shoulders.scale
        val leftX = w * 0.075f
        val rightX = w * 0.925f
        val topY = h * 0.08f
        val bottomY = h * 0.19f
        shoulderButtons.clear()
        shoulderButtons += Rect2(sq(leftX, topY, shoulderHalf), ButtonBit.L1, "L1", true)
        shoulderButtons += Rect2(sq(rightX, topY, shoulderHalf), ButtonBit.R1, "R1", true)

        triggerButtons.clear()
        triggerButtons += Rect2(sq(leftX, bottomY, shoulderHalf), -1, "L2", true)
        triggerButtons += Rect2(sq(rightX, bottomY, shoulderHalf), -2, "R2", true)

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
        if (settings.getValue(ControlGroup.DPAD).visible) {
            dpadButtons.forEach { drawButton(canvas, it) }
        }
        if (settings.getValue(ControlGroup.FACE).visible) {
            faceButtons.forEach { drawButton(canvas, it) }
        }
        if (settings.getValue(ControlGroup.SHOULDERS).visible) {
            shoulderButtons.forEach { drawButton(canvas, it) }
            triggerButtons.forEach { drawButton(canvas, it) }
        }
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
                for (i in 0 until event.pointerCount) {
                    handleMove(event, i)
                }
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
            updateStick(x, y, stickBase) { dx, dy ->
                state.leftX = dx
                state.leftY = dy
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
            updateStick(x, y, stickBase) { dx, dy ->
                state.leftX = dx
                state.leftY = dy
            }
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

        activePointerToRect.remove(id)?.let { applyRect(it, false) }
    }

    private fun applyRect(r: Rect2, pressed: Boolean) {
        when {
            r.bit == -1 -> state.leftTrigger = if (pressed) 255 else 0
            r.bit == -2 -> state.rightTrigger = if (pressed) 255 else 0
            r.bit == ButtonBit.L1 || r.bit == ButtonBit.R1 ->
                state.setButton(r.bit, pressed)

            dpadButtons.contains(r) ->
                state.dpad = if (pressed) r.bit else if (state.dpad == r.bit) 0 else state.dpad

            else -> state.setButton(r.bit, pressed)
        }
    }

    private fun findRect(x: Float, y: Float): Rect2? {
        if (settings.getValue(ControlGroup.DPAD).visible) {
            dpadButtons.firstOrNull { it.r.contains(x, y) }?.let { return it }
        }
        if (settings.getValue(ControlGroup.FACE).visible) {
            faceButtons.firstOrNull { it.r.contains(x, y) }?.let { return it }
        }
        if (settings.getValue(ControlGroup.SHOULDERS).visible) {
            shoulderButtons.firstOrNull { it.r.contains(x, y) }?.let { return it }
            triggerButtons.firstOrNull { it.r.contains(x, y) }?.let { return it }
        }
        return null
    }

    private fun inCircle(x: Float, y: Float, c: Circle): Boolean =
        hypot((x - c.cx).toDouble(), (y - c.cy).toDouble()) <= c.r * 1.25

    private fun updateStick(
        x: Float,
        y: Float,
        base: Circle,
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

        stickKnob.x = base.cx + dx
        stickKnob.y = base.cy + dy
        apply(nx.toByte(), ny.toByte())
    }

    private fun resetInputState() {
        stickPointer = -1
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
    }

    override fun onDetachedFromWindow() {
        resetInputState()
        super.onDetachedFromWindow()
    }
}
