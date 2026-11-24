package com.example.gamepad.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gamepad.i18n.StringsProvider
import com.example.gamepad.model.ConnectionState
import com.example.gamepad.model.ControllerLayout
import com.example.gamepad.model.GamepadButton
import com.example.gamepad.ui.MainUiState
import com.example.gamepad.ui.components.ActionDiamondPad
import com.example.gamepad.ui.components.AnalogStick
import com.example.gamepad.ui.components.MicroSystemButton
import com.example.gamepad.ui.components.MultiTouchDPad
import com.example.gamepad.ui.components.ShoulderTriggerButton
import com.example.ui.theme.ButtonAEmerald
import com.example.ui.theme.ButtonBCrimson
import com.example.ui.theme.CarbonBorder
import com.example.ui.theme.CarbonElevated
import com.example.ui.theme.CarbonSurface
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.NeonIndigo
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.StatusHotspotAmber
import com.example.ui.theme.StatusLiveGreen
import com.example.ui.theme.StatusOfflineRed
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun GamepadScreen(
    state: MainUiState,
    onBack: () -> Unit,
    onButtonsMaskChange: (Int, Boolean) -> Unit,
    onButtonPress: (GamepadButton, Boolean) -> Unit,
    onLeftStickChange: (Float, Float) -> Unit,
    onRightStickChange: (Float, Float) -> Unit,
    onTriggersChange: (Float?, Float?) -> Unit,
    onLayoutCycle: () -> Unit,
    onToggleGyro: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val strings = StringsProvider.forLanguage(state.settings.language)
    var forceLandscape by remember(state.settings.autoLandscape) {
        mutableStateOf(state.settings.autoLandscape)
    }

    DisposableEffect(forceLandscape) {
        val activity = context.findActivity()
        if (activity != null) {
            activity.requestedOrientation = if (forceLandscape) {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            } else {
                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    BackHandler { onBack() }

    val pad = state.localPadState
    val actionMaskBits = GamepadButton.A.mask or GamepadButton.B.mask or
        GamepadButton.X.mask or GamepadButton.Y.mask
    val dpadMaskBits = GamepadButton.DPAD_UP.mask or GamepadButton.DPAD_DOWN.mask or
        GamepadButton.DPAD_LEFT.mask or GamepadButton.DPAD_RIGHT.mask

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF121B2E), ObsidianBg, Color(0xFF06080D)),
                    radius = 1200f
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Telemetry & Shoulder Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left Shoulders
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ShoulderTriggerButton(
                        label = "L2",
                        isHeavyTrigger = true,
                        pressed = pad.leftTrigger > 0.5f || pad.isPressed(GamepadButton.L2_CLICK),
                        testTag = "btn_l2",
                        onPressedChange = { down ->
                            onTriggersChange(if (down) 1f else 0f, null)
                        }
                    )
                    ShoulderTriggerButton(
                        label = "L1",
                        isHeavyTrigger = false,
                        pressed = pad.isPressed(GamepadButton.L1),
                        testTag = "btn_l1",
                        onPressedChange = { down -> onButtonPress(GamepadButton.L1, down) }
                    )
                }

                // Center Telemetry HUD
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("gamepad_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Exit Gamepad",
                            tint = TextPrimary
                        )
                    }

                    TelemetryPill(state.connectionState, state.settings.pollingRate.label)

                    Surface(
                        shape = RoundedCornerShape(50),
                        color = CarbonElevated,
                        border = BorderStroke(1.dp, CarbonBorder),
                        modifier = Modifier
                            .testTag("cycle_layout_button")
                            .clip(RoundedCornerShape(50))
                            .clickable(onClick = onLayoutCycle)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Tune,
                                contentDescription = "Switch pad layout",
                                tint = ElectricCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = when (state.settings.layout) {
                                    ControllerLayout.STANDARD -> strings.layoutStandard
                                    ControllerLayout.ARCADE -> strings.layoutArcade
                                    ControllerLayout.RACING_GYRO -> strings.layoutRacing
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = TextPrimary
                            )
                        }
                    }

                    if (state.settings.layout == ControllerLayout.RACING_GYRO) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (state.isGyroActive) ElectricCyan.copy(alpha = 0.22f) else CarbonElevated,
                            border = BorderStroke(
                                1.dp,
                                if (state.isGyroActive) ElectricCyan else CarbonBorder
                            ),
                            modifier = Modifier
                                .testTag("gyro_toggle_button")
                                .clip(RoundedCornerShape(50))
                                .clickable { onToggleGyro(!state.isGyroActive) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Sensors,
                                    contentDescription = "Toggle gyro steering",
                                    tint = if (state.isGyroActive) ElectricCyan else TextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (state.isGyroActive) strings.gyroActive else strings.gyroOff,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (state.isGyroActive) ElectricCyan else TextSecondary
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = { forceLandscape = !forceLandscape },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("toggle_orientation_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ScreenRotation,
                            contentDescription = "Toggle landscape lock",
                            tint = if (forceLandscape) ElectricCyan else TextSecondary
                        )
                    }
                }

                // Right Shoulders
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ShoulderTriggerButton(
                        label = "R1",
                        isHeavyTrigger = false,
                        pressed = pad.isPressed(GamepadButton.R1),
                        testTag = "btn_r1",
                        onPressedChange = { down -> onButtonPress(GamepadButton.R1, down) }
                    )
                    ShoulderTriggerButton(
                        label = "R2",
                        isHeavyTrigger = true,
                        pressed = pad.rightTrigger > 0.5f || pad.isPressed(GamepadButton.R2_CLICK),
                        testTag = "btn_r2",
                        onPressedChange = { down ->
                            onTriggersChange(null, if (down) 1f else 0f)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Main Control Deck
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                val boxWidth = maxWidth
                val boxHeight = maxHeight
                val isWide = boxWidth >= 560.dp

                when (state.settings.layout) {
                    ControllerLayout.STANDARD -> {
                        if (isWide) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                // Left Thumb Zone: Analog Stick + D-Pad
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.padding(start = 6.dp)
                                ) {
                                    AnalogStick(
                                        label = "Left Analog Stick",
                                        deadzone = state.settings.deadzone,
                                        accentColor = ElectricCyan,
                                        size = (boxHeight * 0.62f).coerceIn(118.dp, 162.dp),
                                        testTag = "left_analog_stick",
                                        onStickChanged = onLeftStickChange
                                    )
                                    MultiTouchDPad(
                                        pressedMask = pad.buttons,
                                        size = (boxHeight * 0.50f).coerceIn(104.dp, 138.dp),
                                        onDirectionsChanged = { dirs ->
                                            val cleared = pad.buttons and dpadMaskBits.inv()
                                            val added = dirs.fold(0) { acc, b -> acc or b.mask }
                                            onButtonsMaskChange(cleared or added, added != 0)
                                        },
                                        modifier = Modifier.padding(top = 34.dp)
                                    )
                                }

                                // Center Menu Strip
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.fillMaxHeight(),
                                ) {
                                    Spacer(modifier = Modifier.weight(1f))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        MicroSystemButton(
                                            label = "SELECT",
                                            pressed = pad.isPressed(GamepadButton.SELECT),
                                            testTag = "btn_select",
                                            onPressedChange = { onButtonPress(GamepadButton.SELECT, it) }
                                        )
                                        MicroSystemButton(
                                            label = "HOME",
                                            pressed = pad.isPressed(GamepadButton.HOME),
                                            testTag = "btn_home",
                                            onPressedChange = { onButtonPress(GamepadButton.HOME, it) }
                                        )
                                        MicroSystemButton(
                                            label = "START",
                                            pressed = pad.isPressed(GamepadButton.START),
                                            testTag = "btn_start",
                                            onPressedChange = { onButtonPress(GamepadButton.START, it) }
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                }

                                // Right Thumb Zone: Right Analog Stick + ABXY Diamond
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.padding(end = 6.dp)
                                ) {
                                    AnalogStick(
                                        label = "Right Analog Stick",
                                        deadzone = state.settings.deadzone,
                                        accentColor = NeonIndigo,
                                        size = (boxHeight * 0.48f).coerceIn(102.dp, 134.dp),
                                        testTag = "right_analog_stick",
                                        onStickChanged = onRightStickChange,
                                        modifier = Modifier.padding(top = 38.dp)
                                    )
                                    ActionDiamondPad(
                                        pressedMask = pad.buttons,
                                        symbolStyle = state.settings.symbolStyle,
                                        padSize = (boxHeight * 0.68f).coerceIn(136.dp, 178.dp),
                                        onActionButtonsChanged = { pressedSet ->
                                            val cleared = pad.buttons and actionMaskBits.inv()
                                            val added = pressedSet.fold(0) { acc, b -> acc or b.mask }
                                            onButtonsMaskChange(cleared or added, added != 0)
                                        }
                                    )
                                }
                            }
                        } else {
                            // Portrait fallback layout
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.SpaceEvenly,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AnalogStick(
                                        label = "Left Analog Stick",
                                        deadzone = state.settings.deadzone,
                                        size = 148.dp,
                                        testTag = "left_analog_stick",
                                        onStickChanged = onLeftStickChange
                                    )
                                    ActionDiamondPad(
                                        pressedMask = pad.buttons,
                                        symbolStyle = state.settings.symbolStyle,
                                        padSize = 158.dp,
                                        onActionButtonsChanged = { pressedSet ->
                                            val cleared = pad.buttons and actionMaskBits.inv()
                                            val added = pressedSet.fold(0) { acc, b -> acc or b.mask }
                                            onButtonsMaskChange(cleared or added, added != 0)
                                        }
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    MicroSystemButton(
                                        label = "SELECT",
                                        pressed = pad.isPressed(GamepadButton.SELECT),
                                        testTag = "btn_select",
                                        onPressedChange = { onButtonPress(GamepadButton.SELECT, it) }
                                    )
                                    MicroSystemButton(
                                        label = "HOME",
                                        pressed = pad.isPressed(GamepadButton.HOME),
                                        testTag = "btn_home",
                                        onPressedChange = { onButtonPress(GamepadButton.HOME, it) }
                                    )
                                    MicroSystemButton(
                                        label = "START",
                                        pressed = pad.isPressed(GamepadButton.START),
                                        testTag = "btn_start",
                                        onPressedChange = { onButtonPress(GamepadButton.START, it) }
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    MultiTouchDPad(
                                        pressedMask = pad.buttons,
                                        size = 132.dp,
                                        onDirectionsChanged = { dirs ->
                                            val cleared = pad.buttons and dpadMaskBits.inv()
                                            val added = dirs.fold(0) { acc, b -> acc or b.mask }
                                            onButtonsMaskChange(cleared or added, added != 0)
                                        }
                                    )
                                    AnalogStick(
                                        label = "Right Analog Stick",
                                        deadzone = state.settings.deadzone,
                                        accentColor = NeonIndigo,
                                        size = 128.dp,
                                        testTag = "right_analog_stick",
                                        onStickChanged = onRightStickChange
                                    )
                                }
                            }
                        }
                    }

                    ControllerLayout.ARCADE -> {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            MultiTouchDPad(
                                pressedMask = pad.buttons,
                                size = (minOf(boxHeight, boxWidth * 0.38f) * 0.78f).coerceIn(136.dp, 194.dp),
                                onDirectionsChanged = { dirs ->
                                    val cleared = pad.buttons and dpadMaskBits.inv()
                                    val added = dirs.fold(0) { acc, b -> acc or b.mask }
                                    onButtonsMaskChange(cleared or added, added != 0)
                                },
                                modifier = Modifier.padding(start = 16.dp)
                            )

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                MicroSystemButton(
                                    label = "SELECT",
                                    pressed = pad.isPressed(GamepadButton.SELECT),
                                    testTag = "btn_select",
                                    onPressedChange = { onButtonPress(GamepadButton.SELECT, it) }
                                )
                                MicroSystemButton(
                                    label = "HOME",
                                    pressed = pad.isPressed(GamepadButton.HOME),
                                    testTag = "btn_home",
                                    onPressedChange = { onButtonPress(GamepadButton.HOME, it) }
                                )
                                MicroSystemButton(
                                    label = "START",
                                    pressed = pad.isPressed(GamepadButton.START),
                                    testTag = "btn_start",
                                    onPressedChange = { onButtonPress(GamepadButton.START, it) }
                                )
                            }

                            ActionDiamondPad(
                                pressedMask = pad.buttons,
                                symbolStyle = state.settings.symbolStyle,
                                padSize = (minOf(boxHeight, boxWidth * 0.40f) * 0.84f).coerceIn(152.dp, 204.dp),
                                onActionButtonsChanged = { pressedSet ->
                                    val cleared = pad.buttons and actionMaskBits.inv()
                                    val added = pressedSet.fold(0) { acc, b -> acc or b.mask }
                                    onButtonsMaskChange(cleared or added, added != 0)
                                },
                                modifier = Modifier.padding(end = 16.dp)
                            )
                        }
                    }

                    ControllerLayout.RACING_GYRO -> {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Left: Steering Wheel Dial / Touch Stick
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(start = 16.dp)
                            ) {
                                if (state.isGyroActive) {
                                    SteeringDialVisualizer(steer = pad.leftX)
                                } else {
                                    AnalogStick(
                                        label = "Steering Stick",
                                        deadzone = state.settings.deadzone,
                                        accentColor = ElectricCyan,
                                        size = (boxHeight * 0.68f).coerceIn(130.dp, 180.dp),
                                        testTag = "left_analog_stick",
                                        onStickChanged = onLeftStickChange
                                    )
                                }
                            }

                            // Center Menu
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                MicroSystemButton(
                                    label = "SELECT",
                                    pressed = pad.isPressed(GamepadButton.SELECT),
                                    testTag = "btn_select",
                                    onPressedChange = { onButtonPress(GamepadButton.SELECT, it) }
                                )
                                MicroSystemButton(
                                    label = "START",
                                    pressed = pad.isPressed(GamepadButton.START),
                                    testTag = "btn_start",
                                    onPressedChange = { onButtonPress(GamepadButton.START, it) }
                                )
                            }

                            // Right: Brake / Boost / Gas Pedals
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(end = 16.dp)
                            ) {
                                PedalButton(
                                    label = "BRAKE\nB / L2",
                                    accent = ButtonBCrimson,
                                    pressed = pad.isPressed(GamepadButton.B) || pad.leftTrigger > 0.5f,
                                    testTag = "racing_brake_button",
                                    onPressedChange = { down ->
                                        onButtonPress(GamepadButton.B, down)
                                        onTriggersChange(if (down) 1f else 0f, null)
                                    }
                                )
                                PedalButton(
                                    label = "GAS\nA / R2",
                                    accent = ButtonAEmerald,
                                    pressed = pad.isPressed(GamepadButton.A) || pad.rightTrigger > 0.5f,
                                    testTag = "racing_gas_button",
                                    onPressedChange = { down ->
                                        onButtonPress(GamepadButton.A, down)
                                        onTriggersChange(null, if (down) 1f else 0f)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TelemetryPill(connectionState: ConnectionState, fallbackHz: String) {
    val (dotColor, label) = when (connectionState) {
        is ConnectionState.Connected -> {
            val c = when {
                connectionState.rttMs <= 12 -> StatusLiveGreen
                connectionState.rttMs <= 35 -> StatusHotspotAmber
                else -> StatusOfflineRed
            }
            c to "${connectionState.rttMs}ms • ${connectionState.packetsPerSec}Hz"
        }
        is ConnectionState.Connecting -> StatusHotspotAmber to "CONNECTING"
        ConnectionState.Disconnected -> TextSecondary to "OFFLINE • $fallbackHz"
    }

    Surface(
        shape = RoundedCornerShape(50),
        color = CarbonSurface,
        border = BorderStroke(1.dp, dotColor.copy(alpha = 0.6f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = TextPrimary
            )
        }
    }
}

@Composable
private fun SteeringDialVisualizer(steer: Float) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(164.dp)
            .testTag("gyro_steering_dial")
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val r = minOf(size.width, size.height) / 2f - 6.dp.toPx()

            drawCircle(
                color = CarbonSurface,
                radius = r,
                center = Offset(cx, cy)
            )
            drawCircle(
                color = ElectricCyan.copy(alpha = 0.7f),
                radius = r,
                center = Offset(cx, cy),
                style = Stroke(width = 3.dp.toPx())
            )
            val rad = Math.toRadians((steer * 90f).toDouble())
            val p1 = Offset(cx - (cos(rad) * r * 0.75).toFloat(), cy - (sin(rad) * r * 0.75).toFloat())
            val p2 = Offset(cx + (cos(rad) * r * 0.75).toFloat(), cy + (sin(rad) * r * 0.75).toFloat())
            drawLine(
                color = ElectricCyan,
                start = p1,
                end = p2,
                strokeWidth = 6.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawCircle(
                color = NeonIndigo,
                radius = 12.dp.toPx(),
                center = Offset(cx, cy)
            )
        }
    }
}

@Composable
private fun PedalButton(
    label: String,
    accent: Color,
    pressed: Boolean,
    testTag: String,
    onPressedChange: (Boolean) -> Unit
) {
    ShoulderTriggerButton(
        label = label,
        isHeavyTrigger = false,
        pressed = pressed,
        testTag = testTag,
        onPressedChange = onPressedChange,
        modifier = Modifier
            .width(108.dp)
            .height(148.dp)
            .border(
                width = 1.5.dp,
                color = if (pressed) Color.White else accent.copy(alpha = 0.7f),
                shape = RoundedCornerShape(14.dp)
            )
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
