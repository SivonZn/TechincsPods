package cn.martinkay.technicspods.utils.miuiStrongToast.data

object TechnicsPodsAction {
    const val ACTION_PODS_UI_INIT = "chen.action.technicspods.ui_init"
    const val ACTION_PODS_CONNECTED = "chen.action.technicspods.pods_connected"
    const val ACTION_PODS_DISCONNECTED = "chen.action.technicspods.pods_disconnected"
    const val ACTION_PODS_BATTERY_CHANGED = "chen.action.technicspods.pods_battery_changed"
    const val ACTION_ANC_SELECT = "chen.action.technicspods.anc_select"
    const val ACTION_PODS_ANC_CHANGED = "chen.action.technicspods.pods_anc_select"
    const val ACTION_GET_PODS_MAC = "chen.action.technicspods.get_pods_mac"
    const val ACTION_PODS_MAC_RECEIVED = "chen.action.technicspods.pods_mac_received"
    const val ACTION_REFRESH_STATUS = "chen.action.technicspods.refresh_status"
    const val ACTION_GAME_MODE_SET = "chen.action.technicspods.game_mode_set"
    const val ACTION_PODS_GAME_MODE_CHANGED = "chen.action.technicspods.pods_game_mode_changed"
    const val ACTION_CYCLE_ANC = "chen.action.technicspods.cycle_anc"
    const val ACTION_GAME_MODE_IMPLEMENTATION_CHANGED = "chen.action.technicspods.game_mode_implementation_changed"
    // Adaptive模式开关状态变更广播，用于跨进程同步偏好设置（App → com.android.bluetooth / com.xiaomi.bluetooth）
    const val ACTION_ADAPTIVE_MODE_CHANGED = "chen.action.technicspods.adaptive_mode_changed"
    const val ACTION_NOTIFICATION_SETTINGS_CHANGED = "chen.action.technicspods.notification_settings_changed"

    const val EXTRA_ALLOW_RFCOMM_RECONNECT = "allow_rfcomm_reconnect"
    const val EXTRA_RFCOMM_CONNECTED = "rfcomm_connected"
}
