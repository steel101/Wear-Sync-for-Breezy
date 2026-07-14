package com.steel101.wearsyncforbreezy

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withPermit
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.util.Collections

class AdbNetworkScanner {
    private val TAG = "AdbNetworkScanner"

    suspend fun discoverWatch(context: Context, onDeviceFound: (ip: String, port: Int) -> Unit) = coroutineScope {
        val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
        var found = false

        fun createListener() = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) { Log.d(TAG, "Discovery started: $regType") }
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (found) return
                Log.d(TAG, "Service found: ${serviceInfo.serviceName}")
                nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                        Log.e(TAG, "Resolve failed: $errorCode")
                    }
                    override fun onServiceResolved(resolvedInfo: NsdServiceInfo) {
                        if (found) return
                        val ip = resolvedInfo.host.hostAddress ?: return
                        val port = resolvedInfo.port
                        Log.d(TAG, "Resolved: $ip:$port")
                        found = true
                        onDeviceFound(ip, port)
                        this@coroutineScope.cancel()
                    }
                })
            }
            override fun onServiceLost(serviceInfo: NsdServiceInfo) {}
            override fun onDiscoveryStopped(regType: String) {}
            override fun onStartDiscoveryFailed(regType: String, errorCode: Int) { 
                try { nsdManager.stopServiceDiscovery(this) } catch (e: Exception) {}
            }
            override fun onStopDiscoveryFailed(regType: String, errorCode: Int) {
                try { nsdManager.stopServiceDiscovery(this) } catch (e: Exception) {}
            }
        }

        val listenerConnect = createListener()
        val listenerPairing = createListener()

        val job = launch {
            try {
                nsdManager.discoverServices("_adb-tls-connect._tcp", NsdManager.PROTOCOL_DNS_SD, listenerConnect)
                nsdManager.discoverServices("_adb-tls-pairing._tcp", NsdManager.PROTOCOL_DNS_SD, listenerPairing)
                delay(10000) // Discovery window
            } finally {
                try { nsdManager.stopServiceDiscovery(listenerConnect) } catch (e: Exception) {}
                try { nsdManager.stopServiceDiscovery(listenerPairing) } catch (e: Exception) {}
            }
        }
        job
    }

    suspend fun scanSubnet(
        ports: List<Int>,
        timeoutMs: Int = 450,
        onDeviceFound: (ip: String, port: Int) -> Unit
    ) = withContext(Dispatchers.IO) {
        val addresses = getAllLocalIps()
        val subnets = (addresses.map { it.substringBeforeLast(".") } + listOf(
            "192.168.43", "192.168.44", "192.168.1", "10.219.222", "172.20.10", "192.168.49"
        )).distinct().filter { it.isNotEmpty() }

        try {
            coroutineScope {
                val semaphore = kotlinx.coroutines.sync.Semaphore(200)
                var found = false

                for (base in subnets) {
                    for (i in 1..254) {
                        val host = "$base.$i"
                        if (addresses.contains(host)) continue
                        
                        launch {
                            for (port in ports) {
                                if (found) break
                                semaphore.withPermit {
                                    if (found) return@withPermit
                                    try {
                                        java.net.Socket().use { socket ->
                                            socket.connect(java.net.InetSocketAddress(host, port), timeoutMs)
                                            if (!found) {
                                                found = true
                                                onDeviceFound(host, port)
                                                this@coroutineScope.cancel()
                                            }
                                        }
                                    } catch (e: Exception) {}
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: CancellationException) {}
    }

    private fun getAllLocalIps(): List<String> {
        val foundIps = mutableListOf<String>()
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (ni in interfaces) {
                if (ni.isLoopback || !ni.isUp) continue
                if (!ni.name.contains("wlan") && !ni.name.contains("eth") && !ni.name.contains("ap")) {
                    if (!ni.name.startsWith("ap") && !ni.name.startsWith("softap")) continue
                }
                
                val addresses = ni.inetAddresses
                for (address in Collections.list(addresses)) {
                    if (!address.isLoopbackAddress && address is java.net.Inet4Address) {
                        val ip = address.hostAddress
                        if (ip != null && !ip.startsWith("127.")) {
                            foundIps.add(ip)
                        }
                    }
                }
            }
        } catch (e: Exception) {}
        return foundIps
    }
}
