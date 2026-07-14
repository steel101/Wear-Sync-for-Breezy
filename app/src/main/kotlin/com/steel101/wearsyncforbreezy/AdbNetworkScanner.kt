package com.steel101.wearsyncforbreezy

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withPermit
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.util.Collections

class AdbNetworkScanner {

    suspend fun scanSubnet(
        ports: List<Int>,
        timeoutMs: Int = 500,
        onDeviceFound: (ip: String, port: Int) -> Unit
    ) = withContext(Dispatchers.IO) {
        val localIp = getLocalIpAddress() ?: return@withContext
        val subnetBase = localIp.substringBeforeLast(".")

        coroutineScope {
            val semaphore = kotlinx.coroutines.sync.Semaphore(50)
            for (i in 1..254) {
                val host = "$subnetBase.$i"
                if (host == localIp) continue
                for (port in ports) {
                    launch {
                        semaphore.withPermit {
                            try {
                                Socket().use { socket ->
                                    socket.connect(InetSocketAddress(host, port), timeoutMs)
                                    onDeviceFound(host, port)
                                }
                            } catch (e: Exception) {
                            }
                        }
                    }
                }
            }
        }
    }

    private fun getLocalIpAddress(): String? {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            val sortedInterfaces = interfaces.sortedByDescending { it.name.contains("wlan") }
            
            for (networkInterface in sortedInterfaces) {
                if (networkInterface.isLoopback || !networkInterface.isUp) continue
                val addresses = networkInterface.inetAddresses
                for (address in Collections.list(addresses)) {
                    if (!address.isLoopbackAddress && address is java.net.Inet4Address) {
                        val ip = address.hostAddress
                        if (ip != null && !ip.startsWith("10.0.2.")) return ip // 10.0.2.x is often emulator
                    }
                }
            }
        } catch (e: Exception) {
        }
        return null
    }
}
