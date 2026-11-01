package com.akashic.kunftig.common.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import com.akashic.kunftig.common.text.NameStyle;
import com.akashic.kunftig.common.text.TextColor;
import com.akashic.kunftig.common.text.TextDecoration;

class KnpCodecTest {

    private static KnpMessage.Clientbound roundTrip(KnpMessage.Clientbound message) throws KnpDecodeException {
        return KnpCodec.decodeClientbound(KnpCodec.encode(message), 1);
    }

    @Test
    void varIntRoundTripsEdgeValues() throws KnpDecodeException {
        int[] values = { 0, 1, 127, 128, 255, 16383, 16384, 2097151, 2097152, Integer.MAX_VALUE, -1, Integer.MIN_VALUE };
        KnpWriter w = new KnpWriter();
        for (int v : values) {
            w.writeVarInt(v);
        }
        KnpReader r = new KnpReader(w.toByteArray());
        for (int v : values) {
            assertEquals(v, r.readVarInt());
        }
        r.expectEnd();
    }

    @Test
    void varIntLongerThanFiveBytesIsRejected() {
        byte[] data = { (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, 0x01 };
        assertThrows(KnpDecodeException.class, () -> new KnpReader(data).readVarInt());
    }

    @Test
    void stringsAreBoundedInCharsAndValidated() throws KnpDecodeException {
        String withSupplementary = "a\uD83D\uDE00\u00e9"; // 4 UTF-16 chars, 7 UTF-8 bytes
        byte[] data = new KnpWriter().writeString(withSupplementary, 4).toByteArray();
        assertEquals(withSupplementary, new KnpReader(data).readString(4));
        assertThrows(KnpDecodeException.class, () -> new KnpReader(data).readString(3));
        assertThrows(IllegalArgumentException.class, () -> new KnpWriter().writeString("abcde", 4));

        byte[] invalidUtf8 = { 2, (byte) 0xC3, (byte) 0x28 };
        assertThrows(KnpDecodeException.class, () -> new KnpReader(invalidUtf8).readString(10));
        byte[] declaredTooLong = { 100, 'a' };
        assertThrows(KnpDecodeException.class, () -> new KnpReader(declaredTooLong).readString(10));
        byte[] truncated = { 5, 'a', 'b' };
        assertThrows(KnpDecodeException.class, () -> new KnpReader(truncated).readString(10));
    }

    @Test
    void helloRoundTripsAndToleratesAppendedFields() throws KnpDecodeException {
        KnpMessage.Hello hello = new KnpMessage.Hello(1, 3, KnpProtocol.CAP_RGB | KnpProtocol.CAP_VISIBILITY, "1.0.0");
        byte[] payload = KnpCodec.encode(hello);
        assertEquals(hello, KnpCodec.decodeServerbound(payload));

        byte[] extended = Arrays.copyOf(payload, payload.length + 3);
        assertEquals(hello, KnpCodec.decodeServerbound(extended), "future clients may append HELLO fields");
    }

    @Test
    void helloWithInvertedRangeIsRejected() {
        byte[] payload = new KnpWriter().writeVarInt(0).writeVarInt(3).writeVarInt(1).writeVarInt(0)
            .writeString("x", 64).toByteArray();
        assertThrows(KnpDecodeException.class, () -> KnpCodec.decodeServerbound(payload));
    }

    @Test
    void unknownPacketIdsDecodeToNull() throws KnpDecodeException {
        byte[] payload = new KnpWriter().writeVarInt(0x7F).writeString("whatever", 64).toByteArray();
        assertNull(KnpCodec.decodeClientbound(payload, 1));
        assertNull(KnpCodec.decodeServerbound(payload));
    }

    @Test
    void simpleClientboundMessagesRoundTrip() throws KnpDecodeException {
        assertEquals(new KnpMessage.HelloAck(1, "Kunftig 1.0"), roundTrip(new KnpMessage.HelloAck(1, "Kunftig 1.0")));
        assertSame(KnpMessage.Probe.INSTANCE, roundTrip(KnpMessage.Probe.INSTANCE));
        assertSame(KnpMessage.Reset.INSTANCE, roundTrip(KnpMessage.Reset.INSTANCE));
        assertEquals(new KnpMessage.TeamStyleRemove("kf000abc"), roundTrip(new KnpMessage.TeamStyleRemove("kf000abc")));
    }

    @Test
    void teamStyleRoundTripsWithEveryFieldCombination() throws KnpDecodeException {
        NameStyle gradient = NameStyle.gradient(TextDecoration.BOLD.bit(), TextColor.rgb(0xFF0000),
            TextColor.rgb(0x00FF00), TextColor.rgb(0x0000FF));
        for (int mask = 0; mask < 16; mask++) {
            KnpMessage.TeamStyle style = new KnpMessage.TeamStyle("team" + mask,
                (mask & 1) != 0 ? "{\"text\":\"[Admin] \",\"color\":\"#ff8800\"}" : null,
                (mask & 2) != 0 ? "\" \u2605\"" : null,
                (mask & 4) != 0 ? gradient : null,
                (mask & 8) != 0 ? NametagVisibility.HIDE_FOR_OTHER_TEAMS : null);
            assertEquals(style, roundTrip(style));
        }
    }

    @Test
    void teamStyleRejectsMalformedBodies() {
        KnpWriter unknownField = new KnpWriter().writeVarInt(KnpProtocol.CB_TEAM_STYLE).writeString("t", 64)
            .writeVarInt(1 << 4);
        assertThrows(KnpDecodeException.class, () -> KnpCodec.decodeClientbound(unknownField.toByteArray(), 1));

        KnpWriter tooManyStops = new KnpWriter().writeVarInt(KnpProtocol.CB_TEAM_STYLE).writeString("t", 64)
            .writeVarInt(4).writeByte(0).writeVarInt(NameStyle.MAX_STOPS + 1);
        assertThrows(KnpDecodeException.class, () -> KnpCodec.decodeClientbound(tooManyStops.toByteArray(), 1));

        KnpWriter badVisibility = new KnpWriter().writeVarInt(KnpProtocol.CB_TEAM_STYLE).writeString("t", 64)
            .writeVarInt(8).writeByte(9);
        assertThrows(KnpDecodeException.class, () -> KnpCodec.decodeClientbound(badVisibility.toByteArray(), 1));

        KnpWriter colorOutOfRange = new KnpWriter().writeVarInt(KnpProtocol.CB_TEAM_STYLE).writeString("t", 64)
            .writeVarInt(4).writeByte(0).writeVarInt(1).writeInt(0x01000000);
        assertThrows(KnpDecodeException.class, () -> KnpCodec.decodeClientbound(colorOutOfRange.toByteArray(), 1));

        byte[] valid = KnpCodec.encode(new KnpMessage.TeamStyleRemove("t"));
        byte[] trailing = Arrays.copyOf(valid, valid.length + 1);
        assertThrows(KnpDecodeException.class, () -> KnpCodec.decodeClientbound(trailing, 1));
        assertThrows(KnpDecodeException.class, () -> KnpCodec.decodeClientbound(new byte[0], 1));
        assertThrows(KnpDecodeException.class, () -> KnpCodec.decodeClientbound(valid, 2));
    }

    @Test
    void encoderRefusesPayloadsBukkitWouldReject() {
        StringBuilder big = new StringBuilder();
        while (big.length() < KnpProtocol.MAX_COMPONENT_JSON) {
            big.append('\u4e2d'); // 3 UTF-8 bytes each
        }
        String json = big.toString();
        KnpMessage.TeamStyle style = new KnpMessage.TeamStyle("t", json, json, null, null);
        assertThrows(IllegalArgumentException.class, () -> KnpCodec.encode(style));
    }

    @Test
    void largestAsciiStyleFitsInOnePayload() throws KnpDecodeException {
        char[] chars = new char[KnpProtocol.MAX_COMPONENT_JSON];
        Arrays.fill(chars, 'a');
        String json = new String(chars);
        KnpMessage.TeamStyle style = new KnpMessage.TeamStyle("t", json, json, null, null);
        byte[] payload = KnpCodec.encode(style);
        assertArrayEquals(payload, KnpCodec.encode(roundTrip(style)));
    }

    @Test
    void negotiationPicksHighestCommonVersion() {
        assertEquals(1, KnpProtocol.negotiate(1, 1));
        assertEquals(1, KnpProtocol.negotiate(1, 5));
        assertEquals(-1, KnpProtocol.negotiate(2, 5));
        assertEquals(-1, KnpProtocol.negotiate(0, 0));
    }
}
