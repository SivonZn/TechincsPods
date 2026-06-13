package cn.martinkay.technicspods.pods

/**
 * Technics Audio Connect uses Airoha RACE packets over SPP/RFCOMM.
 *
 * RACE packet layout:
 *   channel(0x05), type, lengthLo, lengthHi, raceIdLo, raceIdHi, payload...
 * Length includes the 2-byte race id plus payload.
 */
object TechnicsPackets {
    private const val TYPE_COMMAND_NEED_RESPONSE = 0x5A
    private const val RACE_ID_TWS_GET_BATTERY = 0x0CD6
    private const val RACE_ID_GET_CRADLE_BATTERY = 0x0040
    private const val RACE_ID_GET_OUTSIDE_CTRL = 0x000A
    private const val RACE_ID_SET_OUTSIDE_CTRL = 0x000B
    private const val RACE_ID_SET_AMBIENT_MODE = 0x0022
    private const val RACE_ID_SET_NOISE_CANCELING_ADJUST = 0x0039
    private const val RACE_ID_GET_ADAPTIVE_ANC = 0x0067
    private const val RACE_ID_SET_ADAPTIVE_ANC = 0x0068

    private const val OUTSIDE_CTRL_UNSET = 0x00
    private const val OUTSIDE_CTRL_NOISE_CANCELLING = 0x01
    private const val OUTSIDE_CTRL_AMBIENT = 0x02
    private const val DEFAULT_NOISE_CANCEL_LEVEL = 100
    private const val DEFAULT_AMBIENT_LEVEL = 50
    private const val ADAPTIVE_ANC_OFF = 0x00
    private const val ADAPTIVE_ANC_ON = 0x01
    private const val AMBIENT_MODE_TRANSPARENT = 0x00
    private const val AMBIENT_FOR_MUSIC_MODE_PLAY = 0x00
    private const val NOISE_CANCELING_ADJUST_LEVEL_0 = 0x20
    private const val NOISE_CANCELING_ADJUST_MODE_SAVE_PARAM = 0x02

    fun racePacket(
        raceId: Int,
        payload: ByteArray = byteArrayOf(),
        type: Int = TYPE_COMMAND_NEED_RESPONSE
    ): ByteArray {
        val length = 2 + payload.size
        return byteArrayOf(
            0x05,
            type.toByte(),
            (length and 0xFF).toByte(),
            ((length ushr 8) and 0xFF).toByte(),
            (raceId and 0xFF).toByte(),
            ((raceId ushr 8) and 0xFF).toByte(),
            *payload
        )
    }

    val QUERY_AGENT_BATTERY: ByteArray = racePacket(RACE_ID_TWS_GET_BATTERY, byteArrayOf(0x00))
    val QUERY_CLIENT_BATTERY: ByteArray = racePacket(RACE_ID_TWS_GET_BATTERY, byteArrayOf(0x01))
    val QUERY_CRADLE_BATTERY: ByteArray = racePacket(RACE_ID_GET_CRADLE_BATTERY)
    val QUERY_OUTSIDE_CTRL: ByteArray = racePacket(RACE_ID_GET_OUTSIDE_CTRL)
    val QUERY_ADAPTIVE_ANC: ByteArray = racePacket(RACE_ID_GET_ADAPTIVE_ANC)

    val SET_ADAPTIVE_ANC_OFF: ByteArray = setAdaptiveAnc(false)
    val SET_ADAPTIVE_ANC_ON: ByteArray = setAdaptiveAnc(true)
    val SET_AMBIENT_MODE_TRANSPARENT: ByteArray = racePacket(
        RACE_ID_SET_AMBIENT_MODE,
        byteArrayOf(AMBIENT_MODE_TRANSPARENT.toByte(), AMBIENT_FOR_MUSIC_MODE_PLAY.toByte())
    )
    val SET_NOISE_CANCELING_ADJUST_DEFAULT: ByteArray = racePacket(
        RACE_ID_SET_NOISE_CANCELING_ADJUST,
        byteArrayOf(
            NOISE_CANCELING_ADJUST_LEVEL_0.toByte(),
            NOISE_CANCELING_ADJUST_MODE_SAVE_PARAM.toByte()
        )
    )

    fun setOutsideControl(
        mode: Int,
        noiseCancelLevel: Int = DEFAULT_NOISE_CANCEL_LEVEL,
        ambientLevel: Int = DEFAULT_AMBIENT_LEVEL
    ): ByteArray {
        return racePacket(
            RACE_ID_SET_OUTSIDE_CTRL,
            byteArrayOf(
                mode.toByte(),
                outsideControlLevel(noiseCancelLevel).toByte(),
                outsideControlLevel(ambientLevel).toByte()
            )
        )
    }

    fun setNoiseCancelLevel(
        level: Int,
        ambientLevel: Int = DEFAULT_AMBIENT_LEVEL
    ): ByteArray {
        return setOutsideControl(
            OUTSIDE_CTRL_NOISE_CANCELLING,
            level,
            ambientLevel
        )
    }

    fun setTransparencyLevel(
        noiseCancelLevel: Int = DEFAULT_NOISE_CANCEL_LEVEL,
        level: Int
    ): ByteArray {
        return setOutsideControl(
            OUTSIDE_CTRL_AMBIENT,
            noiseCancelLevel,
            level
        )
    }

    fun setAncModeSequence(
        mode: Int,
        noiseCancelLevel: Int = DEFAULT_NOISE_CANCEL_LEVEL,
        ambientLevel: Int = DEFAULT_AMBIENT_LEVEL
    ): List<ByteArray> {
        return when (mode) {
            // OppoPods UI mapping: 1=off, 2=noise cancelling, 3=transparency, 4=adaptive.
            1 -> listOf(
                SET_ADAPTIVE_ANC_OFF,
                setOutsideControl(OUTSIDE_CTRL_UNSET, noiseCancelLevel, ambientLevel)
            )
            2 -> listOf(
                SET_ADAPTIVE_ANC_OFF,
                setNoiseCancelLevel(noiseCancelLevel, ambientLevel),
                SET_NOISE_CANCELING_ADJUST_DEFAULT
            )
            3 -> listOf(
                SET_ADAPTIVE_ANC_OFF,
                SET_AMBIENT_MODE_TRANSPARENT,
                setTransparencyLevel(noiseCancelLevel, ambientLevel)
            )
            4 -> listOf(
                setNoiseCancelLevel(noiseCancelLevel, ambientLevel),
                SET_ADAPTIVE_ANC_ON
            )
            else -> emptyList()
        }
    }

    private fun setAdaptiveAnc(enabled: Boolean): ByteArray {
        return racePacket(
            RACE_ID_SET_ADAPTIVE_ANC,
            byteArrayOf((if (enabled) ADAPTIVE_ANC_ON else ADAPTIVE_ANC_OFF).toByte())
        )
    }

    private fun outsideControlLevel(level: Int): Int {
        return level.coerceIn(1, 100)
    }
}

class TechnicsPacketFramer {
    private var pending = ByteArray(0)

    fun append(buffer: ByteArray, length: Int): List<ByteArray> {
        if (length <= 0) return emptyList()

        pending += buffer.copyOfRange(0, length)
        val frames = mutableListOf<ByteArray>()

        while (pending.isNotEmpty()) {
            val start = pending.indexOf(RACE_CHANNEL)
            if (start < 0) {
                pending = ByteArray(0)
                break
            }
            if (start > 0) {
                pending = pending.copyOfRange(start, pending.size)
            }
            if (pending.size < RACE_HEADER_SIZE) break

            val lengthField = (pending[2].toInt() and 0xFF) or
                ((pending[3].toInt() and 0xFF) shl 8)
            val frameLength = 4 + lengthField
            if (lengthField < RACE_ID_SIZE || frameLength > RACE_MAX_FRAME_LENGTH) {
                pending = pending.copyOfRange(1, pending.size)
                continue
            }
            if (pending.size < frameLength) break

            frames += pending.copyOfRange(0, frameLength)
            pending = pending.copyOfRange(frameLength, pending.size)
        }

        return frames
    }

    companion object {
        private val RACE_CHANNEL = 0x05.toByte()
        private const val RACE_HEADER_SIZE = 6
        private const val RACE_ID_SIZE = 2
        private const val RACE_MAX_FRAME_LENGTH = 512
    }
}

enum class NoiseControlMode {
    OFF, NOISE_CANCELLATION, ADAPTIVE, TRANSPARENCY
}

object TechnicsBatteryParser {
    private const val RACE_ID_TWS_GET_BATTERY = 0x0CD6
    private const val RACE_ID_GET_CRADLE_BATTERY = 0x0040
    private const val STATUS_SUCCESS = 0x00
    private const val COMPONENT_AGENT = 0x00
    private const val COMPONENT_CLIENT = 0x01

    data class BatteryInfo(
        val level: Int,
        val isCharging: Boolean = false
    )

    data class BatteryResult(
        val left: BatteryInfo? = null,
        val right: BatteryInfo? = null,
        val case: BatteryInfo? = null
    )

    fun parse(data: ByteArray): BatteryResult? {
        if (data.size < 7 || data[0] != 0x05.toByte()) return null

        val lengthField = (data[2].toInt() and 0xFF) or ((data[3].toInt() and 0xFF) shl 8)
        if (lengthField < 2 || data.size < 4 + lengthField) return null

        return when (raceId(data)) {
            RACE_ID_TWS_GET_BATTERY -> parseTwsBattery(data)
            RACE_ID_GET_CRADLE_BATTERY -> parseCradleBattery(data)
            else -> null
        }
    }

    private fun parseTwsBattery(data: ByteArray): BatteryResult? {
        if (data.size < 9) return null
        if ((data[6].toInt() and 0xFF) != STATUS_SUCCESS) return null
        val component = data[7].toInt() and 0xFF
        val level = (data[8].toInt() and 0xFF).coerceIn(0, 100)
        val info = BatteryInfo(level)

        return when (component) {
            COMPONENT_AGENT -> BatteryResult(right = info)
            COMPONENT_CLIENT -> BatteryResult(left = info)
            else -> null
        }
    }

    private fun parseCradleBattery(data: ByteArray): BatteryResult? {
        if (data.size < 8) return null
        if ((data[6].toInt() and 0xFF) != STATUS_SUCCESS) return null
        val level = (data[7].toInt() and 0xFF).coerceIn(0, 100)
        return BatteryResult(case = BatteryInfo(level))
    }

    private fun raceId(data: ByteArray): Int {
        return (data[4].toInt() and 0xFF) or ((data[5].toInt() and 0xFF) shl 8)
    }
}

object TechnicsAncParser {
    private const val RACE_ID_SET_OUTSIDE_CTRL = 0x000B
    private const val RACE_ID_GET_OUTSIDE_CTRL = 0x000A
    private const val RACE_ID_SET_ADAPTIVE_ANC = 0x0068
    private const val RACE_ID_GET_ADAPTIVE_ANC = 0x0067
    private const val STATUS_SUCCESS = 0x00
    private const val OUTSIDE_CTRL_UNSET = 0x00
    private const val OUTSIDE_CTRL_NOISE_CANCELLING = 0x01
    private const val OUTSIDE_CTRL_AMBIENT = 0x02
    private const val ADAPTIVE_ANC_ON = 0x01

    data class AncResult(
        val mode: Int? = null,
        val noiseCancelLevel: Int? = null,
        val transparencyLevel: Int? = null
    )

    fun parse(data: ByteArray): AncResult? {
        if (data.size < 7 || data[0] != 0x05.toByte()) return null
        if ((data[6].toInt() and 0xFF) != STATUS_SUCCESS) return null

        return when (raceId(data)) {
            RACE_ID_GET_ADAPTIVE_ANC -> {
                if (data.size > 7 && (data[7].toInt() and 0xFF) == ADAPTIVE_ANC_ON) {
                    AncResult(mode = 4)
                } else {
                    null
                }
            }
            RACE_ID_GET_OUTSIDE_CTRL -> {
                if (data.size < 10) return null
                val outsideMode = data[7].toInt() and 0xFF
                val mode = when (outsideMode) {
                    OUTSIDE_CTRL_UNSET -> 1
                    OUTSIDE_CTRL_NOISE_CANCELLING -> 2
                    OUTSIDE_CTRL_AMBIENT -> 3
                    else -> null
                }
                AncResult(
                    mode = mode,
                    noiseCancelLevel = (data[8].toInt() and 0xFF).coerceIn(0, 100),
                    transparencyLevel = (data[9].toInt() and 0xFF).coerceIn(0, 100)
                )
            }
            RACE_ID_SET_OUTSIDE_CTRL,
            RACE_ID_SET_ADAPTIVE_ANC -> null
            else -> null
        }
    }

    private fun raceId(data: ByteArray): Int {
        return (data[4].toInt() and 0xFF) or ((data[5].toInt() and 0xFF) shl 8)
    }
}
