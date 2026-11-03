package com.wifipad.receiver

import java.net.InetAddress

/** Keeps a handshake provisional until the controller proves possession of the fresh server nonce. */
internal class SecureSessionTracker(
    private val activeTimeoutNanos: Long = Protocol.FAILSAFE_TIMEOUT_MS * 1_000_000L,
    private val pendingTimeoutNanos: Long = 1_200_000_000L,
    private val nonceHistoryLimit: Int = 64
) {
    enum class InputResult { ACCEPTED, PROMOTED, REJECTED }

    class Session internal constructor(
        val address: InetAddress,
        val port: Int,
        val clientNonce: Long,
        val serverNonce: Long,
        val createdNanos: Long,
        var lastInputNanos: Long = Long.MIN_VALUE,
        var lastSequence: Long = -1L
    ) {
        internal fun matchesHello(peer: InetAddress, peerPort: Int, nonce: Long): Boolean =
            address == peer && port == peerPort && clientNonce == nonce
    }

    private var active: Session? = null
    private var pending: Session? = null
    private val recentClientNonces = LinkedHashSet<Long>()
    private var handshakeWindowStartNanos = Long.MIN_VALUE
    private var newHandshakesInWindow = 0

    @Synchronized
    fun acceptHello(
        address: InetAddress,
        port: Int,
        clientNonce: Long,
        serverNonce: Long,
        nowNanos: Long
    ): Session? {
        if (port !in 1..65535) return null
        active?.takeIf { it.matchesHello(address, port, clientNonce) }?.let { return it }

        pending?.let { current ->
            if (nowNanos - current.createdNanos <= pendingTimeoutNanos) {
                return current.takeIf { it.matchesHello(address, port, clientNonce) }
            }
            pending = null
        }

        if (clientNonce in recentClientNonces) return null
        active?.let { current ->
            if (nowNanos - current.lastInputNanos <= activeTimeoutNanos) return null
        }

        if (handshakeWindowStartNanos == Long.MIN_VALUE ||
            nowNanos < handshakeWindowStartNanos ||
            nowNanos - handshakeWindowStartNanos >= HANDSHAKE_RATE_WINDOW_NANOS
        ) {
            handshakeWindowStartNanos = nowNanos
            newHandshakesInWindow = 0
        }
        if (newHandshakesInWindow >= MAX_NEW_HANDSHAKES_PER_WINDOW) return null
        newHandshakesInWindow++

        return Session(address, port, clientNonce, serverNonce, nowNanos).also { session ->
            pending = session
            rememberNonce(clientNonce)
        }
    }

    @Synchronized
    fun candidates(address: InetAddress, port: Int): List<Session> = buildList {
        pending?.takeIf { it.address == address && it.port == port }?.let(::add)
        active?.takeIf { it.address == address && it.port == port }?.let(::add)
    }

    @Synchronized
    fun acceptInput(session: Session, sequence: Long, nowNanos: Long): InputResult {
        if (sequence < 0L) return InputResult.REJECTED
        if (active === session) {
            if (sequence <= session.lastSequence) return InputResult.REJECTED
            session.lastSequence = sequence
            session.lastInputNanos = nowNanos
            return InputResult.ACCEPTED
        }
        if (pending !== session) return InputResult.REJECTED
        val current = active
        if (current != null && nowNanos - current.lastInputNanos <= activeTimeoutNanos) {
            return InputResult.REJECTED
        }
        if (sequence <= session.lastSequence) return InputResult.REJECTED

        active = session
        pending = null
        session.lastSequence = sequence
        session.lastInputNanos = nowNanos
        return InputResult.PROMOTED
    }

    @Synchronized
    fun reset() {
        active = null
        pending = null
        recentClientNonces.clear()
        handshakeWindowStartNanos = Long.MIN_VALUE
        newHandshakesInWindow = 0
    }

    private companion object {
        const val HANDSHAKE_RATE_WINDOW_NANOS = 10_000_000_000L
        const val MAX_NEW_HANDSHAKES_PER_WINDOW = 8
    }

    private fun rememberNonce(nonce: Long) {
        recentClientNonces.add(nonce)
        while (recentClientNonces.size > nonceHistoryLimit.coerceAtLeast(1)) {
            recentClientNonces.remove(recentClientNonces.first())
        }
    }
}
