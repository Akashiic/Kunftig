package com.akashic.kunftig.hologram;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

class HologramTest {

    @Test
    void wireRoundTrips() {
        HologramWire.Message show = HologramWire
            .read(HologramWire.show(7, 1.5, 64.25, -3, Arrays.asList("§6Amuleto", "§a+25% de sorte", "")));
        assertEquals(HologramWire.SHOW, show.op);
        assertEquals(7, show.id);
        assertEquals(64.25, show.y);
        assertEquals(Arrays.asList("§6Amuleto", "§a+25% de sorte", ""), show.lines);

        HologramWire.Message lines = HologramWire.read(HologramWire.lines(7, Collections.singletonList("ção ★")));
        assertEquals(HologramWire.LINES, lines.op);
        assertEquals(Collections.singletonList("ção ★"), lines.lines);

        HologramWire.Message move = HologramWire.read(HologramWire.move(7, 10, 20, 30));
        assertEquals(HologramWire.MOVE, move.op);
        assertEquals(30, move.z);

        assertEquals(HologramWire.HIDE, HologramWire.read(HologramWire.hide(7)).op);
    }

    @Test
    void wireCutsAndRejects() {
        List<String> many = new ArrayList<>();
        StringBuilder longLine = new StringBuilder();
        for (int i = 0; i < 300; i++) {
            longLine.append('x');
        }
        for (int i = 0; i < 20; i++) {
            many.add(longLine.toString());
        }
        HologramWire.Message m = HologramWire.read(HologramWire.show(1, 0, 0, 0, many));
        assertEquals(HologramWire.MAX_LINES, m.lines.size());
        assertEquals(
            HologramWire.MAX_LINE_LENGTH,
            m.lines.get(0)
                .length());
        assertNull(HologramWire.read(new byte[] { 9, 0, 0, 0, 1 }), "unknown operation");
        assertNull(HologramWire.read(new byte[] { 0, 0, 0, 0, 1, 2 }), "truncated");
        assertNull(HologramWire.read(null));
    }

    private static final Object ALICE = new Object();
    private static final Object BOB = new Object();

    private static HologramTracker.Viewer<Object> at(Object who, String world, double x, boolean listens) {
        return new HologramTracker.Viewer<>(who, world, x, 64, 0, listens);
    }

    private static List<Byte> ops(List<HologramTracker.Out<Object>> out, Object who) {
        List<Byte> ops = new ArrayList<>();
        for (HologramTracker.Out<Object> o : out) {
            if (o.connection == who) {
                ops.add(HologramWire.read(o.data).op);
            }
        }
        return ops;
    }

    @Test
    void showsInRangeOnlyToListenersAndHidesWithHysteresis() {
        HologramTracker<Object> t = new HologramTracker<>();
        HologramTracker.State<Object> h = t.create("world", 0, 64, 0, Arrays.asList("a"), 32);
        List<HologramTracker.Out<Object>> out = t
            .update(Arrays.asList(at(ALICE, "world", 10, true), at(BOB, "world", 10, false)));
        assertEquals(Collections.singletonList(HologramWire.SHOW), ops(out, ALICE));
        assertTrue(ops(out, BOB).isEmpty(), "no Kunftig client: nothing sent");

        assertTrue(
            t.update(Collections.singletonList(at(ALICE, "world", 34, true)))
                .isEmpty(),
            "beyond the range but within the hysteresis: stays");
        assertEquals(
            Collections.singletonList(HologramWire.HIDE),
            ops(t.update(Collections.singletonList(at(ALICE, "world", 37, true))), ALICE));
        assertTrue(
            t.update(Collections.singletonList(at(ALICE, "world", 33, true)))
                .isEmpty(),
            "not shown again until inside the range");
        assertEquals(
            Collections.singletonList(HologramWire.SHOW),
            ops(t.update(Collections.singletonList(at(ALICE, "world", 31, true))), ALICE));
        assertEquals(
            Collections.singletonList(HologramWire.HIDE),
            ops(t.update(Collections.singletonList(at(ALICE, "world_nether", 0, true))), ALICE),
            "other world");
        assertEquals(1, h.id());
    }

    @Test
    void sendsChangesToViewersAndHidesOnRemove() {
        HologramTracker<Object> t = new HologramTracker<>();
        HologramTracker.State<Object> h = t.create("world", 0, 64, 0, Arrays.asList("a"), 32);
        List<HologramTracker.Viewer<Object>> both = Arrays
            .asList(at(ALICE, "world", 1, true), at(BOB, "world", 2, true));
        t.update(both);
        h.setLines(Arrays.asList("a"));
        assertTrue(
            t.update(both)
                .isEmpty(),
            "same lines: nothing");
        h.setLines(Arrays.asList("b", "c"));
        List<HologramTracker.Out<Object>> out = t.update(both);
        assertEquals(Collections.singletonList(HologramWire.LINES), ops(out, ALICE));
        assertEquals(Arrays.asList("b", "c"), HologramWire.read(out.get(0).data).lines);
        h.moveTo("world", 2, 64, 0);
        assertEquals(Collections.singletonList(HologramWire.MOVE), ops(t.update(both), BOB));
        h.remove();
        out = t.update(both);
        assertEquals(Collections.singletonList(HologramWire.HIDE), ops(out, ALICE));
        assertEquals(Collections.singletonList(HologramWire.HIDE), ops(out, BOB));
        assertEquals(0, t.size());
    }

    @Test
    void aRelogOrRespawnGetsItAgainAndTheOldConnectionGetsNothing() {
        HologramTracker<Object> t = new HologramTracker<>();
        t.create("world", 0, 64, 0, Arrays.asList("a"), 32);
        t.update(Collections.singletonList(at(ALICE, "world", 1, true)));
        Object aliceAgain = new Object();
        List<HologramTracker.Out<Object>> out = t.update(Collections.singletonList(at(aliceAgain, "world", 1, true)));
        assertEquals(1, out.size());
        assertSame(aliceAgain, out.get(0).connection);
        assertEquals(HologramWire.SHOW, HologramWire.read(out.get(0).data).op);
    }
}
