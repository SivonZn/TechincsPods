package cn.martinkay.technicspods.pods

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import cn.martinkay.technicspods.BuildConfig
import cn.martinkay.technicspods.utils.miuiStrongToast.data.BatteryParams
import cn.martinkay.technicspods.utils.miuiStrongToast.data.PodParams
import java.io.IOException
import java.io.InputStream

/**
 * Standalone RFCOMM controller for direct use from the app process.
 * Does not depend on the hook runtime.
 */
@SuppressLint("MissingPermission")
class AppRfcommController {
    companion object {
        private const val TAG = "TechnicsPods-AppRfcomm"
        private const val BATTERY_POLL_INTERVAL_MS = 30_000L
    }

    enum class ConnectionState {
        DISCONNECTED, CONNECTING, CONNECTED, ERROR
    }

    private var socket: BluetoothSocket? = null
    private var isConnected = false
    private var gameModeImplementation = GameModeImplementation.STANDARD
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var batteryPollJob: Job? = null

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _batteryParams = MutableStateFlow(BatteryParams())
    val batteryParams: StateFlow<BatteryParams> = _batteryParams

    private val _ancMode = MutableStateFlow(NoiseControlMode.OFF)
    val ancMode: StateFlow<NoiseControlMode> = _ancMode

    private val _noiseCancelLevel = MutableStateFlow(100)
    val noiseCancelLevel: StateFlow<Int> = _noiseCancelLevel

    private val _transparencyLevel = MutableStateFlow(50)
    val transparencyLevel: StateFlow<Int> = _transparencyLevel

    private val _deviceName = MutableStateFlow("")
    val deviceName: StateFlow<String> = _deviceName

    private val _gameMode = MutableStateFlow(false)
    val gameMode: StateFlow<Boolean> = _gameMode

    fun connect(
        device: BluetoothDevice,
        connectionMethod: RfcommConnectionMethod = RfcommConnectionMethod.UUID,
        gameModeImplementation: GameModeImplementation = GameModeImplementation.STANDARD
    ) {
        if (_connectionState.value == ConnectionState.CONNECTING) return

        this.gameModeImplementation = gameModeImplementation
        _deviceName.value = device.name ?: device.address
        _connectionState.value = ConnectionState.CONNECTING
        batteryPollJob?.cancel()

        scope.launch {
            try {
                delay(300)
                socket = TechnicsRfcommSocketFactory.connect(device, TAG, connectionMethod)
                Log.d(TAG, "RFCOMM connected to ${device.name}")
                isConnected = true
                _connectionState.value = ConnectionState.CONNECTED

                startPacketReader(socket!!.inputStream)

                delay(300)
                queryStatus()

                startBatteryPolling()
            } catch (e: IOException) {
                Log.e(TAG, "RFCOMM connect failed", e)
                _connectionState.value = ConnectionState.ERROR
                isConnected = false
                batteryPollJob?.cancel()
            }
        }
    }

    private fun startBatteryPolling() {
        batteryPollJob?.cancel()
        batteryPollJob = scope.launch {
            while (isConnected) {
                delay(BATTERY_POLL_INTERVAL_MS)
                if (isConnected) queryStatus()
            }
        }
    }

    private fun startPacketReader(inputStream: InputStream) {
        scope.launch {
            val buffer = ByteArray(1024)
            val framer = TechnicsPacketFramer()
            try {
                while (isConnected) {
                    val bytesRead = inputStream.read(buffer)
                    if (bytesRead > 0) {
                        framer.append(buffer, bytesRead).forEach { packet ->
                            handlePacket(packet)
                        }
                    } else if (bytesRead == -1) {
                        break
                    }
                }
            } catch (e: IOException) {
                if (isConnected) Log.e(TAG, "Read error", e)
            }
            if (isConnected) disconnect()
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun handlePacket(packet: ByteArray) {
        if (BuildConfig.DEBUG) {
            Log.v(TAG, "Received: ${packet.toHexString(HexFormat.UpperCase)}")
        }

        val result = TechnicsBatteryParser.parse(packet)
        if (result != null) {
            val current = _batteryParams.value
            val left = result.left?.let {
                PodParams(it.level, it.isCharging, true, current.left?.rawStatus ?: 0)
            } ?: current.left ?: PodParams()
            val right = result.right?.let {
                PodParams(it.level, it.isCharging, true, current.right?.rawStatus ?: 0)
            } ?: current.right ?: PodParams()
            val case = result.case?.let {
                PodParams(it.level, it.isCharging, true, current.case?.rawStatus ?: 0)
            } ?: current.case ?: PodParams()
            _batteryParams.value = BatteryParams(left, right, case)
            return
        }

        val ancResult = TechnicsAncParser.parse(packet)
        if (ancResult != null) {
            _ancMode.value = intToNoiseControlMode(ancResult)
            return
        }
    }

    private fun sendPacket(packet: ByteArray) {
        try {
            socket?.outputStream?.write(packet)
            socket?.outputStream?.flush()
        } catch (e: IOException) {
            Log.e(TAG, "Send failed", e)
        }
    }

    fun setGameMode(enabled: Boolean) {
        _gameMode.value = enabled
        Log.d(TAG, "setGameMode ignored: Technics game-mode protocol is not implemented")
    }

    fun setGameModeImplementation(implementation: GameModeImplementation) {
        gameModeImplementation = implementation
    }

    fun setANCMode(mode: NoiseControlMode) {
        _ancMode.value = mode
        scope.launch {
            TechnicsPackets.setAncModeSequence(
                noiseControlModeToInt(mode),
                _noiseCancelLevel.value,
                _transparencyLevel.value
            ).forEachIndexed { index, packet ->
                sendPacket(packet)
                Log.d(TAG, "setANCMode sent step ${index + 1} for $mode")
                delay(80)
            }
        }
    }

    fun setAncLevels(noiseCancelLevel: Int, transparencyLevel: Int) {
        _noiseCancelLevel.value = noiseCancelLevel.coerceIn(0, 100)
        _transparencyLevel.value = transparencyLevel.coerceIn(0, 100)
        scope.launch {
            val packet = when (_ancMode.value) {
                NoiseControlMode.NOISE_CANCELLATION,
                NoiseControlMode.ADAPTIVE -> TechnicsPackets.setNoiseCancelLevel(
                    _noiseCancelLevel.value,
                    _transparencyLevel.value
                )
                NoiseControlMode.TRANSPARENCY -> TechnicsPackets.setTransparencyLevel(
                    _noiseCancelLevel.value,
                    _transparencyLevel.value
                )
                NoiseControlMode.OFF -> null
            } ?: return@launch
            sendPacket(packet)
        }
    }

    private fun noiseControlModeToInt(mode: NoiseControlMode): Int {
        return when (mode) {
            NoiseControlMode.OFF -> 1
            NoiseControlMode.NOISE_CANCELLATION -> 2
            NoiseControlMode.TRANSPARENCY -> 3
            NoiseControlMode.ADAPTIVE -> 4
        }
    }

    private fun intToNoiseControlMode(mode: Int): NoiseControlMode {
        return when (mode) {
            2 -> NoiseControlMode.NOISE_CANCELLATION
            3 -> NoiseControlMode.TRANSPARENCY
            4 -> NoiseControlMode.ADAPTIVE
            else -> NoiseControlMode.OFF
        }
    }

    /**
     * Technics battery query strategy: agent side, client side, then cradle.
     */
    private fun queryStatus() {
        scope.launch {
            sendPacket(TechnicsPackets.QUERY_AGENT_BATTERY)
            delay(80)
            sendPacket(TechnicsPackets.QUERY_CLIENT_BATTERY)
            delay(80)
            sendPacket(TechnicsPackets.QUERY_CRADLE_BATTERY)
        }
    }

    /**
     * Public method for UI refresh button.
     */
    fun refreshStatus() {
        if (!isConnected) return
        queryStatus()
    }

    fun disconnect() {
        isConnected = false
        batteryPollJob?.cancel()
        try { socket?.close() } catch (_: IOException) {}
        socket = null
        _connectionState.value = ConnectionState.DISCONNECTED
        _batteryParams.value = BatteryParams()
        _ancMode.value = NoiseControlMode.OFF
        _noiseCancelLevel.value = 100
        _transparencyLevel.value = 50
        _deviceName.value = ""
        _gameMode.value = false
    }
}
