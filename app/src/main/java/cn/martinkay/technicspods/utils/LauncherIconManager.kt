package cn.martinkay.technicspods.utils

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.edit
import cn.martinkay.technicspods.utils.miuiStrongToast.data.TechnicsPodsPrefsKey

object LauncherIconManager {
    private const val TAG = "TechnicsPods-Launcher"
    private const val PREFS_NAME = "technicspods_settings"

    fun sync(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        setHidden(
            context = context,
            hidden = prefs.getBoolean(TechnicsPodsPrefsKey.HIDE_LAUNCHER_ICON, false),
            persist = false
        )
    }

    fun isHidden(context: Context): Boolean {
        return when (context.packageManager.getComponentEnabledSetting(componentName(context))) {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED -> true
            else -> false
        }
    }

    fun setHidden(context: Context, hidden: Boolean, persist: Boolean = true): Boolean {
        return try {
            context.packageManager.setComponentEnabledSetting(
                componentName(context),
                if (hidden) {
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                } else {
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                },
                PackageManager.DONT_KILL_APP
            )
            if (persist) {
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit { putBoolean(TechnicsPodsPrefsKey.HIDE_LAUNCHER_ICON, hidden) }
            }
            true
        } catch (e: RuntimeException) {
            Log.e(TAG, "Failed to update launcher icon visibility", e)
            false
        }
    }

    private fun componentName(context: Context): ComponentName {
        return ComponentName(context.packageName, "${context.packageName}.LauncherActivity")
    }
}
