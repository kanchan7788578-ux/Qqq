package com.example.gamepad.network

import com.example.gamepad.model.NetworkMode
import com.example.gamepad.model.NetworkStatus
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

/**
 * Inspects local network interfaces without requiring Location permissions or internet access.
 * Automatically identifies Android Mobile Hotspot AP interfaces vs standard Local Wi-Fi.
 */
object NetworkTopologyHelper {

    private val hotspotInterfacePrefixes = listOf(
        "ap", "swlan", "softap", "wlan1", "wlan2", "rndis", "p2p"
    )

    fun detectNetworkStatus(): NetworkStatus {
        return try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            var hotspotIp: String? = null
            var hotspotIface: String? = null
            var wifiIp: String? = null
            var wifiIface: String? = null

            val broadcasts = linkedSetOf<String>()
            val gateways = linkedSetOf<String>()

            for (iface in interfaces) {
                if (!iface.isUp || iface.isLoopback) continue
                val name = iface.name.lowercase()

                for (ifaceAddr in iface.interfaceAddresses) {
                    val addr = ifaceAddr.address
                    if (addr !is Inet4Address || addr.isLoopbackAddress) continue
                    val ip = addr.hostAddress ?: continue
                    if (!isPrivateIpv4(ip)) continue

                    ifaceAddr.broadcast?.hostAddress?.let { broadcasts.add(it) }
                    deriveSubnetBroadcast(ip)?.let { broadcasts.add(it) }
                    deriveGatewayCandidate(ip)?.let { gateways.add(it) }

                    val looksLikeHotspot = hotspotInterfacePrefixes.any { name.startsWith(it) } ||
                        ip.startsWith("192.168.43.") ||
                        ip.startsWith("192.168.49.") ||
                        ip.startsWith("192.168.137.")

                    if (looksLikeHotspot) {
                        if (hotspotIp == null) {
                            hotspotIp = ip
                            hotspotIface = iface.name
                        }
                    } else if (name.startsWith("wlan") || name.startsWith("eth") || wifiIp == null) {
                        wifiIp = ip
                        wifiIface = iface.name
                    }
                }
            }

            broadcasts.add("255.255.255.255")

            when {
                hotspotIp != null -> NetworkStatus(
                    mode = NetworkMode.HOTSPOT_AP,
                    localIp = hotspotIp,
                    interfaceName = hotspotIface,
                    broadcastAddresses = broadcasts.toList(),
                    gatewayCandidates = gateways.toList()
                )
                wifiIp != null -> NetworkStatus(
                    mode = NetworkMode.LOCAL_WIFI,
                    localIp = wifiIp,
                    interfaceName = wifiIface,
                    broadcastAddresses = broadcasts.toList(),
                    gatewayCandidates = gateways.toList()
                )
                else -> NetworkStatus(mode = NetworkMode.OFFLINE)
            }
        } catch (_: Exception) {
            NetworkStatus(mode = NetworkMode.OFFLINE)
        }
    }

    private fun isPrivateIpv4(ip: String): Boolean {
        val parts = ip.split(".")
        if (parts.size != 4) return false
        val a = parts[0].toIntOrNull() ?: return false
        val b = parts[1].toIntOrNull() ?: return false
        return a == 10 ||
            (a == 172 && b in 16..31) ||
            (a == 192 && b == 168) ||
            (a == 169 && b == 254)
    }

    private fun deriveSubnetBroadcast(ip: String): String? {
        val lastDot = ip.lastIndexOf('.')
        if (lastDot <= 0) return null
        return ip.substring(0, lastDot) + ".255"
    }

    private fun deriveGatewayCandidate(ip: String): String? {
        val lastDot = ip.lastIndexOf('.')
        if (lastDot <= 0) return null
        return ip.substring(0, lastDot) + ".1"
    }

    fun isValidIpv4(ip: String): Boolean {
        val trimmed = ip.trim()
        val parts = trimmed.split(".")
        if (parts.size != 4) return false
        return parts.all { part ->
            val n = part.toIntOrNull() ?: return@all false
            n in 0..255 && part == n.toString()
        }
    }
}
