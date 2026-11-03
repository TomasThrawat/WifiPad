package com.wifipad.receiver

import android.os.Process as AndroidProcess
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class GamepadUserService : IGamepadService.Stub() {
    @Volatile private var socket: DatagramSocket? = null
    @Volatile private var uinputProcess: Process? = null
    @Volatile private var uinputPad: UinputGamepad? = null
    private val running = AtomicBoolean(false)
    @Volatile private var error: String = ""
    private val received = AtomicLong(0)
    private val rejected = AtomicLong(0)
    private val lost = AtomicLong(0)
    private val inputLock = Any()
    private val eventBuffer = IntArray(96)
    private var lastButtons = 0
    private var lastDpad = -1
    private var lastLeftX = 0
    private var lastLeftY = 0
    private var lastRightX = 0
    private var lastRightY = 0
    private var lastLeftTrigger = 0
    private var lastRightTrigger = 0
    @Volatile private var lastPacketNanos = 0L
    @Volatile private var lastRemote = ""
    @Volatile private var rateWindowStart = System.nanoTime()
    @Volatile private var rateWindowPackets = 0L
    @Volatile private var currentRate = 0L
    @Volatile private var pairingId = ByteArray(0)
    @Volatile private var pairingSecret = ByteArray(0)
    @Volatile private var sessionAddress: InetAddress? = null
    @Volatile private var sessionPort = -1
    @Volatile private var clientNonce = 0L
    @Volatile private var serverNonce = 0L
    private var lastSequence = -1L
    @Volatile private var failsafeApplied = false

    override fun start(port: Int, controllerId: String, secret: String): Boolean {
        if (running.get()) return true
        val id = SecureProtocol.fromHex(controllerId, 16) ?: run { error = "Invalid paired controller ID"; return false }
        val key = SecureProtocol.fromHex(secret, 32) ?: run { error = "Invalid pairing key"; return false }
        if (port !in 1..65535) { error = "Invalid UDP port"; return false }
        error = ""
        pairingId = id; pairingSecret = key
        received.set(0); rejected.set(0); lost.set(0)
        rateWindowStart = System.nanoTime(); rateWindowPackets = 0L; currentRate = 0L
        lastRemote = ""
        sessionAddress = null; sessionPort = -1; lastSequence = -1L
        lastPacketNanos = System.nanoTime(); failsafeApplied = false
        var proc: Process? = null
        var sock: DatagramSocket? = null
        return try {
            proc = ProcessBuilder("uinput", "-").redirectErrorStream(true).start()
            uinputProcess = proc
            Thread {
                try { proc.inputStream.bufferedReader().forEachLine { } } catch (_: Exception) { }
                val exit = try { proc.waitFor() } catch (_: Exception) { -1 }
                if (running.get()) { error = "uinput process ended (exit code: $exit)"; stop() }
            }.apply { isDaemon = true; name = "uinput-drain"; start() }
            val pad = UinputGamepad(proc.outputStream)
            pad.register()
            uinputPad = pad
            if (!proc.isAlive) throw IllegalStateException("uinput process exited during registration")
            sock = DatagramSocket(null).apply { reuseAddress = true; bind(InetSocketAddress(port)) }
            socket = sock
            running.set(true)
            Thread { receiveLoop(sock, pad) }.apply { isDaemon = true; priority = Thread.MAX_PRIORITY; name = "wifipad-secure-recv"; start() }
            Thread { failsafeLoop(pad) }.apply { isDaemon = true; name = "wifipad-failsafe"; start() }
            true
        } catch (e: Exception) {
            error = e.message ?: "start failed"
            running.set(false)
            runCatching { sock?.close() }
            uinputPad = null
            proc?.let { runCatching { it.outputStream.close() }; runCatching { it.destroy() } }
            uinputProcess = null
            false
        }
    }

    private fun receiveLoop(sock: DatagramSocket, pad: UinputGamepad) {
        val buffer = ByteArray(256)
        val packet = DatagramPacket(buffer, buffer.size)
        while (running.get()) {
            try {
                packet.length = buffer.size
                sock.receive(packet)
                val frame = packet.data.copyOfRange(packet.offset, packet.offset + packet.length)
                if (frame.size == SecureProtocol.HELLO_SIZE) {
                    val hello = SecureProtocol.parseHello(frame, pairingId, pairingSecret)
                    if (hello == null) { rejected.incrementAndGet(); continue }
                    val nonce = SecureRandom().nextLong()
                    synchronized(inputLock) {
                        sessionAddress = packet.address; sessionPort = packet.port
                        clientNonce = hello.clientNonce; serverNonce = nonce; lastSequence = -1L
                        lastPacketNanos = System.nanoTime(); failsafeApplied = false
                    }
                    val reply = SecureProtocol.encodeWelcome(pairingId, hello.clientNonce, nonce, pairingSecret)
                    sock.send(DatagramPacket(reply, reply.size, packet.address, packet.port))
                    continue
                }
                val currentAddress = sessionAddress
                if (currentAddress == null || packet.address != currentAddress || packet.port != sessionPort) {
                    rejected.incrementAndGet(); continue
                }
                val parsed = SecureProtocol.parseInput(frame, pairingId, clientNonce, serverNonce, pairingSecret)
                if (parsed == null) { rejected.incrementAndGet(); continue }
                var accepted = false
                synchronized(inputLock) {
                    if (!running.get() || parsed.sequence <= lastSequence) {
                        rejected.incrementAndGet()
                    } else {
                        if (lastSequence >= 0 && parsed.sequence > lastSequence + 1) lost.addAndGet(parsed.sequence - lastSequence - 1)
                        lastSequence = parsed.sequence
                        received.incrementAndGet()
                        lastPacketNanos = System.nanoTime()
                        failsafeApplied = false
                        lastRemote = packet.address.hostAddress ?: "unknown"
                        updateRate()
                        handleInput(parsed.input, pad)
                        accepted = true
                    }
                }
                if (!accepted) continue
                val ack = SecureProtocol.encodeAck(pairingId, clientNonce, serverNonce, parsed.sequence, pairingSecret)
                sock.send(DatagramPacket(ack, ack.size, packet.address, packet.port))
            } catch (e: Exception) {
                if (running.get()) error = e.message ?: "receive error"
            }
        }
    }

    private fun updateRate() {
        rateWindowPackets++
        val now = System.nanoTime()
        val elapsed = now - rateWindowStart
        if (elapsed >= 1_000_000_000L) {
            currentRate = (rateWindowPackets * 1_000_000_000L / elapsed).coerceAtLeast(0L)
            rateWindowPackets = 0
            rateWindowStart = now
        }
    }

    private fun failsafeLoop(pad: UinputGamepad) {
        val timeout = Protocol.FAILSAFE_TIMEOUT_MS * 1_000_000L
        while (running.get()) {
            try { Thread.sleep(100) } catch (_: InterruptedException) { return }
            var failed = false
            synchronized(inputLock) {
                if (running.get() && !failsafeApplied && System.nanoTime() - lastPacketNanos > timeout) {
                    try { neutralize(pad); failsafeApplied = true }
                    catch (_: Exception) { error = "failsafe failed"; failed = true }
                }
            }
            if (failed) { stop(); return }
        }
    }

    private fun handleInput(input: SecureProtocol.Input, pad: UinputGamepad) {
        val buttons = input.buttons
        val leftX = input.leftX
        val leftY = input.leftY
        val rightX = input.rightX
        val rightY = input.rightY
        val lt = input.leftTrigger
        val rt = input.rightTrigger
        val dpad = input.dpad
        var count = 0
        fun add(type: Int, code: Int, value: Int) {
            eventBuffer[count++] = type; eventBuffer[count++] = code; eventBuffer[count++] = value
        }
        if (leftX != lastLeftX) { add(UinputGamepad.EV_ABS, UinputGamepad.ABS_X, leftX); lastLeftX = leftX }
        if (leftY != lastLeftY) { add(UinputGamepad.EV_ABS, UinputGamepad.ABS_Y, leftY); lastLeftY = leftY }
        if (rightX != lastRightX) { add(UinputGamepad.EV_ABS, UinputGamepad.ABS_RX, rightX); lastRightX = rightX }
        if (rightY != lastRightY) { add(UinputGamepad.EV_ABS, UinputGamepad.ABS_RY, rightY); lastRightY = rightY }
        if (lt != lastLeftTrigger) { add(UinputGamepad.EV_ABS, UinputGamepad.ABS_Z, lt); lastLeftTrigger = lt }
        if (rt != lastRightTrigger) { add(UinputGamepad.EV_ABS, UinputGamepad.ABS_RZ, rt); lastRightTrigger = rt }
        if (dpad != lastDpad) {
            val hat = DpadHat.fromWireValue(dpad)
            add(UinputGamepad.EV_ABS, UinputGamepad.ABS_HAT0X, hat.first)
            add(UinputGamepad.EV_ABS, UinputGamepad.ABS_HAT0Y, hat.second)
            lastDpad = dpad
        }
        val map = listOf(
            ButtonBit.A to UinputGamepad.BTN_A, ButtonBit.B to UinputGamepad.BTN_B,
            ButtonBit.X to UinputGamepad.BTN_X, ButtonBit.Y to UinputGamepad.BTN_Y,
            ButtonBit.L1 to UinputGamepad.BTN_TL, ButtonBit.R1 to UinputGamepad.BTN_TR,
            ButtonBit.L3 to UinputGamepad.BTN_THUMBL, ButtonBit.R3 to UinputGamepad.BTN_THUMBR,
            ButtonBit.SELECT to UinputGamepad.BTN_SELECT, ButtonBit.START to UinputGamepad.BTN_START,
            ButtonBit.MODE to UinputGamepad.BTN_MODE
        )
        for ((bit, key) in map) {
            val was = lastButtons and bit != 0; val now = buttons and bit != 0
            if (was != now) add(UinputGamepad.EV_KEY, key, if (now) 1 else 0)
        }
        lastButtons = buttons
        if (count > 0) try { pad.inject(eventBuffer, count) }
        catch (_: Exception) { error = "inject failed"; stop() }
    }

    private fun neutralize(pad: UinputGamepad) {
        lastButtons = 0; lastDpad = 0
        lastLeftX = 0; lastLeftY = 0; lastRightX = 0; lastRightY = 0
        lastLeftTrigger = 0; lastRightTrigger = 0
        pad.injectNeutral()
    }

    override fun stop() {
        if (!running.compareAndSet(true, false)) return
        synchronized(inputLock) {
            uinputPad?.let { runCatching { neutralize(it) } }
            uinputPad = null
            runCatching { socket?.close() }; socket = null
            uinputProcess?.let { runCatching { it.outputStream.close() }; runCatching { it.destroy() } }
            uinputProcess = null
            sessionAddress = null; sessionPort = -1; lastSequence = -1
            pairingId.fill(0); pairingSecret.fill(0)
        }
    }

    override fun isRunning(): Boolean = running.get()
    override fun lastError(): String = error
    override fun packetsReceived(): Long = received.get()
    override fun packetsPerSecond(): Long = currentRate
    override fun rejectedPackets(): Long = rejected.get()
    override fun lostPackets(): Long = lost.get()
    override fun lastPacketTimeMs(): Long = if (received.get() == 0L) 0L else (System.nanoTime() - lastPacketNanos) / 1_000_000L
    override fun lastRemoteHost(): String = lastRemote
    override fun currentButtons(): Int = synchronized(inputLock) { lastButtons }
    override fun currentDpad(): Int = synchronized(inputLock) { lastDpad.coerceAtLeast(0) }
    override fun currentLeftX(): Int = synchronized(inputLock) { lastLeftX }
    override fun currentLeftY(): Int = synchronized(inputLock) { lastLeftY }
    override fun currentRightX(): Int = synchronized(inputLock) { lastRightX }
    override fun currentRightY(): Int = synchronized(inputLock) { lastRightY }
    override fun currentLeftTrigger(): Int = synchronized(inputLock) { lastLeftTrigger }
    override fun currentRightTrigger(): Int = synchronized(inputLock) { lastRightTrigger }
    override fun destroy() { stop(); AndroidProcess.killProcess(AndroidProcess.myPid()) }
}
