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

    private lateinit var stickBase: Circle
    private var stickRadius = 0f
    private var stickPointer = -1
    private var stickKnob = PointF(0f, 0f)

    private val faceButtons = mutableListOf<Rect2>()
    private val dpadButtons = mutableListOf<Rect2>()
    private val shoulderButtons = mutableListOf<Rect2>()
    private val triggerButtons = mutableListOf<Rect2>()
    private val activePointerToRect = mutableMapOf<Int, Rect2>()

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

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val s = min(w, h).toFloat()
        stickRadius = s * 0.16f

        stickBase = Circle(w * 0.22f, h * 0.65f, stickRadius)
        stickKnob = PointF(stickBase.cx, stickBase.cy)

        val btn = s * 0.06f
        val btnGap = s * 0.025f
        val btnSpacing = 2f * btn + btnGap

        val dpadCx = w * 0.22f
        val dpadCy = h * 0.24f
        dpadButtons.clear()
        dpadButtons += Rect2(sq(dpadCx, dpadCy - btnSpacing, btn), 1, "↑")
        dpadButtons += Rect2(sq(dpadCx + btnSpacing, dpadCy, btn), 3, "→")
        dpadButtons += Rect2(sq(dpadCx, dpadCy + btnSpacing, btn), 5, "↓")
        dpadButtons += Rect2(sq(dpadCx - btnSpacing, dpadCy, btn), 7, "←")

        val faceCx = w * 0.78f
        val faceCy = h * 0.65f
        faceButtons.clear()
        faceButtons += Rect2(sq(faceCx, faceCy - btnSpacing, btn), ButtonBit.Y, "△")
        faceButtons += Rect2(sq(faceCx + btnSpacing, faceCy, btn), ButtonBit.B, "○")
        faceButtons += Rect2(sq(faceCx, faceCy + btnSpacing, btn), ButtonBit.A, "×")
        faceButtons += Rect2(sq(faceCx - btnSpacing, faceCy, btn), ButtonBit.X, "□")

        val shAnchor = s * 0.055f
        val shHalf = s * 0.063f
        val shSpacing = 2f * shAnchor + s * 0.02f
        val shLx = w * 0.02f + shAnchor
        val shRx = w * 0.98f - shAnchor
        val shTopY = h * 0.02f + shAnchor
        val shBottomY = shTopY + shSpacing

        shoulderButtons.clear()
        shoulderButtons += Rect2(sq(shLx, shTopY, shHalf), ButtonBit.L1, "L1", circle = true)
        shoulderButtons += Rect2(sq(shRx, shTopY, shHalf), ButtonBit.R1, "R1", circle = true)

        triggerButtons.clear()
        triggerButtons += Rect2(sq(shLx, shBottomY, shHalf), -1, "L2", circle = true)
        triggerButtons += Rect2(sq(shRx, shBottomY, shHalf), -2, "R2", circle = true)

        bgPaint.color = surfaceColor
        stickBasePaint.color = surfaceContainerColor
        stickKnobPaint.color = primaryContainerColor
        buttonPaint.color = surfaceContainerColor
        buttonActivePaint.color = primaryContainerColor
        textPaint.color = onSurfaceColor
        textPaint.textSize = (s * 0.032f).coerceIn(24f, 48f)
    }

    private fun sq(cx: Float, cy: Float, half: Float) =
        RectF(cx - half, cy - half, cx + half, cy + half)

    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        drawStick(canvas, stickBase, stickKnob)
        for (list in listOf(dpadButtons, faceButtons, shoulderButtons, triggerButtons)) {
            for (r in list) drawButton(canvas, r)
        }
    }

    private fun drawStick(canvas: Canvas, base: Circle, knob: PointF) {
        canvas.drawCircle(base.cx, base.cy, base.r, stickBasePaint)
        canvas.drawCircle(knob.x, knob.y, base.r * 0.45f, stickKnobPaint)

        val indicator = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = onPrimaryContainerColor
            alpha = 80
        }
        canvas.drawCircle(knob.x, knob.y, base.r * 0.18f, indicator)
    }

    private fun drawButton(canvas: Canvas, r: Rect2) {
        val pressed = activePointerToRect.containsValue(r)
        val paint = if (pressed) buttonActivePaint else buttonPaint

        if (r.circle) {
            val radius = min(r.r.width(), r.r.height()) / 2f
            canvas.drawCircle(r.r.centerX(), r.r.centerY(), radius, paint)
        } else {
            canvas.drawRoundRect(r.r, 20f, 20f, paint)
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
        val action = event.actionMasked
        val index = event.actionIndex
        when (action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> handleDown(event, index)
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) handleMove(event, i)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> handleUp(event, index)
            MotionEvent.ACTION_CANCEL -> {
                stickPointer = -1
                stickKnob = PointF(stickBase.cx, stickBase.cy)
                activePointerToRect.clear()
                state.buttons = 0
                state.leftTrigger = 0
                state.rightTrigger = 0
                state.leftX = 0
                state.leftY = 0
                state.rightX = 0
                state.rightY = 0
            }
        }
        invalidate()
        return true
    }

    private fun handleDown(event: MotionEvent, index: Int) {
        val id = event.getPointerId(index)
        val x = event.getX(index)
        val y = event.getY(index)

        if (inCircle(x, y, stickBase) && stickPointer == -1) {
            stickPointer = id
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            updateStick(x, y, stickBase) { dx, dy ->
                state.leftX = dx
                state.leftY = dy
                stickKnob = PointF(stickBase.cx + dx, stickBase.cy + dy)
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
                stickKnob = PointF(stickBase.cx + dx, stickBase.cy + dy)
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
            dpadButtons.contains(r) ->
                state.dpad = if (pressed) r.bit else if (state.dpad == r.bit) 0 else state.dpad
            else -> state.setButton(r.bit, pressed)
        }
    }

    private fun findRect(x: Float, y: Float): Rect2? {
        for (list in listOf(dpadButtons, faceButtons, shoulderButtons, triggerButtons)) {
            for (r in list) if (r.r.contains(x, y)) return r
        }
        return null
    }

    private fun inCircle(x: Float, y: Float, c: Circle): Boolean =
        hypot((x - c.cx).toDouble(), (y - c.cy).toDouble()) <= c.r * 1.6

    private fun updateStick(x: Float, y: Float, base: Circle, apply: (Byte, Byte) -> Unit) {
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
        apply(nx.toByte(), ny.toByte())
    }
}
