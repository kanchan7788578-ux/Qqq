package com.example.gamepad.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gamepad.model.ButtonSymbolStyle
import com.example.gamepad.model.GamepadButton
import com.example.ui.theme.ButtonAEmerald
import com.example.ui.theme.ButtonBCrimson
import com.example.ui.theme.ButtonXCyan
import com.example.ui.theme.ButtonYAmber
import com.example.ui.theme.CarbonBorder
import com.example.ui.theme.CarbonElevated
import com.example.ui.theme.CarbonSurface
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.SpaceGroteskFontFamily
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Multi-touch ABXY / PlayStation Diamond Pad.
 * Supports pressing multiple buttons simultaneously or sliding across buttons.
 */
@Composable
fun ActionDiamondPad(
    pressedMask: Int,
    symbolStyle: ButtonSymbolStyle,
    padSize: Dp = 166.dp,
    onActionButtonsChanged: (pressedSet: Set<GamepadButton>) -> Unit,
    modifier: Modifier = Modifier
) {
    val activePointers = remember { mutableMapOf<PointerId, Offset>() }

    Box(
        modifier = modifier
            .size(padSize)
            .testTag("action_diamond_pad")
            .semantics { contentDescription = "Action buttons A B X Y" }
            .pointerInput(Unit) {
                val w = this.size.width.toFloat()
                val h = this.size.height.toFloat()
                val centers = mapOf(
                    GamepadButton.Y to Offset(w * 0.50f, h * 0.21f),
                    GamepadButton.A to Offset(w * 0.50f, h * 0.79f),
                    GamepadButton.X to Offset(w * 0.21f, h * 0.50f),
                    GamepadButton.B to Offset(w * 0.79f, h * 0.50f)
                )
                val hitRadius = minOf(w, h) * 0.25f

                fun recomputeMask() {
                    val pressed = mutableSetOf<GamepadButton>()
                    for (pos in activePointers.values) {
                        for ((btn, c) in centers) {
                            if (hypot(pos.x - c.x, pos.y - c.y) <= hitRadius) {
                                pressed.add(btn)
                            }
                        }
                    }
                    onActionButtonsChanged(pressed)
                }

                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        var changed = false
                        for (change in event.changes) {
                            if (change.pressed) {
                                activePointers[change.id] = change.position
                                change.consume()
                                changed = true
                            } else {
                                if (activePointers.remove(change.id) != null) {
                                    change.consume()
                                    changed = true
                                }
                            }
                        }
                        if (changed) {
                            recomputeMask()
                        }
                    }
                }
            }
    ) {
        val labelY = if (symbolStyle == ButtonSymbolStyle.XBOX_ABXY) "Y" else "△"
        val labelB = if (symbolStyle == ButtonSymbolStyle.XBOX_ABXY) "B" else "○"
        val labelA = if (symbolStyle == ButtonSymbolStyle.XBOX_ABXY) "A" else "✕"
        val labelX = if (symbolStyle == ButtonSymbolStyle.XBOX_ABXY) "X" else "□"

        val btnDiameter = padSize * 0.34f

        DiamondCap(
            label = labelY,
            accent = ButtonYAmber,
            pressed = (pressedMask and GamepadButton.Y.mask) != 0,
            diameter = btnDiameter,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 4.dp)
        )
        DiamondCap(
            label = labelA,
            accent = ButtonAEmerald,
            pressed = (pressedMask and GamepadButton.A.mask) != 0,
            diameter = btnDiameter,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp)
        )
        DiamondCap(
            label = labelX,
            accent = ButtonXCyan,
            pressed = (pressedMask and GamepadButton.X.mask) != 0,
            diameter = btnDiameter,
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 4.dp)
        )
        DiamondCap(
            label = labelB,
            accent = ButtonBCrimson,
            pressed = (pressedMask and GamepadButton.B.mask) != 0,
            diameter = btnDiameter,
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 4.dp)
        )
    }
}

@Composable
private fun DiamondCap(
    label: String,
    accent: Color,
    pressed: Boolean,
    diameter: Dp,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(diameter)
            .clip(RoundedCornerShape(50))
            .background(
                brush = Brush.radialGradient(
                    colors = if (pressed) {
                        listOf(accent.copy(alpha = 0.95f), accent.copy(alpha = 0.65f))
                    } else {
                        listOf(CarbonElevated, CarbonSurface)
                    }
                )
            )
            .border(
                width = if (pressed) 2.5.dp else 1.5.dp,
                color = if (pressed) Color.White.copy(alpha = 0.9f) else accent.copy(alpha = 0.72f),
                shape = RoundedCornerShape(50)
            )
    ) {
        Text(
            text = label,
            fontFamily = SpaceGroteskFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 19.sp,
            color = if (pressed) Color(0xFF080A0F) else accent
        )
    }
}

/**
 * 8-way Multi-touch D-Pad supporting cardinal and diagonal directions.
 */
@Composable
fun MultiTouchDPad(
    pressedMask: Int,
    size: Dp = 136.dp,
    onDirectionsChanged: (Set<GamepadButton>) -> Unit,
    modifier: Modifier = Modifier
) {
    val activePointers = remember { mutableMapOf<PointerId, Offset>() }

    Box(
        modifier = modifier
            .size(size)
            .testTag("dpad_control")
            .semantics { contentDescription = "Directional D-Pad" }
            .pointerInput(Unit) {
                val center = Offset(this.size.width / 2f, this.size.height / 2f)
                val deadRadius = minOf(this.size.width, this.size.height) * 0.12f

                fun computeDirs(): Set<GamepadButton> {
                    val dirs = mutableSetOf<GamepadButton>()
                    for (pos in activePointers.values) {
                        val dx = pos.x - center.x
                        val dy = pos.y - center.y
                        if (hypot(dx, dy) < deadRadius) continue
                        val deg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble()))
                        // 8-way sectors with generous overlap for smooth diagonals
                        if (deg in -157.5..-22.5) dirs.add(GamepadButton.DPAD_UP)
                        if (deg in 22.5..157.5) dirs.add(GamepadButton.DPAD_DOWN)
                        if (deg >= 112.5 || deg <= -112.5) dirs.add(GamepadButton.DPAD_LEFT)
                        if (deg in -67.5..67.5) dirs.add(GamepadButton.DPAD_RIGHT)
                    }
                    return dirs
                }

                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        var changed = false
                        for (change in event.changes) {
                            if (change.pressed) {
                                activePointers[change.id] = change.position
                                change.consume()
                                changed = true
                            } else {
                                if (activePointers.remove(change.id) != null) {
                                    change.consume()
                                    changed = true
                                }
                            }
                        }
                        if (changed) {
                            onDirectionsChanged(computeDirs())
                        }
                    }
                }
            }
    ) {
        val up = (pressedMask and GamepadButton.DPAD_UP.mask) != 0
        val down = (pressedMask and GamepadButton.DPAD_DOWN.mask) != 0
        val left = (pressedMask and GamepadButton.DPAD_LEFT.mask) != 0
        val right = (pressedMask and GamepadButton.DPAD_RIGHT.mask) != 0

        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height
            val arm = w * 0.34f
            val halfArm = arm / 2f
            val cx = w / 2f
            val cy = h / 2f
            val pad = 4.dp.toPx()

            // Base circular shadow disc
            drawCircle(
                color = CarbonSurface.copy(alpha = 0.85f),
                radius = (w / 2f) - 2.dp.toPx(),
                center = Offset(cx, cy)
            )
            drawCircle(
                color = CarbonBorder,
                radius = (w / 2f) - 2.dp.toPx(),
                center = Offset(cx, cy),
                style = Stroke(width = 1.5.dp.toPx())
            )

            // Cross bars
            drawRoundRect(
                color = CarbonElevated,
                topLeft = Offset(cx - halfArm, pad),
                size = Size(arm, h - pad * 2),
                cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx())
            )
            drawRoundRect(
                color = CarbonElevated,
                topLeft = Offset(pad, cy - halfArm),
                size = Size(w - pad * 2, arm),
                cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx())
            )

            // Highlight pressed arms
            val activeGlow = ElectricCyan.copy(alpha = 0.85f)
            if (up) {
                drawRoundRect(
                    color = activeGlow,
                    topLeft = Offset(cx - halfArm + 2.dp.toPx(), pad + 2.dp.toPx()),
                    size = Size(arm - 4.dp.toPx(), h * 0.34f),
                    cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                )
            }
            if (down) {
                drawRoundRect(
                    color = activeGlow,
                    topLeft = Offset(cx - halfArm + 2.dp.toPx(), h - pad - h * 0.34f - 2.dp.toPx()),
                    size = Size(arm - 4.dp.toPx(), h * 0.34f),
                    cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                )
            }
            if (left) {
                drawRoundRect(
                    color = activeGlow,
                    topLeft = Offset(pad + 2.dp.toPx(), cy - halfArm + 2.dp.toPx()),
                    size = Size(w * 0.34f, arm - 4.dp.toPx()),
                    cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                )
            }
            if (right) {
                drawRoundRect(
                    color = activeGlow,
                    topLeft = Offset(w - pad - w * 0.34f - 2.dp.toPx(), cy - halfArm + 2.dp.toPx()),
                    size = Size(w * 0.34f, arm - 4.dp.toPx()),
                    cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                )
            }

            // Directional chevrons
            fun drawTriangle(tip: Offset, a: Offset, b: Offset, active: Boolean) {
                val p = Path().apply {
                    moveTo(tip.x, tip.y)
                    lineTo(a.x, a.y)
                    lineTo(b.x, b.y)
                    close()
                }
                drawPath(p, color = if (active) Color(0xFF080A0F) else TextSecondary)
            }

            val arrowSpan = 7.dp.toPx()
            drawTriangle(
                Offset(cx, pad + 11.dp.toPx()),
                Offset(cx - arrowSpan, pad + 21.dp.toPx()),
                Offset(cx + arrowSpan, pad + 21.dp.toPx()),
                up
            )
            drawTriangle(
                Offset(cx, h - pad - 11.dp.toPx()),
                Offset(cx - arrowSpan, h - pad - 21.dp.toPx()),
                Offset(cx + arrowSpan, h - pad - 21.dp.toPx()),
                down
            )
            drawTriangle(
                Offset(pad + 11.dp.toPx(), cy),
                Offset(pad + 21.dp.toPx(), cy - arrowSpan),
                Offset(pad + 21.dp.toPx(), cy + arrowSpan),
                left
            )
            drawTriangle(
                Offset(w - pad - 11.dp.toPx(), cy),
                Offset(w - pad - 21.dp.toPx(), cy - arrowSpan),
                Offset(w - pad - 21.dp.toPx(), cy + arrowSpan),
                right
            )

            // Center pivot dimple
            drawCircle(
                color = CarbonSurface,
                radius = 7.dp.toPx(),
                center = Offset(cx, cy)
            )
        }
    }
}

/**
 * Instant multi-touch shoulder/trigger button (L1, L2, R1, R2).
 */
@Composable
fun ShoulderTriggerButton(
    label: String,
    isHeavyTrigger: Boolean,
    pressed: Boolean,
    testTag: String,
    onPressedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var localPressed by remember { mutableStateOf(false) }
    val active = pressed || localPressed
    val accent = if (isHeavyTrigger) Color(0xFF6C63FF) else ElectricCyan

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .widthIn(min = 74.dp)
            .heightIn(min = 48.dp)
            .testTag(testTag)
            .semantics { contentDescription = "$label shoulder button" }
            .clip(RoundedCornerShape(14.dp))
            .background(
                brush = Brush.verticalGradient(
                    colors = if (active) {
                        listOf(accent.copy(alpha = 0.92f), accent.copy(alpha = 0.58f))
                    } else {
                        listOf(CarbonElevated, CarbonSurface)
                    }
                )
            )
            .border(
                width = if (active) 2.dp else 1.2.dp,
                color = if (active) Color.White.copy(alpha = 0.85f) else CarbonBorder,
                shape = RoundedCornerShape(14.dp)
            )
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val id = down.id
                    localPressed = true
                    onPressedChange(true)
                    down.consume()

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == id } ?: break
                        if (!change.pressed) {
                            change.consume()
                            break
                        }
                        change.consume()
                    }
                    localPressed = false
                    onPressedChange(false)
                }
            }
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(
            text = label,
            fontFamily = JetBrainsMonoFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = if (active) Color(0xFF07090E) else TextPrimary
        )
    }
}

/**
 * Tactile center menu button (SELECT / HOME / START).
 */
@Composable
fun MicroSystemButton(
    label: String,
    pressed: Boolean,
    testTag: String,
    onPressedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var localPressed by remember { mutableStateOf(false) }
    val active = pressed || localPressed

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .widthIn(min = 58.dp)
            .heightIn(min = 48.dp)
            .testTag(testTag)
            .semantics { contentDescription = "$label button" }
            .clip(RoundedCornerShape(50))
            .background(if (active) ElectricCyan else CarbonElevated)
            .border(
                width = 1.dp,
                color = if (active) Color.White else CarbonBorder,
                shape = RoundedCornerShape(50)
            )
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val id = down.id
                    localPressed = true
                    onPressedChange(true)
                    down.consume()
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == id } ?: break
                        if (!change.pressed) {
                            change.consume()
                            break
                        }
                        change.consume()
                    }
                    localPressed = false
                    onPressedChange(false)
                }
            }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = if (active) Color(0xFF080A0F) else TextSecondary
        )
    }
}
