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
