package com.wifipad.controller

import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.security.SecureRandom
import java.util.concurrent.Executors
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class UdpSender(
    private val state: GamepadState,
    private val pairing: TvPairing,
    private val hz: Int = 60
) {
    private val running = AtomicBoolean(false)
    private val sent = AtomicLong(0)
    private val acknowledged = AtomicLong(0)
    private val lost = AtomicLong(0)
    private val lastRtt = AtomicLong(0)
    @Volatile private var socket: DatagramSocket? = null
    @Volatile private var executor: ExecutorService? = null
    @Volatile private var lastAckNanos = 0L
    @Volatile private var lastAckSequence = -1L
    @Volatile var onError: ((String) -> Unit)? = null
    @Volatile var onStatus: ((ConnectionStats) -> Unit)? = null

    data class ConnectionStats(val connected: Boolean, val sent: Long, val acknowledged: Long, val lost: Long, val rttMs: Long)

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
                try {
                    val resolved = InetAddress.getByName(host)
                    active = DatagramSocket().apply { soTimeout = 350 }
                    socket = active
                    val clientNonce = SecureRandom().nextLong()
                    val hello = SecureProtocol.encodeHello(controllerId, clientNonce, secret)
                    active.send(DatagramPacket(hello, hello.size, resolved, port))
                    val response = ByteArray(SecureProtocol.WELCOME_SIZE)
                    val welcome = DatagramPacket(response, response.size)
                    active.receive(welcome)
                    val serverNonce = SecureProtocol.parseWelcome(
                        response.copyOf(welcome.length), controllerId, clientNonce, secret
                    ) ?: throw SecurityException("The TV rejected this pairing. Scan its current QR code again.")
                    active.soTimeout = 6
                    lastAckNanos = System.nanoTime()
                    lastAckSequence = -1L
                    reconnectDelayMs = 500L
                    onStatus?.invoke(stats(true))
                    val period = 1_000_000_000L / hz.coerceIn(1, 120)
                    var nextTick = System.nanoTime()
                    var sequence = 0L
                    var lastStatsNanos = nextTick
                    while (running.get() && !Thread.currentThread().isInterrupted) {
                        val payload = SecureProtocol.encodePayload(
                            SecureProtocol.Input(
                                state.buttons, state.leftX.toInt(), state.leftY.toInt(),
                                state.rightX.toInt(), state.rightY.toInt(), state.leftTrigger,
                                state.rightTrigger, state.dpad
                            )
                        )
                        val frame = SecureProtocol.encodeInput(
                            controllerId, clientNonce, serverNonce, sequence, payload, secret
                        )
                        val sentAt = System.nanoTime()
                        active.send(DatagramPacket(frame, frame.size, resolved, port))
                        sent.incrementAndGet()
                        receiveAck(active, resolved, port, controllerId, clientNonce, serverNonce, secret, sentAt)
                        sequence++
                        if (sequence > 10 && System.nanoTime() - lastAckNanos > 2_000_000_000L) {
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
                    // TV or Wi-Fi is temporarily unavailable. Retry with bounded backoff.
                    lost.incrementAndGet()
                } catch (_: Exception) {
                    if (running.get()) lost.incrementAndGet()
                } finally {
                    runCatching { active?.close() }
                    if (socket === active) socket = null
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

    private fun receiveAck(
        active: DatagramSocket, host: InetAddress, port: Int, controllerId: ByteArray,
        clientNonce: Long, serverNonce: Long, secret: ByteArray, sentAt: Long
    ) {
        val buffer = ByteArray(SecureProtocol.ACK_SIZE)
        val packet = DatagramPacket(buffer, buffer.size)
        try {
            active.receive(packet)
            if (packet.address != host || packet.port != port) return
            val ack = SecureProtocol.parseAck(packet.data.copyOf(packet.length), controllerId, clientNonce, serverNonce, secret)
                ?: return
            if (ack.sequence <= lastAckSequence) return
            lastAckSequence = ack.sequence
            acknowledged.incrementAndGet()
            lastRtt.set(((System.nanoTime() - sentAt) / 1_000_000L).coerceAtLeast(0L))
            lastAckNanos = System.nanoTime()
        } catch (_: SocketTimeoutException) { }
    }

    private fun stats(connected: Boolean) = ConnectionStats(
        connected, sent.get(), acknowledged.get(), lost.get(), lastRtt.get()
    )

    fun stop() {
        running.set(false)
        executor?.shutdownNow()
        executor = null
        runCatching { socket?.close() }
        socket = null
        state.buttons = 0; state.leftX = 0; state.leftY = 0
        state.rightX = 0; state.rightY = 0; state.leftTrigger = 0; state.rightTrigger = 0
        state.dpad = 0
        onStatus?.invoke(stats(false))
    }
}
