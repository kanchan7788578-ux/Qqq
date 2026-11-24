package com.example

import com.example.gamepad.model.GamepadButton
import com.example.gamepad.model.GamepadInputState
import com.example.gamepad.network.GamepadPacketCodec
import com.example.gamepad.network.NetworkTopologyHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GamepadPacketCodecTest {

    @Test
    fun inputPacket_roundTripPreservesButtonsAndAxes() {
        val original = GamepadInputState(
            buttons = GamepadButton.A.mask or GamepadButton.R1.mask or GamepadButton.DPAD_UP.mask,
            leftX = -0.75f,
            leftY = 0.5f,
            rightX = 1.0f,
            rightY = -1.0f,
            leftTrigger = 0.25f,
            rightTrigger = 1.0f
        )

        val encoded = GamepadPacketCodec.encodeInputState(original, sequence = 42)
        assertEquals(GamepadPacketCodec.INPUT_PACKET_SIZE, encoded.size)

        val decodedPair = GamepadPacketCodec.decodeInputState(encoded)
        assertNotNull(decodedPair)
        val (seq, decoded) = decodedPair!!

        assertEquals(42, seq)
        assertEquals(original.buttons, decoded.buttons)
        assertEquals(original.leftX, decoded.leftX, 0.001f)
        assertEquals(original.leftY, decoded.leftY, 0.001f)
        assertEquals(original.rightX, decoded.rightX, 0.001f)
        assertEquals(original.rightY, decoded.rightY, 0.001f)
        assertEquals(original.leftTrigger, decoded.leftTrigger, 0.001f)
        assertEquals(original.rightTrigger, decoded.rightTrigger, 0.001f)
    }

    @Test
    fun pingAndDiscoveryPackets_roundTrip() {
        val ts = 9876543210123L
        val ping = GamepadPacketCodec.encodePing(ts, GamepadPacketCodec.TYPE_PING)
        assertEquals(ts, GamepadPacketCodec.decodePingTimestamp(ping, ping.size, GamepadPacketCodec.TYPE_PING))
        assertNull(GamepadPacketCodec.decodePingTimestamp(ping, ping.size, GamepadPacketCodec.TYPE_PONG))

        val req = GamepadPacketCodec.encodeDiscoverRequest("Pixel 9")
        assertEquals("Pixel 9", GamepadPacketCodec.decodeDiscoverRequest(req, req.size))

        val resp = GamepadPacketCodec.encodeDiscoverResponse("Living Room TV", 55888)
        val parsedResp = GamepadPacketCodec.decodeDiscoverResponse(resp, resp.size)
        assertEquals(55888 to "Living Room TV", parsedResp)
    }

    @Test
    fun ipv4Validator_acceptsValidAndRejectsInvalid() {
        assertTrue(NetworkTopologyHelper.isValidIpv4("192.168.43.1"))
        assertTrue(NetworkTopologyHelper.isValidIpv4("127.0.0.1"))
        assertFalse(NetworkTopologyHelper.isValidIpv4("192.168.43"))
        assertFalse(NetworkTopologyHelper.isValidIpv4("999.168.1.1"))
        assertFalse(NetworkTopologyHelper.isValidIpv4("abc.def.0.1"))
    }
}
