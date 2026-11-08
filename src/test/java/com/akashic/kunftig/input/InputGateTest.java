package com.akashic.kunftig.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.Test;

class InputGateTest {

    private static InputWire.Action a(String action, boolean pressed, int seq) {
        return new InputWire.Action(action, pressed, seq, seq, 0, 0, 0);
    }

    @Test
    void onlyBoundActionsGetThrough() {
        InputGate<String> gate = new InputGate<>();
        assertFalse(gate.accept("ana", a(InputWire.SPECIAL, true, 1), 0), "nothing bound");
        assertEquals(
            Arrays.asList(InputWire.SPECIAL),
            Arrays.asList(
                gate.bind("ana", Arrays.asList(InputWire.SPECIAL, "voar"))
                    .toArray()));
        assertTrue(gate.accept("ana", a(InputWire.SPECIAL, true, 2), 0));
        assertTrue(gate.isHeld("ana", InputWire.SPECIAL));
        assertFalse(gate.accept("ana", a(InputWire.DODGE, true, 3), 0));
        assertTrue(gate.accept("ana", a(InputWire.SPECIAL, false, 4), 0));
        assertFalse(gate.isHeld("ana", InputWire.SPECIAL));
        assertEquals(1, gate.dropped("ana"));
    }

    @Test
    void repeatsAndOldMessagesAreDropped() {
        InputGate<String> gate = new InputGate<>();
        gate.bind("ana", Collections.singleton(InputWire.DODGE));
        assertTrue(gate.accept("ana", a(InputWire.DODGE, true, 10), 0));
        assertFalse(gate.accept("ana", a(InputWire.DODGE, false, 10), 0), "repeat");
        assertFalse(gate.accept("ana", a(InputWire.DODGE, false, 9), 0), "old");
        assertTrue(gate.isHeld("ana", InputWire.DODGE), "dropped messages change nothing");
        assertTrue(gate.accept("ana", a(InputWire.DODGE, false, 11), 0));
        // a new connection starts its sequence again
        gate.resetConnection("ana");
        assertTrue(gate.accept("ana", a(InputWire.DODGE, true, 1), 0));
        // the sequence may wrap around
        assertTrue(gate.accept("ana", a(InputWire.DODGE, false, Integer.MAX_VALUE), 0));
        assertTrue(gate.accept("ana", a(InputWire.DODGE, true, Integer.MIN_VALUE), 0));
    }

    @Test
    void floodsAreCut() {
        InputGate<String> gate = new InputGate<>();
        gate.bind("ana", Collections.singleton(InputWire.SPECIAL));
        int seq = 0;
        int through = 0;
        for (int i = 0; i < 100; i++) {
            if (gate.accept("ana", a(InputWire.SPECIAL, (i & 1) == 0, ++seq), 0)) {
                through++;
            }
        }
        assertEquals(InputGate.BURST, through, "a burst in one tick");
        // refills with time
        assertTrue(gate.accept("ana", a(InputWire.SPECIAL, true, ++seq), 1));
        assertFalse(gate.accept("ana", a(InputWire.SPECIAL, false, ++seq), 1));
        assertTrue(gate.accept("ana", a(InputWire.SPECIAL, false, ++seq), 100));
    }

    @Test
    void unbindingReleasesAndForgetting() {
        InputGate<String> gate = new InputGate<>();
        gate.bind("ana", Arrays.asList(InputWire.SPECIAL, InputWire.LOCK_ON));
        gate.accept("ana", a(InputWire.LOCK_ON, true, 1), 0);
        gate.bind("ana", Collections.singleton(InputWire.SPECIAL));
        assertFalse(gate.isHeld("ana", InputWire.LOCK_ON));
        gate.remove("ana");
        assertTrue(
            gate.bound("ana")
                .isEmpty());
        assertTrue(
            gate.players()
                .isEmpty());
    }

    @Test
    void keysGetThroughOnlyWhileBoundAndRememberWhenEachWentDown() {
        InputGate<String> g = new InputGate<>();
        g.bind("p", java.util.Arrays.asList(InputWire.SPECIAL));
        assertEquals(-1, g.acceptKeys("p", new InputWire.Keys(1, 0, InputWire.KEY_FORWARD), 10), "keys not bound");
        g.bind("p", java.util.Arrays.asList(InputWire.SPECIAL, InputWire.KEYS_ACTION));
        assertEquals(0, g.acceptKeys("p", new InputWire.Keys(2, 0, InputWire.KEY_FORWARD | InputWire.KEY_SNEAK), 11));
        assertEquals(InputWire.KEY_FORWARD | InputWire.KEY_SNEAK, g.keys("p"));
        assertEquals(11, g.lastPress("p", InputWire.KEY_SNEAK));
        assertEquals(-1, g.acceptKeys("p", new InputWire.Keys(2, 0, 0), 12), "old sequence");
        assertEquals(
            InputWire.KEY_FORWARD | InputWire.KEY_SNEAK,
            g.acceptKeys("p", new InputWire.Keys(3, 0, InputWire.KEY_SNEAK), 13));
        assertEquals(11, g.lastPress("p", InputWire.KEY_SNEAK), "still held: the press tick stays");
        assertEquals(-1, g.lastPress("p", InputWire.KEY_USE));
        g.bind("p", java.util.Arrays.asList(InputWire.SPECIAL));
        assertEquals(0, g.keys("p"), "unbinding clears the keys");
    }
}
