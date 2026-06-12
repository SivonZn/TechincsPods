package cn.martinkay.technicspods.utils.miuiStrongToast.data

import android.content.Intent
import android.content.SharedPreferences

data class NotificationSettings(
    val showConnectionBatteryIsland: Boolean = TechnicsPodsPrefsKey.DEFAULT_SHOW_CONNECTION_BATTERY_ISLAND,
    val showConnectionPopup: Boolean = TechnicsPodsPrefsKey.DEFAULT_SHOW_CONNECTION_POPUP,
    val connectionPopupDismissSeconds: Int = TechnicsPodsPrefsKey.DEFAULT_CONNECTION_POPUP_DISMISS_SECONDS,
    val showConnectionNotification: Boolean = TechnicsPodsPrefsKey.DEFAULT_SHOW_CONNECTION_NOTIFICATION,
    val notificationIslandStyle: Boolean = TechnicsPodsPrefsKey.DEFAULT_NOTIFICATION_ISLAND_STYLE
) {
    val showNotificationAsIsland: Boolean
        get() = showConnectionNotification && notificationIslandStyle

    fun putExtras(intent: Intent) {
        intent.putExtra(TechnicsPodsPrefsKey.SHOW_CONNECTION_BATTERY_ISLAND, showConnectionBatteryIsland)
        intent.putExtra(TechnicsPodsPrefsKey.SHOW_CONNECTION_POPUP, showConnectionPopup)
        intent.putExtra(TechnicsPodsPrefsKey.CONNECTION_POPUP_DISMISS_SECONDS, connectionPopupDismissSeconds)
        intent.putExtra(TechnicsPodsPrefsKey.SHOW_CONNECTION_NOTIFICATION, showConnectionNotification)
        intent.putExtra(TechnicsPodsPrefsKey.NOTIFICATION_ISLAND_STYLE, notificationIslandStyle)
    }

    companion object {
        fun fromPrefs(prefs: SharedPreferences): NotificationSettings {
            return NotificationSettings(
                showConnectionBatteryIsland = prefs.getBoolean(
                    TechnicsPodsPrefsKey.SHOW_CONNECTION_BATTERY_ISLAND,
                    TechnicsPodsPrefsKey.DEFAULT_SHOW_CONNECTION_BATTERY_ISLAND
                ),
                showConnectionPopup = prefs.getBoolean(
                    TechnicsPodsPrefsKey.SHOW_CONNECTION_POPUP,
                    TechnicsPodsPrefsKey.DEFAULT_SHOW_CONNECTION_POPUP
                ),
                connectionPopupDismissSeconds = prefs.getInt(
                    TechnicsPodsPrefsKey.CONNECTION_POPUP_DISMISS_SECONDS,
                    TechnicsPodsPrefsKey.DEFAULT_CONNECTION_POPUP_DISMISS_SECONDS
                ).normalizedPopupDismissSeconds(),
                showConnectionNotification = prefs.getBoolean(
                    TechnicsPodsPrefsKey.SHOW_CONNECTION_NOTIFICATION,
                    TechnicsPodsPrefsKey.DEFAULT_SHOW_CONNECTION_NOTIFICATION
                ),
                notificationIslandStyle = prefs.getBoolean(
                    TechnicsPodsPrefsKey.NOTIFICATION_ISLAND_STYLE,
                    TechnicsPodsPrefsKey.DEFAULT_NOTIFICATION_ISLAND_STYLE
                )
            )
        }

        fun fromIntent(intent: Intent?, fallback: NotificationSettings): NotificationSettings {
            if (intent == null) return fallback
            return NotificationSettings(
                showConnectionBatteryIsland = intent.getBooleanExtra(
                    TechnicsPodsPrefsKey.SHOW_CONNECTION_BATTERY_ISLAND,
                    fallback.showConnectionBatteryIsland
                ),
                showConnectionPopup = intent.getBooleanExtra(
                    TechnicsPodsPrefsKey.SHOW_CONNECTION_POPUP,
                    fallback.showConnectionPopup
                ),
                connectionPopupDismissSeconds = intent.getIntExtra(
                    TechnicsPodsPrefsKey.CONNECTION_POPUP_DISMISS_SECONDS,
                    fallback.connectionPopupDismissSeconds
                ).normalizedPopupDismissSeconds(),
                showConnectionNotification = intent.getBooleanExtra(
                    TechnicsPodsPrefsKey.SHOW_CONNECTION_NOTIFICATION,
                    fallback.showConnectionNotification
                ),
                notificationIslandStyle = intent.getBooleanExtra(
                    TechnicsPodsPrefsKey.NOTIFICATION_ISLAND_STYLE,
                    fallback.notificationIslandStyle
                )
            )
        }

        private fun Int.normalizedPopupDismissSeconds(): Int {
            return if (this in TechnicsPodsPrefsKey.CONNECTION_POPUP_DISMISS_SECOND_OPTIONS) {
                this
            } else {
                TechnicsPodsPrefsKey.DEFAULT_CONNECTION_POPUP_DISMISS_SECONDS
            }
        }
    }
}
