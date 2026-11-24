package com.example.gamepad.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import com.example.gamepad.model.GamepadInputState
import com.example.gamepad.model.ReceiverTelemetry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Built-in TV Receiver / Host mode:
 * - Listens on UDP 55888 for 24-byte gamepad input packets & echoes PING -> PONG immediately
 * - Responds on UDP 55889 to discovery broadcasts over Hotspot/Wi-Fi
 * - Registers NSD service (_pulsepad._udp.) for zero-config pairing
 */
class TvReceiverServer(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val _telemetry = MutableStateFlow(ReceiverTelemetry())
    val telemetry: StateFlow<ReceiverTelemetry> = _telemetry.asStateFlow()

    private var gameJob: Job? = null
    private var discoveryJob: Job? = null
    private var statsJob: Job? = null
    private var gameSocket: DatagramSocket? = null
    private var discoverySocket: DatagramSocket? = null
    private var nsdManager: NsdManager? = null
    private var registrationListener: NsdManager.RegistrationListener? = null

    private val packetsThisSec = AtomicInteger(0)
    private val totalPackets = AtomicLong(0L)
    private var lastSequence = -1
    private var lastSenderIp: String? = null

    fun start(port: Int = GamepadPacketCodec.GAMEPAD_UDP_PORT) {
        if (_telemetry.value.isRunning) return
        lastSequence = -1
        packetsThisSec.set(0)
        totalPackets.set(0L)
        _telemetry.value = ReceiverTelemetry(isRunning = true, listenPort = port)

        registerNsdService(port)

        gameJob = scope.launch(Dispatchers.IO) {
            try {
                val sock = DatagramSocket(port).apply {
                    reuseAddress = true
                    trafficClass = 0x10
                    soTimeout = 500
                }
                gameSocket = sock
                val buf = ByteArray(128)
                val pkt = DatagramPacket(buf, buf.size)

                while (isActive && !sock.isClosed) {
                    try {
                        sock.receive(pkt)
                        val sender = pkt.address?.hostAddress
                        val data = pkt.data
                        val len = pkt.length
                        if (len >= 4 && data[0] == GamepadPacketCodec.MAGIC_0 && data[1] == GamepadPacketCodec.MAGIC_1) {
                            when (data[3]) {
                                GamepadPacketCodec.TYPE_PING -> {
                                    val ts = GamepadPacketCodec.decodePingTimestamp(
                                        data,
                                        len,
                                        GamepadPacketCodec.TYPE_PING
                                    )
                                    if (ts != null) {
                                        val pong = GamepadPacketCodec.encodePing(ts, GamepadPacketCodec.TYPE_PONG)
                                        val reply = DatagramPacket(pong, pong.size, pkt.address, pkt.port)
                                        sock.send(reply)
                                    }
                                }
                                GamepadPacketCodec.TYPE_INPUT_STATE -> {
                                    val decoded = GamepadPacketCodec.decodeInputState(data, len)
                                    if (decoded != null) {
                                        val (seq, input) = decoded
                                        // Drop stale out-of-order UDP packets (allow wrap/reset)
                                        if (seq > lastSequence || lastSequence - seq > 1000) {
                                            lastSequence = seq
                                            lastSenderIp = sender
                                            packetsThisSec.incrementAndGet()
                                            val total = totalPackets.incrementAndGet()
                                            _telemetry.value = _telemetry.value.copy(
                                                isRunning = true,
                                                lastSenderIp = sender,
                                                totalPackets = total,
                                                inputState = input
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } catch (_: SocketTimeoutException) {
                    } catch (_: Exception) {
                        if (!isActive) break
                    }
                }
            } catch (_: Exception) {
            }
        }

        discoveryJob = scope.launch(Dispatchers.IO) {
            try {
                val sock = DatagramSocket(GamepadPacketCodec.DISCOVERY_UDP_PORT).apply {
                    reuseAddress = true
                    broadcast = true
                    soTimeout = 600
                }
                discoverySocket = sock
                val buf = ByteArray(128)
                val pkt = DatagramPacket(buf, buf.size)
                val tvName = "${Build.MODEL ?: "Android"} TV"
                val respBytes = GamepadPacketCodec.encodeDiscoverResponse(tvName, port)

                while (isActive && !sock.isClosed) {
                    try {
                        sock.receive(pkt)
                        val reqName = GamepadPacketCodec.decodeDiscoverRequest(pkt.data, pkt.length)
                        if (reqName != null) {
                            val reply = DatagramPacket(respBytes, respBytes.size, pkt.address, pkt.port)
                            sock.send(reply)
                        }
                    } catch (_: SocketTimeoutException) {
                    } catch (_: Exception) {
                        if (!isActive) break
                    }
                }
            } catch (_: Exception) {
            }
        }

        statsJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(1000L)
                val pps = packetsThisSec.getAndSet(0)
                _telemetry.value = _telemetry.value.copy(
                    packetsPerSec = pps,
                    lastSenderIp = lastSenderIp
                )
            }
        }
    }

    fun stop() {
        gameJob?.cancel()
        discoveryJob?.cancel()
        statsJob?.cancel()
        gameJob = null
        discoveryJob = null
        statsJob = null

        try { gameSocket?.close() } catch (_: Exception) {}
        try { discoverySocket?.close() } catch (_: Exception) {}
        gameSocket = null
        discoverySocket = null

        unregisterNsdService()
        _telemetry.value = ReceiverTelemetry(isRunning = false)
    }

    private fun registerNsdService(port: Int) {
        try {
            val manager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return
            nsdManager = manager
            val serviceInfo = NsdServiceInfo().apply {
                serviceName = "${Build.MODEL ?: "PulsePad"} TV"
                serviceType = GamepadPacketCodec.NSD_SERVICE_TYPE
                setPort(port)
            }
            val listener = object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(info: NsdServiceInfo) {}
                override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
                override fun onServiceUnregistered(arg0: NsdServiceInfo) {}
                override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
            }
            registrationListener = listener
            manager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (_: Exception) {
        }
    }

    private fun unregisterNsdService() {
        val manager = nsdManager
        val listener = registrationListener
        if (manager != null && listener != null) {
            try {
                manager.unregisterService(listener)
            } catch (_: Exception) {
            }
        }
        registrationListener = null
    }
}
