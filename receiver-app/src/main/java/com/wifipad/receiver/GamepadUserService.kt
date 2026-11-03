package com.wifipad.receiver

import android.os.Process as AndroidProcess
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class GamepadUserService : IGamepadService.Stub() {
    @Volatile private var socket: DatagramSocket? = null
    @Volatile private var uinputProcess: Process? = null
    @Volatile private var uinputPad: UinputGamepad? = null
    private val running = AtomicBoolean(false)
    @Volatile private var error: String = ""
    private val received = AtomicLong(0)
    private val inputLock = Any()
    private val eventBuffer = IntArray(60)
    // All input-state cache access is serialized by inputLock.
    private var lastButtons = 0
    private var lastDpad = -1
    private var lastLeftX = 0
    private var lastLeftY = 0
    private var lastRightX = 0
    private var lastRightY = 0
    private var lastLeftTrigger = 0
    private var lastRightTrigger = 0
    @Volatile private var lastPacketNanos = 0L
    @Volatile private var failsafeApplied = false

    override fun start(port: Int): Boolean {
        if (running.get()) return true
        error = ""
        lastButtons = 0
        lastLeftX = 0
        lastLeftY = 0
        lastRightX = 0
        lastRightY = 0
        lastLeftTrigger = 0
        lastRightTrigger = 0
        lastDpad = -1
        lastPacketNanos = System.nanoTime()
        failsafeApplied = false

        var proc: Process? = null
        var sock: DatagramSocket? = null

        return try {
            proc = ProcessBuilder("uinput", "-").redirectErrorStream(true).start()
            uinputProcess = proc
            Thread {
                try { proc.inputStream.bufferedReader().forEachLine { } } catch (_: Exception) {}
                val exitCode = try { proc.waitFor() } catch (_: Exception) { -1 }
                if (running.get()) {
                    error = "uinput process ended (exit code: $exitCode)"
                    stop()
                }
            }.apply {
                isDaemon = true
                name = "uinput-drain"
                start()
            }

            val pad = UinputGamepad(proc.outputStream)
            pad.register()
            uinputPad = pad
            if (!proc.isAlive) throw IllegalStateException("uinput process exited during registration")

            sock = DatagramSocket(null)
            sock.reuseAddress = true
            sock.bind(InetSocketAddress(port))
            socket = sock
            running.set(true)

            Thread { receiveLoop(sock, pad) }.apply {
                isDaemon = true
                priority = Thread.MAX_PRIORITY
                name = "wifipad-recv"
                start()
            }
            Thread { failsafeLoop(pad) }.apply {
                isDaemon = true
                name = "wifipad-failsafe"
                start()
            }
            true
        } catch (e: Exception) {
            error = e.message ?: "start failed"
            running.set(false)
            try { sock?.close() } catch (_: Exception) {}
            if (socket === sock) socket = null
            uinputPad = null
            proc?.let {
                try { it.outputStream.close() } catch (_: Exception) {}
                try { it.destroy() } catch (_: Exception) {}
            }
            if (uinputProcess === proc) uinputProcess = null
            false
        }
    }

    private fun receiveLoop(sock: DatagramSocket, pad: UinputGamepad) {
        val buf = ByteArray(64)
        val packet = DatagramPacket(buf, buf.size)
        while (running.get()) {
            try {
                packet.length = buf.size
                sock.receive(packet)
                val parsed = ProtocolPacket.parse(packet.data, packet.offset, packet.length) ?: continue
                synchronized(inputLock) {
                    if (running.get()) {
                        received.incrementAndGet()
                        lastPacketNanos = System.nanoTime()
                        failsafeApplied = false
                        handlePacket(parsed, pad)
                    }
                }
            } catch (e: Exception) {
                if (running.get()) error = e.message ?: "recv error"
            }
        }
    }

    private fun failsafeLoop(pad: UinputGamepad) {
        val timeoutNanos = Protocol.FAILSAFE_TIMEOUT_MS * 1_000_000L
        while (running.get()) {
            try { Thread.sleep(100) } catch (_: InterruptedException) { return }
            if (!running.get()) return
            var failed = false
            synchronized(inputLock) {
                if (running.get() && !failsafeApplied &&
                    System.nanoTime() - lastPacketNanos > timeoutNanos
                ) {
                    try {
                        neutralize(pad)
                        failsafeApplied = true
                    } catch (_: Exception) {
                        error = "failsafe failed"
                        failed = true
                    }
                }
            }
            if (failed) {
                stop()
                return
            }
        }
    }

    private fun handlePacket(packet: ProtocolPacket, pad: UinputGamepad) {
        val buttons = packet.buttons
        val leftX = packet.leftX
        val leftY = packet.leftY
        val rightX = packet.rightX
        val rightY = packet.rightY
        val lt = packet.leftTrigger
        val rt = packet.rightTrigger
        val dpad = packet.dpad

        var count = 0
        fun add(type: Int, code: Int, value: Int) {
            eventBuffer[count++] = type
            eventBuffer[count++] = code
            eventBuffer[count++] = value
        }

        // Only emit axis updates when their value changes. This avoids writing
        // six redundant ABS events for every idle 60 Hz packet.
        if (leftX != lastLeftX) {
            add(UinputGamepad.EV_ABS, UinputGamepad.ABS_X, leftX)
            lastLeftX = leftX
        }
        if (leftY != lastLeftY) {
            add(UinputGamepad.EV_ABS, UinputGamepad.ABS_Y, leftY)
            lastLeftY = leftY
        }
        if (rightX != lastRightX) {
            add(UinputGamepad.EV_ABS, UinputGamepad.ABS_RX, rightX)
            lastRightX = rightX
        }
        if (rightY != lastRightY) {
            add(UinputGamepad.EV_ABS, UinputGamepad.ABS_RY, rightY)
            lastRightY = rightY
        }
        if (lt != lastLeftTrigger) {
            add(UinputGamepad.EV_ABS, UinputGamepad.ABS_Z, lt)
            lastLeftTrigger = lt
        }
        if (rt != lastRightTrigger) {
            add(UinputGamepad.EV_ABS, UinputGamepad.ABS_RZ, rt)
            lastRightTrigger = rt
        }

        if (dpad != lastDpad) {
            val hat = hatFor(dpad)
            add(UinputGamepad.EV_ABS, UinputGamepad.ABS_HAT0X, hat.first)
            add(UinputGamepad.EV_ABS, UinputGamepad.ABS_HAT0Y, hat.second)
            lastDpad = dpad
        }

        if (buttons != lastButtons) {
            for ((bit, key) in buttonKeyMap) {
                val was = lastButtons and bit != 0
                val now = buttons and bit != 0
                if (was != now) add(UinputGamepad.EV_KEY, key, if (now) 1 else 0)
            }
            lastButtons = buttons
        }

        if (count > 0) {
            try {
                pad.inject(eventBuffer, count)
            } catch (_: Exception) {
                error = "inject failed"
                stop()
            }
        }
    }

    private fun neutralize(pad: UinputGamepad) {
        lastButtons = 0
        lastDpad = 0
        lastLeftX = 0
        lastLeftY = 0
        lastRightX = 0
        lastRightY = 0
        lastLeftTrigger = 0
        lastRightTrigger = 0
        pad.injectNeutral()
    }

    private fun hatFor(dpad: Int): Pair<Int, Int> = DpadHat.fromWireValue(dpad)

    private val buttonKeyMap = listOf(
        ButtonBit.A to UinputGamepad.BTN_A, ButtonBit.B to UinputGamepad.BTN_B,
        ButtonBit.X to UinputGamepad.BTN_X, ButtonBit.Y to UinputGamepad.BTN_Y,
        ButtonBit.L1 to UinputGamepad.BTN_TL, ButtonBit.R1 to UinputGamepad.BTN_TR,
        ButtonBit.L3 to UinputGamepad.BTN_THUMBL, ButtonBit.R3 to UinputGamepad.BTN_THUMBR,
        ButtonBit.SELECT to UinputGamepad.BTN_SELECT, ButtonBit.START to UinputGamepad.BTN_START,
        ButtonBit.MODE to UinputGamepad.BTN_MODE
    )

    override fun stop() {
        if (!running.compareAndSet(true, false)) return
        synchronized(inputLock) {
            uinputPad?.let { pad ->
                try { neutralize(pad) } catch (_: Exception) {}
            }
            uinputPad = null
            socket?.close()
            socket = null
            uinputProcess?.let {
                try { it.outputStream.close() } catch (_: Exception) {}
                try { it.destroy() } catch (_: Exception) {}
            }
            uinputProcess = null
            lastButtons = 0
            lastDpad = -1
            lastLeftX = 0
            lastLeftY = 0
            lastRightX = 0
            lastRightY = 0
            lastLeftTrigger = 0
            lastRightTrigger = 0
        }
    }

    override fun isRunning(): Boolean = running.get()
    override fun lastError(): String = error
    override fun packetsReceived(): Long = received.get()

    override fun destroy() {
        stop()
        AndroidProcess.killProcess(AndroidProcess.myPid())
    }
}
