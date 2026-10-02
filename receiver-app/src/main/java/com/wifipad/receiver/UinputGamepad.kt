package com.wifipad.receiver

import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStream

class UinputGamepad(private val out: OutputStream) {
    companion object {
        const val DEVICE_ID = 1
        const val UI_SET_EVBIT = 100
        const val UI_SET_KEYBIT = 101
        const val UI_SET_ABSBIT = 103
        const val EV_KEY = 1
        const val EV_ABS = 3
        const val EV_SYN = 0
        const val SYN_REPORT = 0
        const val BTN_A = 304
        const val BTN_B = 305
        const val BTN_X = 307
        const val BTN_Y = 308
        const val BTN_TL = 310
        const val BTN_TR = 311
        const val BTN_SELECT = 314
        const val BTN_START = 315
        const val BTN_MODE = 316
        const val BTN_THUMBL = 317
        const val BTN_THUMBR = 318
        const val ABS_X = 0
        const val ABS_Y = 1
        const val ABS_Z = 2
        const val ABS_RX = 3
        const val ABS_RY = 4
        const val ABS_RZ = 5
        const val ABS_HAT0X = 16
        const val ABS_HAT0Y = 17
    }

    private val line = StringBuilder(256)

    fun register() {
        val configuration = JSONArray().apply {
            put(cfg(UI_SET_EVBIT, listOf(EV_KEY, EV_ABS)))
            put(cfg(UI_SET_KEYBIT, listOf(
                BTN_A, BTN_B, BTN_X, BTN_Y, BTN_TL, BTN_TR,
                BTN_SELECT, BTN_START, BTN_MODE, BTN_THUMBL, BTN_THUMBR
            )))
            put(cfg(UI_SET_ABSBIT, listOf(
                ABS_X, ABS_Y, ABS_RX, ABS_RY, ABS_Z, ABS_RZ, ABS_HAT0X, ABS_HAT0Y
            )))
        }
        val absInfo = JSONArray().apply {
            put(abs(ABS_X, -127, 127, 0, 8))
            put(abs(ABS_Y, -127, 127, 0, 8))
            put(abs(ABS_RX, -127, 127, 0, 8))
            put(abs(ABS_RY, -127, 127, 0, 8))
            put(abs(ABS_Z, 0, 255, 0, 0))
            put(abs(ABS_RZ, 0, 255, 0, 0))
            put(abs(ABS_HAT0X, -1, 1, 0, 0))
            put(abs(ABS_HAT0Y, -1, 1, 0, 0))
        }
        write(JSONObject().apply {
            put("id", DEVICE_ID)
            put("command", "register")
            put("name", "Xbox 360 Controller")
            put("vid", 0x045e)
            put("pid", 0x028e)
            put("bus", "usb")
            put("configuration", configuration)
            put("abs_info", absInfo)
        })
        write(JSONObject().apply {
            put("id", DEVICE_ID)
            put("command", "delay")
            put("duration", 300)
        })
    }

    @Synchronized
    fun inject(events: IntArray, count: Int) {
        if (count <= 0) return
        line.setLength(0)
        line.append("""{"id":1,"command":"inject","events":[""")
        for (i in 0 until count step 3) {
            if (i > 0) line.append(',')
            line.append(events[i]).append(',')
                .append(events[i + 1]).append(',')
                .append(events[i + 2])
        }
        line.append(",0,0,0]}")
        line.append('
')
        writeLine()
    }

    @Synchronized
    fun injectNeutral() {
        line.setLength(0)
        line.append("""{"id":1,"command":"inject","events":[""")
        line.append("3,0,0,3,1,0,3,3,0,3,4,0,3,2,0,3,5,0")
        line.append(",3,16,0,3,17,0")
        line.append(",1,304,0,1,305,0,1,307,0,1,308,0")
        line.append(",1,310,0,1,311,0,1,314,0,1,315,0,1,316,0")
        line.append(",1,317,0,1,318,0,0,0,0]}")
        line.append('
')
        writeLine()
    }

    private fun write(obj: JSONObject) {
        line.setLength(0)
        line.append(obj.toString()).append('
')
        writeLine()
    }

    private fun writeLine() {
        out.write(line.toString().toByteArray())
        out.flush()
    }

    private fun cfg(type: Int, data: List<Int>) = JSONObject().apply {
        put("type", type)
        put("data", JSONArray(data))
    }

    private fun abs(code: Int, min: Int, max: Int, fuzz: Int, flat: Int) = JSONObject().apply {
        put("code", code)
        put("info", JSONObject().apply {
            put("value", 0)
            put("minimum", min)
            put("maximum", max)
            put("fuzz", fuzz)
            put("flat", flat)
            put("resolution", 0)
        })
    }
}
