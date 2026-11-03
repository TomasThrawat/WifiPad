package com.wifipad.controller

import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.security.SecureRandom
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

internal class UdpSender(
    private val state: GamepadState,
    private val pairing: TvPairing,
    private val hz: Int = 60
) {
    private val running = AtomicBoolean(false)
    private val sent = AtomicLong(0)
    private val acknowledged = AtomicLong(0)
    private val lost = AtomicLong(0)
    private val lastRtt = AtomicLong(0)
    @Volatile private var executor: ExecutorService? = null
    @Volatile private var activeSession: ActiveSession? = null
    @Volatile private var lastAckNanos = 0L
    @Volatile var onError: ((String) -> Unit)? = null
    @Volatile var onStatus: ((ConnectionStats) -> Unit)? = null

    data class ConnectionStats(val connected: Boolean, val sent: Long, val acknowledged: Long, val lost: Long, val rttMs: Long)

    private data class ActiveSession(
        val socket: DatagramSocket,
        val address: InetAddress,
        val port: Int,
        val controllerId: ByteArray,
        val clientNonce: Long,
        val serverNonce: Long,
        val secret: ByteArray
    ) {
        val lock = Any()
        val ackLedger = AckLedger(MAX_PENDING_ACK_TIMES)
        var nextSequence = 0L
        @Volatile var stopRequested = false
    }

    private data class Handshake(val clientNonce: Long, val serverNonce: Long)

    fun start(host: String, port: Int) {
        stop()
        if (host.isBlank() || port !in 1..65535) {
            onError?.invoke("Invalid receiver address")
            return
        }
        val controllerId = SecureProtocol.fromHex(pairing.controllerId, 16)
        val secret = SecureProtocol.fromHex(pairing.secret, 32)
        if (controllerId == null || secret == null || pairing.tvId.isBlank()) {
            onError?.invoke("The saved TV pairing is invalid. Scan its QR code again.")
            return
        }

        val exec = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "wifipad-secure-udp").apply { isDaemon = true }
        }
        executor = exec
        running.set(true)
        exec.execute {
            var reconnectDelayMs = 500L
            while (running.get() && !Thread.currentThread().isInterrupted) {
                var active: DatagramSocket? = null
                var session: ActiveSession? = null
                try {
                    val resolved = InetAddress.getByName(host)
                    active = DatagramSocket().apply { soTimeout = HANDSHAKE_TIMEOUT_MS }
                    val handshake = performHandshake(active, resolved, port, controllerId, secret)
                    if (!running.get()) throw InterruptedException("Controller stopped during handshake")

                    active.soTimeout = ACK_TIMEOUT_MS
                    session = ActiveSession(
                        active, resolved, port, controllerId, handshake.clientNonce,
                        handshake.serverNonce, secret
                    )
                    activeSession = session
                    lastAckNanos = System.nanoTime()
                    reconnectDelayMs = 500L
                    onStatus?.invoke(stats(true))

                    val period = 1_000_000_000L / hz.coerceIn(1, 120)
                    var nextTick = System.nanoTime()
                    var lastStatsNanos = nextTick
                    while (running.get() && !Thread.currentThread().isInterrupted) {
                        sendCurrentInput(session, ACK_TIMEOUT_MS)
                        if (session.nextSequence > 10L && System.nanoTime() - lastAckNanos > ACK_LOSS_TIMEOUT_NANOS) {
                            throw IOException("No authenticated acknowledgements; reconnecting")
                        }
                        nextTick += period
                        val remaining = nextTick - System.nanoTime()
                        if (remaining > 0L) TimeUnit.NANOSECONDS.sleep(remaining)
                        else nextTick = System.nanoTime() + period
                        val now = System.nanoTime()
                        if (now - lastStatsNanos >= 1_000_000_000L) {
                            onStatus?.invoke(stats(true))
                            lastStatsNanos = now
                        }
                    }
                } catch (failure: SecurityException) {
                    if (running.get()) onError?.invoke(failure.message ?: "Pairing rejected")
                    running.set(false)
                } catch (_: SocketTimeoutException) {
                    if (running.get()) lost.incrementAndGet()
                } catch (_: InterruptedException) {
                    if (running.get()) lost.incrementAndGet()
                } catch (_: Exception) {
                    if (running.get()) lost.incrementAndGet()
                } finally {
                    if (session != null) {
                        synchronized(session.lock) {
                            if (!session.stopRequested) {
                                runCatching { session.socket.close() }
                                if (activeSession === session) activeSession = null
                            }
                        }
                    } else {
                        runCatching { active?.close() }
                    }
                    onStatus?.invoke(stats(false))
                }
                if (running.get()) {
                    try { TimeUnit.MILLISECONDS.sleep(reconnectDelayMs) }
                    catch (_: InterruptedException) { break }
                    reconnectDelayMs = (reconnectDelayMs * 2).coerceAtMost(5_000L)
                }
            }
            onStatus?.invoke(stats(false))
        }
    }

    private fun performHandshake(
        socket: DatagramSocket,
        address: InetAddress,
        port: Int,
        controllerId: ByteArray,
        secret: ByteArray
    ): Handshake {
        val clientNonce = SecureRandom().nextLong()
        val hello = SecureProtocol.encodeHello(controllerId, clientNonce, secret)
        var attempts = 0
        while (attempts < HANDSHAKE_ATTEMPTS) {
            socket.send(DatagramPacket(hello, hello.size, address, port))
            val response = ByteArray(SecureProtocol.WELCOME_SIZE)
            val packet = DatagramPacket(response, response.size)
            try {
                socket.receive(packet)
                if (packet.address != address || packet.port != port) {
                    attempts++
                    continue
                }
                val serverNonce = SecureProtocol.parseWelcome(
                    packet.data.copyOf(packet.length), controllerId, clientNonce, secret
                ) ?: throw SecurityException("The TV rejected this pairing. Scan its current QR code again.")
                return Handshake(clientNonce, serverNonce)
            } catch (_: SocketTimeoutException) {
                attempts++
            }
        }
        throw SocketTimeoutException("The TV did not complete the secure gamepad handshake")
    }

    private fun sendCurrentInput(session: ActiveSession, ackTimeoutMs: Int): Boolean = synchronized(session.lock) {
        val input = SecureProtocol.Input(
            state.buttons, state.leftX.toInt(), state.leftY.toInt(), state.rightX.toInt(), state.rightY.toInt(),
            state.leftTrigger, state.rightTrigger, state.dpad
        )
        sendInputLocked(session, input, ackTimeoutMs, allowStopping = false)
    }

    private fun sendInputLocked(
        session: ActiveSession,
        input: SecureProtocol.Input,
        ackTimeoutMs: Int,
        allowStopping: Boolean
    ): Boolean {
        if (session.socket.isClosed || (!allowStopping && (!running.get() || session.stopRequested))) return false
        if (session.nextSequence < 0L || session.nextSequence == Long.MAX_VALUE) {
            throw IOException("Gamepad session sequence exhausted; reconnecting")
        }
        val sequence = session.nextSequence++
        val payload = SecureProtocol.encodePayload(input)
        val frame = SecureProtocol.encodeInput(
            session.controllerId, session.clientNonce, session.serverNonce, sequence, payload, session.secret
        )
        val sentAt = System.nanoTime()
        session.socket.send(DatagramPacket(frame, frame.size, session.address, session.port))
        sent.incrementAndGet()
        session.ackLedger.recordSent(sequence, sentAt)
        return receiveAcksLocked(session, ackTimeoutMs, sequence)
    }

    /** Reads until timeout (or the requested ACK) and pairs each ACK with its own send timestamp. */
    private fun receiveAcksLocked(session: ActiveSession, timeoutMs: Int, expectedSequence: Long): Boolean {
        if (timeoutMs <= 0 || session.socket.isClosed) return false
        val previousTimeout = session.socket.soTimeout
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs.toLong())
        val buffer = ByteArray(SecureProtocol.ACK_SIZE)
        var expectedAcknowledged = false
        try {
            while (!session.socket.isClosed) {
                val remaining = deadline - System.nanoTime()
                if (remaining <= 0L) break
                val remainingMs = ((remaining + 999_999L) / 1_000_000L).coerceIn(1L, timeoutMs.toLong())
                session.socket.soTimeout = remainingMs.toInt()
                val packet = DatagramPacket(buffer, buffer.size)
                try {
                    session.socket.receive(packet)
                } catch (_: SocketTimeoutException) {
                    break
                }
                if (packet.address != session.address || packet.port != session.port) continue
                val ack = SecureProtocol.parseAck(
                    packet.data.copyOf(packet.length), session.controllerId,
                    session.clientNonce, session.serverNonce, session.secret
                ) ?: continue
                val now = System.nanoTime()
                val rttNanos = session.ackLedger.acknowledge(ack.sequence, now) ?: continue
                acknowledged.incrementAndGet()
                lastRtt.set((rttNanos / 1_000_000L).coerceAtLeast(0L))
                lastAckNanos = now
                if (ack.sequence == expectedSequence) {
                    expectedAcknowledged = true
                    break
                }
            }
        } finally {
            if (!session.socket.isClosed) session.socket.soTimeout = previousTimeout
        }
        return expectedAcknowledged
    }

    private fun stats(connected: Boolean) = ConnectionStats(
        connected, sent.get(), acknowledged.get(), lost.get(), lastRtt.get()
    )

    /** Releases all gamepad controls immediately while keeping the authenticated session alive. */
    fun sendNeutralNow(): Boolean {
        state.clear()
        val session = activeSession ?: return false
        return synchronized(session.lock) {
            if (session.stopRequested) return@synchronized false
            sendInputLocked(session, NEUTRAL_INPUT, NEUTRAL_ACK_TIMEOUT_MS, allowStopping = false)
        }
    }

    fun stop() {
        state.clear()
        val session = activeSession
        session?.stopRequested = true
        running.set(false)
        executor?.shutdownNow()
        executor = null
        if (session != null) {
            synchronized(session.lock) {
                if (!session.socket.isClosed) {
                    runCatching {
                        sendInputLocked(session, NEUTRAL_INPUT, STOP_ACK_TIMEOUT_MS, allowStopping = true)
                    }
                }
                if (activeSession === session) activeSession = null
                runCatching { session.socket.close() }
            }
        }
        onStatus?.invoke(stats(false))
    }

    private companion object {
        const val HANDSHAKE_TIMEOUT_MS = 300
        const val HANDSHAKE_ATTEMPTS = 3
        const val ACK_TIMEOUT_MS = 6
        const val NEUTRAL_ACK_TIMEOUT_MS = 40
        const val STOP_ACK_TIMEOUT_MS = 60
        const val MAX_PENDING_ACK_TIMES = 128
        const val ACK_LOSS_TIMEOUT_NANOS = 2_000_000_000L
        val NEUTRAL_INPUT = SecureProtocol.Input(0, 0, 0, 0, 0, 0, 0, 0)
    }
}
