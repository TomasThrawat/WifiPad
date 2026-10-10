package com.wifipad.controller

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class UdpSender(private val state: GamepadState, private val hz: Int = 60) {
    private val running = AtomicBoolean(false)
    private val transportLock = Any()
    @Volatile private var socket: DatagramSocket? = null
    @Volatile private var address: InetAddress? = null
    @Volatile private var port: Int = Protocol.DEFAULT_PORT
    @Volatile private var executor: ScheduledExecutorService? = null
    private val packetBuffer = ByteArray(Protocol.PACKET_SIZE)
    private var datagram: DatagramPacket? = null
    @Volatile var onError: ((String) -> Unit)? = null

    fun start(host: String, port: Int) {
        stop()
        if (host.isBlank()) {
            onError?.invoke("Host is empty")
            return
        }
        if (port !in 1..65535) {
            onError?.invoke("Invalid UDP port")
            return
        }

        val exec = Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "wifipad-udp").apply { isDaemon = true }
        }
        executor = exec
        running.set(true)

        try {
            exec.execute {
                try {
                    // DNS and socket setup run off the main thread.
                    val resolvedAddress = InetAddress.getByName(host)
                    val opened = synchronized(transportLock) {
                        if (!running.get()) {
                            false
                        } else {
                            address = resolvedAddress
                            this.port = port
                            openSocket()
                            true
                        }
                    }
                    if (opened && running.get()) {
                        val periodNanos = 1_000_000_000L / hz.coerceIn(1, 240)
                        exec.scheduleAtFixedRate(
                            { if (running.get()) sendOnce() },
                            0,
                            periodNanos,
                            TimeUnit.NANOSECONDS
                        )
                    }
                } catch (e: Exception) {
                    if (running.get()) {
                        val message = e.message ?: "connect error"
                        stop()
                        onError?.invoke(message)
                    }
                }
            }
        } catch (e: Exception) {
            if (running.get()) {
                val message = e.message ?: "connect error"
                stop()
                onError?.invoke(message)
            }
        }
    }

    private fun openSocket() {
        val target = address ?: return
        val newSocket = DatagramSocket()
        socket = newSocket
        datagram = DatagramPacket(packetBuffer, packetBuffer.size, target, port)
    }

    private fun sendOnce() {
        synchronized(transportLock) {
            if (!running.get()) return
            try {
                if (socket == null || socket?.isClosed == true) openSocket()
                state.writePacket(packetBuffer)
                val packet = datagram ?: return
                packet.length = packetBuffer.size
                socket?.send(packet)
            } catch (_: Exception) {
                // Retry on the next tick. The receiver's watchdog releases stale inputs.
                socket?.close()
                socket = null
                datagram = null
            }
        }
    }

    fun stop() {
        running.set(false)
        val oldExecutor = executor
        executor = null
        oldExecutor?.shutdownNow()

        synchronized(transportLock) {
            val currentSocket = socket
            val currentPacket = datagram
            if (currentSocket != null && !currentSocket.isClosed && currentPacket != null) {
                try {
                    // Release held buttons immediately instead of waiting for the receiver watchdog.
                    val neutral = GamepadState().toPacket()
                    currentSocket.send(
                        DatagramPacket(
                            neutral,
                            neutral.size,
                            currentPacket.address,
                            currentPacket.port
                        )
                    )
                } catch (_: Exception) {
                    // Best effort: closing the socket still activates receiver-side failsafe.
                }
            }
            currentSocket?.close()
            socket = null
            datagram = null
            address = null
            port = Protocol.DEFAULT_PORT
        }
    }
}
