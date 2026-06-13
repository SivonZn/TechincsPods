package cn.martinkay.technicspods.pods

import android.annotation.SuppressLint
import android.app.StatusBarManager
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.MediaRoute2Info
import android.media.MediaRouter2
import android.media.RouteDiscoveryPreference
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import cn.martinkay.technicspods.BuildConfig
import cn.martinkay.technicspods.utils.MediaControl
import cn.martinkay.technicspods.utils.SystemApisUtils
import cn.martinkay.technicspods.utils.SystemApisUtils.setIconVisibility
import cn.martinkay.technicspods.utils.miuiStrongToast.MiuiStrongToastUtil
import cn.martinkay.technicspods.utils.miuiStrongToast.MiuiStrongToastUtil.cancelPodsNotificationByMiuiBt
import cn.martinkay.technicspods.utils.miuiStrongToast.data.BatteryParams
import cn.martinkay.technicspods.utils.miuiStrongToast.data.NotificationSettings
import cn.martinkay.technicspods.utils.miuiStrongToast.data.TechnicsPodsAction
import cn.martinkay.technicspods.utils.miuiStrongToast.data.TechnicsPodsPrefsKey
import cn.martinkay.technicspods.utils.miuiStrongToast.data.PodParams
import java.io.IOException
import android.content.SharedPreferences
import java.util.concurrent.Executor

@SuppressLint("MissingPermission", "StaticFieldLeak")
object RfcommController {
    private const val TAG = "TechnicsPods-RfcommController"
    private const val BATTERY_POLL_INTERVAL_MS = 30_000L
    private const val INITIAL_TOAST_SINGLE_EAR_GRACE_MS = 350L

    // Basic Objects
    private val rfcommLock = Any()
    private var socket: BluetoothSocket? = null
    private var mContext: Context? = null
    lateinit var mDevice: BluetoothDevice
    private val audioManager: AudioManager? by lazy {
        mContext?.getSystemService(AudioManager::class.java)
    }
    private lateinit var mPrefs: SharedPreferences

    private var scanToken: MediaRouter2.ScanToken? = null
    var routes: List<MediaRoute2Info> = listOf()
    private lateinit var mediaRouter: MediaRouter2

    // Status
    private var mShowedConnectedToast = false
    @Volatile
    private var isPodConnected = false
    @Volatile
    private var isRfcommConnected = false
    private var lastTempBatt = 0
    lateinit var currentBatteryParams: BatteryParams
    private var currentAnc: Int = 1
    private var currentNoiseCancelLevel: Int = TechnicsPodsPrefsKey.DEFAULT_NOISE_CANCEL_LEVEL
    private var currentTransparencyLevel: Int = TechnicsPodsPrefsKey.DEFAULT_TRANSPARENCY_LEVEL
    private var currentGameMode: Boolean = false
    private var gameModeImplementation: GameModeImplementation = GameModeImplementation.STANDARD
    private var rfcommConnectionMethod: RfcommConnectionMethod = RfcommConnectionMethod.UUID
    // Adaptive模式状态缓存，通过广播同步确保跨进程实时一致，避免 SharedPreferences 跨进程缓存导致读取过时值
    private var adaptiveModeEnabled: Boolean = true
    private var notificationSettings: NotificationSettings = NotificationSettings()
    private val showConnectionBatteryIslandEnabled: Boolean
        get() = notificationSettings.showConnectionBatteryIsland
    private val showConnectionPopupEnabled: Boolean
        get() = notificationSettings.showConnectionPopup
    private val connectionPopupDismissSeconds: Int
        get() = notificationSettings.connectionPopupDismissSeconds
    private val showConnectionNotificationEnabled: Boolean
        get() = notificationSettings.showConnectionNotification
    private val notificationIslandStyleEnabled: Boolean
        get() = notificationSettings.notificationIslandStyle
    private var lastKnownCaseBattery: Int = 0
    private var lastKnownCaseCharging: Boolean = false
    private var cachedDeviceName: String = ""

    // Polling job
    private var batteryPollJob: kotlinx.coroutines.Job? = null
    private var pendingConnectionToastJob: kotlinx.coroutines.Job? = null

    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(p0: Context?, p1: Intent?) {
            p1?.let { handleUIEvent(it) }
        }
    }

    private fun changeUIAncStatus(status: Int) {
        if (status < 1 || status > 4) return
        Intent(TechnicsPodsAction.ACTION_PODS_ANC_CHANGED).apply {
            this.putExtra("status", status)
            this.`package` = BuildConfig.APPLICATION_ID
            this.addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            mContext!!.sendBroadcast(this)
        }
        sendExternalPodsStatusBroadcast(TechnicsPodsAction.ACTION_PODS_ANC_CHANGED) {
            putExtra("status", status)
        }
    }

    private fun changeUIBatteryStatus(status: BatteryParams) {
        Intent(TechnicsPodsAction.ACTION_PODS_BATTERY_CHANGED).apply {
            this.putExtra("status", status)
            putBatteryExtras(status)
            this.`package` = BuildConfig.APPLICATION_ID
            this.addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            mContext!!.sendBroadcast(this)
        }
        sendExternalPodsStatusBroadcast(TechnicsPodsAction.ACTION_PODS_BATTERY_CHANGED) {
            putExtra("status", status)
            putBatteryExtras(status)
        }
    }

    private fun changeUIGameModeStatus(enabled: Boolean) {
        Intent(TechnicsPodsAction.ACTION_PODS_GAME_MODE_CHANGED).apply {
            this.putExtra("enabled", enabled)
            this.`package` = BuildConfig.APPLICATION_ID
            this.addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            mContext!!.sendBroadcast(this)
        }
        sendExternalPodsStatusBroadcast(TechnicsPodsAction.ACTION_PODS_GAME_MODE_CHANGED) {
            putExtra("enabled", enabled)
        }
    }

    private fun refreshPodsNotification() {
        val context = mContext ?: return
        if (!::mDevice.isInitialized) return

        if (!showConnectionNotificationEnabled) {
            cancelPodsNotificationByMiuiBt(context, mDevice)
            return
        }

        if (!::currentBatteryParams.isInitialized) return
        MiuiStrongToastUtil.showPodsNotificationByMiuiBt(
            context,
            currentBatteryParams,
            mDevice,
            notificationSettings,
            isRfcommConnected
        )
    }

    fun handleUIEvent(intent: Intent) {
        when (intent.action) {
            TechnicsPodsAction.ACTION_PODS_UI_INIT -> {
                Log.i(TAG, "UI Init")
                if (::currentBatteryParams.isInitialized)
                    changeUIBatteryStatus(currentBatteryParams)
                changeUIAncStatus(currentAnc)
                changeUIAncLevelStatus()
                changeUIGameModeStatus(currentGameMode)
                Intent(TechnicsPodsAction.ACTION_PODS_CONNECTED).apply {
                    this.putExtra("device_name", mDevice.name ?: cachedDeviceName)
                    this.`package` = BuildConfig.APPLICATION_ID
                    this.addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                    mContext!!.sendBroadcast(this)
                }
                sendExternalPodsStatusBroadcast(TechnicsPodsAction.ACTION_PODS_CONNECTED) {
                    putExtra("device_name", mDevice.name ?: cachedDeviceName)
                }
            }
            TechnicsPodsAction.ACTION_ANC_SELECT -> {
                val status = intent.getIntExtra("status", 0)
                setANCMode(status)
            }
            TechnicsPodsAction.ACTION_ANC_LEVEL_SET -> {
                setAncLevels(
                    intent.getIntExtra(
                        TechnicsPodsAction.EXTRA_NOISE_CANCEL_LEVEL,
                        currentNoiseCancelLevel
                    ),
                    intent.getIntExtra(
                        TechnicsPodsAction.EXTRA_TRANSPARENCY_LEVEL,
                        currentTransparencyLevel
                    )
                )
            }
            TechnicsPodsAction.ACTION_REFRESH_STATUS -> {
                val allowReconnect = intent.getBooleanExtra(
                    TechnicsPodsAction.EXTRA_ALLOW_RFCOMM_RECONNECT,
                    false
                )
                queryStatus(allowReconnect)
            }
            TechnicsPodsAction.ACTION_GAME_MODE_SET -> {
                val enabled = intent.getBooleanExtra("enabled", false)
                setGameMode(enabled)
            }
            TechnicsPodsAction.ACTION_GAME_MODE_IMPLEMENTATION_CHANGED -> {
                gameModeImplementation = GameModeImplementation.fromPreference(
                    intent.getStringExtra(GameModeImplementation.PREF_KEY)
                )
                Log.d(TAG, "Game mode implementation synced: ${gameModeImplementation.preferenceValue}")
            }
            TechnicsPodsAction.ACTION_CYCLE_ANC -> {
                cycleAnc()
            }
            TechnicsPodsAction.ACTION_ADAPTIVE_MODE_CHANGED -> {
                // 跨进程同步 Adaptive 模式开关状态，确保 cycleAnc() 使用实时值
                adaptiveModeEnabled = intent.getBooleanExtra("enabled", true)
                Log.d(TAG, "Adaptive mode synced: $adaptiveModeEnabled")
                // 若关闭 Adaptive 且当前处于 Adaptive 模式，自动切换至降噪模式
                if (!adaptiveModeEnabled && currentAnc == 4) {
                    setANCMode(2)
                }
            }
            TechnicsPodsAction.ACTION_NOTIFICATION_SETTINGS_CHANGED -> {
                notificationSettings = NotificationSettings.fromIntent(intent, notificationSettings)
                Log.d(
                    TAG,
                    "Notification settings synced: batteryIsland=$showConnectionBatteryIslandEnabled, popup=$showConnectionPopupEnabled, popupDismiss=${connectionPopupDismissSeconds}s, show=$showConnectionNotificationEnabled, island=$notificationIslandStyleEnabled"
                )
                refreshPodsNotification()
            }
        }
    }

    private fun currentBatterySnapshot(): BatteryParams {
        return if (::currentBatteryParams.isInitialized) {
            BatteryParams(
                currentBatteryParams.left?.copy(),
                currentBatteryParams.right?.copy(),
                currentBatteryParams.case?.copy()
            )
        } else {
            BatteryParams()
        }
    }

    private fun batteryInfoToPodParams(
        info: TechnicsBatteryParser.BatteryInfo?,
        previous: PodParams?,
        preserveMissing: Boolean
    ): PodParams {
        if (info != null) {
            return PodParams(info.level, info.isCharging, true, previous?.rawStatus ?: 0)
        }
        if (preserveMissing && previous != null) return previous.copy()
        return PodParams(0, false, false, previous?.rawStatus ?: 0)
    }

    private fun caseInfoToPodParams(
        info: TechnicsBatteryParser.BatteryInfo?,
        previous: PodParams?,
        preserveMissing: Boolean
    ): PodParams {
        if (info != null) {
            lastKnownCaseBattery = info.level
            lastKnownCaseCharging = info.isCharging
            return PodParams(info.level, info.isCharging, true, previous?.rawStatus ?: 0)
        }
        if (preserveMissing && previous != null) return previous.copy()
        return PodParams(lastKnownCaseBattery, lastKnownCaseCharging, false, previous?.rawStatus ?: 0)
    }

    fun handleBatteryChanged(result: TechnicsBatteryParser.BatteryResult, preserveMissing: Boolean = false) {
        val previous = currentBatterySnapshot()
        val batteryParams = BatteryParams(
            left = batteryInfoToPodParams(result.left, previous.left, preserveMissing),
            right = batteryInfoToPodParams(result.right, previous.right, preserveMissing),
            case = caseInfoToPodParams(result.case, previous.case, preserveMissing)
        )
        publishBatteryParams(batteryParams)
    }

    private fun hasValidEarBattery(left: PodParams, right: PodParams): Boolean {
        return (left.isConnected && left.battery > 0) || (right.isConnected && right.battery > 0)
    }

    private fun hasBothValidEarBatteries(left: PodParams, right: PodParams): Boolean {
        return left.isConnected && left.battery > 0 && right.isConnected && right.battery > 0
    }

    private fun showInitialConnectionSurfaces(context: Context, batteryParams: BatteryParams) {
        if (mShowedConnectedToast) return

        mShowedConnectedToast = true
        pendingConnectionToastJob?.cancel()
        pendingConnectionToastJob = null

        if (showConnectionBatteryIslandEnabled) {
            MiuiStrongToastUtil.showPodsBatteryToastByMiuiBt(
                context,
                batteryParams
            )
        }
        if (showConnectionPopupEnabled) {
            showConnectionPopup(context, batteryParams)
        }
    }

    private fun changeUIAncLevelStatus() {
        Intent(TechnicsPodsAction.ACTION_PODS_ANC_LEVEL_CHANGED).apply {
            putExtra(TechnicsPodsAction.EXTRA_NOISE_CANCEL_LEVEL, currentNoiseCancelLevel)
            putExtra(TechnicsPodsAction.EXTRA_TRANSPARENCY_LEVEL, currentTransparencyLevel)
            this.`package` = BuildConfig.APPLICATION_ID
            this.addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            mContext!!.sendBroadcast(this)
        }
        sendExternalPodsStatusBroadcast(TechnicsPodsAction.ACTION_PODS_ANC_LEVEL_CHANGED) {
            putExtra(TechnicsPodsAction.EXTRA_NOISE_CANCEL_LEVEL, currentNoiseCancelLevel)
            putExtra(TechnicsPodsAction.EXTRA_TRANSPARENCY_LEVEL, currentTransparencyLevel)
        }
    }

    private fun scheduleSingleEarConnectionSurfaces(context: Context) {
        if (pendingConnectionToastJob != null) return

        pendingConnectionToastJob = CoroutineScope(Dispatchers.IO).launch {
            delay(INITIAL_TOAST_SINGLE_EAR_GRACE_MS)
            val latest = currentBatterySnapshot()
            val left = latest.left ?: PodParams()
            val right = latest.right ?: PodParams()
            if (!mShowedConnectedToast && hasValidEarBattery(left, right)) {
                showInitialConnectionSurfaces(context, latest)
            }
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun publishBatteryParams(batteryParams: BatteryParams) {
        val context = mContext ?: return
        val left = batteryParams.left ?: PodParams()
        val right = batteryParams.right ?: PodParams()
        val case = batteryParams.case ?: PodParams()

        if (BuildConfig.DEBUG) {
            Log.v(
                TAG,
                "batt left ${left.battery}/${left.isCharging} right ${right.battery}/${right.isCharging} case ${case.battery}/${case.isCharging}"
            )
        }

        val shouldShowToast = !mShowedConnectedToast
        if (shouldShowToast) {
            // Wait until at least one connected ear has valid battery data
            if (!hasValidEarBattery(left, right)) return
        }

        currentBatteryParams = batteryParams

        if (shouldShowToast) {
            if (hasBothValidEarBatteries(left, right)) {
                showInitialConnectionSurfaces(context, batteryParams)
            } else {
                scheduleSingleEarConnectionSurfaces(context)
            }
        }
        if (showConnectionNotificationEnabled) {
            MiuiStrongToastUtil.showPodsNotificationByMiuiBt(
                context,
                batteryParams,
                mDevice,
                notificationSettings,
                isRfcommConnected
            )
        } else {
            cancelPodsNotificationByMiuiBt(context, mDevice)
        }
        changeUIBatteryStatus(batteryParams)

        lastTempBatt = if (left.isConnected && right.isConnected)
            minOf(left.battery, right.battery)
        else if (left.isConnected)
            left.battery
        else if (right.isConnected)
            right.battery
        else SystemApisUtils.BATTERY_LEVEL_UNKNOWN

        setRegularBatteryLevel(lastTempBatt)
    }

    private fun showConnectionPopup(context: Context, batteryParams: BatteryParams) {
        try {
            Intent().apply {
                setClassName(BuildConfig.APPLICATION_ID, "cn.martinkay.technicspods.ConnectionPopupActivity")
                putExtra("status", batteryParams)
                putExtra("device_name", currentDeviceDisplayName())
                putExtra(
                    TechnicsPodsPrefsKey.CONNECTION_POPUP_DISMISS_SECONDS,
                    connectionPopupDismissSeconds
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                context.startActivity(this)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show connection popup", e)
        }
    }

    private fun currentDeviceDisplayName(): String {
        return if (::mDevice.isInitialized) {
            mDevice.alias?.takeIf { it.isNotBlank() }
                ?: mDevice.name
                ?: cachedDeviceName
        } else {
            cachedDeviceName
        }
    }

    private val routeCallback = object : MediaRouter2.RouteCallback() {
        override fun onRoutesUpdated(routes: List<MediaRoute2Info>) {
            Log.v(TAG, "routes updated: $routes")
            this@RfcommController.routes = routes
        }
    }

    private fun startRoutesScan() {
        val executor = Executor { p0 ->
            CoroutineScope(Dispatchers.IO).launch { p0?.run() }
        }
        val preferredFeature = listOf(MediaRoute2Info.FEATURE_LIVE_AUDIO, MediaRoute2Info.FEATURE_LIVE_VIDEO)
        mediaRouter.registerRouteCallback(executor, routeCallback, RouteDiscoveryPreference.Builder(preferredFeature, true).build())
        scanToken = mediaRouter.requestScan(MediaRouter2.ScanRequest.Builder().build())
    }

    private fun stopRoutesScan() {
        scanToken?.let { mediaRouter.cancelScanRequest(it) }
        mediaRouter.unregisterRouteCallback(routeCallback)
    }

    fun connectPod(context: Context, device: BluetoothDevice, prefs: SharedPreferences) {
        mContext = context
        mDevice = device
        mPrefs = prefs
        cachedDeviceName = device.name ?: ""
        // 初始化 Adaptive 模式状态缓存，从 SharedPreferences 读取当前值
        adaptiveModeEnabled = mPrefs.getBoolean("adaptive_mode", true)
        currentNoiseCancelLevel = mPrefs.getInt(
            TechnicsPodsPrefsKey.NOISE_CANCEL_LEVEL,
            TechnicsPodsPrefsKey.DEFAULT_NOISE_CANCEL_LEVEL
        ).coerceIn(0, 100)
        currentTransparencyLevel = mPrefs.getInt(
            TechnicsPodsPrefsKey.TRANSPARENCY_LEVEL,
            TechnicsPodsPrefsKey.DEFAULT_TRANSPARENCY_LEVEL
        ).coerceIn(0, 100)
        gameModeImplementation = GameModeImplementation.fromPreference(
            mPrefs.getString(GameModeImplementation.PREF_KEY, null)
        )
        notificationSettings = NotificationSettings.fromPrefs(mPrefs)
        rfcommConnectionMethod = RfcommConnectionMethod.fromPreference(
            mPrefs.getString(RfcommConnectionMethod.PREF_KEY, null)
        )
        Log.d(TAG, "Adaptive mode initial: $adaptiveModeEnabled")
        Log.d(
            TAG,
            "Notification settings initial: batteryIsland=$showConnectionBatteryIslandEnabled, popup=$showConnectionPopupEnabled, popupDismiss=${connectionPopupDismissSeconds}s, show=$showConnectionNotificationEnabled, island=$notificationIslandStyleEnabled"
        )
        Log.d(TAG, "Game mode implementation initial: ${gameModeImplementation.preferenceValue}")
        Log.d(TAG, "RFCOMM connection method initial: ${rfcommConnectionMethod.preferenceValue}")

        context.registerReceiver(broadcastReceiver, IntentFilter().apply {
            this.addAction(TechnicsPodsAction.ACTION_ANC_SELECT)
            this.addAction(TechnicsPodsAction.ACTION_ANC_LEVEL_SET)
            this.addAction(TechnicsPodsAction.ACTION_PODS_UI_INIT)
            this.addAction(TechnicsPodsAction.ACTION_REFRESH_STATUS)
            this.addAction(TechnicsPodsAction.ACTION_GAME_MODE_SET)
            this.addAction(TechnicsPodsAction.ACTION_GAME_MODE_IMPLEMENTATION_CHANGED)
            this.addAction(TechnicsPodsAction.ACTION_CYCLE_ANC)
            this.addAction(TechnicsPodsAction.ACTION_ADAPTIVE_MODE_CHANGED)
            this.addAction(TechnicsPodsAction.ACTION_NOTIFICATION_SETTINGS_CHANGED)
        }, Context.RECEIVER_EXPORTED)

        Intent(TechnicsPodsAction.ACTION_PODS_CONNECTED).apply {
            this.putExtra("device_name", cachedDeviceName)
            this.`package` = BuildConfig.APPLICATION_ID
            this.addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            context.sendBroadcast(this)
        }
        sendExternalPodsStatusBroadcast(TechnicsPodsAction.ACTION_PODS_CONNECTED) {
            putExtra("device_name", cachedDeviceName)
        }

        MediaControl.mContext = mContext
        mediaRouter = MediaRouter2.getInstance(mContext!!)
        startRoutesScan()

        isPodConnected = true

        // Start persistent RFCOMM connection and battery polling
        CoroutineScope(Dispatchers.IO).launch {
            var initialConnected = connectRfcomm("initial connect")
            if (!initialConnected) {
                delay(500)
                initialConnected = connectRfcomm("initial connect retry")
            }

            if (initialConnected) {
                sendStatusQueryPackets()
            } else {
                Log.w(TAG, "Initial RFCOMM connect failed; will retry on the next control/query operation")
            }
        }

        // Start battery polling
        batteryPollJob = CoroutineScope(Dispatchers.IO).launch {
            delay(2000) // Wait for initial connection
            while (isPodConnected) {
                delay(BATTERY_POLL_INTERVAL_MS)
                if (isPodConnected) {
                    queryStatus(allowReconnect = false)
                }
            }
        }
    }

    private fun sendExternalPodsStatusBroadcast(action: String, fill: Intent.() -> Unit = {}) {
        val ctx = mContext ?: return
        listOf("com.milink.service", "com.xiaomi.bluetooth", "com.android.settings").forEach { targetPackage ->
            Intent(action).apply {
                if (::mDevice.isInitialized) {
                    putExtra("address", mDevice.address)
                    putExtra("device_name", mDevice.name ?: cachedDeviceName)
                }
                fill()
                setPackage(targetPackage)
                addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                ctx.sendBroadcast(this)
            }
        }
    }

    private fun Intent.putBatteryExtras(status: BatteryParams) {
        putExtra("left_battery", status.left?.battery ?: 0)
        putExtra("left_charging", status.left?.isCharging == true)
        putExtra("left_connected", status.left?.isConnected == true)
        putExtra("right_battery", status.right?.battery ?: 0)
        putExtra("right_charging", status.right?.isCharging == true)
        putExtra("right_connected", status.right?.isConnected == true)
        putExtra("case_battery", status.case?.battery ?: 0)
        putExtra("case_charging", status.case?.isCharging == true)
        putExtra("case_connected", status.case?.isConnected == true)
    }

    private fun refreshRfcommConnectionMethod() {
        if (::mPrefs.isInitialized) {
            rfcommConnectionMethod = RfcommConnectionMethod.fromPreference(
                mPrefs.getString(RfcommConnectionMethod.PREF_KEY, null)
            )
        }
    }

    private fun connectRfcomm(reason: String): Boolean {
        if (!isPodConnected || mContext == null || !::mDevice.isInitialized) {
            Log.d(TAG, "Skip RFCOMM connect: podConnected=$isPodConnected, reason=$reason")
            return false
        }

        synchronized(rfcommLock) {
            if (isRfcommConnected && socket != null) {
                return true
            }

            refreshRfcommConnectionMethod()
            closeRfcommSocketLocked()

            return try {
                Log.d(
                    TAG,
                    "RFCOMM connecting: reason=$reason, method=${rfcommConnectionMethod.preferenceValue}"
                )
                val connectedSocket = TechnicsRfcommSocketFactory.connect(
                    mDevice,
                    TAG,
                    rfcommConnectionMethod
                )
                socket = connectedSocket
                isRfcommConnected = true
                startPacketReader(connectedSocket)
                Log.d(TAG, "RFCOMM connected: reason=$reason")
                refreshPodsNotification()
                true
            } catch (e: IOException) {
                Log.e(TAG, "RFCOMM connect failed: reason=$reason", e)
                closeRfcommSocketLocked()
                false
            }
        }
    }

    private fun closeRfcommSocketLocked() {
        try {
            socket?.close()
        } catch (e: IOException) {
            Log.w(TAG, "RFCOMM socket close failed", e)
        } finally {
            socket = null
            isRfcommConnected = false
        }
    }

    private fun markRfcommDisconnected(
        reason: String,
        failedSocket: BluetoothSocket? = null,
        error: Throwable? = null
    ) {
        if (error != null) {
            Log.e(TAG, "RFCOMM disconnected: $reason", error)
        } else {
            Log.d(TAG, "RFCOMM disconnected: $reason")
        }

        synchronized(rfcommLock) {
            if (failedSocket == null || socket === failedSocket) {
                closeRfcommSocketLocked()
                refreshPodsNotification()
            }
        }
    }

    private fun isActiveRfcommSocket(targetSocket: BluetoothSocket): Boolean {
        return synchronized(rfcommLock) {
            isRfcommConnected && socket === targetSocket
        }
    }

    private fun startPacketReader(readerSocket: BluetoothSocket) {
        CoroutineScope(Dispatchers.IO).launch {
            val buffer = ByteArray(1024)
            val framer = TechnicsPacketFramer()
            try {
                val inputStream = readerSocket.inputStream
                while (isPodConnected && isActiveRfcommSocket(readerSocket)) {
                    val bytesRead = inputStream.read(buffer)
                    if (bytesRead > 0) {
                        framer.append(buffer, bytesRead).forEach { packet ->
                            handleTechnicsPacket(packet)
                        }
                    } else if (bytesRead == -1) {
                        Log.d(TAG, "RFCOMM stream ended")
                        break
                    }
                }
            } catch (e: IOException) {
                if (isPodConnected && isActiveRfcommSocket(readerSocket)) {
                    markRfcommDisconnected("read error", readerSocket, e)
                }
                return@launch
            }

            if (isPodConnected && isActiveRfcommSocket(readerSocket)) {
                markRfcommDisconnected("reader stopped", readerSocket)
            }
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun handleTechnicsPacket(packet: ByteArray) {
        if (BuildConfig.DEBUG) {
            Log.v(TAG, "Received: ${packet.toHexString(HexFormat.UpperCase)}")
        }

        val batteryResult = TechnicsBatteryParser.parse(packet)
        if (batteryResult != null) {
            handleBatteryChanged(batteryResult, preserveMissing = true)
            return
        }

        val ancResult = TechnicsAncParser.parse(packet)
        if (ancResult != null) {
            handleAncChanged(ancResult)
            return
        }

        // Unknown packet - log in debug
        if (BuildConfig.DEBUG) {
            Log.v(TAG, "Unknown Technics packet: ${packet.toHexString(HexFormat.UpperCase)}")
        }
    }

    fun disconnectedPod(context: Context, device: BluetoothDevice) {
        isPodConnected = false
        batteryPollJob?.cancel()

        synchronized(rfcommLock) {
            closeRfcommSocketLocked()
        }

        mContext?.let {
            stopRoutesScan()
            cancelPodsNotificationByMiuiBt(context, device)
            Intent(TechnicsPodsAction.ACTION_PODS_DISCONNECTED).apply {
                this.`package` = BuildConfig.APPLICATION_ID
                this.addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                context.sendBroadcast(this)
            }
            it.unregisterReceiver(broadcastReceiver)
        }

        mShowedConnectedToast = false
        pendingConnectionToastJob?.cancel()
        pendingConnectionToastJob = null
        lastKnownCaseBattery = 0
        lastKnownCaseCharging = false
        cachedDeviceName = ""
        mContext = null
        MediaControl.mContext = null
    }

    private fun writePacket(targetSocket: BluetoothSocket, packet: ByteArray, reason: String): Boolean {
        try {
            targetSocket.outputStream.write(packet)
            targetSocket.outputStream.flush()
            return true
        } catch (e: IOException) {
            markRfcommDisconnected("send failed: $reason", targetSocket, e)
            return false
        }
    }

    private fun sendPacketSafe(
        packet: ByteArray,
        reason: String = "send packet",
        allowReconnect: Boolean = true
    ): Boolean {
        if (allowReconnect) {
            if (!connectRfcomm(reason)) return false
        }

        val targetSocket = synchronized(rfcommLock) { socket } ?: run {
            Log.d(TAG, "Skip packet: RFCOMM disconnected and reconnect not allowed, reason=$reason")
            return false
        }

        if (writePacket(targetSocket, packet, reason)) {
            return true
        }

        if (!allowReconnect) return false
        if (!connectRfcomm("$reason retry")) return false

        val retrySocket = synchronized(rfcommLock) { socket } ?: return false
        return writePacket(retrySocket, packet, "$reason retry")
    }

    fun setGameMode(enabled: Boolean) {
        currentGameMode = enabled
        changeUIGameModeStatus(enabled)
        Log.d(TAG, "setGameMode ignored: Technics game-mode protocol is not implemented")
    }

    private fun handleAncChanged(result: TechnicsAncParser.AncResult) {
        result.noiseCancelLevel?.let {
            currentNoiseCancelLevel = it.coerceIn(0, 100)
        }
        result.transparencyLevel?.let {
            currentTransparencyLevel = it.coerceIn(0, 100)
        }
        if (result.noiseCancelLevel != null || result.transparencyLevel != null) {
            changeUIAncLevelStatus()
            if (::mPrefs.isInitialized) {
                mPrefs.edit()
                    .putInt(TechnicsPodsPrefsKey.NOISE_CANCEL_LEVEL, currentNoiseCancelLevel)
                    .putInt(TechnicsPodsPrefsKey.TRANSPARENCY_LEVEL, currentTransparencyLevel)
                    .apply()
            }
        }
        result.mode?.let {
            currentAnc = it
            changeUIAncStatus(it)
        }
    }

    fun cycleAnc() {
        // 使用广播同步的缓存值，避免 SharedPreferences 跨进程缓存导致读取过时值
        val next = when (currentAnc) {
            2 -> if (adaptiveModeEnabled) 4 else 3  // NC → Adaptive（若启用）或 Transparency
            4 -> 3  // Adaptive → Transparency
            3 -> 1  // Transparency → OFF
            else -> 2  // OFF or unknown → NC
        }
        setANCMode(next)
    }

    fun setANCMode(mode: Int) {
        Log.d(TAG, "setANCMode: $mode")
        if (mode !in 1..4) return
        currentAnc = mode
        changeUIAncStatus(mode)
        CoroutineScope(Dispatchers.IO).launch {
            val packets = TechnicsPackets.setAncModeSequence(
                mode,
                currentNoiseCancelLevel,
                currentTransparencyLevel
            )
            if (packets.isEmpty()) return@launch
            packets.forEachIndexed { index, packet ->
                if (!sendPacketSafe(packet, "set ANC mode $mode step ${index + 1}", true)) {
                    return@launch
                }
                delay(80)
            }
            sendAncStatusQueryPackets(allowReconnect = false)
        }
    }

    fun setAncLevels(noiseCancelLevel: Int, transparencyLevel: Int) {
        currentNoiseCancelLevel = noiseCancelLevel.coerceIn(1, 100)
        currentTransparencyLevel = transparencyLevel.coerceIn(1, 100)
        changeUIAncLevelStatus()

        if (::mPrefs.isInitialized) {
            mPrefs.edit()
                .putInt(TechnicsPodsPrefsKey.NOISE_CANCEL_LEVEL, currentNoiseCancelLevel)
                .putInt(TechnicsPodsPrefsKey.TRANSPARENCY_LEVEL, currentTransparencyLevel)
                .apply()
        }

        CoroutineScope(Dispatchers.IO).launch {
            val packet = when (currentAnc) {
                2, 4 -> TechnicsPackets.setNoiseCancelLevel(
                    currentNoiseCancelLevel,
                    currentTransparencyLevel
                )
                3 -> TechnicsPackets.setTransparencyLevel(
                    currentNoiseCancelLevel,
                    currentTransparencyLevel
                )
                else -> null
            } ?: return@launch

            sendPacketSafe(packet, "set ANC level", true)
            delay(80)
            sendPacketSafe(TechnicsPackets.QUERY_OUTSIDE_CTRL, "query outside control after set level", false)
        }
    }

    fun queryBattery(allowReconnect: Boolean = false) {
        CoroutineScope(Dispatchers.IO).launch {
            sendStatusQueryPackets(allowReconnect)
        }
    }

    private suspend fun sendStatusQueryPackets(allowReconnect: Boolean = false) {
        if (!sendPacketSafe(TechnicsPackets.QUERY_AGENT_BATTERY, "query agent battery", allowReconnect)) return
        delay(80)
        if (!sendPacketSafe(TechnicsPackets.QUERY_CLIENT_BATTERY, "query client battery", allowReconnect)) return
        delay(80)
        if (!sendPacketSafe(TechnicsPackets.QUERY_CRADLE_BATTERY, "query cradle battery", allowReconnect)) return
        delay(80)
        if (!sendPacketSafe(TechnicsPackets.QUERY_OUTSIDE_CTRL, "query outside control", allowReconnect)) return
        delay(80)
        sendPacketSafe(TechnicsPackets.QUERY_ADAPTIVE_ANC, "query adaptive ANC", allowReconnect)
    }

    private suspend fun sendAncStatusQueryPackets(allowReconnect: Boolean = false) {
        if (!sendPacketSafe(TechnicsPackets.QUERY_OUTSIDE_CTRL, "query outside control", allowReconnect)) return
        delay(80)
        sendPacketSafe(TechnicsPackets.QUERY_ADAPTIVE_ANC, "query adaptive ANC", allowReconnect)
    }

    /**
     * Technics battery query strategy: agent side, client side, then cradle.
     */
    fun queryStatus(allowReconnect: Boolean = false) {
        CoroutineScope(Dispatchers.IO).launch {
            sendStatusQueryPackets(allowReconnect)
        }
    }

    fun disconnectAudio(context: Context, device: BluetoothDevice?) {
        val bluetoothAdapter = context.getSystemService(BluetoothManager::class.java).adapter

        MediaControl.sendPause()

        bluetoothAdapter?.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                if (profile == BluetoothProfile.HEADSET) {
                    try {
                        val method = proxy.javaClass.getMethod("disconnect", BluetoothDevice::class.java)
                        method.invoke(proxy, device)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        bluetoothAdapter.closeProfileProxy(BluetoothProfile.HEADSET, proxy)
                    }
                }
            }
            override fun onServiceDisconnected(profile: Int) { }
        }, BluetoothProfile.HEADSET)

        CoroutineScope(Dispatchers.Default).launch {
            delay(500)
            for (route in routes) {
                if (route.type == MediaRoute2Info.TYPE_BUILTIN_SPEAKER) {
                    Log.d(TAG, "found speaker route $route")
                    mediaRouter.transferTo(route)
                }
            }
        }

        setRegularBatteryLevel(lastTempBatt)
    }

    fun connectAudio(context: Context, device: BluetoothDevice?) {
        val targetDevice = device ?: return
        val bluetoothAdapter = context.getSystemService(BluetoothManager::class.java).adapter

        bluetoothAdapter?.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                if (profile == BluetoothProfile.HEADSET) {
                    try {
                        val method = proxy.javaClass.getMethod("connect", BluetoothDevice::class.java)
                        method.invoke(proxy, targetDevice)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        bluetoothAdapter.closeProfileProxy(BluetoothProfile.HEADSET, proxy)
                    }
                }
            }
            override fun onServiceDisconnected(profile: Int) { }
        }, BluetoothProfile.HEADSET)

        for (route in routes) {
            if (route.type == MediaRoute2Info.TYPE_BLUETOOTH_A2DP && route.name == targetDevice.name) {
                Log.d(TAG, "found bt route $route")
                mediaRouter.transferTo(route)
            }
        }

        val statusBarManager = context.getSystemService("statusbar") as StatusBarManager
        statusBarManager.setIconVisibility("wireless_headset", true)
        setRegularBatteryLevel(lastTempBatt)
    }

    fun setRegularBatteryLevel(level: Int) {
        try {
            val service = getObjectField(mContext, "mAdapterService")
            callMethod(service, "setBatteryLevel", mDevice, level, false)
        } catch (e: Exception) {
            Log.e(TAG, "setRegularBatteryLevel failed", e)
        }
    }

    private fun getObjectField(instance: Any?, fieldName: String): Any? {
        if (instance == null) return null
        var cls: Class<*>? = instance.javaClass
        while (cls != null) {
            runCatching {
                return cls.getDeclaredField(fieldName).apply { isAccessible = true }.get(instance)
            }
            cls = cls.superclass
        }
        throw NoSuchFieldException(fieldName)
    }

    private fun callMethod(instance: Any?, methodName: String, vararg args: Any?): Any? {
        if (instance == null) return null
        var cls: Class<*>? = instance.javaClass
        while (cls != null) {
            cls.declaredMethods.firstOrNull { it.name == methodName && it.parameterTypes.size == args.size }?.let {
                it.isAccessible = true
                return it.invoke(instance, *args)
            }
            cls = cls.superclass
        }
        throw NoSuchMethodException(methodName)
    }
}
