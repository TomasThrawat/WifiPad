package com.wifipad.receiver

import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStream

/**
 * Drives the AOSP `uinput` shell command (frameworks/base/cmds/uinput, source:
 * https://android.googlesource.com/platform/frameworks/base/+/master/cmds/uinput/)
 * to register a virtual joystick device node. `uinput` is a normal shell-executable
 * binary shipped on stock Android/AOSP images (used by CTS input tests) — it needs
 * no root, only what Shizuku already grants (shell/uid 2000).
 *
 * The device registers with the vendor/product ID of a wired Xbox 360 controller
 * (0x045e / 0x028e). Every stock Android build ships a matching keylayout file
 * (data/keyboards/Vendor_045e_Product_028e.kl) that remaps the controller's raw
 * axes to Android's recommended MotionEvent axes:
 *   ABS_X/ABS_Y   -> AXIS_X/AXIS_Y   (left stick)
 *   ABS_RX/ABS_RY -> AXIS_Z/AXIS_RZ  (right stick, per Android's own game
 *                                     controller guide)
 *   ABS_Z/ABS_RZ  -> AXIS_LTRIGGER/AXIS_RTRIGGER
 * Spoofing this specific, already-recognized VID/PID means the device gets a
 * fully correct mapping without installing any file into /system (which would
 * need root we don't have) — Android just uses the layout it already ships with.
 */
class UinputGamepad(private val out: OutputStream) {

    companion object {
        const val DEVICE_ID = 1
    }

    fun register() {
        val configuration = JSONArray().apply {
            put(cfg("UI_SET_EVBIT", listOf("EV_KEY", "EV_ABS")))
            put(cfg("UI_SET_KEYBIT", listOf(
                "BTN_A", "BTN_B", "BTN_X", "BTN_Y",
                "BTN_TL", "BTN_TR",
                "BTN_SELECT", "BTN_START", "BTN_MODE",
                "BTN_THUMBL", "BTN_THUMBR"
            )))
            put(cfg("UI_SET_ABSBIT", listOf(
                "ABS_X", "ABS_Y", "ABS_RX", "ABS_RY", "ABS_Z", "ABS_RZ",
                "ABS_HAT0X", "ABS_HAT0Y"
            )))
        }

        val absInfo = JSONArray().apply {
            put(abs("ABS_X", -127, 127, 0, 8))
            put(abs("ABS_Y", -127, 127, 0, 8))
            put(abs("ABS_RX", -127, 127, 0, 8))
            put(abs("ABS_RY", -127, 127, 0, 8))
            put(abs("ABS_Z", 0, 255, 0, 0))
            put(abs("ABS_RZ", 0, 255, 0, 0))
            put(abs("ABS_HAT0X", -1, 1, 0, 0))
            put(abs("ABS_HAT0Y", -1, 1, 0, 0))
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

        // The input stack needs a moment to add the device; commands sent before
        // that finishes are silently dropped (documented uinput behaviour).
        write(JSONObject().apply {
            put("id", DEVICE_ID)
            put("command", "delay")
            put("duration", 300)
        })
    }

    /** events: list of (EV_* type, code, value) triples; a SYN_REPORT is appended automatically. */
    fun inject(events: List<Triple<String, String, Int>>) {
        if (events.isEmpty()) return
        val arr = JSONArray()
        for ((type, code, value) in events) {
            arr.put(type); arr.put(code); arr.put(value)
        }
        arr.put("EV_SYN"); arr.put("SYN_REPORT"); arr.put(0)
        write(JSONObject().apply {
            put("id", DEVICE_ID)
            put("command", "inject")
            put("events", arr)
        })
    }

    private fun cfg(type: String, data: List<String>) = JSONObject().apply {
        put("type", type)
        put("data", JSONArray(data))
    }

    private fun abs(code: String, min: Int, max: Int, fuzz: Int, flat: Int) = JSONObject().apply {
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

    private fun write(obj: JSONObject) {
        out.write(obj.toString().toByteArray())
        out.write('\n'.code)
        out.flush()
    }
}
