package cn.martinkay.technicspods.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cn.martinkay.technicspods.R
import cn.martinkay.technicspods.pods.NoiseControlMode
import cn.martinkay.technicspods.ui.components.AncLevelKnobs
import cn.martinkay.technicspods.ui.components.AncSwitch
import cn.martinkay.technicspods.ui.components.PodStatus
import cn.martinkay.technicspods.utils.miuiStrongToast.data.BatteryParams
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.preference.SwitchPreference

@Composable
fun PodDetailPage(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    batteryParams: BatteryParams,
    ancMode: NoiseControlMode,
    onAncModeChange: (NoiseControlMode) -> Unit,
    noiseCancelLevel: Int = 100,
    transparencyLevel: Int = 50,
    onNoiseCancelLevelChange: (Int) -> Unit = {},
    onTransparencyLevelChange: (Int) -> Unit = {},
    onNoiseCancelLevelCommit: (Int) -> Unit = onNoiseCancelLevelChange,
    onTransparencyLevelCommit: (Int) -> Unit = onTransparencyLevelChange,
    gameMode: Boolean = false,
    onGameModeChange: (Boolean) -> Unit = {},
    adaptiveModeEnabled: Boolean = true
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Card(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 16.dp)
            ) {
                PodStatus(batteryParams, modifier = Modifier.padding(horizontal = 12.dp, vertical = 16.dp))
            }
        }

        item {
            Card(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)
            ) {
                AncSwitch(ancMode, onAncModeChange, adaptiveModeEnabled = adaptiveModeEnabled)
            }
        }

        item {
            Card(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)
            ) {
                AncLevelKnobs(
                    noiseCancelLevel = noiseCancelLevel,
                    transparencyLevel = transparencyLevel,
                    onNoiseCancelLevelChange = onNoiseCancelLevelChange,
                    onTransparencyLevelChange = onTransparencyLevelChange,
                    onNoiseCancelLevelCommit = onNoiseCancelLevelCommit,
                    onTransparencyLevelCommit = onTransparencyLevelCommit
                )
            }
        }

        item {
            Card(
                modifier = Modifier.padding(horizontal = 12.dp)
            ) {
                SwitchPreference(
                    title = stringResource(R.string.game_mode),
                    summary = stringResource(R.string.game_mode_summary),
                    checked = gameMode,
                    onCheckedChange = onGameModeChange
                )
            }
        }
    }
}
