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
        val circle: Boolean = false,
        val group: ControlGroup
    )

    private var stickBase = Circle(0f, 0f, 0f)
    private var stickPointer = -1
    private var stickKnob = PointF(0f, 0f)
    private var stickDownX = 0f
    private var stickDownY = 0f
    private var stickMoved = false

    private var rightStickBase = Circle(0f, 0f, 0f)
    private var rightStickPointer = -1
    private var rightStickKnob = PointF(0f, 0f)
    private var rightStickDownX = 0f
    private var rightStickDownY = 0f
    private var rightStickMoved = false

    private var profile = ControllerProfile.RETRO

    private val faceButtons = mutableListOf<Rect2>()
    private val dpadButtons = mutableListOf<Rect2>()
    private val shoulderButtons = mutableListOf<Rect2>()
    private val triggerButtons = mutableListOf<Rect2>()
    private val auxiliaryButtons = mutableListOf<Rect2>()
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
        profile = ControlSettingsStore.loadProfile(context)
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
        profile.faceButtons().forEach { spec ->
            faceButtons += Rect2(
                sq(faceCx + faceSpacing * spec.dx, faceCy + faceSpacing * spec.dy, faceButton),
                spec.bit,
                spec.label,
                group = ControlGroup.FACE
            )
        }

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

        auxiliaryButtons.clear()
        val auxiliaryHalf = s * 0.042f
        profile.auxiliaryButtons().forEach { spec ->
            val cx = w * (0.5f + spec.dx * 0.055f)
            val cy = h * (0.54f + spec.dy * 0.10f)
            auxiliaryButtons += Rect2(
                sq(cx, cy, auxiliaryHalf),
                spec.bit,
                spec.label,
                group = ControlGroup.FACE
            )
        }

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

        if (profile.leftStickVisible && settings.getValue(ControlGroup.STICK).visible) {
            drawStick(canvas, stickBase, stickKnob)
        }
        if (profile.rightStickVisible && settings.getValue(ControlGroup.RIGHT_STICK).visible) {
            drawStick(canvas, rightStickBase, rightStickKnob)
        }
        if (settings.getValue(ControlGroup.DPAD).visible) {
            dpadButtons.forEach { drawButton(canvas, it) }
        }
        if (settings.getValue(ControlGroup.FACE).visible) {
            faceButtons.forEach { drawButton(canvas, it) }
        }

        if (profile.shouldersVisible) {
            shoulderButtons.forEach {
                if (settings.getValue(it.group).visible) drawButton(canvas, it)
            }
            triggerButtons.forEach {
                if (settings.getValue(it.group).visible) drawButton(canvas, it)
            }
        }
        auxiliaryButtons.forEach { drawButton(canvas, it) }
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

        if (profile.leftStickVisible &&
            settings.getValue(ControlGroup.STICK).visible &&
            stickPointer == -1 &&
            inCircle(x, y, stickBase)
        ) {
            stickPointer = id
            stickDownX = x
            stickDownY = y
            stickMoved = false
            state.setButton(ButtonBit.L3, true)
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            updateStick(x, y, stickBase, stickKnob) { dx, dy ->
                state.leftX = dx
                state.leftY = dy
            }
            return
        }

        if (profile.rightStickVisible &&
            settings.getValue(ControlGroup.RIGHT_STICK).visible &&
            rightStickPointer == -1 &&
            inCircle(x, y, rightStickBase)
        ) {
            rightStickPointer = id
            rightStickDownX = x
            rightStickDownY = y
            rightStickMoved = false
            state.setButton(ButtonBit.R3, true)
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
            if (!stickMoved &&
                hypot((x - stickDownX).toDouble(), (y - stickDownY).toDouble()) > stickBase.r * 0.22f
            ) {
                stickMoved = true
                state.setButton(ButtonBit.L3, false)
            }
            updateStick(x, y, stickBase, stickKnob) { dx, dy ->
                state.leftX = dx
                state.leftY = dy
            }
        } else if (id == rightStickPointer) {
            if (!rightStickMoved &&
                hypot((x - rightStickDownX).toDouble(), (y - rightStickDownY).toDouble()) > rightStickBase.r * 0.22f
            ) {
                rightStickMoved = true
                state.setButton(ButtonBit.R3, false)
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
            stickPointer = -1
            stickKnob = PointF(stickBase.cx, stickBase.cy)
            state.leftX = 0
            state.leftY = 0
            state.setButton(ButtonBit.L3, false)
            stickMoved = false
        }

        if (id == rightStickPointer) {
            rightStickPointer = -1
            rightStickKnob = PointF(rightStickBase.cx, rightStickBase.cy)
            state.rightX = 0
            state.rightY = 0
            state.setButton(ButtonBit.R3, false)
            rightStickMoved = false
        }

        activePointerToRect.remove(id)?.let { applyRect(it, false) }
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
        var directionMask = 0
        for (rect in activePointerToRect.values) {
            if (dpadButtons.contains(rect)) directionMask = directionMask or rect.bit
        }
        state.dpad = DpadCode.fromPressedDirections(directionMask)
    }

    private fun findRect(x: Float, y: Float): Rect2? {
        val allLists = listOf(dpadButtons, faceButtons, shoulderButtons, triggerButtons, auxiliaryButtons)
        for (list in allLists) {
            for (r in list) {
                if (auxiliaryButtons.contains(r)) {
                    if (r.r.contains(x, y)) return r
                    continue
                }
                val isShoulder = r.group == ControlGroup.LEFT_SHOULDER ||
                    r.group == ControlGroup.RIGHT_SHOULDER
                val enabledByProfile = !isShoulder || profile.shouldersVisible
                if (enabledByProfile && settings.getValue(r.group).visible && r.r.contains(x, y)) {
                    return r
                }
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
