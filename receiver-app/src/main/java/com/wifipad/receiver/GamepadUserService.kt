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
    private val running = AtomicBoolean(false)
    @Volatile private var error: String = ""
    private val received = AtomicLong(0)
    private var lastButtons = 0
    private var lastDpad = -1
    @Volatile private var lastPacketNanos = 0L
    @Volatile private var failsafeApplied = false

    override fun start(port: Int): Boolean {
        if (running.get()) return true

        error = ""
        lastButtons = 0
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
                if (packet.length < Protocol.PACKET_SIZE) continue

                val d = packet.data
                if (d[0] != Protocol.MAGIC || d[1] != Protocol.VERSION) continue

                received.incrementAndGet()
                lastPacketNanos = System.nanoTime()
                failsafeApplied = false
                handlePacket(d, pad)
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

            if (!failsafeApplied && System.nanoTime() - lastPacketNanos > timeoutNanos) {
                try {
                    neutralize(pad)
                    failsafeApplied = true
                } catch (e: Exception) {
                    error = "failsafe failed"
                    stop()
                    return
                }
            }
        }
    }

    private fun handlePacket(d: ByteArray, pad: UinputGamepad) {
        val buttons = (d[2].toInt() and 0xFF) or ((d[3].toInt() and 0xFF) shl 8)
        val leftX = d[4].toInt()
        val leftY = d[5].toInt()
        val rightX = d[6].toInt()
        val rightY = d[7].toInt()
        val lt = d[8].toInt() and 0xFF
        val rt = d[9].toInt() and 0xFF
        val dpad = d[10].toInt() and 0xFF

        // Six axes are always sent. D-pad/buttons are added only when their
        // state changes, reducing unnecessary uinput JSON work.
        val events = IntArray(36)
        var count = 0
        fun add(type: Int, code: Int, value: Int) {
            events[count++] = type
            events[count++] = code
            events[count++] = value
        }

        add(UinputGamepad.EV_ABS, UinputGamepad.ABS_X, leftX)
        add(UinputGamepad.EV_ABS, UinputGamepad.ABS_Y, leftY)
        add(UinputGamepad.EV_ABS, UinputGamepad.ABS_RX, rightX)
        add(UinputGamepad.EV_ABS, UinputGamepad.ABS_RY, rightY)
        add(UinputGamepad.EV_ABS, UinputGamepad.ABS_Z, lt)
        add(UinputGamepad.EV_ABS, UinputGamepad.ABS_RZ, rt)

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

        try {
            pad.inject(events, count)
        } catch (e: Exception) {
            error = "inject failed"
            stop()
        }
    }

    private fun neutralize(pad: UinputGamepad) {
        lastButtons = 0
        lastDpad = 0
        pad.injectNeutral()
    }

    private fun hatFor(dpad: Int): Pair<Int, Int> = when (dpad) {
        1 -> 0 to -1; 2 -> 1 to -1; 3 -> 1 to 0; 4 -> 1 to 1
        5 -> 0 to 1; 6 -> -1 to 1; 7 -> -1 to 0; 8 -> -1 to -1
        else -> 0 to 0
    }

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
        socket?.close()
        socket = null
        uinputProcess?.let {
            try { it.outputStream.close() } catch (_: Exception) {}
            try { it.destroy() } catch (_: Exception) {}
        }
        uinputProcess = null
        lastButtons = 0
        lastDpad = -1
    }

    override fun isRunning(): Boolean = running.get()
    override fun lastError(): String = error
    override fun packetsReceived(): Long = received.get()

    override fun destroy() {
        stop()
        AndroidProcess.killProcess(AndroidProcess.myPid())
    }
}
