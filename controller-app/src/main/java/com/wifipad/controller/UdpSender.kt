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
    private var socket: DatagramSocket? = null
    private var address: InetAddress? = null
    private var port: Int = Protocol.DEFAULT_PORT
    private var executor: ScheduledExecutorService? = null
    private val packetBuffer = ByteArray(Protocol.PACKET_SIZE)
    private var datagram: DatagramPacket? = null
    var onError: ((String) -> Unit)? = null

    fun start(host: String, port: Int) {
        stop()
        try {
            address = InetAddress.getByName(host)
            this.port = port
            openSocket()
            running.set(true)
            val exec = Executors.newSingleThreadScheduledExecutor { runnable ->
                Thread(runnable, "wifipad-udp").apply { isDaemon = true }
            }
            executor = exec
            val periodNanos = 1_000_000_000L / hz.coerceAtLeast(1)
            exec.scheduleAtFixedRate({ if (running.get()) sendOnce() }, 0, periodNanos, TimeUnit.NANOSECONDS)
        } catch (e: Exception) {
            stop()
            onError?.invoke(e.message ?: "connect error")
        }
    }

    private fun openSocket() {
        val target = address ?: return
        val newSocket = DatagramSocket()
        socket = newSocket
        datagram = DatagramPacket(packetBuffer, packetBuffer.size, target, port)
    }

    private fun sendOnce() {
        try {
            if (socket == null || socket?.isClosed == true) openSocket()
            state.writePacket(packetBuffer)
            val p = datagram ?: return
            p.length = packetBuffer.size
            socket?.send(p)
        } catch (_: Exception) {
            socket?.close()
            socket = null
            datagram = null
        }
    }

    fun stop() {
        running.set(false)
        executor?.shutdownNow()
        executor = null
        socket?.close()
        socket = null
        datagram = null
    }
}
