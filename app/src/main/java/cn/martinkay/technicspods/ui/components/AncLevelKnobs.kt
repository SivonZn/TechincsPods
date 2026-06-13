package cn.martinkay.technicspods.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.martinkay.technicspods.R
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val POWERAMP_VALUE_START_ANGLE = 30f
private const val POWERAMP_VALUE_END_ANGLE = 330f
private const val POWERAMP_SWEEP_ANGLE = POWERAMP_VALUE_END_ANGLE - POWERAMP_VALUE_START_ANGLE
private const val POWERAMP_DRAW_START_ANGLE = 90f + POWERAMP_VALUE_START_ANGLE

@Composable
fun AncLevelKnobs(
    noiseCancelLevel: Int,
    transparencyLevel: Int,
    onNoiseCancelLevelChange: (Int) -> Unit,
    onTransparencyLevelChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = if (compact) 8.dp else 14.dp,
                vertical = if (compact) 10.dp else 16.dp
            ),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        AncLevelKnob(
            label = stringResource(R.string.noise_cancellation_title),
            value = noiseCancelLevel,
            onValueChange = onNoiseCancelLevelChange,
            compact = compact,
            modifier = Modifier.weight(1f)
        )
        AncLevelKnob(
            label = stringResource(R.string.transparency_title),
            value = transparencyLevel,
            onValueChange = onTransparencyLevelChange,
            compact = compact,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun AncLevelKnob(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val size = if (compact) 82.dp else 108.dp
    val knobSize = if (compact) 58.dp else 76.dp
    val textColor = MiuixTheme.colorScheme.onBackground
    val palette = powerampKnobPalette(isSystemInDarkTheme())

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(size),
            contentAlignment = Alignment.Center
        ) {
            RoundAncKnobCanvas(
                value = value,
                onValueChange = onValueChange,
                size = size,
                knobSize = knobSize,
                palette = palette
            )
        }
        Spacer(modifier = Modifier.height(if (compact) 3.dp else 6.dp))
        Text(
            text = "$value%",
            fontSize = if (compact) 14.sp else 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = textColor,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(if (compact) 1.dp else 2.dp))
        Text(
            text = label,
            fontSize = if (compact) 11.sp else 12.sp,
            fontWeight = FontWeight.Medium,
            color = textColor.copy(alpha = 0.82f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun RoundAncKnobCanvas(
    value: Int,
    onValueChange: (Int) -> Unit,
    size: Dp,
    knobSize: Dp,
    palette: PowerampKnobPalette
) {
    var draggingValue by remember { mutableIntStateOf(value) }
    var draggingValueFloat by remember { mutableFloatStateOf(value.toFloat()) }
    var isDragging by remember { mutableFloatStateOf(0f) }
    var lastDragAngle by remember { mutableFloatStateOf(0f) }
    val shownValue = if (isDragging > 0f) draggingValue else value

    Canvas(
        modifier = Modifier
            .size(size)
            .pointerInput(value) {
                fun Offset.pointerAngle(): Float {
                    val side = size.toPx()
                    val center = Offset(side / 2f, side / 2f)
                    val rawDegrees = Math.toDegrees(
                        atan2(y - center.y, x - center.x).toDouble()
                    ).toFloat()
                    return (rawDegrees + 360f) % 360f
                }

                fun shortestAngleDelta(from: Float, to: Float): Float {
                    var delta = to - from
                    while (delta > 180f) delta -= 360f
                    while (delta < -180f) delta += 360f
                    return delta
                }

                detectDragGestures(
                    onDragStart = {
                        draggingValue = value
                        draggingValueFloat = value.toFloat()
                        lastDragAngle = it.pointerAngle()
                        isDragging = 1f
                    },
                    onDragEnd = { isDragging = 0f },
                    onDragCancel = { isDragging = 0f },
                    onDrag = { change, _ ->
                        val nextAngle = change.position.pointerAngle()
                        val delta = shortestAngleDelta(lastDragAngle, nextAngle)
                        lastDragAngle = nextAngle
                        if (abs(delta) > 120f) return@detectDragGestures

                        draggingValueFloat = (draggingValueFloat + delta / POWERAMP_SWEEP_ANGLE * 100f)
                            .coerceIn(0f, 100f)
                        val next = draggingValueFloat.roundToInt().coerceIn(0, 100)
                        if (next != draggingValue) {
                            draggingValue = next
                            onValueChange(next)
                        }
                        change.consume()
                    }
                )
            }
    ) {
        val stroke = 3.dp.toPx()
        val arcOffset = 3.dp.toPx()
        val arcInset = ((size.toPx() - knobSize.toPx()) / 2f) - arcOffset
        val arcSize = Size(size.toPx() - arcInset * 2f, size.toPx() - arcInset * 2f)
        val sweep = POWERAMP_SWEEP_ANGLE * shownValue / 100f
        val center = Offset(size.toPx() / 2f, size.toPx() / 2f)
        val knobRadius = knobSize.toPx() / 2f
        val indicatorAngle = POWERAMP_VALUE_START_ANGLE + sweep
        val indicatorWidth = 5.dp.toPx()
        val indicatorHeight = 10.dp.toPx()
        val indicatorTop = knobRadius - 20.dp.toPx()

        drawCircle(
            color = Color.Black.copy(alpha = if (isDragging > 0f) 0.18f else 0.10f),
            radius = knobRadius,
            center = center.copy(y = center.y + 1.dp.toPx())
        )
        drawArc(
            color = palette.arcTrack,
            startAngle = POWERAMP_DRAW_START_ANGLE,
            sweepAngle = POWERAMP_SWEEP_ANGLE,
            useCenter = false,
            topLeft = Offset(arcInset, arcInset),
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
        drawArc(
            color = if (isDragging > 0f) palette.arcHilitePressed else palette.arcHilite,
            startAngle = POWERAMP_DRAW_START_ANGLE,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = Offset(arcInset, arcInset),
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
        drawCircle(
            color = palette.knobBg,
            radius = knobRadius,
            center = center
        )
        drawCircle(
            color = if (isDragging > 0f) palette.knobPressed else palette.knobBorder,
            radius = knobRadius,
            center = center,
            style = Stroke(width = if (isDragging > 0f) 3.5.dp.toPx() else 2.dp.toPx())
        )
        rotate(degrees = indicatorAngle, pivot = center) {
            drawRoundRect(
                color = if (isDragging > 0f) palette.indicatorPressed else palette.indicator,
                topLeft = Offset(
                    center.x - indicatorWidth / 2f,
                    center.y + indicatorTop
                ),
                size = Size(indicatorWidth, indicatorHeight),
                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
            )
        }
    }
}

private data class PowerampKnobPalette(
    val knobBg: Color,
    val knobBorder: Color,
    val knobPressed: Color,
    val indicator: Color,
    val indicatorPressed: Color,
    val arcTrack: Color,
    val arcHilite: Color,
    val arcHilitePressed: Color
)

@Composable
private fun powerampKnobPalette(isDark: Boolean): PowerampKnobPalette {
    return if (isDark) {
        PowerampKnobPalette(
            knobBg = Color(0xFF222222),
            knobBorder = Color(0xFF484848),
            knobPressed = Color.White,
            indicator = Color(0xFFCCCCCC),
            indicatorPressed = Color.White,
            arcTrack = Color(0xFF777777),
            arcHilite = Color(0xFF746CDA),
            arcHilitePressed = Color(0xFF8187FF)
        )
    } else {
        PowerampKnobPalette(
            knobBg = Color.White,
            knobBorder = Color(0xFF333333),
            knobPressed = Color.Black,
            indicator = Color(0xFF656565),
            indicatorPressed = Color.Black,
            arcTrack = Color(0xFF777777),
            arcHilite = Color(0xFF000000),
            arcHilitePressed = Color(0xFF888888)
        )
    }
}
