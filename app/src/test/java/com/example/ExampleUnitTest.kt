package com.example

import com.example.network.Protocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

class ExampleUnitTest {

    @Test
    fun testProtocolAuthPacket() {
        val baos = ByteArrayOutputStream()
        val dos = DataOutputStream(baos)
        Protocol.writeAuth(dos, "123456")

        val bais = ByteArrayInputStream(baos.toByteArray())
        val dis = DataInputStream(bais)

        assertEquals(Protocol.MAGIC_BYTE_1, dis.readByte())
        assertEquals(Protocol.MAGIC_BYTE_2, dis.readByte())
        assertEquals(Protocol.TYPE_AUTH, dis.readByte())

        val len = dis.readInt()
        val pinBytes = ByteArray(len)
        dis.readFully(pinBytes)
        assertEquals("123456", String(pinBytes, Charsets.UTF_8))
    }

    @Test
    fun testProtocolTouchPacket() {
        val baos = ByteArrayOutputStream()
        val dos = DataOutputStream(baos)
        Protocol.writeTouch(dos, Protocol.TOUCH_DOWN, 0.45f, 0.85f)

        val bais = ByteArrayInputStream(baos.toByteArray())
        val dis = DataInputStream(bais)

        assertEquals(Protocol.MAGIC_BYTE_1, dis.readByte())
        assertEquals(Protocol.MAGIC_BYTE_2, dis.readByte())
        assertEquals(Protocol.TYPE_TOUCH, dis.readByte())

        val payloadLen = dis.readInt()
        assertEquals(9, payloadLen)

        val action = dis.readByte()
        val xRatio = dis.readFloat()
        val yRatio = dis.readFloat()

        assertEquals(Protocol.TOUCH_DOWN, action)
        assertEquals(0.45f, xRatio, 0.001f)
        assertEquals(0.85f, yRatio, 0.001f)
    }

    @Test
    fun testProtocolGlobalAction() {
        val baos = ByteArrayOutputStream()
        val dos = DataOutputStream(baos)
        Protocol.writeGlobalAction(dos, Protocol.GLOBAL_HOME)

        val bais = ByteArrayInputStream(baos.toByteArray())
        val dis = DataInputStream(bais)

        assertEquals(Protocol.MAGIC_BYTE_1, dis.readByte())
        assertEquals(Protocol.MAGIC_BYTE_2, dis.readByte())
        assertEquals(Protocol.TYPE_GLOBAL_ACTION, dis.readByte())
        assertEquals(1, dis.readInt())
        assertEquals(Protocol.GLOBAL_HOME, dis.readByte())
    }
}
