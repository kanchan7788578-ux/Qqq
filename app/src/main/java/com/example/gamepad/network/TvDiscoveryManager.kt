package com.example.gamepad.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import com.example.gamepad.model.DiscoveredTv
import com.example.gamepad.model.NetworkMode
import com.example.gamepad.model.NetworkStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException

/**
 * Dual-engine zero-internet TV discovery:
 * 1. UDP Subnet Broadcast + Gateway Probing (most reliable across Android Mobile Hotspot APs)
 * 2. Android NsdManager (mDNS) for _pulsepad._udp. services
 */
class TvDiscoveryManager(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val _discoveredTvs = MutableStateFlow<List<DiscoveredTv>>(emptyList())
    val discoveredTvs: StateFlow<List<DiscoveredTv>> = _discoveredTvs.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private var scanJob: Job? = null
    private var nsdManager: NsdManager? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    fun startScan(networkStatus: NetworkStatus, durationMs: Long = 8000L) {
        stopScan()
        _isScanning.value = true

        acquireMulticastLock()
        startNsdDiscovery()

        scanJob = scope.launch(Dispatchers.IO) {
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket().apply {
                    broadcast = true
                    soTimeout = 350
                }
                val deviceName = Build.MODEL ?: "Android Phone"
                val requestPayload = GamepadPacketCodec.encodeDiscoverRequest(deviceName)
                val targets = buildProbeTargets(networkStatus)

                val recvBuf = ByteArray(256)
                val recvPacket = DatagramPacket(recvBuf, recvBuf.size)
                val endAt = System.currentTimeMillis() + durationMs
                var lastProbeAt = 0L

                while (isActive && System.currentTimeMillis() < endAt) {
                    val now = System.currentTimeMillis()
                    if (now - lastProbeAt >= 650L) {
                        lastProbeAt = now
                        for (ip in targets) {
                            try {
                                val addr = InetAddress.getByName(ip)
                                val pkt = DatagramPacket(
                                    requestPayload,
                                    requestPayload.size,
                                    addr,
                                    GamepadPacketCodec.DISCOVERY_UDP_PORT
                                )
                                socket.send(pkt)
                            } catch (_: Exception) {
                            }
                        }
                    }

                    try {
                        socket.receive(recvPacket)
                        val decoded = GamepadPacketCodec.decodeDiscoverResponse(
                            recvPacket.data,
                            recvPacket.length
                        )
                        val senderIp = recvPacket.address.hostAddress
                        if (decoded != null && senderIp != null) {
                            val (port, tvName) = decoded
                            val transport = if (networkStatus.mode == NetworkMode.HOTSPOT_AP) {
                                "Hotspot UDP"
                            } else {
                                "Wi-Fi UDP"
                            }
                            upsertTv(
                                DiscoveredTv(
                                    id = "$senderIp:$port",
                                    name = tvName,
                                    host = senderIp,
                                    port = port,
                                    transport = transport,
                                    lastSeenMs = System.currentTimeMillis()
                                )
                            )
                        }
                    } catch (_: SocketTimeoutException) {
                        // Continue loop until timeout
                    }
                }
            } catch (_: Exception) {
            } finally {
                try {
                    socket?.close()
                } catch (_: Exception) {
                }
                stopNsdDiscovery()
                releaseMulticastLock()
                _isScanning.value = false
            }
        }
    }

    fun stopScan() {
        scanJob?.cancel()
        scanJob = null
        stopNsdDiscovery()
        releaseMulticastLock()
        _isScanning.value = false
    }

    private fun buildProbeTargets(status: NetworkStatus): List<String> {
        val set = linkedSetOf<String>()
        set.addAll(status.broadcastAddresses)
        set.addAll(status.gatewayCandidates)
        set.add("255.255.255.255")
        return set.toList()
    }

    private fun upsertTv(tv: DiscoveredTv) {
        _discoveredTvs.update { current ->
            val filtered = current.filterNot { it.host == tv.host && it.port == tv.port }
            listOf(tv) + filtered
        }
    }

    private fun startNsdDiscovery() {
        try {
            val manager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return
            nsdManager = manager
            val listener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(regType: String) {}
                override fun onServiceFound(service: NsdServiceInfo) {
                    if (service.serviceType.contains("pulsepad")) {
                        resolveNsdService(manager, service)
                    }
                }
                override fun onServiceLost(service: NsdServiceInfo) {}
                override fun onDiscoveryStopped(serviceType: String) {}
                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                    try { manager.stopServiceDiscovery(this) } catch (_: Exception) {}
                }
                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                    try { manager.stopServiceDiscovery(this) } catch (_: Exception) {}
                }
            }
            discoveryListener = listener
            manager.discoverServices(
                GamepadPacketCodec.NSD_SERVICE_TYPE,
                NsdManager.PROTOCOL_DNS_SD,
                listener
            )
        } catch (_: Exception) {
        }
    }

    @Suppress("DEPRECATION")
    private fun resolveNsdService(manager: NsdManager, service: NsdServiceInfo) {
        try {
            manager.resolveService(service, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    val host = serviceInfo.host?.hostAddress ?: return
                    val port = if (serviceInfo.port > 0) serviceInfo.port else GamepadPacketCodec.GAMEPAD_UDP_PORT
                    upsertTv(
                        DiscoveredTv(
                            id = "$host:$port",
                            name = serviceInfo.serviceName.ifBlank { "Smart TV" },
                            host = host,
                            port = port,
                            transport = "mDNS / NSD",
                            lastSeenMs = System.currentTimeMillis()
                        )
                    )
                }
            })
        } catch (_: Exception) {
        }
    }

    private fun stopNsdDiscovery() {
        val manager = nsdManager
        val listener = discoveryListener
        if (manager != null && listener != null) {
            try {
                manager.stopServiceDiscovery(listener)
            } catch (_: Exception) {
            }
        }
        discoveryListener = null
    }

    private fun acquireMulticastLock() {
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            multicastLock = wifiManager?.createMulticastLock("pulsepad_discovery")?.apply {
                setReferenceCounted(false)
                acquire()
            }
        } catch (_: Exception) {
        }
    }

    private fun releaseMulticastLock() {
        try {
            if (multicastLock?.isHeld == true) {
                multicastLock?.release()
            }
        } catch (_: Exception) {
        } finally {
            multicastLock = null
        }
    }
}
