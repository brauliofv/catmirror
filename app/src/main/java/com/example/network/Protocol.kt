package com.example.network

import java.io.DataInputStream
import java.io.DataOutputStream

object Protocol {
    const val MAGIC_BYTE_1: Byte = 0xCA.toByte()
    const val MAGIC_BYTE_2: Byte = 0x7E.toByte()

    const val DEFAULT_PORT = 8888
    const val DISCOVERY_PORT = 8889

    const val TYPE_AUTH: Byte = 0x01
    const val TYPE_AUTH_RESP: Byte = 0x02
    const val TYPE_FRAME: Byte = 0x10
    const val TYPE_TOUCH: Byte = 0x20
    const val TYPE_GLOBAL_ACTION: Byte = 0x21
    const val TYPE_PING: Byte = 0x30
    const val TYPE_PONG: Byte = 0x31
    const val TYPE_DISCONNECT: Byte = 0x40

    const val TOUCH_DOWN: Byte = 0x00
    const val TOUCH_UP: Byte = 0x01
    const val TOUCH_MOVE: Byte = 0x02

    const val GLOBAL_BACK: Byte = 0x01
    const val GLOBAL_HOME: Byte = 0x02
    const val GLOBAL_RECENTS: Byte = 0x03

    fun writeAuth(out: DataOutputStream, pin: String) {
        val pinBytes = pin.toByteArray(Charsets.UTF_8)
        out.writeByte(MAGIC_BYTE_1.toInt())
        out.writeByte(MAGIC_BYTE_2.toInt())
        out.writeByte(TYPE_AUTH.toInt())
        out.writeInt(pinBytes.size)
        out.write(pinBytes)
        out.flush()
    }

    fun writeAuthResponse(out: DataOutputStream, success: Boolean) {
        out.writeByte(MAGIC_BYTE_1.toInt())
        out.writeByte(MAGIC_BYTE_2.toInt())
        out.writeByte(TYPE_AUTH_RESP.toInt())
        out.writeInt(1)
        out.writeBoolean(success)
        out.flush()
    }

    fun writeFrame(out: DataOutputStream, width: Int, height: Int, timestamp: Long, jpegData: ByteArray) {
        out.writeByte(MAGIC_BYTE_1.toInt())
        out.writeByte(MAGIC_BYTE_2.toInt())
        out.writeByte(TYPE_FRAME.toInt())
        // payload: width (2) + height (2) + timestamp (8) + image bytes (size)
        out.writeInt(2 + 2 + 8 + jpegData.size)
        out.writeShort(width)
        out.writeShort(height)
        out.writeLong(timestamp)
        out.write(jpegData)
        out.flush()
    }

    fun writeTouch(out: DataOutputStream, action: Byte, xRatio: Float, yRatio: Float) {
        out.writeByte(MAGIC_BYTE_1.toInt())
        out.writeByte(MAGIC_BYTE_2.toInt())
        out.writeByte(TYPE_TOUCH.toInt())
        out.writeInt(1 + 4 + 4)
        out.writeByte(action.toInt())
        out.writeFloat(xRatio)
        out.writeFloat(yRatio)
        out.flush()
    }

    fun writeGlobalAction(out: DataOutputStream, action: Byte) {
        out.writeByte(MAGIC_BYTE_1.toInt())
        out.writeByte(MAGIC_BYTE_2.toInt())
        out.writeByte(TYPE_GLOBAL_ACTION.toInt())
        out.writeInt(1)
        out.writeByte(action.toInt())
        out.flush()
    }

    fun writePing(out: DataOutputStream, timestamp: Long) {
        out.writeByte(MAGIC_BYTE_1.toInt())
        out.writeByte(MAGIC_BYTE_2.toInt())
        out.writeByte(TYPE_PING.toInt())
        out.writeInt(8)
        out.writeLong(timestamp)
        out.flush()
    }

    fun writePong(out: DataOutputStream, timestamp: Long) {
        out.writeByte(MAGIC_BYTE_1.toInt())
        out.writeByte(MAGIC_BYTE_2.toInt())
        out.writeByte(TYPE_PONG.toInt())
        out.writeInt(8)
        out.writeLong(timestamp)
        out.flush()
    }
}

data class VideoFrame(
    val width: Int,
    val height: Int,
    val timestamp: Long,
    val jpegData: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as VideoFrame
        return timestamp == other.timestamp && width == other.width && height == other.height
    }

    override fun hashCode(): Int {
        var result = width
        result = 31 * result + height
        result = 31 * result + timestamp.hashCode()
        return result
    }
}
