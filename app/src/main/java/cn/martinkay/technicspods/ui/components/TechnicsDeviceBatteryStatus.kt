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
import androidx.compose.foundation.layout.width
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
        if (isConnected) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Image(
                    painter = painterResource(getBatteryIconRes(battery, pod.isCharging)),
                    contentDescription = null,
                    modifier = Modifier.size(
                        width = if (compact) 24.dp else 28.dp,
                        height = if (compact) 13.dp else 15.dp
                    ),
                    contentScale = ContentScale.Fit
                )
                Spacer(modifier = Modifier.width(if (compact) 4.dp else 5.dp))
                Text(
                    text = "$battery%",
                    fontSize = if (compact) 12.sp else 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            Text(
                text = "-",
                fontSize = if (compact) 12.sp else 14.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
        }
        Text(
            text = label,
            fontSize = if (compact) 11.sp else 12.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )
    }
}

@DrawableRes
private fun getBatteryIconRes(level: Int, isCharging: Boolean): Int {
    val index = when {
        level <= 10 -> 1
        level <= 20 -> 2
        level <= 30 -> 3
        level <= 40 -> 4
        level <= 50 -> 5
        level <= 60 -> 6
        level <= 70 -> 7
        level <= 80 -> 8
        level <= 90 -> 9
        else -> 10
    }

    return if (isCharging) {
        when (index) {
            1 -> R.drawable.charge_1
            2 -> R.drawable.charge_2
            3 -> R.drawable.charge_3
            4 -> R.drawable.charge_4
            5 -> R.drawable.charge_5
            6 -> R.drawable.charge_6
            7 -> R.drawable.charge_7
            8 -> R.drawable.charge_8
            9 -> R.drawable.charge_9
            else -> R.drawable.charge_10
        }
    } else {
        when (index) {
            1 -> R.drawable.common_1
            2 -> R.drawable.common_2
            3 -> R.drawable.common_3
            4 -> R.drawable.common_4
            5 -> R.drawable.common_5
            6 -> R.drawable.common_6
            7 -> R.drawable.common_7
            8 -> R.drawable.common_8
            9 -> R.drawable.common_9
            else -> R.drawable.common_10
        }
    }
}
