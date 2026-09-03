package com.wifipad.receiver

import android.os.Process as AndroidProcess
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Runs in the separate process Shizuku spawns with shell (uid 2000) privilege via
 * Shizuku.bindUserService() -- Shizuku's currently-recommended replacement for the
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

            // uinput's stdout (merged with stderr above) must be drained continuously.
            // java.lang.Process's own docs warn that failing to read a subprocess's
            // output pipe can block -- even deadlock -- it once the OS pipe buffer
            // fills (source: developer.android.com/reference/kotlin/java/lang/Process).
            // Left undrained here, uinput eventually blocks trying to write and stops
            // reading any further commands from its stdin -- so every button/stick
            // packet after that point gets received and counted by receiveLoop() same
            // as before, but silently has zero effect because the frozen uinput
            // process never gets to act on the injected events.
            //
            // Every line is also logged (UINPUT OUTPUT) instead of just discarded --
            // uinput only writes to this stream on error, so a line here is itself a
            // diagnostic signal, not just plumbing. And when forEachLine returns, that
            // means EOF on the pipe, i.e. uinput closed stdout, which in practice means
            // the process is exiting/dead -- that's the earliest, cheapest point to
            // detect the death that otherwise only shows up later as a broken pad.inject()
            // write ("Stream closed"), so stop() is triggered right here instead of
            // waiting for the next packet to fail.
            Thread {
                try {
                    proc.inputStream.bufferedReader().forEachLine { line ->
                        FileLogger.logRaw("UINPUT OUTPUT - $line")
                    }
                } catch (_: Exception) {
                    // Expected once the process exits and the pipe closes.
                }
                val exitCode = try { proc.waitFor() } catch (_: Exception) { -1 }
                FileLogger.logRaw("UINPUT PROCESS ENDED - exit code: $exitCode")
                if (running.get()) {
                    error = "uinput process ended (exit code: $exitCode)"
                    stop()
                }
            }.apply { isDaemon = true; name = "uinput-drain"; start() }

            val pad = UinputGamepad(proc.outputStream)
            pad.register()
            FileLogger.logRaw("UINPUT REGISTERED - vid=0x045e pid=0x028e")

            val sock = DatagramSocket(null)
            sock.reuseAddress = true
            sock.bind(InetSocketAddress(port))
            socket = sock
            FileLogger.logRaw("START - service listening on port $port")

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

        val events = mutableListOf<Triple<Int, Int, Int>>()
        events += Triple(UinputGamepad.EV_ABS, UinputGamepad.ABS_X, leftX)
        events += Triple(UinputGamepad.EV_ABS, UinputGamepad.ABS_Y, leftY)
        events += Triple(UinputGamepad.EV_ABS, UinputGamepad.ABS_RX, rightX)
        events += Triple(UinputGamepad.EV_ABS, UinputGamepad.ABS_RY, rightY)
        events += Triple(UinputGamepad.EV_ABS, UinputGamepad.ABS_Z, lt)
        events += Triple(UinputGamepad.EV_ABS, UinputGamepad.ABS_RZ, rt)

        if (dpad != lastDpad) {
            FileLogger.logRaw("DPAD CHANGED - $lastDpad -> $dpad")
            val (hx, hy) = hatFor(dpad)
            events += Triple(UinputGamepad.EV_ABS, UinputGamepad.ABS_HAT0X, hx)
            events += Triple(UinputGamepad.EV_ABS, UinputGamepad.ABS_HAT0Y, hy)
            lastDpad = dpad
        }

        if (buttons != lastButtons) {
            for ((bit, key) in buttonKeyMap) {
                val was = lastButtons and bit != 0
                val now = buttons and bit != 0
                if (was != now) events += Triple(UinputGamepad.EV_KEY, key, if (now) 1 else 0)
            }
            lastButtons = buttons
        }

        try {
            pad.inject(events)
        } catch (e: Exception) {
            // The stream to uinput's stdin is gone (process died, or the OS pipe was
            // torn down) -- writing to it again on every subsequent packet would just
            // repeat the same failure at up to 60 Hz for nothing. Log it once, surface
            // it as the service's error state, and shut the pipeline down instead of
            // spinning; the drain thread above independently detects a dead uinput
            // process too, so whichever notices first wins -- stop() is idempotent.
            FileLogger.logRaw("INJECT ERROR - ${e.message ?: e.javaClass.simpleName}")
            error = "inject failed: ${e.message ?: e.javaClass.simpleName}"
            stop()
        }
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
        // CAS guard: stop() can now be reached from three places -- the Activity's
        // Stop button (Binder thread), the drain thread noticing uinput died, and
        // handlePacket() noticing an inject failure. Without this guard a death that
        // trips both detectors (or a Stop press racing either) would log "STOP" twice
        // and run destroy-order cleanup twice.
        if (!running.compareAndSet(true, false)) return
        FileLogger.logRaw("STOP - service stopping (packets received: ${received.get()})")
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
