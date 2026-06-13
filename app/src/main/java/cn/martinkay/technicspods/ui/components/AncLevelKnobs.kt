package cn.martinkay.technicspods.ui.components

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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.martinkay.technicspods.R
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun AncLevelKnobs(
    noiseCancelLevel: Int,
    transparencyLevel: Int,
    onNoiseCancelLevelChange: (Int) -> Unit,
    onTransparencyLevelChange: (Int) -> Unit,
    onNoiseCancelLevelCommit: (Int) -> Unit = onNoiseCancelLevelChange,
    onTransparencyLevelCommit: (Int) -> Unit = onTransparencyLevelChange,
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
            onValueCommit = onNoiseCancelLevelCommit,
            compact = compact,
            modifier = Modifier.weight(1f)
        )
        AncLevelKnob(
            label = stringResource(R.string.transparency_title),
            value = transparencyLevel,
            onValueChange = onTransparencyLevelChange,
            onValueCommit = onTransparencyLevelCommit,
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
    onValueCommit: (Int) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val size = if (compact) 82.dp else 108.dp
    val textColor = MiuixTheme.colorScheme.onBackground
    val context = LocalContext.current

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(size),
            contentAlignment = Alignment.Center
        ) {
            val currentOnValueChange = remember(onValueChange) { onValueChange }
            val currentOnValueCommit = remember(onValueCommit) { onValueCommit }
            AndroidView(
                modifier = Modifier.size(size),
                factory = {
                    PowerampRoundKnobView(context).apply {
                        this.value = value
                        this.onValueChange = currentOnValueChange
                        this.onValueCommit = currentOnValueCommit
                    }
                },
                update = { view ->
                    if (!view.isPressed) {
                        view.value = value
                    }
                    view.onValueChange = currentOnValueChange
                    view.onValueCommit = currentOnValueCommit
                }
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
