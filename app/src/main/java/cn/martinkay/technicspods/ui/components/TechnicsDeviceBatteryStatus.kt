package cn.martinkay.technicspods.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.martinkay.technicspods.R
import cn.martinkay.technicspods.utils.miuiStrongToast.data.BatteryParams
import cn.martinkay.technicspods.utils.miuiStrongToast.data.PodParams
import top.yukonga.miuix.kmp.basic.Text

@Composable
fun TechnicsDeviceBatteryStatus(
    batteryParams: BatteryParams,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val imageSize = if (compact) 54.dp else 78.dp
    val caseImageSize = if (compact) 58.dp else 84.dp

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        TechnicsBatteryItem(
            label = stringResource(R.string.batt_left_pod),
            pod = batteryParams.left,
            imageRes = R.drawable.img_left,
            imageSize = imageSize,
            modifier = Modifier.weight(1f),
            compact = compact
        )
        TechnicsBatteryItem(
            label = stringResource(R.string.pod_case),
            pod = batteryParams.case,
            imageRes = R.drawable.img_box,
            imageSize = caseImageSize,
            modifier = Modifier.weight(1f),
            compact = compact
        )
        TechnicsBatteryItem(
            label = stringResource(R.string.batt_right_pod),
            pod = batteryParams.right,
            imageRes = R.drawable.img_right,
            imageSize = imageSize,
            modifier = Modifier.weight(1f),
            compact = compact
        )
    }
}

@Composable
private fun TechnicsBatteryItem(
    label: String,
    pod: PodParams?,
    @DrawableRes imageRes: Int,
    imageSize: Dp,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val isConnected = pod?.isConnected == true
    val battery = pod?.battery ?: 0
    val chargingText = stringResource(R.string.charging)
    val batteryText = if (isConnected) {
        if (pod.isCharging) "$battery% $chargingText" else "$battery%"
    } else {
        "-"
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(imageRes),
            contentDescription = label,
            modifier = Modifier.size(imageSize),
            contentScale = ContentScale.Fit
        )
        Spacer(modifier = Modifier.height(if (compact) 4.dp else 8.dp))
        Text(
            text = batteryText,
            fontSize = if (compact) 12.sp else 14.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Text(
            text = label,
            fontSize = if (compact) 11.sp else 12.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )
    }
}
