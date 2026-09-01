package com.wifipad.receiver

import android.os.Process as AndroidProcess
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Runs in the separate process Shizuku spawns with shell (uid 2000) privilege via
 * Shizuku.bindUserService() — Shizuku's currently-recommended replacement for the
 * deprecated Shizuku#newProcess text-pipe API (see RikkaApps/Shizuku-API README).
 *
 * It owns both the `uinput` child process and the UDP socket so the whole
 * receive -> translate -> inject pipeline stays inside one privileged process
 * instead of crossing the Binder boundary on every packet (packets arrive at up
 * to 60 Hz).
 */
class GamepadUserService : IGamepadService.Stub() {

    @Volatile private var socket: DatagramSocket? = null
    @Volatile private var uinputProcess: Process? = null
    private val running = AtomicBoolean(false)
    @Volatile private var error: String = ""
    private val received = AtomicLong(0)
    private var lastButtons = 0
    private var lastDpad = -1

    override fun start(port: Int): Boolean {
        if (running.get()) return true
        return try {
            val proc = ProcessBuilder("uinput", "-")
                .redirectErrorStream(true)
                .start()
            uinputProcess = proc
            val pad = UinputGamepad(proc.outputStream)
            pad.register()

            val sock = DatagramSocket(null)
            sock.reuseAddress = true
            sock.bind(InetSocketAddress(port))
            socket = sock

            running.set(true)
            Thread { receiveLoop(sock, pad) }.apply {
                isDaemon = true
                priority = Thread.MAX_PRIORITY
                start()
            }
            true
        } catch (e: Exception) {
            error = e.message ?: "start failed"
            false
        }
    }

    private fun receiveLoop(sock: DatagramSocket, pad: UinputGamepad) {
        val buf = ByteArray(64)
        while (running.get()) {
            try {
                val packet = DatagramPacket(buf, buf.size)
                sock.receive(packet)
                if (packet.length < Protocol.PACKET_SIZE) continue
                val d = packet.data
                if (d[0] != Protocol.MAGIC) continue
                received.incrementAndGet()
                handlePacket(d, pad)
            } catch (e: Exception) {
                if (running.get()) error = e.message ?: "recv error"
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

        val events = mutableListOf<Triple<String, String, Int>>()
        events += Triple("EV_ABS", "ABS_X", leftX)
        events += Triple("EV_ABS", "ABS_Y", leftY)
        events += Triple("EV_ABS", "ABS_RX", rightX)
        events += Triple("EV_ABS", "ABS_RY", rightY)
        events += Triple("EV_ABS", "ABS_Z", lt)
        events += Triple("EV_ABS", "ABS_RZ", rt)

        if (dpad != lastDpad) {
            val (hx, hy) = hatFor(dpad)
            events += Triple("EV_ABS", "ABS_HAT0X", hx)
            events += Triple("EV_ABS", "ABS_HAT0Y", hy)
            lastDpad = dpad
        }

        if (buttons != lastButtons) {
            for ((bit, key) in buttonKeyMap) {
                val was = lastButtons and bit != 0
                val now = buttons and bit != 0
                if (was != now) events += Triple("EV_KEY", key, if (now) 1 else 0)
            }
            lastButtons = buttons
        }

        pad.inject(events)
    }

    private fun hatFor(dpad: Int): Pair<Int, Int> = when (dpad) {
        1 -> 0 to -1; 2 -> 1 to -1; 3 -> 1 to 0; 4 -> 1 to 1
        5 -> 0 to 1; 6 -> -1 to 1; 7 -> -1 to 0; 8 -> -1 to -1
        else -> 0 to 0
    }

    private val buttonKeyMap = listOf(
        ButtonBit.A to "BTN_A", ButtonBit.B to "BTN_B",
        ButtonBit.X to "BTN_X", ButtonBit.Y to "BTN_Y",
        ButtonBit.L1 to "BTN_TL", ButtonBit.R1 to "BTN_TR",
        ButtonBit.L3 to "BTN_THUMBL", ButtonBit.R3 to "BTN_THUMBR",
        ButtonBit.SELECT to "BTN_SELECT", ButtonBit.START to "BTN_START",
        ButtonBit.MODE to "BTN_MODE"
    )

    override fun stop() {
        running.set(false)
        socket?.close()
        socket = null
        uinputProcess?.let {
            try { it.outputStream.close() } catch (_: Exception) {}
            it.destroy()
        }
        uinputProcess = null
    }

    override fun isRunning(): Boolean = running.get()
    override fun lastError(): String = error
    override fun packetsReceived(): Long = received.get()

    override fun destroy() {
        stop()
        AndroidProcess.killProcess(AndroidProcess.myPid())
    }
}
