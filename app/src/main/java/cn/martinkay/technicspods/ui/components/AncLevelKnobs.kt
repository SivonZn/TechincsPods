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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
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
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
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
            Text(
                text = value.toString(),
                fontSize = if (compact) 16.sp else 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = textColor,
                textAlign = TextAlign.Center
            )
        }
        Spacer(modifier = Modifier.height(if (compact) 4.dp else 8.dp))
        Text(
            text = label,
            fontSize = if (compact) 11.sp else 13.sp,
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
    var isDragging by remember { mutableFloatStateOf(0f) }
    val shownValue = if (isDragging > 0f) draggingValue else value

    Canvas(
        modifier = Modifier
            .size(size)
            .pointerInput(Unit) {
                fun Offset.toLevel(): Int {
                    val center = Offset(size.toPx() / 2f, size.toPx() / 2f)
                    val rawDegrees = Math.toDegrees(
                        atan2(y - center.y, x - center.x).toDouble()
                    ).toFloat()
                    val degrees = (rawDegrees + 360f) % 360f
                    val normalized = ((degrees - POWERAMP_VALUE_START_ANGLE + 360f) % 360f)
                    val clamped = when {
                        normalized <= POWERAMP_SWEEP_ANGLE -> normalized
                        degrees < POWERAMP_VALUE_START_ANGLE -> 0f
                        else -> POWERAMP_SWEEP_ANGLE
                    }
                    return ((clamped / POWERAMP_SWEEP_ANGLE) * 100f).roundToInt().coerceIn(0, 100)
                }

                detectDragGestures(
                    onDragStart = {
                        draggingValue = it.toLevel()
                        isDragging = 1f
                        onValueChange(draggingValue)
                    },
                    onDragEnd = { isDragging = 0f },
                    onDragCancel = { isDragging = 0f },
                    onDrag = { change, _ ->
                        val next = change.position.toLevel()
                        if (next != draggingValue) {
                            draggingValue = next
                            onValueChange(next)
                        }
                    }
                )
            }
    ) {
        val stroke = 3.dp.toPx()
        val arcInset = ((size.toPx() - knobSize.toPx()) / 2f) - 3.dp.toPx()
        val arcSize = Size(size.toPx() - arcInset * 2f, size.toPx() - arcInset * 2f)
        val sweep = POWERAMP_SWEEP_ANGLE * shownValue / 100f
        val center = Offset(size.toPx() / 2f, size.toPx() / 2f)
        val knobRadius = knobSize.toPx() / 2f
        val indicatorAngle = POWERAMP_VALUE_START_ANGLE + sweep
        val indicatorWidth = 5.dp.toPx()
        val indicatorHeight = 10.dp.toPx()
        val indicatorTop = knobRadius - 20.dp.toPx()

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
            brush = Brush.sweepGradient(
                0.0f to palette.arcHilite.copy(alpha = 0.45f),
                0.35f to palette.arcHilite,
                1.0f to palette.arcHilite.copy(alpha = 0.78f),
                center = center
            ),
            startAngle = POWERAMP_DRAW_START_ANGLE,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = Offset(arcInset, arcInset),
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
        repeat(7) { tick ->
            val tickAngle = Math.toRadians(
                (POWERAMP_DRAW_START_ANGLE + POWERAMP_SWEEP_ANGLE * tick / 6f).toDouble()
            )
            val outer = size.toPx() / 2f - 2.dp.toPx()
            val inner = outer - if (tick % 3 == 0) 6.dp.toPx() else 3.5.dp.toPx()
            drawLine(
                color = palette.arcTrack.copy(alpha = if (tick % 3 == 0) 0.86f else 0.55f),
                start = Offset(
                    center.x + cos(tickAngle).toFloat() * inner,
                    center.y + sin(tickAngle).toFloat() * inner
                ),
                end = Offset(
                    center.x + cos(tickAngle).toFloat() * outer,
                    center.y + sin(tickAngle).toFloat() * outer
                ),
                strokeWidth = if (tick % 3 == 0) 1.3.dp.toPx() else 1.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
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
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx())
            )
        }
        drawCircle(
            color = palette.hole,
            radius = 1.5.dp.toPx(),
            center = center.copy(y = center.y + knobRadius - 20.dp.toPx()),
            style = Stroke(width = 1.dp.toPx())
        )
    }
}

private data class PowerampKnobPalette(
    val knobBg: Color,
    val knobBorder: Color,
    val knobPressed: Color,
    val indicator: Color,
    val indicatorPressed: Color,
    val hole: Color,
    val arcTrack: Color,
    val arcHilite: Color
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
            hole = Color(0xFF555555),
            arcTrack = Color(0xFF777777),
            arcHilite = MiuixTheme.colorScheme.primary
        )
    } else {
        PowerampKnobPalette(
            knobBg = Color.White,
            knobBorder = Color(0xFF333333),
            knobPressed = Color.Black,
            indicator = Color(0xFF656565),
            indicatorPressed = Color.Black,
            hole = Color(0xFF555555),
            arcTrack = Color(0xFF777777),
            arcHilite = MiuixTheme.colorScheme.primary
        )
    }
}
