package com.wifipad.receiver

import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStream

/**
 * Drives the AOSP `uinput` shell command (frameworks/base/cmds/uinput, source:
 * https://android.googlesource.com/platform/frameworks/base/+/master/cmds/uinput/)
 * to register a virtual joystick device node. `uinput` is a normal shell-executable
 * binary shipped on stock Android/AOSP images (used by CTS input tests) -- it needs
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
 * need root we don't have) -- Android just uses the layout it already ships with.
 *
 * NOTE on codes: every uinput/input-event code below is sent as a raw integer,
 * not a symbolic string. `Event.readInt()` on this device's AOSP branch
 * (android14-release, matching its Android 14 / SDK 34) parses config/event
 * fields with `Integer.decode()` only -- it does not resolve names like
 * "EV_KEY" or "BTN_A" (that symbol-resolution fallback exists only on newer
 * `main`). Sending a symbolic name throws inside `readConfiguration()`, gets
 * swallowed, and uinput just prints its generic "Error reading in object,
 * ignoring." with no indication of which field failed. The symbolic names are
 * kept as Kotlin constants purely for readability; see
 * bionic/libc/kernel/uapi/linux/{uinput.h,input-event-codes.h} for the source
 * of each numeric value.
 */
class UinputGamepad(private val out: OutputStream) {

    companion object {
        const val DEVICE_ID = 1

        // uinput ioctl codes (bionic/libc/kernel/uapi/linux/uinput.h)
        const val UI_SET_EVBIT = 100
        const val UI_SET_KEYBIT = 101
        const val UI_SET_ABSBIT = 103

        // event types (input-event-codes.h)
        const val EV_KEY = 1
        const val EV_ABS = 3
        const val EV_SYN = 0

        // sync codes (input-event-codes.h)
        const val SYN_REPORT = 0

        // button codes (input-event-codes.h)
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

        // abs axis codes (input-event-codes.h)
        const val ABS_X = 0
        const val ABS_Y = 1
        const val ABS_Z = 2
        const val ABS_RX = 3
        const val ABS_RY = 4
        const val ABS_RZ = 5
        const val ABS_HAT0X = 16
        const val ABS_HAT0Y = 17
    }

    fun register() {
        val configuration = JSONArray().apply {
            put(cfg(UI_SET_EVBIT, listOf(EV_KEY, EV_ABS)))
            put(cfg(UI_SET_KEYBIT, listOf(
                BTN_A, BTN_B, BTN_X, BTN_Y,
                BTN_TL, BTN_TR,
                BTN_SELECT, BTN_START, BTN_MODE,
                BTN_THUMBL, BTN_THUMBR
            )))
            put(cfg(UI_SET_ABSBIT, listOf(
                ABS_X, ABS_Y, ABS_RX, ABS_RY, ABS_Z, ABS_RZ,
                ABS_HAT0X, ABS_HAT0Y
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

        // The input stack needs a moment to add the device; commands sent before
        // that finishes are silently dropped (documented uinput behaviour).
        write(JSONObject().apply {
            put("id", DEVICE_ID)
            put("command", "delay")
            put("duration", 300)
        })
    }

    /** events: list of (EV_* type, code, value) integer triples; a SYN_REPORT is appended automatically. */
    fun inject(events: List<Triple<Int, Int, Int>>) {
        if (events.isEmpty()) return
        val arr = JSONArray()
        for ((type, code, value) in events) {
            arr.put(type); arr.put(code); arr.put(value)
        }
        arr.put(EV_SYN); arr.put(SYN_REPORT); arr.put(0)
        write(JSONObject().apply {
            put("id", DEVICE_ID)
            put("command", "inject")
            put("events", arr)
        })
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

    // Logs the exact JSON object handed to uinput's stdin right before it goes out.
    // uinput's own parser errors ("Error reading in object, ignoring.") never say
    // which field tripped it, so this is the only way to see precisely which
    // register/delay/inject payload uinput choked on.
    private fun write(obj: JSONObject) {
        val json = obj.toString()
        FileLogger.logRaw("UINPUT WRITE - $json")
        out.write(json.toByteArray())
        out.write('\n'.code)
        out.flush()
    }
}
