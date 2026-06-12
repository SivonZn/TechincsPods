package cn.martinkay.technicspods.utils

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice

object TechnicsDeviceMatcher {
    private val nameKeywords = listOf(
        "technics",
        "eah-az",
        "az100",
        "az80",
        "az70",
        "az60",
        "az40",
        "az30",
        "eah-a800"
    )

    fun isTechnicsName(name: String?): Boolean {
        val normalized = name?.lowercase().orEmpty()
        return nameKeywords.any { normalized.contains(it) }
    }

    @SuppressLint("MissingPermission")
    fun isTechnicsDevice(device: BluetoothDevice?): Boolean {
        if (device == null) return false
        return isTechnicsName(runCatching { device.name ?: device.alias }.getOrNull())
    }
}
