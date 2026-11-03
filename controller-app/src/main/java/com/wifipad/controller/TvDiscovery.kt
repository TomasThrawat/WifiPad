package com.wifipad.controller

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import java.net.Inet4Address

internal data class DiscoveredTv(val name: String, val tvId: String, val host: String, val port: Int)

internal class TvDiscovery(context: Context, private val onDevice: (DiscoveredTv) -> Unit) {
    private val manager = context.applicationContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var listener: NsdManager.DiscoveryListener? = null
    private val resolving = mutableSetOf<String>()

    @Suppress("DEPRECATION")
    fun start() {
        if (listener != null) return
        val current = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) = stop()
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = stop()
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onServiceLost(serviceInfo: NsdServiceInfo) { resolving.remove(serviceInfo.serviceName) }
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (serviceInfo.serviceType != SERVICE_TYPE || !resolving.add(serviceInfo.serviceName)) return
                manager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) { resolving.remove(info.serviceName) }
                    override fun onServiceResolved(info: NsdServiceInfo) {
                        resolving.remove(info.serviceName)
                        val id = info.attributes["tvId"]?.toString(Charsets.UTF_8).orEmpty()
                        val host = info.host?.hostAddress.orEmpty()
                        if (id.isNotBlank() && host.isNotBlank() && info.port in 1..65535) {
                            onDevice(DiscoveredTv(info.serviceName, id, host, info.port))
                        }
                    }
                })
            }
        }
        listener = current
        try { manager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, current) }
        catch (_: Exception) { listener = null }
    }

    fun stop() {
        val current = listener ?: return
        listener = null
        resolving.clear()
        runCatching { manager.stopServiceDiscovery(current) }
    }

    companion object { const val SERVICE_TYPE = "_wifipad._udp." }
}
