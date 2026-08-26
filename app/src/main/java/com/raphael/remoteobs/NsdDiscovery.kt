package com.raphael.remoteobs

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo

// ponytail: mDNS via Android NsdManager (stdlib). Companion script needed on OBS machine.
class NsdDiscovery(
    context: Context,
    private val onServiceFound: (host: String, port: Int) -> Unit
) {
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var listener: NsdManager.DiscoveryListener? = null

    fun start() {
        stop()
        val l = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onServiceFound(info: NsdServiceInfo) {
                nsdManager.resolveService(info, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {}
                    override fun onServiceResolved(info: NsdServiceInfo) {
                        info.host?.hostAddress?.let { onServiceFound(it, info.port) }
                    }
                })
            }
            override fun onServiceLost(info: NsdServiceInfo) {}
        }
        listener = l
        nsdManager.discoverServices("_obs-websocket._tcp", NsdManager.PROTOCOL_DNS_SD, l)
    }

    fun stop() {
        listener?.let { try { nsdManager.stopServiceDiscovery(it) } catch (_: Exception) {} }
        listener = null
    }
}
