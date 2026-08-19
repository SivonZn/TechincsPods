package cn.martinkay.technicspods.utils.miuiStrongToast.data

object TechnicsPodsPrefsKey {
    const val EAR_DETECTION = "ear_detection"
    const val EAR_DETECTION_SWITCH_SPEAKER = "ear_detection_switch_speaker"
    const val SHOW_CONNECTION_BATTERY_ISLAND = "show_connection_battery_island"
    const val SHOW_CONNECTION_POPUP = "show_connection_popup"
    const val CONNECTION_POPUP_DISMISS_SECONDS = "connection_popup_dismiss_seconds"
    const val SHOW_CONNECTION_NOTIFICATION = "show_connection_notification"
    const val NOTIFICATION_ISLAND_STYLE = "notification_island_style"
    const val NOISE_CANCEL_LEVEL = "noise_cancel_level"
    const val TRANSPARENCY_LEVEL = "transparency_level"
    const val HIDE_LAUNCHER_ICON = "hide_launcher_icon"

    const val DEFAULT_SHOW_CONNECTION_BATTERY_ISLAND = true
    const val DEFAULT_SHOW_CONNECTION_POPUP = false
    const val DEFAULT_CONNECTION_POPUP_DISMISS_SECONDS = 8
    val CONNECTION_POPUP_DISMISS_SECOND_OPTIONS = listOf(3, 5, 8, 10, 15, 30)
    const val DEFAULT_SHOW_CONNECTION_NOTIFICATION = true
    const val DEFAULT_NOTIFICATION_ISLAND_STYLE = false
    const val DEFAULT_NOISE_CANCEL_LEVEL = 100
    const val DEFAULT_TRANSPARENCY_LEVEL = 50
}
