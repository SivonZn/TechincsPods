package cn.martinkay.technicspods

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import cn.martinkay.technicspods.R
import cn.martinkay.technicspods.pods.NoiseControlMode
import cn.martinkay.technicspods.ui.AppTheme
import cn.martinkay.technicspods.ui.components.AncLevelKnobs
import cn.martinkay.technicspods.ui.components.AncSwitch
import cn.martinkay.technicspods.ui.components.TechnicsDeviceBatteryStatus
import cn.martinkay.technicspods.utils.miuiStrongToast.data.BatteryParams
import cn.martinkay.technicspods.utils.miuiStrongToast.data.TechnicsPodsAction
import cn.martinkay.technicspods.utils.miuiStrongToast.data.TechnicsPodsPrefsKey
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.ColorSchemeMode

class PopupActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val prefs = getSharedPreferences("technicspods_settings", Context.MODE_PRIVATE)
            val colorSchemeMode = when (prefs.getInt("theme_mode", 0)) {
                1 -> ColorSchemeMode.Light
                2 -> ColorSchemeMode.Dark
                else -> ColorSchemeMode.System
            }
            AppTheme(colorSchemeMode = colorSchemeMode) {
                PopupContent(
                    onMore = {
                        val prefs = getSharedPreferences("technicspods_settings", Context.MODE_PRIVATE)
                        if (prefs.getBoolean("open_heytap", false)) {
                            val intent = packageManager.getLaunchIntentForPackage("com.panasonic.technicsaudioconnect")
                            if (intent != null) {
                                startActivity(intent)
                            } else {
                                startActivity(Intent(this@PopupActivity, MainActivity::class.java))
                            }
                        } else {
                            startActivity(Intent(this@PopupActivity, MainActivity::class.java))
                        }
                        finish()
                    },
                    onDone = { finish() }
                )
            }
        }
    }
}

@Composable
private fun PopupContent(onMore: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    val showDialog = remember { mutableStateOf(false) }

    val prefs = remember { context.getSharedPreferences("technicspods_settings", Context.MODE_PRIVATE) }
    val themeMode = remember { prefs.getInt("theme_mode", 0) }
    // 读取Adaptive模式偏好设置
    val adaptiveModeEnabled = remember { prefs.getBoolean("adaptive_mode", true) }
    val systemDark = isSystemInDarkTheme()
    val isDarkMode = when (themeMode) {
        1 -> false
        2 -> true
        else -> systemDark
    }

    val batteryParams = remember { mutableStateOf(BatteryParams()) }
    val ancMode = remember { mutableStateOf(NoiseControlMode.OFF) }
    val noiseCancelLevel = remember {
        mutableStateOf(
            prefs.getInt(
                TechnicsPodsPrefsKey.NOISE_CANCEL_LEVEL,
                TechnicsPodsPrefsKey.DEFAULT_NOISE_CANCEL_LEVEL
            ).coerceIn(0, 100)
        )
    }
    val transparencyLevel = remember {
        mutableStateOf(
            prefs.getInt(
                TechnicsPodsPrefsKey.TRANSPARENCY_LEVEL,
                TechnicsPodsPrefsKey.DEFAULT_TRANSPARENCY_LEVEL
            ).coerceIn(0, 100)
        )
    }
    val gameMode = remember { mutableStateOf(false) }
    val deviceName = remember { mutableStateOf("") }

    val broadcastReceiver = remember {
        object : BroadcastReceiver() {
            override fun onReceive(p0: Context?, p1: Intent?) {
                when (p1?.action) {
                    TechnicsPodsAction.ACTION_PODS_ANC_CHANGED -> {
                        val status = p1.getIntExtra("status", 1)
                        ancMode.value = when (status) {
                            1 -> NoiseControlMode.OFF
                            2 -> NoiseControlMode.NOISE_CANCELLATION
                            3 -> NoiseControlMode.TRANSPARENCY
                            4 -> NoiseControlMode.ADAPTIVE
                            else -> NoiseControlMode.OFF
                        }
                    }
                    TechnicsPodsAction.ACTION_PODS_BATTERY_CHANGED -> {
                        p1.getParcelableExtra("status", BatteryParams::class.java)?.let {
                            batteryParams.value = it
                        }
                    }
                    TechnicsPodsAction.ACTION_PODS_CONNECTED -> {
                        deviceName.value = p1.getStringExtra("device_name") ?: ""
                        if (!showDialog.value) showDialog.value = true
                    }
                    TechnicsPodsAction.ACTION_PODS_DISCONNECTED -> {
                        showDialog.value = false
                    }
                    TechnicsPodsAction.ACTION_PODS_GAME_MODE_CHANGED -> {
                        gameMode.value = p1.getBooleanExtra("enabled", false)
                    }
                    TechnicsPodsAction.ACTION_PODS_ANC_LEVEL_CHANGED -> {
                        noiseCancelLevel.value = p1.getIntExtra(
                            TechnicsPodsAction.EXTRA_NOISE_CANCEL_LEVEL,
                            noiseCancelLevel.value
                        ).coerceIn(0, 100)
                        transparencyLevel.value = p1.getIntExtra(
                            TechnicsPodsAction.EXTRA_TRANSPARENCY_LEVEL,
                            transparencyLevel.value
                        ).coerceIn(0, 100)
                    }
                }
            }
        }
    }

    DisposableEffect(Unit) {
        context.registerReceiver(broadcastReceiver, IntentFilter().apply {
            addAction(TechnicsPodsAction.ACTION_PODS_ANC_CHANGED)
            addAction(TechnicsPodsAction.ACTION_PODS_BATTERY_CHANGED)
            addAction(TechnicsPodsAction.ACTION_PODS_CONNECTED)
            addAction(TechnicsPodsAction.ACTION_PODS_DISCONNECTED)
            addAction(TechnicsPodsAction.ACTION_PODS_GAME_MODE_CHANGED)
            addAction(TechnicsPodsAction.ACTION_PODS_ANC_LEVEL_CHANGED)
        }, Context.RECEIVER_EXPORTED)

        context.sendBroadcast(Intent(TechnicsPodsAction.ACTION_PODS_UI_INIT).apply {
            setPackage("com.android.bluetooth")
        })
        context.sendBroadcast(Intent(TechnicsPodsAction.ACTION_REFRESH_STATUS).apply {
            setPackage("com.android.bluetooth")
            putExtra(TechnicsPodsAction.EXTRA_ALLOW_RFCOMM_RECONNECT, true)
        })

        onDispose {
            try { context.unregisterReceiver(broadcastReceiver) } catch (_: Exception) {}
        }
    }

    // Timeout fallback: show dialog even if no response within 500ms
    // Periodic refresh: poll earbuds every 15s while popup is open
    LaunchedEffect(Unit) {
        delay(500)
        if (!showDialog.value) showDialog.value = true

        while (true) {
            delay(15_000)
            context.sendBroadcast(Intent(TechnicsPodsAction.ACTION_REFRESH_STATUS).apply {
                setPackage("com.android.bluetooth")
                putExtra(TechnicsPodsAction.EXTRA_ALLOW_RFCOMM_RECONNECT, true)
            })
        }
    }

    fun setAncMode(mode: NoiseControlMode) {
        ancMode.value = mode
        val status = when (mode) {
            NoiseControlMode.OFF -> 1
            NoiseControlMode.NOISE_CANCELLATION -> 2
            NoiseControlMode.TRANSPARENCY -> 3
            NoiseControlMode.ADAPTIVE -> 4
        }
        Intent(TechnicsPodsAction.ACTION_ANC_SELECT).apply {
            putExtra("status", status)
            setPackage("com.android.bluetooth")
            context.sendBroadcast(this)
        }
    }

    fun setGameMode(enabled: Boolean) {
        gameMode.value = enabled
        Intent(TechnicsPodsAction.ACTION_GAME_MODE_SET).apply {
            putExtra("enabled", enabled)
            setPackage("com.android.bluetooth")
            context.sendBroadcast(this)
        }
    }

    fun setAncLevels(nextNoiseCancelLevel: Int, nextTransparencyLevel: Int) {
        val safeNoiseCancelLevel = nextNoiseCancelLevel.coerceIn(0, 100)
        val safeTransparencyLevel = nextTransparencyLevel.coerceIn(0, 100)
        noiseCancelLevel.value = safeNoiseCancelLevel
        transparencyLevel.value = safeTransparencyLevel
        prefs.edit()
            .putInt(TechnicsPodsPrefsKey.NOISE_CANCEL_LEVEL, safeNoiseCancelLevel)
            .putInt(TechnicsPodsPrefsKey.TRANSPARENCY_LEVEL, safeTransparencyLevel)
            .apply()
        Intent(TechnicsPodsAction.ACTION_ANC_LEVEL_SET).apply {
            putExtra(TechnicsPodsAction.EXTRA_NOISE_CANCEL_LEVEL, safeNoiseCancelLevel)
            putExtra(TechnicsPodsAction.EXTRA_TRANSPARENCY_LEVEL, safeTransparencyLevel)
            setPackage("com.android.bluetooth")
            context.sendBroadcast(this)
        }
    }

    fun previewAncLevels(nextNoiseCancelLevel: Int, nextTransparencyLevel: Int) {
        noiseCancelLevel.value = nextNoiseCancelLevel.coerceIn(0, 100)
        transparencyLevel.value = nextTransparencyLevel.coerceIn(0, 100)
    }

    val dialogBgColor = if (isDarkMode) Color(0xFF1A1A1A) else Color(0xFFF7F7F7)
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    Scaffold(containerColor = Color.Transparent) { _ ->
        OverlayDialog(
            title = deviceName.value.ifEmpty { stringResource(R.string.app_name) },
            show = showDialog.value,
            backgroundColor = dialogBgColor,
            onDismissRequest = {
                showDialog.value = false
            },
            onDismissFinished = {
                onDone()
            }
        ) {
            if (isLandscape) {
                LandscapePopupBody(
                    batteryParams = batteryParams.value,
                    ancMode = ancMode.value,
                    noiseCancelLevel = noiseCancelLevel.value,
                    transparencyLevel = transparencyLevel.value,
                    gameMode = gameMode.value,
                    onAncModeChange = ::setAncMode,
                    onNoiseCancelLevelChange = {
                        previewAncLevels(it, transparencyLevel.value)
                    },
                    onTransparencyLevelChange = {
                        previewAncLevels(noiseCancelLevel.value, it)
                    },
                    onNoiseCancelLevelCommit = {
                        setAncLevels(it, transparencyLevel.value)
                    },
                    onTransparencyLevelCommit = {
                        setAncLevels(noiseCancelLevel.value, it)
                    },
                    onGameModeChange = ::setGameMode,
                    onMore = onMore,
                    onDone = { showDialog.value = false },
                    adaptiveModeEnabled = adaptiveModeEnabled
                )
            } else {
                PortraitPopupBody(
                    batteryParams = batteryParams.value,
                    ancMode = ancMode.value,
                    noiseCancelLevel = noiseCancelLevel.value,
                    transparencyLevel = transparencyLevel.value,
                    gameMode = gameMode.value,
                    onAncModeChange = ::setAncMode,
                    onNoiseCancelLevelChange = {
                        previewAncLevels(it, transparencyLevel.value)
                    },
                    onTransparencyLevelChange = {
                        previewAncLevels(noiseCancelLevel.value, it)
                    },
                    onNoiseCancelLevelCommit = {
                        setAncLevels(it, transparencyLevel.value)
                    },
                    onTransparencyLevelCommit = {
                        setAncLevels(noiseCancelLevel.value, it)
                    },
                    onGameModeChange = ::setGameMode,
                    onMore = onMore,
                    onDone = { showDialog.value = false },
                    adaptiveModeEnabled = adaptiveModeEnabled
                )
            }
        }
    }
}

@Composable
private fun PortraitPopupBody(
    batteryParams: BatteryParams,
    ancMode: NoiseControlMode,
    noiseCancelLevel: Int,
    transparencyLevel: Int,
    gameMode: Boolean,
    onAncModeChange: (NoiseControlMode) -> Unit,
    onNoiseCancelLevelChange: (Int) -> Unit,
    onTransparencyLevelChange: (Int) -> Unit,
    onNoiseCancelLevelCommit: (Int) -> Unit,
    onTransparencyLevelCommit: (Int) -> Unit,
    onGameModeChange: (Boolean) -> Unit,
    onMore: () -> Unit,
    onDone: () -> Unit,
    adaptiveModeEnabled: Boolean = true
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Card(modifier = Modifier.fillMaxWidth()) {
            TechnicsDeviceBatteryStatus(
                batteryParams,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 16.dp)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            AncSwitch(ancMode, onAncModeChange = onAncModeChange, adaptiveModeEnabled = adaptiveModeEnabled)
        }
        Spacer(modifier = Modifier.height(12.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            AncLevelKnobs(
                noiseCancelLevel = noiseCancelLevel,
                transparencyLevel = transparencyLevel,
                onNoiseCancelLevelChange = onNoiseCancelLevelChange,
                onTransparencyLevelChange = onTransparencyLevelChange,
                onNoiseCancelLevelCommit = onNoiseCancelLevelCommit,
                onTransparencyLevelCommit = onTransparencyLevelCommit
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            SwitchPreference(
                title = stringResource(R.string.game_mode),
                summary = stringResource(R.string.game_mode_summary),
                checked = gameMode,
                onCheckedChange = onGameModeChange
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TextButton(
                text = stringResource(R.string.more),
                onClick = onMore,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                text = stringResource(R.string.done),
                onClick = onDone,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun LandscapePopupBody(
    batteryParams: BatteryParams,
    ancMode: NoiseControlMode,
    noiseCancelLevel: Int,
    transparencyLevel: Int,
    gameMode: Boolean,
    onAncModeChange: (NoiseControlMode) -> Unit,
    onNoiseCancelLevelChange: (Int) -> Unit,
    onTransparencyLevelChange: (Int) -> Unit,
    onNoiseCancelLevelCommit: (Int) -> Unit,
    onTransparencyLevelCommit: (Int) -> Unit,
    onGameModeChange: (Boolean) -> Unit,
    onMore: () -> Unit,
    onDone: () -> Unit,
    adaptiveModeEnabled: Boolean = true
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(modifier = Modifier.weight(0.60f)) {
            Card(modifier = Modifier.fillMaxWidth()) {
                TechnicsDeviceBatteryStatus(
                    batteryParams,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                    compact = true
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                AncSwitch(
                    ancMode,
                    onAncModeChange = onAncModeChange,
                    compact = true,
                    adaptiveModeEnabled = adaptiveModeEnabled
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                AncLevelKnobs(
                    noiseCancelLevel = noiseCancelLevel,
                    transparencyLevel = transparencyLevel,
                    onNoiseCancelLevelChange = onNoiseCancelLevelChange,
                    onTransparencyLevelChange = onTransparencyLevelChange,
                    onNoiseCancelLevelCommit = onNoiseCancelLevelCommit,
                    onTransparencyLevelCommit = onTransparencyLevelCommit,
                    compact = true
                )
            }
        }
        Column(
            modifier = Modifier.weight(0.40f).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            TextButton(
                text = stringResource(
                    if (gameMode) R.string.disable_game_mode else R.string.enable_game_mode
                ),
                onClick = { onGameModeChange(!gameMode) },
                modifier = Modifier.fillMaxWidth().weight(1f)
            )
            TextButton(
                text = stringResource(R.string.more),
                onClick = onMore,
                modifier = Modifier.fillMaxWidth().weight(1f)
            )
            TextButton(
                text = stringResource(R.string.done),
                onClick = onDone,
                modifier = Modifier.fillMaxWidth().weight(1f)
            )
        }
    }
}
