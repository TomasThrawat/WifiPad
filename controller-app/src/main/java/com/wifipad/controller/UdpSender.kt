package com.wifipad.controller

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Sends the current [GamepadState] as a UDP packet at a fixed rate.
 * UDP is used (not TCP) because a dropped frame every so often is harmless for
 * a controller — a stalled/retransmitting TCP stream is not.
 */
class UdpSender(private val state: GamepadState, private val hz: Int = 60) {

    private val running = AtomicBoolean(false)
    private var socket: DatagramSocket? = null
    private var address: InetAddress? = null
    private var port: Int = Protocol.DEFAULT_PORT
    private var executor: ScheduledExecutorService? = null
    var onError: ((String) -> Unit)? = null

    fun start(host: String, port: Int) {
        stop()
        try {
            address = InetAddress.getByName(host)
            this.port = port
            socket = DatagramSocket()
            running.set(true)
            val exec = Executors.newSingleThreadScheduledExecutor()
            executor = exec
            val periodMs = (1000L / hz).coerceAtLeast(1)
            exec.scheduleAtFixedRate({
                if (running.get()) sendOnce()
            }, 0, periodMs, TimeUnit.MILLISECONDS)
        } catch (e: Exception) {
            onError?.invoke(e.message ?: "connect error")
        }
    }

    private fun sendOnce() {
        try {
            val data = state.toPacket()
            socket?.send(DatagramPacket(data, data.size, address, port))
        } catch (e: Exception) {
            onError?.invoke(e.message ?: "send error")
        }
    }

    fun stop() {
        running.set(false)
        executor?.shutdownNow()
        executor = null
        socket?.close()
        socket = null
    }
}
