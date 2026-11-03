package com.wifipad.receiver

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo

internal class TvNsdAdvertiser(context: Context) {
    private val manager = context.applicationContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var registration: NsdManager.RegistrationListener? = null

    fun start(tvId: String, port: Int) {
        if (registration != null || port !in 1..65535) return
        val info = NsdServiceInfo().apply {
            serviceName = "WifiPad-${tvId.take(8)}"
            serviceType = SERVICE_TYPE
            setPort(port)
            setAttribute("tvId", tvId)
            setAttribute("version", "2")
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) = Unit
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) { registration = null }
            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) { registration = null }
        }
        registration = listener
        runCatching { manager.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener) }
            .onFailure { registration = null }
    }

    fun stop() {
        val current = registration ?: return
        registration = null
        runCatching { manager.unregisterService(current) }
    }

    companion object { const val SERVICE_TYPE = "_wifipad._udp." }
}
