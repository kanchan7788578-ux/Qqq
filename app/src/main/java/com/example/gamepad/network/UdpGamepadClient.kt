package com.example.gamepad.network

import android.os.Process
import com.example.gamepad.model.ConnectionState
import com.example.gamepad.model.DiscoveredTv
import com.example.gamepad.model.GamepadInputState
import com.example.gamepad.model.PollingRate
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
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs

/**
 * Ultra-low-latency UDP client.
 * - Pre-allocated 24-byte buffer to avoid GC allocations during gameplay
 * - Immediate event-driven dispatch when buttons/sticks change
 * - Configurable steady polling rate (60/120/240 Hz)
 * - Continuous RTT ping/pong monitor for live latency & jitter telemetry
 */
class UdpGamepadClient(
    private val scope: CoroutineScope
) {
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val currentInput = AtomicReference(GamepadInputState())
    private val sequenceCounter = AtomicInteger(0)
    private val sendExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "PulsePad-UDP-TX").apply {
            priority = Thread.MAX_PRIORITY
        }
    }

    @Volatile
    private var socket: DatagramSocket? = null

    @Volatile
    private var targetAddress: InetAddress? = null

    @Volatile
    private var targetPort: Int = GamepadPacketCodec.GAMEPAD_UDP_PORT

    @Volatile
    private var pollingRate: PollingRate = PollingRate.HZ_120

    @Volatile
    private var lastSentInput: GamepadInputState? = null

    private val txBuffer = ByteArray(GamepadPacketCodec.INPUT_PACKET_SIZE)
    private val packetsSentThisSecond = AtomicInteger(0)

    private var heartbeatJob: Job? = null
    private var pingRxJob: Job? = null
    private var pingTxJob: Job? = null

    @Volatile
    private var latestRttMs: Int = 1

    @Volatile
    private var latestJitterMs: Int = 0

    @Volatile
    private var pingsSent: Int = 0

    @Volatile
    private var pongsReceived: Int = 0

    fun setPollingRate(rate: PollingRate) {
        pollingRate = rate
    }

    fun connect(tv: DiscoveredTv, rate: PollingRate) {
        disconnect()
        pollingRate = rate
        _connectionState.value = ConnectionState.Connecting(tv)

        scope.launch(Dispatchers.IO) {
            try {
                val inetAddr = InetAddress.getByName(tv.host)
                val udpSocket = DatagramSocket().apply {
                    // IPTOS_LOWDELAY (0x10) hints Wi-Fi/router WMM AC_VO / low-latency queuing
                    trafficClass = 0x10
                    soTimeout = 400
                    reuseAddress = true
                }
                socket = udpSocket
                targetAddress = inetAddr
                targetPort = tv.port
                sequenceCounter.set(0)
                pingsSent = 0
                pongsReceived = 0
                latestRttMs = 1
                latestJitterMs = 0

                _connectionState.value = ConnectionState.Connected(
                    target = tv,
                    rttMs = 1,
                    jitterMs = 0,
                    packetsPerSec = 0,
                    packetLossPercent = 0
                )

                startPingReceiver(udpSocket, tv)
                startPingTransmitter()
                startPollingLoop(tv)
            } catch (_: Exception) {
                _connectionState.value = ConnectionState.Disconnected
            }
        }
    }

    fun updateInput(newState: GamepadInputState) {
        val prev = currentInput.getAndSet(newState)
        if (prev != newState) {
            // Instant event-driven UDP send for 0ms artificial delay
            dispatchInputAsync(newState)
        }
    }

    private fun dispatchInputAsync(state: GamepadInputState) {
        val activeSocket = socket ?: return
        val addr = targetAddress ?: return
        val port = targetPort

        sendExecutor.execute {
            try {
                Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            } catch (_: Exception) {
            }
            try {
                val seq = sequenceCounter.incrementAndGet()
                GamepadPacketCodec.encodeInputState(state, seq, txBuffer)
                val packet = DatagramPacket(txBuffer, GamepadPacketCodec.INPUT_PACKET_SIZE, addr, port)
                activeSocket.send(packet)
                lastSentInput = state
                packetsSentThisSecond.incrementAndGet()
            } catch (_: Exception) {
            }
        }
    }

    private fun startPollingLoop(tv: DiscoveredTv) {
        heartbeatJob = scope.launch(Dispatchers.IO) {
            var lastStatsTick = System.currentTimeMillis()
            while (isActive && socket != null) {
                val state = currentInput.get()
                val hasActiveAnalog = state.leftX != 0f || state.leftY != 0f ||
                    state.rightX != 0f || state.rightY != 0f ||
                    state.leftTrigger > 0f || state.rightTrigger > 0f ||
                    state.buttons != 0

                if (hasActiveAnalog || lastSentInput != state) {
                    dispatchInputAsync(state)
                }

                val now = System.currentTimeMillis()
                if (now - lastStatsTick >= 1000L) {
                    val pps = packetsSentThisSecond.getAndSet(0)
                    val loss = if (pingsSent > 2) {
                        (((pingsSent - pongsReceived).coerceAtLeast(0)) * 100 / pingsSent).coerceIn(0, 100)
                    } else 0
                    _connectionState.value = ConnectionState.Connected(
                        target = tv,
                        rttMs = latestRttMs,
                        jitterMs = latestJitterMs,
                        packetsPerSec = pps,
                        packetLossPercent = loss
                    )
                    lastStatsTick = now
                }
                delay(pollingRate.intervalMs)
            }
        }
    }

    private fun startPingTransmitter() {
        pingTxJob = scope.launch(Dispatchers.IO) {
            while (isActive && socket != null) {
                try {
                    val activeSocket = socket ?: break
                    val addr = targetAddress ?: break
                    val pingBytes = GamepadPacketCodec.encodePing(System.nanoTime())
                    val packet = DatagramPacket(pingBytes, pingBytes.size, addr, targetPort)
                    activeSocket.send(packet)
                    pingsSent++
                } catch (_: Exception) {
                }
                delay(350L)
            }
        }
    }

    private fun startPingReceiver(udpSocket: DatagramSocket, tv: DiscoveredTv) {
        pingRxJob = scope.launch(Dispatchers.IO) {
            val rxBuf = ByteArray(64)
            val rxPacket = DatagramPacket(rxBuf, rxBuf.size)
            while (isActive && !udpSocket.isClosed) {
                try {
                    udpSocket.receive(rxPacket)
                    val sentNanos = GamepadPacketCodec.decodePingTimestamp(
                        rxPacket.data,
                        rxPacket.length,
                        GamepadPacketCodec.TYPE_PONG
                    )
                    if (sentNanos != null) {
                        pongsReceived++
                        val rttNs = (System.nanoTime() - sentNanos).coerceAtLeast(100_000L)
                        val rttMs = (rttNs / 1_000_000L).toInt().coerceAtLeast(1)
                        val prev = latestRttMs
                        latestJitterMs = ((latestJitterMs * 3) + abs(rttMs - prev)) / 4
                        latestRttMs = rttMs

                        val current = _connectionState.value
                        if (current is ConnectionState.Connected) {
                            _connectionState.value = current.copy(
                                target = tv,
                                rttMs = latestRttMs,
                                jitterMs = latestJitterMs
                            )
                        }
                    }
                } catch (_: SocketTimeoutException) {
                } catch (_: Exception) {
                    break
                }
            }
        }
    }

    fun disconnect() {
        heartbeatJob?.cancel()
        pingTxJob?.cancel()
        pingRxJob?.cancel()
        heartbeatJob = null
        pingTxJob = null
        pingRxJob = null
        try {
            socket?.close()
        } catch (_: Exception) {
        }
        socket = null
        targetAddress = null
        currentInput.set(GamepadInputState())
        lastSentInput = null
        _connectionState.value = ConnectionState.Disconnected
    }
}
