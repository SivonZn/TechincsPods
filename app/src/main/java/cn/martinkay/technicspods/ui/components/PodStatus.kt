package cn.martinkay.technicspods.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import cn.martinkay.technicspods.utils.miuiStrongToast.data.BatteryParams

@Composable
fun PodStatus(
    batteryParams: BatteryParams,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    TechnicsDeviceBatteryStatus(
        batteryParams = batteryParams,
        modifier = modifier,
        compact = compact
    )
}
