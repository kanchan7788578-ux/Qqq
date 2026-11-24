package com.example.gamepad.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.CarbonBorder
import com.example.ui.theme.CarbonElevated
import com.example.ui.theme.CarbonSurface
import com.example.ui.theme.ElectricCyan
import kotlin.math.hypot

@Composable
fun AnalogStick(
    label: String,
    deadzone: Float,
    accentColor: Color = ElectricCyan,
    size: Dp = 148.dp,
    externalOffset: Offset? = null,
    testTag: String = "analog_stick",
    onStickChanged: (x: Float, y: Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var normalizedOffset by remember { mutableStateOf(Offset.Zero) }
    var isTouching by remember { mutableStateOf(false) }

    val displayOffset = if (!isTouching && externalOffset != null) externalOffset else normalizedOffset

    Box(
        modifier = modifier
            .size(size)
            .testTag(testTag)
            .semantics { contentDescription = label }
            .pointerInput(deadzone) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val pointerId = down.id
                    isTouching = true

                    val center = Offset(this.size.width / 2f, this.size.height / 2f)
                    val maxRadius = (minOf(this.size.width, this.size.height) / 2f) * 0.68f

                    fun updateFromPosition(pos: Offset) {
                        val dx = pos.x - center.x
                        val dy = pos.y - center.y
                        val dist = hypot(dx, dy)
                        val clampedDist = dist.coerceAtMost(maxRadius)
                        val rawNormX = if (dist > 0.001f) (dx / dist) * (clampedDist / maxRadius) else 0f
                        val rawNormY = if (dist > 0.001f) (dy / dist) * (clampedDist / maxRadius) else 0f

                        val magnitude = hypot(rawNormX, rawNormY)
                        if (magnitude < deadzone) {
                            normalizedOffset = Offset(rawNormX, rawNormY)
                            onStickChanged(0f, 0f)
                        } else {
                            val rescaled = ((magnitude - deadzone) / (1f - deadzone)).coerceIn(0f, 1f)
                            val outX = (rawNormX / magnitude) * rescaled
                            val outY = (rawNormY / magnitude) * rescaled
                            normalizedOffset = Offset(rawNormX, rawNormY)
                            onStickChanged(outX, outY)
                        }
                    }

                    updateFromPosition(down.position)
                    down.consume()

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                        if (!change.pressed) {
                            change.consume()
                            break
                        }
                        updateFromPosition(change.position)
                        change.consume()
                    }

                    isTouching = false
                    normalizedOffset = Offset.Zero
                    onStickChanged(0f, 0f)
                }
            }
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val outerRadius = minOf(this.size.width, this.size.height) / 2f
            val travelRadius = outerRadius * 0.64f
            val knobRadius = outerRadius * 0.36f

            // Outer glowing housing
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        if (isTouching) accentColor.copy(alpha = 0.22f) else CarbonElevated,
                        CarbonSurface,
                        Color(0xFF090C12)
                    ),
                    center = center,
                    radius = outerRadius
                ),
                radius = outerRadius,
                center = center
            )

            // Machined outer bezel ring
            drawCircle(
                color = if (isTouching) accentColor.copy(alpha = 0.85f) else CarbonBorder,
                radius = outerRadius - 2.dp.toPx(),
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )

            // Cardinal tick guides
            val tickInner = outerRadius * 0.78f
            val tickOuter = outerRadius * 0.90f
            val tickColor = CarbonBorder.copy(alpha = 0.7f)
            drawLine(tickColor, Offset(center.x, center.y - tickInner), Offset(center.x, center.y - tickOuter), 1.5.dp.toPx(), StrokeCap.Round)
            drawLine(tickColor, Offset(center.x, center.y + tickInner), Offset(center.x, center.y + tickOuter), 1.5.dp.toPx(), StrokeCap.Round)
            drawLine(tickColor, Offset(center.x - tickInner, center.y), Offset(center.x - tickOuter, center.y), 1.5.dp.toPx(), StrokeCap.Round)
            drawLine(tickColor, Offset(center.x + tickInner, center.y), Offset(center.x + tickOuter, center.y), 1.5.dp.toPx(), StrokeCap.Round)

            // Deadzone ring
            drawCircle(
                color = accentColor.copy(alpha = 0.14f),
                radius = travelRadius * deadzone.coerceIn(0.05f, 0.35f) * 2.2f,
                center = center,
                style = Stroke(width = 1.dp.toPx())
            )

            val knobCenter = Offset(
                x = center.x + displayOffset.x * travelRadius,
                y = center.y + displayOffset.y * travelRadius
            )

            // Vector tether when moved
            if (hypot(displayOffset.x, displayOffset.y) > 0.04f) {
                drawLine(
                    brush = Brush.linearGradient(
                        colors = listOf(accentColor.copy(alpha = 0.15f), accentColor.copy(alpha = 0.9f)),
                        start = center,
                        end = knobCenter
                    ),
                    start = center,
                    end = knobCenter,
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // Thumb cap shadow
            drawCircle(
                color = Color.Black.copy(alpha = 0.55f),
                radius = knobRadius * 1.08f,
                center = knobCenter + Offset(0f, 3.dp.toPx())
            )

            // Thumb cap body
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF2D384F),
                        Color(0xFF1A2233),
                        Color(0xFF101521)
                    ),
                    center = knobCenter - Offset(knobRadius * 0.25f, knobRadius * 0.25f),
                    radius = knobRadius * 1.3f
                ),
                radius = knobRadius,
                center = knobCenter
            )

            // Glowing accent rim on thumb cap
            drawCircle(
                color = if (isTouching || externalOffset != null) accentColor else accentColor.copy(alpha = 0.45f),
                radius = knobRadius - 1.5.dp.toPx(),
                center = knobCenter,
                style = Stroke(width = if (isTouching) 2.5.dp.toPx() else 1.5.dp.toPx())
            )

            // Inner grip concavity ring
            drawCircle(
                color = Color.White.copy(alpha = 0.08f),
                radius = knobRadius * 0.56f,
                center = knobCenter,
                style = Stroke(width = 1.2.dp.toPx())
            )
        }
    }
}
