package cn.martinkay.technicspods.ui.components

import androidx.compose.foundation.Canvas
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

private const val START_ANGLE = 135f
private const val SWEEP_ANGLE = 270f

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
    val knobSize = if (compact) 62.dp else 82.dp
    val primary = MiuixTheme.colorScheme.primary
    val textColor = MiuixTheme.colorScheme.onBackground
    val trackColor = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.24f)
    val knobBorder = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.44f)
    val knobFill = MiuixTheme.colorScheme.background.copy(alpha = 0.92f)
    val indicator = MiuixTheme.colorScheme.onBackground

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
                primary = primary,
                trackColor = trackColor,
                knobBorder = knobBorder,
                knobFill = knobFill,
                indicator = indicator
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
    primary: Color,
    trackColor: Color,
    knobBorder: Color,
    knobFill: Color,
    indicator: Color
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
                    val normalized = ((degrees - START_ANGLE + 360f) % 360f)
                        .coerceIn(0f, SWEEP_ANGLE)
                    return ((normalized / SWEEP_ANGLE) * 100f).roundToInt().coerceIn(0, 100)
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
        val stroke = 4.dp.toPx()
        val arcInset = stroke / 2f + 2.dp.toPx()
        val arcSize = Size(size.toPx() - arcInset * 2f, size.toPx() - arcInset * 2f)
        val sweep = SWEEP_ANGLE * shownValue / 100f
        val center = Offset(size.toPx() / 2f, size.toPx() / 2f)
        val knobRadius = knobSize.toPx() / 2f
        val indicatorAngle = Math.toRadians((START_ANGLE + sweep).toDouble())
        val indicatorStart = Offset(
            center.x + cos(indicatorAngle).toFloat() * (knobRadius - 15.dp.toPx()),
            center.y + sin(indicatorAngle).toFloat() * (knobRadius - 15.dp.toPx())
        )
        val indicatorEnd = Offset(
            center.x + cos(indicatorAngle).toFloat() * (knobRadius - 5.dp.toPx()),
            center.y + sin(indicatorAngle).toFloat() * (knobRadius - 5.dp.toPx())
        )

        drawArc(
            color = trackColor,
            startAngle = START_ANGLE,
            sweepAngle = SWEEP_ANGLE,
            useCenter = false,
            topLeft = Offset(arcInset, arcInset),
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
        drawArc(
            brush = Brush.sweepGradient(
                0.0f to primary.copy(alpha = 0.52f),
                0.72f to primary,
                1.0f to primary.copy(alpha = 0.52f),
                center = center
            ),
            startAngle = START_ANGLE,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = Offset(arcInset, arcInset),
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
        repeat(11) { tick ->
            val tickAngle = Math.toRadians((START_ANGLE + SWEEP_ANGLE * tick / 10f).toDouble())
            val outer = size.toPx() / 2f - 1.dp.toPx()
            val inner = outer - if (tick % 5 == 0) 7.dp.toPx() else 4.dp.toPx()
            drawLine(
                color = trackColor.copy(alpha = if (tick % 5 == 0) 0.72f else 0.45f),
                start = Offset(
                    center.x + cos(tickAngle).toFloat() * inner,
                    center.y + sin(tickAngle).toFloat() * inner
                ),
                end = Offset(
                    center.x + cos(tickAngle).toFloat() * outer,
                    center.y + sin(tickAngle).toFloat() * outer
                ),
                strokeWidth = if (tick % 5 == 0) 1.5.dp.toPx() else 1.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
        drawCircle(
            color = knobFill,
            radius = knobRadius,
            center = center
        )
        drawCircle(
            color = knobBorder,
            radius = knobRadius,
            center = center,
            style = Stroke(width = 2.dp.toPx())
        )
        drawLine(
            color = indicator,
            start = indicatorStart,
            end = indicatorEnd,
            strokeWidth = 4.dp.toPx(),
            cap = StrokeCap.Round
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.09f),
            radius = knobRadius - 7.dp.toPx(),
            center = center.copy(y = center.y - 3.dp.toPx()),
            style = Stroke(width = 1.dp.toPx())
        )
    }
}
