package com.example.gamepad.model

enum class GamepadButton(val bit: Int) {
    A(0),
    B(1),
    X(2),
    Y(3),
    L1(4),
    R1(5),
    L2_CLICK(6),
    R2_CLICK(7),
    DPAD_UP(8),
    DPAD_DOWN(9),
    DPAD_LEFT(10),
    DPAD_RIGHT(11),
    START(12),
    SELECT(13),
    HOME(14),
    L3(15),
    R3(16);

    val mask: Int get() = 1 shl bit
}

data class GamepadInputState(
    val buttons: Int = 0,
    val leftX: Float = 0f,      // -1f .. 1f
    val leftY: Float = 0f,      // -1f .. 1f
    val rightX: Float = 0f,     // -1f .. 1f
    val rightY: Float = 0f,     // -1f .. 1f
    val leftTrigger: Float = 0f, // 0f .. 1f
    val rightTrigger: Float = 0f // 0f .. 1f
) {
    fun isPressed(button: GamepadButton): Boolean = (buttons and button.mask) != 0
}

enum class NetworkMode {
    HOTSPOT_AP,
    LOCAL_WIFI,
    OFFLINE
}

data class NetworkStatus(
    val mode: NetworkMode = NetworkMode.OFFLINE,
    val localIp: String? = null,
    val interfaceName: String? = null,
    val broadcastAddresses: List<String> = emptyList(),
    val gatewayCandidates: List<String> = emptyList()
)

data class DiscoveredTv(
    val id: String,
    val name: String,
    val host: String,
    val port: Int,
    val transport: String,
    val rttMs: Int? = null,
    val lastSeenMs: Long = System.currentTimeMillis(),
    val isSaved: Boolean = false
)

sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data class Connecting(val target: DiscoveredTv) : ConnectionState
    data class Connected(
        val target: DiscoveredTv,
        val rttMs: Int,
        val jitterMs: Int,
        val packetsPerSec: Int,
        val packetLossPercent: Int
    ) : ConnectionState
}

enum class PollingRate(val hz: Int, val intervalMs: Long, val label: String) {
    HZ_60(60, 16L, "60 Hz"),
    HZ_120(120, 8L, "120 Hz"),
    HZ_240(240, 4L, "240 Hz")
}

enum class ControllerLayout {
    STANDARD,
    ARCADE,
    RACING_GYRO
}

enum class ButtonSymbolStyle {
    XBOX_ABXY,
    PLAYSTATION_SHAPES
}

enum class AppLanguage {
    EN,
    BN
}

data class UserSettings(
    val language: AppLanguage = AppLanguage.BN,
    val pollingRate: PollingRate = PollingRate.HZ_120,
    val hapticsEnabled: Boolean = true,
    val deadzone: Float = 0.08f,
    val layout: ControllerLayout = ControllerLayout.STANDARD,
    val symbolStyle: ButtonSymbolStyle = ButtonSymbolStyle.XBOX_ABXY,
    val autoLandscape: Boolean = true,
    val savedTvs: List<DiscoveredTv> = emptyList()
)

data class ReceiverTelemetry(
    val isRunning: Boolean = false,
    val listenPort: Int = 55888,
    val lastSenderIp: String? = null,
    val packetsPerSec: Int = 0,
    val totalPackets: Long = 0L,
    val inputState: GamepadInputState = GamepadInputState()
)
