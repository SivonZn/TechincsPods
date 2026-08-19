package cn.martinkay.technicspods.pods

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.os.SystemClock
import android.util.Log
import cn.martinkay.technicspods.BuildConfig
import cn.martinkay.technicspods.utils.miuiStrongToast.data.BatteryParams
import cn.martinkay.technicspods.utils.miuiStrongToast.data.PodParams
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Standalone RFCOMM controller for direct use from the app process.
 * Does not depend on the hook runtime.
 */
@SuppressLint("MissingPermission")
class AppRfcommController {
    companion object {
        private const val TAG = "TechnicsPods-AppRfcomm"
        private const val BATTERY_POLL_INTERVAL_MS = 30_000L
        private const val PACKET_STEP_DELAY_MS = 80L
        private const val ANC_MODE_CONFIRM_DELAY_MS = 200L
        private const val ANC_RESPONSE_SETTLE_MS = 300L
        private const val ANC_MODE_SETTLE_GUARD_MS = 2_000L
    }

    enum class ConnectionState {
        DISCONNECTED, CONNECTING, CONNECTED, ERROR
    }

    private data class PendingAncSync(
        val generation: Long,
        var outsideMode: NoiseControlMode? = null,
        var adaptiveEnabled: Boolean? = null
    )

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val socketLock = Any()
    private val ancStateLock = Any()
    private val operationMutex = Mutex()

    private var socket: BluetoothSocket? = null
    private var connectionGeneration = 0L
    @Volatile
    private var isConnected = false
    private var gameModeImplementation = GameModeImplementation.STANDARD
    private var lastDevice: BluetoothDevice? = null
    private var lastConnectionMethod = RfcommConnectionMethod.UUID

    private var connectJob: Job? = null
    private var batteryPollJob: Job? = null
    private var statusQueryJob: Job? = null
    private var ancModeJob: Job? = null
    private var ancLevelJob: Job? = null

    private var ancSyncGeneration = 0L
    private var pendingAncSync: PendingAncSync? = null
    private var lastOutsideMode: NoiseControlMode? = null
    private var lastAdaptiveEnabled: Boolean? = null
    @Volatile
    private var currentAncSynced = false
    private var guardedAncMode: NoiseControlMode? = null
    private var guardedAncModeUntilMs = 0L

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

        closeConnection(resetUi = false)
        this.gameModeImplementation = gameModeImplementation
        lastDevice = device
        lastConnectionMethod = connectionMethod
        _deviceName.value = device.name ?: device.address
        _connectionState.value = ConnectionState.CONNECTING

        val generation = synchronized(socketLock) {
            connectionGeneration += 1
            connectionGeneration
        }
        connectJob = scope.launch {
            try {
                delay(300)
                val connectedSocket = TechnicsRfcommSocketFactory.connect(
                    device,
                    TAG,
                    connectionMethod
                )
                val accepted = synchronized(socketLock) {
                    if (connectionGeneration != generation) {
                        false
                    } else {
                        socket = connectedSocket
                        isConnected = true
                        true
                    }
                }
                if (!accepted) {
                    try {
                        connectedSocket.close()
                    } catch (_: IOException) {
                    }
                    return@launch
                }

                Log.d(TAG, "RFCOMM connected to ${device.name}")
                _connectionState.value = ConnectionState.CONNECTED
                startPacketReader(connectedSocket, generation, connectedSocket.inputStream)
                queryStatus()
                startBatteryPolling()
            } catch (_: CancellationException) {
                // An explicit disconnect or a newer connection attempt owns the state now.
            } catch (e: IOException) {
                Log.e(TAG, "RFCOMM connect failed", e)
                markConnectionFailed(generation)
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

    private fun startPacketReader(
        readerSocket: BluetoothSocket,
        generation: Long,
        inputStream: InputStream
    ) {
        scope.launch {
            val buffer = ByteArray(1024)
            val framer = TechnicsPacketFramer()
            try {
                while (isActiveSocket(readerSocket, generation)) {
                    val bytesRead = inputStream.read(buffer)
                    if (bytesRead <= 0) break
                    framer.append(buffer, bytesRead).forEach { packet ->
                        try {
                            handlePacket(packet)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to handle Technics packet", e)
                        }
                    }
                }
            } catch (e: IOException) {
                if (isActiveSocket(readerSocket, generation)) {
                    Log.e(TAG, "RFCOMM read failed", e)
                }
            }

            if (isActiveSocket(readerSocket, generation)) {
                markConnectionLost(readerSocket, generation)
            }
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun handlePacket(packet: ByteArray) {
        if (BuildConfig.DEBUG) {
            Log.v(TAG, "Received: ${packet.toHexString(HexFormat.UpperCase)}")
        }

        TechnicsBatteryParser.parse(packet)?.let {
            handleBatteryChanged(it)
            return
        }

        TechnicsAncParser.parse(packet)?.let {
            handleAncChanged(it)
            return
        }
    }

    private fun handleBatteryChanged(result: TechnicsBatteryParser.BatteryResult) {
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
    }

    private fun handleAncChanged(result: TechnicsAncParser.AncResult) {
        result.noiseCancelLevel?.let {
            _noiseCancelLevel.value = it.coerceIn(0, 100)
        }
        result.transparencyLevel?.let {
            _transparencyLevel.value = it.coerceIn(0, 100)
        }

        val resolvedMode = synchronized(ancStateLock) {
            result.outsideMode?.let { mode ->
                intToNoiseControlMode(mode).also {
                    lastOutsideMode = it
                    pendingAncSync?.outsideMode = it
                }
            }
            result.adaptiveEnabled?.let { enabled ->
                lastAdaptiveEnabled = enabled
                pendingAncSync?.adaptiveEnabled = enabled
            }

            val pending = pendingAncSync
            if (pending != null) {
                val outsideMode = pending.outsideMode
                val adaptiveEnabled = pending.adaptiveEnabled
                if (outsideMode != null && adaptiveEnabled != null) {
                    pendingAncSync = null
                    resolveAncMode(outsideMode, adaptiveEnabled)
                } else {
                    null
                }
            } else {
                val outsideMode = lastOutsideMode
                val adaptiveEnabled = lastAdaptiveEnabled
                if (outsideMode != null && adaptiveEnabled != null) {
                    resolveAncMode(outsideMode, adaptiveEnabled)
                } else {
                    null
                }
            }
        }
        resolvedMode?.let(::publishAncMode)
    }

    private fun resolveAncMode(
        outsideMode: NoiseControlMode,
        adaptiveEnabled: Boolean
    ): NoiseControlMode {
        return if (adaptiveEnabled) NoiseControlMode.ADAPTIVE else outsideMode
    }

    private fun publishAncMode(mode: NoiseControlMode) {
        if (!shouldAcceptAncMode(mode)) {
            Log.d(TAG, "Ignored stale ANC mode during transition: received=$mode target=${guardedAncMode()}")
            return
        }
        currentAncSynced = true
        _ancMode.value = mode
    }

    private fun beginAncSync(): Long = synchronized(ancStateLock) {
        ancSyncGeneration += 1
        pendingAncSync = PendingAncSync(ancSyncGeneration)
        ancSyncGeneration
    }

    private fun finishAncSync(generation: Long) {
        val fallbackMode = synchronized(ancStateLock) {
            val pending = pendingAncSync?.takeIf { it.generation == generation }
                ?: return@synchronized null
            pendingAncSync = null
            when {
                pending.outsideMode != null && pending.adaptiveEnabled != null -> {
                    resolveAncMode(pending.outsideMode!!, pending.adaptiveEnabled!!)
                }
                !currentAncSynced && pending.outsideMode != null -> pending.outsideMode
                pending.adaptiveEnabled == true -> NoiseControlMode.ADAPTIVE
                pending.adaptiveEnabled == false && lastOutsideMode != null -> lastOutsideMode
                else -> null
            }
        }
        fallbackMode?.let(::publishAncMode)
    }

    private fun discardAncSync(generation: Long) {
        synchronized(ancStateLock) {
            if (pendingAncSync?.generation == generation) pendingAncSync = null
        }
    }

    private fun guardAncMode(mode: NoiseControlMode) {
        synchronized(ancStateLock) {
            guardedAncMode = mode
            guardedAncModeUntilMs = SystemClock.elapsedRealtime() + ANC_MODE_SETTLE_GUARD_MS
        }
    }

    private fun guardedAncMode(): NoiseControlMode? = synchronized(ancStateLock) {
        guardedAncMode
    }

    private fun shouldAcceptAncMode(mode: NoiseControlMode): Boolean {
        return synchronized(ancStateLock) {
            val target = guardedAncMode ?: return@synchronized true
            if (SystemClock.elapsedRealtime() >= guardedAncModeUntilMs) {
                guardedAncMode = null
                guardedAncModeUntilMs = 0L
                true
            } else {
                mode == target
            }
        }
    }

    private fun sendPacket(packet: ByteArray): Boolean {
        val targetSocket = synchronized(socketLock) {
            socket?.takeIf { isConnected }
        } ?: return false

        return try {
            targetSocket.outputStream.write(packet)
            targetSocket.outputStream.flush()
            true
        } catch (e: IOException) {
            Log.e(TAG, "RFCOMM send failed", e)
            val generation = synchronized(socketLock) { connectionGeneration }
            markConnectionLost(targetSocket, generation)
            false
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
        if (!isConnected) return

        guardAncMode(mode)
        currentAncSynced = true
        _ancMode.value = mode
        statusQueryJob?.cancel()
        ancModeJob?.cancel()
        ancLevelJob?.cancel()
        ancModeJob = scope.launch {
            operationMutex.withLock {
                val packets = TechnicsPackets.setAncModeSequence(
                    noiseControlModeToInt(mode),
                    _noiseCancelLevel.value,
                    _transparencyLevel.value
                )
                packets.forEachIndexed { index, packet ->
                    if (!sendPacket(packet)) return@withLock
                    Log.d(TAG, "setANCMode sent step ${index + 1} for $mode")
                    delay(PACKET_STEP_DELAY_MS)
                }
                delay(ANC_MODE_CONFIRM_DELAY_MS)
                sendAncStatusQueryPackets()
            }
        }
    }

    fun setAncLevels(noiseCancelLevel: Int, transparencyLevel: Int) {
        _noiseCancelLevel.value = noiseCancelLevel.coerceIn(0, 100)
        _transparencyLevel.value = transparencyLevel.coerceIn(0, 100)
        if (!isConnected) return

        ancLevelJob?.cancel()
        ancLevelJob = scope.launch {
            operationMutex.withLock {
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
                } ?: return@withLock
                if (!sendPacket(packet)) return@withLock
                delay(PACKET_STEP_DELAY_MS)
                sendAncStatusQueryPackets()
            }
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

    private fun queryStatus() {
        if (!isConnected || statusQueryJob?.isActive == true) return
        statusQueryJob = scope.launch {
            operationMutex.withLock {
                if (!isConnected) return@withLock
                if (!sendAncStatusQueryPackets()) return@withLock
                if (!sendPacket(TechnicsPackets.QUERY_AGENT_BATTERY)) return@withLock
                delay(PACKET_STEP_DELAY_MS)
                if (!sendPacket(TechnicsPackets.QUERY_CLIENT_BATTERY)) return@withLock
                delay(PACKET_STEP_DELAY_MS)
                sendPacket(TechnicsPackets.QUERY_CRADLE_BATTERY)
            }
        }
    }

    private suspend fun sendAncStatusQueryPackets(): Boolean {
        val generation = beginAncSync()
        var completed = false
        try {
            if (!sendPacket(TechnicsPackets.QUERY_OUTSIDE_CTRL)) return false
            delay(PACKET_STEP_DELAY_MS)
            if (!sendPacket(TechnicsPackets.QUERY_ADAPTIVE_ANC)) return false
            delay(ANC_RESPONSE_SETTLE_MS)
            completed = true
            finishAncSync(generation)
            return true
        } finally {
            if (!completed) discardAncSync(generation)
        }
    }

    fun refreshStatus() {
        queryStatus()
    }

    fun retryConnection() {
        val device = lastDevice ?: return
        connect(device, lastConnectionMethod, gameModeImplementation)
    }

    fun disconnect() {
        closeConnection(resetUi = true)
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    private fun closeConnection(resetUi: Boolean) {
        connectJob?.cancel()
        batteryPollJob?.cancel()
        statusQueryJob?.cancel()
        ancModeJob?.cancel()
        ancLevelJob?.cancel()
        synchronized(socketLock) {
            connectionGeneration += 1
            isConnected = false
            try {
                socket?.close()
            } catch (_: IOException) {
            }
            socket = null
        }
        resetAncSyncState()
        if (resetUi) resetUiState()
    }

    private fun markConnectionFailed(generation: Long) {
        val active = synchronized(socketLock) {
            if (connectionGeneration != generation) {
                false
            } else {
                isConnected = false
                socket = null
                true
            }
        }
        if (active) {
            resetAncSyncState()
            _connectionState.value = ConnectionState.ERROR
        }
    }

    private fun markConnectionLost(readerSocket: BluetoothSocket, generation: Long) {
        val active = synchronized(socketLock) {
            if (connectionGeneration != generation || socket !== readerSocket) {
                false
            } else {
                connectionGeneration += 1
                isConnected = false
                try {
                    readerSocket.close()
                } catch (_: IOException) {
                }
                socket = null
                true
            }
        }
        if (active) {
            batteryPollJob?.cancel()
            statusQueryJob?.cancel()
            ancModeJob?.cancel()
            ancLevelJob?.cancel()
            resetAncSyncState()
            _connectionState.value = ConnectionState.ERROR
        }
    }

    private fun isActiveSocket(readerSocket: BluetoothSocket, generation: Long): Boolean {
        return synchronized(socketLock) {
            isConnected && connectionGeneration == generation && socket === readerSocket
        }
    }

    private fun resetAncSyncState() {
        synchronized(ancStateLock) {
            pendingAncSync = null
            lastOutsideMode = null
            lastAdaptiveEnabled = null
            currentAncSynced = false
            guardedAncMode = null
            guardedAncModeUntilMs = 0L
        }
    }

    private fun resetUiState() {
        _batteryParams.value = BatteryParams()
        _ancMode.value = NoiseControlMode.OFF
        _noiseCancelLevel.value = 100
        _transparencyLevel.value = 50
        _deviceName.value = ""
        _gameMode.value = false
    }
}
