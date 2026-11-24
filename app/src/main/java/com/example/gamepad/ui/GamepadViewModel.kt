package com.example.gamepad.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.gamepad.data.PreferencesRepository
import com.example.gamepad.haptics.HapticController
import com.example.gamepad.model.AppLanguage
import com.example.gamepad.model.ButtonSymbolStyle
import com.example.gamepad.model.ConnectionState
import com.example.gamepad.model.ControllerLayout
import com.example.gamepad.model.DiscoveredTv
import com.example.gamepad.model.GamepadButton
import com.example.gamepad.model.GamepadInputState
import com.example.gamepad.model.NetworkStatus
import com.example.gamepad.model.PollingRate
import com.example.gamepad.model.ReceiverTelemetry
import com.example.gamepad.model.UserSettings
import com.example.gamepad.network.GamepadPacketCodec
import com.example.gamepad.network.NetworkTopologyHelper
import com.example.gamepad.network.TvDiscoveryManager
import com.example.gamepad.network.TvReceiverServer
import com.example.gamepad.network.UdpGamepadClient
import com.example.gamepad.sensors.TiltSteeringSensor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class MainUiState(
    val settings: UserSettings = UserSettings(),
    val networkStatus: NetworkStatus = NetworkStatus(),
    val discoveredTvs: List<DiscoveredTv> = emptyList(),
    val isScanning: Boolean = false,
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    val localPadState: GamepadInputState = GamepadInputState(),
    val receiverTelemetry: ReceiverTelemetry = ReceiverTelemetry(),
    val isGyroActive: Boolean = false,
    val isLoopbackSession: Boolean = false
)

class GamepadViewModel(application: Application) : AndroidViewModel(application) {

    private val prefsRepo = PreferencesRepository(application)
    private val haptics = HapticController(application)
    private val discoveryManager = TvDiscoveryManager(application, viewModelScope)
    private val udpClient = UdpGamepadClient(viewModelScope)
    private val receiverServer = TvReceiverServer(application, viewModelScope)
    private val tiltSensor = TiltSteeringSensor(application)

    private val _networkStatus = MutableStateFlow(NetworkTopologyHelper.detectNetworkStatus())
    private val _localPadState = MutableStateFlow(GamepadInputState())
    private val _isGyroActive = MutableStateFlow(false)
    private val _isLoopbackSession = MutableStateFlow(false)

    private var gyroJob: Job? = null

    val uiState: StateFlow<MainUiState> = combine(
        prefsRepo.settingsFlow,
        _networkStatus,
        discoveryManager.discoveredTvs,
        discoveryManager.isScanning,
        udpClient.connectionState
    ) { settings, net, tvs, scanning, conn ->
        MainUiState(
            settings = settings,
            networkStatus = net,
            discoveredTvs = tvs,
            isScanning = scanning,
            connectionState = conn
        )
    }.combine(_localPadState) { state, pad ->
        state.copy(localPadState = pad)
    }.combine(receiverServer.telemetry) { state, rx ->
        state.copy(receiverTelemetry = rx)
    }.combine(_isGyroActive) { state, gyro ->
        state.copy(isGyroActive = gyro)
    }.combine(_isLoopbackSession) { state, loopback ->
        state.copy(isLoopbackSession = loopback)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MainUiState()
    )

    init {
        viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                _networkStatus.value = NetworkTopologyHelper.detectNetworkStatus()
                delay(2000L)
            }
        }
    }

    fun refreshNetworkAndScan() {
        viewModelScope.launch(Dispatchers.IO) {
            val net = NetworkTopologyHelper.detectNetworkStatus()
            _networkStatus.value = net
            discoveryManager.startScan(net)
        }
    }

    fun stopScan() {
        discoveryManager.stopScan()
    }

    fun connectToTv(tv: DiscoveredTv, isLoopback: Boolean = false) {
        _isLoopbackSession.value = isLoopback
        discoveryManager.stopScan()
        val rate = uiState.value.settings.pollingRate
        udpClient.connect(tv, rate)
        if (!isLoopback) {
            viewModelScope.launch {
                prefsRepo.saveTv(tv)
            }
        }
    }

    fun connectManualIp(ip: String, port: Int, customName: String) {
        val cleanIp = ip.trim()
        if (!NetworkTopologyHelper.isValidIpv4(cleanIp)) return
        val tv = DiscoveredTv(
            id = "$cleanIp:$port",
            name = customName.trim().ifBlank { "TV ($cleanIp)" },
            host = cleanIp,
            port = port,
            transport = "Direct IP"
        )
        connectToTv(tv, isLoopback = cleanIp == "127.0.0.1")
    }

    fun startLoopbackTest() {
        receiverServer.start(GamepadPacketCodec.GAMEPAD_UDP_PORT)
        val loopbackTv = DiscoveredTv(
            id = "127.0.0.1:${GamepadPacketCodec.GAMEPAD_UDP_PORT}",
            name = "Loopback Benchmark",
            host = "127.0.0.1",
            port = GamepadPacketCodec.GAMEPAD_UDP_PORT,
            transport = "Local UDP"
        )
        connectToTv(loopbackTv, isLoopback = true)
    }

    fun disconnectGamepad() {
        setGyroEnabled(false)
        _localPadState.value = GamepadInputState()
        udpClient.disconnect()
        if (_isLoopbackSession.value) {
            receiverServer.stop()
            _isLoopbackSession.value = false
        }
    }

    fun startTvReceiver() {
        _networkStatus.value = NetworkTopologyHelper.detectNetworkStatus()
        receiverServer.start(GamepadPacketCodec.GAMEPAD_UDP_PORT)
    }

    fun stopTvReceiver() {
        receiverServer.stop()
    }

    fun removeSavedTv(tv: DiscoveredTv) {
        viewModelScope.launch {
            prefsRepo.removeSavedTv(tv)
        }
    }

    fun setLanguage(language: AppLanguage) {
        viewModelScope.launch { prefsRepo.setLanguage(language) }
    }

    fun setPollingRate(rate: PollingRate) {
        udpClient.setPollingRate(rate)
        viewModelScope.launch { prefsRepo.setPollingRate(rate) }
    }

    fun setHapticsEnabled(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.setHapticsEnabled(enabled) }
    }

    fun setDeadzone(deadzone: Float) {
        viewModelScope.launch { prefsRepo.setDeadzone(deadzone) }
    }

    fun setLayout(layout: ControllerLayout) {
        if (layout != ControllerLayout.RACING_GYRO) {
            setGyroEnabled(false)
        }
        viewModelScope.launch { prefsRepo.setLayout(layout) }
    }

    fun setSymbolStyle(style: ButtonSymbolStyle) {
        viewModelScope.launch { prefsRepo.setSymbolStyle(style) }
    }

    fun setAutoLandscape(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.setAutoLandscape(enabled) }
    }

    fun setGyroEnabled(enabled: Boolean) {
        if (_isGyroActive.value == enabled) return
        _isGyroActive.value = enabled
        gyroJob?.cancel()
        gyroJob = null

        if (enabled) {
            gyroJob = viewModelScope.launch {
                tiltSensor.steeringFlow().collect { steer ->
                    updateLeftStick(steer, _localPadState.value.leftY)
                }
            }
        } else {
            updateLeftStick(0f, _localPadState.value.leftY)
        }
    }

    fun updateButtonsMask(newMask: Int, triggerHapticForPress: Boolean = false) {
        val current = _localPadState.value
        if (current.buttons == newMask) return
        val newlyPressed = newMask and current.buttons.inv()
        if (triggerHapticForPress && newlyPressed != 0 && uiState.value.settings.hapticsEnabled) {
            val isHeavy = (newlyPressed and (GamepadButton.L2_CLICK.mask or GamepadButton.R2_CLICK.mask)) != 0
            haptics.tick(heavy = isHeavy)
        }
        val updated = current.copy(buttons = newMask)
        _localPadState.value = updated
        udpClient.updateInput(updated)
    }

    fun setButtonPressed(button: GamepadButton, pressed: Boolean) {
        val current = _localPadState.value
        val newMask = if (pressed) {
            current.buttons or button.mask
        } else {
            current.buttons and button.mask.inv()
        }
        updateButtonsMask(newMask, triggerHapticForPress = pressed)
    }

    fun updateLeftStick(x: Float, y: Float) {
        val current = _localPadState.value
        if (current.leftX == x && current.leftY == y) return
        val updated = current.copy(leftX = x.coerceIn(-1f, 1f), leftY = y.coerceIn(-1f, 1f))
        _localPadState.value = updated
        udpClient.updateInput(updated)
    }

    fun updateRightStick(x: Float, y: Float) {
        val current = _localPadState.value
        if (current.rightX == x && current.rightY == y) return
        val updated = current.copy(rightX = x.coerceIn(-1f, 1f), rightY = y.coerceIn(-1f, 1f))
        _localPadState.value = updated
        udpClient.updateInput(updated)
    }

    fun updateTriggers(left: Float? = null, right: Float? = null) {
        val current = _localPadState.value
        val lt = (left ?: current.leftTrigger).coerceIn(0f, 1f)
        val rt = (right ?: current.rightTrigger).coerceIn(0f, 1f)
        var mask = current.buttons
        if (left != null) {
            mask = if (lt > 0.5f) mask or GamepadButton.L2_CLICK.mask else mask and GamepadButton.L2_CLICK.mask.inv()
        }
        if (right != null) {
            mask = if (rt > 0.5f) mask or GamepadButton.R2_CLICK.mask else mask and GamepadButton.R2_CLICK.mask.inv()
        }
        val newlyPressed = mask and current.buttons.inv()
        if (newlyPressed != 0 && uiState.value.settings.hapticsEnabled) {
            haptics.tick(heavy = true)
        }
        val updated = current.copy(buttons = mask, leftTrigger = lt, rightTrigger = rt)
        if (updated == current) return
        _localPadState.value = updated
        udpClient.updateInput(updated)
    }

    override fun onCleared() {
        super.onCleared()
        discoveryManager.stopScan()
        udpClient.disconnect()
        receiverServer.stop()
    }
}
