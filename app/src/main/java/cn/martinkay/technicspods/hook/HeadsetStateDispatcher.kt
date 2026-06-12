package cn.martinkay.technicspods.hook

import android.annotation.SuppressLint
import android.app.StatusBarManager
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.content.ContextWrapper
import android.os.Handler
import android.util.Log
import cn.martinkay.technicspods.pods.RfcommController
import cn.martinkay.technicspods.utils.SystemApisUtils.setIconVisibility
import cn.martinkay.technicspods.utils.TechnicsDeviceMatcher

object HeadsetStateDispatcher : HookContext() {

    override fun onHook() {
        hookAfter(findMethodByParamCount("com.android.bluetooth.a2dp.A2dpService", "handleConnectionStateChanged", 3)) {
            val currState = args[2] as Int
            val fromState = args[1] as Int
            val device = args[0] as BluetoothDevice?
            val handler = getObjectField(instance, "mHandler") as Handler
            if (device == null || currState == fromState) {
                return@hookAfter
            }
            handler.post {
                Log.d("TechnicsPods", "A2DP Connection State: $currState, isTechnicsPod ${isOppoPod(device)}")
                val context = instance as ContextWrapper
                if (!isOppoPod(device)) return@post

                val statusBarManager = context.getSystemService("statusbar") as StatusBarManager
                if (currState == BluetoothHeadset.STATE_CONNECTED) {
                    statusBarManager.setIconVisibility("wireless_headset", true)
                    RfcommController.connectPod(context, device, prefs)
                } else if (currState == BluetoothHeadset.STATE_DISCONNECTING || currState == BluetoothHeadset.STATE_DISCONNECTED) {
                    statusBarManager.setIconVisibility("wireless_headset", false)
                    RfcommController.disconnectedPod(context, device)
                }
            }
        }
    }

    /**
     * Detect Technics earphones by checking known Technics model keywords.
     */
    @SuppressLint("MissingPermission")
    fun isOppoPod(device: BluetoothDevice): Boolean {
        val name = device.name ?: return false
        return TechnicsDeviceMatcher.isTechnicsName(name)
    }
}
