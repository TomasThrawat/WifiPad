package com.wifipad.controller

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Draws and drives a PS4-style layout: two analog sticks, a D-pad, four face
 * buttons, shoulder buttons/triggers and Share/Options/PS. All controls write
 * directly into [state]; the caller is responsible for sending it over the network.
 */
class GamepadView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    val state = GamepadState()

    private data class Circle(val cx: Float, val cy: Float, val r: Float)
    private data class Rect2(val r: RectF, val bit: Int, val label: String)

    private lateinit var leftStickBase: Circle
    private lateinit var rightStickBase: Circle
    private var stickRadius = 0f

    private var leftStickPointer = -1
    private var rightStickPointer = -1
    private var leftKnob = PointF(0f, 0f)
    private var rightKnob = PointF(0f, 0f)

    private val faceButtons = mutableListOf<Rect2>()
    private val dpadButtons = mutableListOf<Rect2>()
    private val shoulderButtons = mutableListOf<Rect2>()
    private val triggerButtons = mutableListOf<Rect2>()
    private val systemButtons = mutableListOf<Rect2>()

    /** Which pointer id is currently holding down which button rect, so multi-touch works. */
    private val activePointerToRect = mutableMapOf<Int, Rect2>()

    private val bgPaint = Paint().apply { color = Color.rgb(28, 28, 32) }
    private val stickBasePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(50, 50, 56) }
    private val stickKnobPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(90, 90, 100) }
    private val buttonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(60, 60, 68) }
    private val buttonActivePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(120, 170, 255) }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textAlign = Paint.Align.CENTER; textSize = 34f
    }

    private class PointF(var x: Float, var y: Float)

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val s = min(w, h).toFloat()
        stickRadius = s * 0.16f

        leftStickBase = Circle(w * 0.20f, h * 0.62f, stickRadius)
        rightStickBase = Circle(w * 0.80f, h * 0.62f, stickRadius)
        leftKnob = PointF(leftStickBase.cx, leftStickBase.cy)
        rightKnob = PointF(rightStickBase.cx, rightStickBase.cy)

        val btn = s * 0.075f

        // D-pad, above the left stick.
        val dpadCx = w * 0.20f
        val dpadCy = h * 0.28f
        dpadButtons.clear()
        dpadButtons += Rect2(sq(dpadCx, dpadCy - btn, btn), 1, "^")           // up
        dpadButtons += Rect2(sq(dpadCx + btn, dpadCy, btn), 3, ">")           // right
        dpadButtons += Rect2(sq(dpadCx, dpadCy + btn, btn), 5, "v")           // down
        dpadButtons += Rect2(sq(dpadCx - btn, dpadCy, btn), 7, "<")           // left

        // Face buttons (Triangle/Circle/Cross/Square), above the right stick.
        val faceCx = w * 0.80f
        val faceCy = h * 0.28f
        faceButtons.clear()
        faceButtons += Rect2(sq(faceCx, faceCy - btn, btn), ButtonBit.Y, "△")
        faceButtons += Rect2(sq(faceCx + btn, faceCy, btn), ButtonBit.B, "○")
        faceButtons += Rect2(sq(faceCx, faceCy + btn, btn), ButtonBit.A, "✕")
        faceButtons += Rect2(sq(faceCx - btn, faceCy, btn), ButtonBit.X, "□")

        // Shoulder buttons L1/R1, top corners.
        val shW = w * 0.16f
        val shH = h * 0.07f
        shoulderButtons.clear()
        shoulderButtons += Rect2(RectF(w * 0.02f, h * 0.02f, w * 0.02f + shW, h * 0.02f + shH), ButtonBit.L1, "L1")
        shoulderButtons += Rect2(RectF(w * 0.98f - shW, h * 0.02f, w * 0.98f, h * 0.02f + shH), ButtonBit.R1, "R1")

        // Triggers L2/R2 (simple press = 0/255, not a smooth analog drag).
        triggerButtons.clear()
        triggerButtons += Rect2(RectF(w * 0.02f, h * 0.02f + shH + 8, w * 0.02f + shW, h * 0.02f + 2 * shH + 8), -1, "L2")
        triggerButtons += Rect2(RectF(w * 0.98f - shW, h * 0.02f + shH + 8, w * 0.98f, h * 0.02f + 2 * shH + 8), -2, "R2")

        // Share / PS / Options, top center.
        val sysW = w * 0.09f
        val sysH = h * 0.05f
        val midY = h * 0.03f
        systemButtons.clear()
        systemButtons += Rect2(RectF(w * 0.5f - sysW * 1.8f, midY, w * 0.5f - sysW * 0.8f, midY + sysH), ButtonBit.SELECT, "Share")
        systemButtons += Rect2(RectF(w * 0.5f - sysW / 2, midY, w * 0.5f + sysW / 2, midY + sysH), ButtonBit.MODE, "PS")
        systemButtons += Rect2(RectF(w * 0.5f + sysW * 0.8f, midY, w * 0.5f + sysW * 1.8f, midY + sysH), ButtonBit.START, "Opt")
    }

    private fun sq(cx: Float, cy: Float, half: Float) = RectF(cx - half, cy - half, cx + half, cy + half)

    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        drawStick(canvas, leftStickBase, leftKnob)
        drawStick(canvas, rightStickBase, rightKnob)

        for (list in listOf(dpadButtons, faceButtons, shoulderButtons, triggerButtons, systemButtons)) {
            for (r in list) drawButton(canvas, r)
        }
    }

    private fun drawStick(canvas: Canvas, base: Circle, knob: PointF) {
        canvas.drawCircle(base.cx, base.cy, base.r, stickBasePaint)
        canvas.drawCircle(knob.x, knob.y, base.r * 0.45f, stickKnobPaint)
    }

    private fun drawButton(canvas: Canvas, r: Rect2) {
        val pressed = activePointerToRect.containsValue(r)
        canvas.drawRoundRect(r.r, 16f, 16f, if (pressed) buttonActivePaint else buttonPaint)
        canvas.drawText(r.label, r.r.centerX(), r.r.centerY() + textPaint.textSize / 3, textPaint)
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
                leftStickPointer = -1; rightStickPointer = -1
                leftKnob = PointF(leftStickBase.cx, leftStickBase.cy)
                rightKnob = PointF(rightStickBase.cx, rightStickBase.cy)
                activePointerToRect.clear()
                state.buttons = 0; state.leftTrigger = 0; state.rightTrigger = 0
                state.leftX = 0; state.leftY = 0; state.rightX = 0; state.rightY = 0
            }
        }
        invalidate()
        return true
    }

    private fun handleDown(event: MotionEvent, index: Int) {
        val id = event.getPointerId(index)
        val x = event.getX(index)
        val y = event.getY(index)

        if (inCircle(x, y, leftStickBase) && leftStickPointer == -1) {
            leftStickPointer = id
            updateStick(x, y, leftStickBase) { dx, dy -> state.leftX = dx; state.leftY = dy; leftKnob = PointF(leftStickBase.cx + dx, leftStickBase.cy + dy) }
            return
        }
        if (inCircle(x, y, rightStickBase) && rightStickPointer == -1) {
            rightStickPointer = id
            updateStick(x, y, rightStickBase) { dx, dy -> state.rightX = dx; state.rightY = dy; rightKnob = PointF(rightStickBase.cx + dx, rightStickBase.cy + dy) }
            return
        }
        val rect = findRect(x, y) ?: return
        activePointerToRect[id] = rect
        applyRect(rect, true)
    }

    private fun handleMove(event: MotionEvent, index: Int) {
        val id = event.getPointerId(index)
        val x = event.getX(index)
        val y = event.getY(index)
        if (id == leftStickPointer) {
            updateStick(x, y, leftStickBase) { dx, dy -> state.leftX = dx; state.leftY = dy; leftKnob = PointF(leftStickBase.cx + dx, leftStickBase.cy + dy) }
        } else if (id == rightStickPointer) {
            updateStick(x, y, rightStickBase) { dx, dy -> state.rightX = dx; state.rightY = dy; rightKnob = PointF(rightStickBase.cx + dx, rightStickBase.cy + dy) }
        }
    }

    private fun handleUp(event: MotionEvent, index: Int) {
        val id = event.getPointerId(index)
        if (id == leftStickPointer) {
            leftStickPointer = -1
            leftKnob = PointF(leftStickBase.cx, leftStickBase.cy)
            state.leftX = 0; state.leftY = 0
        }
        if (id == rightStickPointer) {
            rightStickPointer = -1
            rightKnob = PointF(rightStickBase.cx, rightStickBase.cy)
            state.rightX = 0; state.rightY = 0
        }
        activePointerToRect.remove(id)?.let { applyRect(it, false) }
    }

    private fun applyRect(r: Rect2, pressed: Boolean) {
        when {
            r.bit == -1 -> state.leftTrigger = if (pressed) 255 else 0   // L2
            r.bit == -2 -> state.rightTrigger = if (pressed) 255 else 0  // R2
            dpadButtons.contains(r) -> state.dpad = if (pressed) r.bit else if (state.dpad == r.bit) 0 else state.dpad
            else -> state.setButton(r.bit, pressed)
        }
    }

    private fun findRect(x: Float, y: Float): Rect2? {
        for (list in listOf(dpadButtons, faceButtons, shoulderButtons, triggerButtons, systemButtons)) {
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
            dx *= scale; dy *= scale
        }
        val nx = (dx / base.r * 127f).roundToInt().coerceIn(-127, 127)
        val ny = (dy / base.r * 127f).roundToInt().coerceIn(-127, 127)
        apply(nx.toByte(), ny.toByte())
    }
}
