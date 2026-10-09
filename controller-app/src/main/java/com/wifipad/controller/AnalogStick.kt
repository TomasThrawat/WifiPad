package com.wifipad.controller

import kotlin.math.hypot
import kotlin.math.roundToInt

data class StickVector(
    val offsetX: Float,
    val offsetY: Float,
    val axisX: Byte,
    val axisY: Byte
)

object AnalogStick {
    const val DEAD_ZONE_FRACTION = 0.06f

    /**
     * Projects a touch delta into a bounded analog stick vector.
     * Small accidental movements stay neutral; larger movements preserve direction
     * and scale smoothly until the configured sensitivity reaches the axis limit.
     */
    fun project(
        dx: Float,
        dy: Float,
        radius: Float,
        sensitivity: Float = 1.0f
    ): StickVector {
        val neutral = StickVector(0f, 0f, 0, 0)
        if (!radius.isFinite() || radius <= 0f ||
            !dx.isFinite() || !dy.isFinite() || !sensitivity.isFinite()
        ) return neutral

        val distance = hypot(dx.toDouble(), dy.toDouble()).toFloat()
        if (!distance.isFinite() || distance <= radius * DEAD_ZONE_FRACTION) {
            return neutral
        }

        val clampedDistance = distance.coerceAtMost(radius)
        val response = (
            (clampedDistance / radius - DEAD_ZONE_FRACTION) /
                (1f - DEAD_ZONE_FRACTION)
            ).coerceIn(0f, 1f)
        val scaledDistance = (
            response * sensitivity.coerceIn(0.25f, 2.0f)
            ).coerceIn(0f, 1f) * radius
        val scale = scaledDistance / distance
        val projectedX = dx * scale
        val projectedY = dy * scale

        val x = (projectedX / radius * 127f).roundToInt().coerceIn(-127, 127)
        val y = (projectedY / radius * 127f).roundToInt().coerceIn(-127, 127)
        return StickVector(projectedX, projectedY, x.toByte(), y.toByte())
    }
}
