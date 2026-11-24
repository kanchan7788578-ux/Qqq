package com.example.gamepad.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.gamepad.i18n.StringsProvider
import com.example.gamepad.model.GamepadButton
import com.example.gamepad.ui.MainUiState
import com.example.ui.theme.ButtonAEmerald
import com.example.ui.theme.ButtonBCrimson
import com.example.ui.theme.ButtonXCyan
import com.example.ui.theme.ButtonYAmber
import com.example.ui.theme.CarbonBorder
import com.example.ui.theme.CarbonElevated
import com.example.ui.theme.CarbonSurface
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.NeonIndigo
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.StatusLiveGreen
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlin.math.hypot
import kotlin.random.Random

@Composable
fun TvReceiverScreen(
    state: MainUiState,
    onStopAndBack: () -> Unit
) {
    val strings = StringsProvider.forLanguage(state.settings.language)
    val rx = state.receiverTelemetry
    val input = rx.inputState

    BackHandler { onStopAndBack() }

    // Interactive Target Arena Mini-Game driven directly by incoming UDP Gamepad packets!
    var aimX by remember { mutableFloatStateOf(0.5f) }
    var aimY by remember { mutableFloatStateOf(0.5f) }
    var targetX by remember { mutableFloatStateOf(0.72f) }
    var targetY by remember { mutableFloatStateOf(0.40f) }
    var score by remember { mutableIntStateOf(0) }
    var hitFlash by remember { mutableStateOf(false) }
    var prevFirePressed by remember { mutableStateOf(false) }

    LaunchedEffect(rx.isRunning) {
        while (rx.isRunning) {
            val cur = state.receiverTelemetry.inputState
            val dx = cur.leftX +
                (if (cur.isPressed(GamepadButton.DPAD_RIGHT)) 0.8f else 0f) -
                (if (cur.isPressed(GamepadButton.DPAD_LEFT)) 0.8f else 0f)
            val dy = cur.leftY +
                (if (cur.isPressed(GamepadButton.DPAD_DOWN)) 0.8f else 0f) -
                (if (cur.isPressed(GamepadButton.DPAD_UP)) 0.8f else 0f)

            aimX = (aimX + dx * 0.022f).coerceIn(0.06f, 0.94f)
            aimY = (aimY + dy * 0.022f).coerceIn(0.08f, 0.92f)

            val fireNow = cur.isPressed(GamepadButton.A) || cur.rightTrigger > 0.5f
            if (fireNow && !prevFirePressed) {
                val dist = hypot(aimX - targetX, aimY - targetY)
                if (dist < 0.13f) {
                    score += 100
                    hitFlash = true
                    targetX = Random.nextFloat() * 0.76f + 0.12f
                    targetY = Random.nextFloat() * 0.72f + 0.14f
                }
            }
            prevFirePressed = fireNow
            delay(16L)
        }
    }

    LaunchedEffect(hitFlash) {
        if (hitFlash) {
            delay(160L)
            hitFlash = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(Color(0xFF0E1525), ObsidianBg))
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onStopAndBack,
                    modifier = Modifier.testTag("receiver_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = strings.stopReceiver,
                        tint = TextPrimary
                    )
                }
                Icon(
                    imageVector = Icons.Rounded.Tv,
                    contentDescription = null,
                    tint = NeonIndigo
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = strings.tvHostListening,
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary
                )
            }

            OutlinedButton(
                onClick = onStopAndBack,
                border = BorderStroke(1.dp, CarbonBorder),
                modifier = Modifier.testTag("stop_tv_receiver_button")
            ) {
                Text(strings.stopReceiver, color = TextPrimary)
            }
        }

        // Network Host Info Banner
        Card(
            colors = CardDefaults.cardColors(containerColor = CarbonSurface),
            border = BorderStroke(1.dp, NeonIndigo.copy(alpha = 0.6f)),
            shape = MaterialTheme.shapes.medium
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(strings.tvHostIpHint, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                Text(
                    text = "${state.networkStatus.localIp ?: "127.0.0.1"} : ${rx.listenPort}",
                    style = MaterialTheme.typography.headlineMedium,
                    fontFamily = JetBrainsMonoFontFamily,
                    color = ElectricCyan
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(if (rx.lastSenderIp != null) StatusLiveGreen else NeonIndigo)
                    )
                    Text(
                        text = if (rx.lastSenderIp != null) {
                            "${strings.gamepadConnectedFrom} ${rx.lastSenderIp} • ${rx.packetsPerSec} pkt/s"
                        } else {
                            strings.waitingForGamepad
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (rx.lastSenderIp != null) StatusLiveGreen else TextSecondary
                    )
                }
            }
        }

        // Responsive Split: Input Visualizer + Target Arena Mini-Game
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val wide = maxWidth >= 640.dp
            if (wide) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    ReceiverVisualizerCard(
                        input = input,
                        modifier = Modifier.weight(1f)
                    )
                    TargetArenaCard(
                        title = strings.miniGameTitle,
                        hint = strings.miniGameHint,
                        scoreLabel = strings.scoreLabel,
                        resetLabel = strings.resetScore,
                        score = score,
                        aimX = aimX,
                        aimY = aimY,
                        targetX = targetX,
                        targetY = targetY,
                        hitFlash = hitFlash,
                        onReset = { score = 0 },
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    ReceiverVisualizerCard(input = input, modifier = Modifier.fillMaxWidth())
                    TargetArenaCard(
                        title = strings.miniGameTitle,
                        hint = strings.miniGameHint,
                        scoreLabel = strings.scoreLabel,
                        resetLabel = strings.resetScore,
                        score = score,
                        aimX = aimX,
                        aimY = aimY,
                        targetX = targetX,
                        targetY = targetY,
                        hitFlash = hitFlash,
                        onReset = { score = 0 },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun ReceiverVisualizerCard(
    input: com.example.gamepad.model.GamepadInputState,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CarbonSurface),
        border = BorderStroke(1.dp, CarbonBorder),
        shape = MaterialTheme.shapes.medium,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StickMirror(label = "L", x = input.leftX, y = input.leftY, accent = ElectricCyan)
                StickMirror(label = "R", x = input.rightX, y = input.rightY, accent = NeonIndigo)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ButtonBadge("L2", input.leftTrigger > 0.5f || input.isPressed(GamepadButton.L2_CLICK), NeonIndigo)
                ButtonBadge("L1", input.isPressed(GamepadButton.L1), ElectricCyan)
                ButtonBadge("R1", input.isPressed(GamepadButton.R1), ElectricCyan)
                ButtonBadge("R2", input.rightTrigger > 0.5f || input.isPressed(GamepadButton.R2_CLICK), NeonIndigo)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ButtonBadge("A", input.isPressed(GamepadButton.A), ButtonAEmerald)
                ButtonBadge("B", input.isPressed(GamepadButton.B), ButtonBCrimson)
                ButtonBadge("X", input.isPressed(GamepadButton.X), ButtonXCyan)
                ButtonBadge("Y", input.isPressed(GamepadButton.Y), ButtonYAmber)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ButtonBadge("▲", input.isPressed(GamepadButton.DPAD_UP), ElectricCyan)
                ButtonBadge("▼", input.isPressed(GamepadButton.DPAD_DOWN), ElectricCyan)
                ButtonBadge("◀", input.isPressed(GamepadButton.DPAD_LEFT), ElectricCyan)
                ButtonBadge("▶", input.isPressed(GamepadButton.DPAD_RIGHT), ElectricCyan)
                ButtonBadge("START", input.isPressed(GamepadButton.START), Color.White)
            }
        }
    }
}

@Composable
private fun StickMirror(label: String, x: Float, y: Float, accent: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(modifier = Modifier.size(88.dp)) {
            val c = Offset(size.width / 2f, size.height / 2f)
            val r = size.minDimension / 2f - 4.dp.toPx()
            drawCircle(color = CarbonElevated, radius = r, center = c)
            drawCircle(color = CarbonBorder, radius = r, center = c, style = Stroke(1.5.dp.toPx()))
            val dot = Offset(c.x + x * (r * 0.65f), c.y + y * (r * 0.65f))
            drawLine(accent.copy(alpha = 0.6f), c, dot, strokeWidth = 2.dp.toPx())
            drawCircle(color = accent, radius = 12.dp.toPx(), center = dot)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "$label (${"%.2f".format(x)}, ${"%.2f".format(y)})",
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary
        )
    }
}

@Composable
private fun ButtonBadge(label: String, active: Boolean, color: Color) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (active) color else CarbonElevated,
        border = BorderStroke(1.dp, if (active) Color.White else CarbonBorder)
    ) {
        Text(
            text = label,
            fontFamily = JetBrainsMonoFontFamily,
            fontWeight = FontWeight.Bold,
            color = if (active) Color(0xFF07090D) else TextSecondary,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun TargetArenaCard(
    title: String,
    hint: String,
    scoreLabel: String,
    resetLabel: String,
    score: Int,
    aimX: Float,
    aimY: Float,
    targetX: Float,
    targetY: Float,
    hitFlash: Boolean,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CarbonSurface),
        border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.45f)),
        shape = MaterialTheme.shapes.medium,
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Text(hint, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "$scoreLabel: $score",
                        style = MaterialTheme.typography.labelMedium,
                        color = ButtonAEmerald
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onReset,
                        colors = ButtonDefaults.buttonColors(containerColor = CarbonElevated),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Text(resetLabel, color = TextPrimary)
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (hitFlash) Color(0xFF123626) else ObsidianBg)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val tx = targetX * size.width
                    val ty = targetY * size.height
                    drawCircle(color = ButtonBCrimson.copy(alpha = 0.25f), radius = 26.dp.toPx(), center = Offset(tx, ty))
                    drawCircle(color = ButtonBCrimson, radius = 16.dp.toPx(), center = Offset(tx, ty), style = Stroke(3.dp.toPx()))
                    drawCircle(color = Color.White, radius = 5.dp.toPx(), center = Offset(tx, ty))

                    val ax = aimX * size.width
                    val ay = aimY * size.height
                    drawCircle(color = ElectricCyan, radius = 14.dp.toPx(), center = Offset(ax, ay), style = Stroke(2.dp.toPx()))
                    drawLine(ElectricCyan, Offset(ax - 20.dp.toPx(), ay), Offset(ax + 20.dp.toPx(), ay), strokeWidth = 2.dp.toPx())
                    drawLine(ElectricCyan, Offset(ax, ay - 20.dp.toPx()), Offset(ax, ay + 20.dp.toPx()), strokeWidth = 2.dp.toPx())
                }
            }
        }
    }
}
