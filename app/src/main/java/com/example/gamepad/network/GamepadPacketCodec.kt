package com.example.gamepad.network

import com.example.gamepad.model.GamepadInputState
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

/**
 * Ultra-compact binary UDP protocol for sub-millisecond gamepad transport over local Hotspot/Wi-Fi.
 *
 * INPUT_STATE (24 bytes):
 *   [0..1]   Magic "PP" (0x50, 0x50)
 *   [2]      Version (1)
 *   [3]      Packet Type (0x01 = INPUT_STATE)
 *   [4..7]   Sequence number (Int32 big-endian)
 *   [8..11]  Buttons bitmask (Int32)
 *   [12..13] Left X (-32767..32767)
 *   [14..15] Left Y (-32767..32767)
 *   [16..17] Right X (-32767..32767)
 *   [18..19] Right Y (-32767..32767)
 *   [20..21] Left Trigger (0..32767)
 *   [22..23] Right Trigger (0..32767)
 */
object GamepadPacketCodec {
    const val MAGIC_0: Byte = 0x50 // 'P'
    const val MAGIC_1: Byte = 0x50 // 'P'
    const val VERSION: Byte = 0x01

    const val TYPE_INPUT_STATE: Byte = 0x01
    const val TYPE_PING: Byte = 0x02
    const val TYPE_PONG: Byte = 0x03
    const val TYPE_DISCOVER_REQ: Byte = 0x10
    const val TYPE_DISCOVER_RESP: Byte = 0x11

    const val INPUT_PACKET_SIZE = 24
    const val PING_PACKET_SIZE = 12

    const val GAMEPAD_UDP_PORT = 55888
    const val DISCOVERY_UDP_PORT = 55889
    const val NSD_SERVICE_TYPE = "_pulsepad._udp."
    const val NSD_SERVICE_NAME = "PulsePad-TV"

    fun encodeInputState(
        state: GamepadInputState,
        sequence: Int,
        outBuffer: ByteArray = ByteArray(INPUT_PACKET_SIZE)
    ): ByteArray {
        val bb = ByteBuffer.wrap(outBuffer).order(ByteOrder.BIG_ENDIAN)
        bb.put(MAGIC_0)
        bb.put(MAGIC_1)
        bb.put(VERSION)
        bb.put(TYPE_INPUT_STATE)
        bb.putInt(sequence)
        bb.putInt(state.buttons)
        bb.putShort(floatToSignedShort(state.leftX))
        bb.putShort(floatToSignedShort(state.leftY))
        bb.putShort(floatToSignedShort(state.rightX))
        bb.putShort(floatToSignedShort(state.rightY))
        bb.putShort(floatToTriggerShort(state.leftTrigger))
        bb.putShort(floatToTriggerShort(state.rightTrigger))
        return outBuffer
    }

    fun decodeInputState(bytes: ByteArray, length: Int = bytes.size): Pair<Int, GamepadInputState>? {
        if (length < INPUT_PACKET_SIZE) return null
        if (bytes[0] != MAGIC_0 || bytes[1] != MAGIC_1 || bytes[2] != VERSION || bytes[3] != TYPE_INPUT_STATE) {
            return null
        }
        val bb = ByteBuffer.wrap(bytes, 4, INPUT_PACKET_SIZE - 4).order(ByteOrder.BIG_ENDIAN)
        val seq = bb.int
        val buttons = bb.int
        val lx = signedShortToFloat(bb.short)
        val ly = signedShortToFloat(bb.short)
        val rx = signedShortToFloat(bb.short)
        val ry = signedShortToFloat(bb.short)
        val lt = triggerShortToFloat(bb.short)
        val rt = triggerShortToFloat(bb.short)
        return seq to GamepadInputState(
            buttons = buttons,
            leftX = lx,
            leftY = ly,
            rightX = rx,
            rightY = ry,
            leftTrigger = lt,
            rightTrigger = rt
        )
    }

    fun encodePing(timestampNanos: Long, type: Byte = TYPE_PING): ByteArray {
        val buf = ByteArray(PING_PACKET_SIZE)
        val bb = ByteBuffer.wrap(buf).order(ByteOrder.BIG_ENDIAN)
        bb.put(MAGIC_0)
        bb.put(MAGIC_1)
        bb.put(VERSION)
        bb.put(type)
        bb.putLong(timestampNanos)
        return buf
    }

    fun decodePingTimestamp(bytes: ByteArray, length: Int, expectedType: Byte): Long? {
        if (length < PING_PACKET_SIZE) return null
        if (bytes[0] != MAGIC_0 || bytes[1] != MAGIC_1 || bytes[2] != VERSION || bytes[3] != expectedType) {
            return null
        }
        return ByteBuffer.wrap(bytes, 4, 8).order(ByteOrder.BIG_ENDIAN).long
    }

    fun encodeDiscoverRequest(deviceName: String): ByteArray {
        val nameBytes = deviceName.take(32).toByteArray(Charsets.UTF_8)
        val buf = ByteArray(5 + nameBytes.size)
        buf[0] = MAGIC_0
        buf[1] = MAGIC_1
        buf[2] = VERSION
        buf[3] = TYPE_DISCOVER_REQ
        buf[4] = nameBytes.size.toByte()
        nameBytes.copyInto(buf, destinationOffset = 5)
        return buf
    }

    fun decodeDiscoverRequest(bytes: ByteArray, length: Int): String? {
        if (length < 5) return null
        if (bytes[0] != MAGIC_0 || bytes[1] != MAGIC_1 || bytes[2] != VERSION || bytes[3] != TYPE_DISCOVER_REQ) {
            return null
        }
        val nameLen = (bytes[4].toInt() and 0xFF).coerceAtMost(length - 5)
        return String(bytes, 5, nameLen, Charsets.UTF_8).ifBlank { "Phone Controller" }
    }

    fun encodeDiscoverResponse(tvName: String, gamePort: Int): ByteArray {
        val nameBytes = tvName.take(36).toByteArray(Charsets.UTF_8)
        val buf = ByteArray(7 + nameBytes.size)
        val bb = ByteBuffer.wrap(buf).order(ByteOrder.BIG_ENDIAN)
        bb.put(MAGIC_0)
        bb.put(MAGIC_1)
        bb.put(VERSION)
        bb.put(TYPE_DISCOVER_RESP)
        bb.putShort((gamePort and 0xFFFF).toShort())
        bb.put(nameBytes.size.toByte())
        bb.put(nameBytes)
        return buf
    }

    fun decodeDiscoverResponse(bytes: ByteArray, length: Int): Pair<Int, String>? {
        if (length < 7) return null
        if (bytes[0] != MAGIC_0 || bytes[1] != MAGIC_1 || bytes[2] != VERSION || bytes[3] != TYPE_DISCOVER_RESP) {
            return null
        }
        val bb = ByteBuffer.wrap(bytes, 4, length - 4).order(ByteOrder.BIG_ENDIAN)
        val port = bb.short.toInt() and 0xFFFF
        val nameLen = (bb.get().toInt() and 0xFF).coerceAtMost(bb.remaining())
        val nameBytes = ByteArray(nameLen)
        bb.get(nameBytes)
        val name = String(nameBytes, Charsets.UTF_8).ifBlank { "Smart TV" }
        return port to name
    }

    private fun floatToSignedShort(value: Float): Short {
        val clamped = value.coerceIn(-1f, 1f)
        return (clamped * 32767f).roundToInt().coerceIn(-32767, 32767).toShort()
    }

    private fun signedShortToFloat(value: Short): Float {
        return (value.toFloat() / 32767f).coerceIn(-1f, 1f)
    }

    private fun floatToTriggerShort(value: Float): Short {
        val clamped = value.coerceIn(0f, 1f)
        return (clamped * 32767f).roundToInt().coerceIn(0, 32767).toShort()
    }

    private fun triggerShortToFloat(value: Short): Float {
        return (value.toFloat() / 32767f).coerceIn(0f, 1f)
    }
}
