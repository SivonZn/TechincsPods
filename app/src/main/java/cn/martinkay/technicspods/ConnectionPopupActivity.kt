package cn.martinkay.technicspods

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.delay
import cn.martinkay.technicspods.ui.AppTheme
import cn.martinkay.technicspods.ui.components.TechnicsDeviceBatteryStatus
import cn.martinkay.technicspods.utils.miuiStrongToast.data.BatteryParams
import cn.martinkay.technicspods.utils.miuiStrongToast.data.TechnicsPodsAction
import cn.martinkay.technicspods.utils.miuiStrongToast.data.TechnicsPodsPrefsKey
import top.yukonga.miuix.kmp.theme.ColorSchemeMode

class ConnectionPopupActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setBackgroundDrawable(ColorDrawable(AndroidColor.TRANSPARENT))
        window.statusBarColor = AndroidColor.TRANSPARENT
        window.navigationBarColor = AndroidColor.TRANSPARENT
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val prefs = getSharedPreferences("technicspods_settings", Context.MODE_PRIVATE)
        val colorSchemeMode = when (prefs.getInt("theme_mode", 0)) {
            1 -> ColorSchemeMode.Light
            2 -> ColorSchemeMode.Dark
            else -> ColorSchemeMode.System
        }

        val initialBatteryParams = intent.getParcelableExtra("status", BatteryParams::class.java)
            ?: BatteryParams()
        val initialDeviceName = intent.getStringExtra("device_name").orEmpty()
        val autoDismissSeconds = intent.getIntExtra(
            TechnicsPodsPrefsKey.CONNECTION_POPUP_DISMISS_SECONDS,
            TechnicsPodsPrefsKey.DEFAULT_CONNECTION_POPUP_DISMISS_SECONDS
        ).takeIf { it in TechnicsPodsPrefsKey.CONNECTION_POPUP_DISMISS_SECOND_OPTIONS }
            ?: TechnicsPodsPrefsKey.DEFAULT_CONNECTION_POPUP_DISMISS_SECONDS

        setContent {
            AppTheme(colorSchemeMode = colorSchemeMode) {
                ConnectionPopupContent(
                    initialDeviceName = initialDeviceName,
                    initialBatteryParams = initialBatteryParams,
                    autoDismissSeconds = autoDismissSeconds,
                    onDismiss = { finish() }
                )
            }
        }
    }
}

@Composable
private fun ConnectionPopupContent(
    initialDeviceName: String,
    initialBatteryParams: BatteryParams,
    autoDismissSeconds: Int,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val deviceName = remember { mutableStateOf(initialDeviceName) }
    val batteryParams = remember { mutableStateOf(initialBatteryParams) }

    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    TechnicsPodsAction.ACTION_PODS_CONNECTED -> {
                        deviceName.value = intent.getStringExtra("device_name") ?: deviceName.value
                    }
                    TechnicsPodsAction.ACTION_PODS_BATTERY_CHANGED -> {
                        intent.getParcelableExtra("status", BatteryParams::class.java)?.let {
                            batteryParams.value = it
                        }
                    }
                    TechnicsPodsAction.ACTION_PODS_DISCONNECTED -> {
                        onDismiss()
                    }
                }
            }
        }

        context.registerReceiver(receiver, IntentFilter().apply {
            addAction(TechnicsPodsAction.ACTION_PODS_CONNECTED)
            addAction(TechnicsPodsAction.ACTION_PODS_BATTERY_CHANGED)
            addAction(TechnicsPodsAction.ACTION_PODS_DISCONNECTED)
        }, Context.RECEIVER_EXPORTED)

        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) {
            }
        }
    }

    LaunchedEffect(autoDismissSeconds) {
        delay(autoDismissSeconds * 1000L)
        onDismiss()
    }

    ConnectionPopupCard(
        deviceName = deviceName.value.ifEmpty { stringResource(R.string.app_name) },
        batteryParams = batteryParams.value,
        onDismiss = onDismiss
    )
}

@Composable
private fun ConnectionPopupCard(
    deviceName: String,
    batteryParams: BatteryParams,
    onDismiss: () -> Unit
) {
    val containerColor = Color(0xFFFBFBFB)
    val textColor = Color(0xFF111111)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.34f))
            .navigationBarsPadding()
            .padding(horizontal = 12.dp)
            .padding(bottom = 6.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 430.dp)
                .clip(RoundedCornerShape(55.dp))
                .background(containerColor)
        ) {
            CloseButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 22.dp, end = 22.dp)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 29.dp, bottom = 31.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                BasicText(
                    text = deviceName,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 75.dp),
                    style = TextStyle(
                        color = textColor,
                        fontSize = 19.sp,
                        lineHeight = 25.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(38.dp))
                TechnicsDeviceBatteryStatus(
                    batteryParams = batteryParams,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp)
                )

                Spacer(modifier = Modifier.height(52.dp))
                DoneButton(
                    onClick = onDismiss,
                    textColor = textColor,
                    modifier = Modifier.padding(horizontal = 30.dp)
                )
            }
        }
    }
}

@Composable
private fun CloseButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(27.dp)
            .clip(CircleShape)
            .background(Color(0xFFF1F1F1))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(9.dp)) {
            val strokeWidth = 2.8.dp.toPx()
            drawLine(
                color = Color(0xFF8B8B8B),
                start = Offset(1.dp.toPx(), 1.dp.toPx()),
                end = Offset(size.width - 1.dp.toPx(), size.height - 1.dp.toPx()),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color(0xFF8B8B8B),
                start = Offset(size.width - 1.dp.toPx(), 1.dp.toPx()),
                end = Offset(1.dp.toPx(), size.height - 1.dp.toPx()),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun DoneButton(onClick: () -> Unit, textColor: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFFF2F2F2))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        BasicText(
            text = stringResource(R.string.done),
            style = TextStyle(
                color = textColor,
                fontSize = 16.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Normal,
                textAlign = TextAlign.Center
            )
        )
    }
}
