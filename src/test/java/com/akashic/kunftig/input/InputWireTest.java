package com.akashic.kunftig.input;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Set;

import org.junit.jupiter.api.Test;

class InputWireTest {

    @Test
    void bindKeepsKnownActionsInOrder() {
        Set<String> read = InputWire
            .readBind(InputWire.bind(Arrays.asList(InputWire.LOCK_ON, "voar", InputWire.SPECIAL, InputWire.LOCK_ON)));
        assertEquals(Arrays.asList(InputWire.LOCK_ON, InputWire.SPECIAL), Arrays.asList(read.toArray()));
        assertTrue(
            InputWire.readBind(InputWire.bind(null))
                .isEmpty());
    }

    @Test
    void actionRoundTrips() {
        InputWire.Action a = new InputWire.Action(
            InputWire.DODGE,
            true,
            41,
            1200,
            InputWire.BACK | InputWire.SNEAK,
            -91.5F,
            12.25F);
        InputWire.Action b = InputWire.readAction(InputWire.action(a));
        assertEquals(InputWire.DODGE, b.action);
        assertTrue(b.pressed);
        assertEquals(41, b.sequence);
        assertEquals(1200, b.clientTick);
        assertEquals(InputWire.BACK | InputWire.SNEAK, b.movement);
        assertEquals(-91.5F, b.yaw);
        assertEquals(12.25F, b.pitch);
    }

    @Test
    void untrustedActionsAreDropped() throws IOException {
        assertNull(InputWire.readAction(null));
        assertNull(InputWire.readAction(new byte[] { InputWire.ACTION }));
        assertNull(InputWire.readAction(InputWire.bind(Arrays.asList(InputWire.SPECIAL))));
        assertNull(InputWire.readAction(raw("voar", 0, 0F)));
        assertNull(InputWire.readAction(raw(InputWire.SPECIAL, 0x80, 0F)));
        assertNull(InputWire.readAction(raw(InputWire.SPECIAL, 0, Float.NaN)));
        assertNull(InputWire.readAction(raw(InputWire.SPECIAL, 0, Float.POSITIVE_INFINITY)));
        byte[] good = raw(InputWire.SPECIAL, 0, 0F);
        assertEquals(InputWire.SPECIAL, InputWire.readAction(good).action);
        assertNull(InputWire.readAction(Arrays.copyOf(good, good.length - 1)), "truncated");
        assertThrows(
            IllegalArgumentException.class,
            () -> InputWire.action(new InputWire.Action("voar", true, 1, 1, 0, 0, 0)));
    }

    @Test
    void bindIgnoresGarbage() {
        assertNull(InputWire.readBind(new byte[] { InputWire.BIND }));
        assertNull(InputWire.readBind(new byte[] { InputWire.BIND, 3, 0 }));
        assertNull(InputWire.readBind(new byte[] { InputWire.ACTION, 0 }));
        assertArrayEquals(new byte[] { InputWire.BIND, 0 }, InputWire.bind(Arrays.asList("x")));
    }

    @Test
    void apiAndWireAgree() {
        assertEquals(InputWire.ACTIONS, com.akashic.kunftig.api.bukkit.input.KunftigInput.ACTIONS);
        assertEquals(InputWire.FORWARD, com.akashic.kunftig.api.bukkit.input.KunftigInputEvent.FORWARD);
        assertEquals(InputWire.BACK, com.akashic.kunftig.api.bukkit.input.KunftigInputEvent.BACK);
        assertEquals(InputWire.LEFT, com.akashic.kunftig.api.bukkit.input.KunftigInputEvent.LEFT);
        assertEquals(InputWire.RIGHT, com.akashic.kunftig.api.bukkit.input.KunftigInputEvent.RIGHT);
        assertEquals(InputWire.JUMP, com.akashic.kunftig.api.bukkit.input.KunftigInputEvent.JUMP);
        assertEquals(InputWire.SNEAK, com.akashic.kunftig.api.bukkit.input.KunftigInputEvent.SNEAK);
        assertEquals(InputWire.SPRINT, com.akashic.kunftig.api.bukkit.input.KunftigInputEvent.SPRINT);
    }

    private static byte[] raw(String action, int movement, float yaw) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeByte(InputWire.ACTION);
            out.writeUTF(action);
            out.writeBoolean(true);
            out.writeInt(1);
            out.writeInt(1);
            out.writeByte(movement);
            out.writeFloat(yaw);
            out.writeFloat(0);
        }
        return bytes.toByteArray();
    }
}
