package com.akashic.kunftig.display;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

/** API 11: displays glide, fly and turn on every axis, without breaking older clients. */
class DisplayMotionTest {

    @Test
    void newFieldsTravelAndOldMessagesStillRead() {
        DisplayWire.Message show = DisplayWire
            .read(DisplayWire.show(3, 1, 2, 3, 90, -30, 45, 0.5, 0, -0.25, "ns:drive", "", "voar", 0, null));
        assertEquals(-30, show.pitch, 1e-6);
        assertEquals(45, show.roll, 1e-6);
        assertEquals(0.5, show.vx, 1e-9);
        assertEquals(-0.25, show.vz, 1e-9);
        assertEquals("voar", show.state);

        DisplayWire.Message move = DisplayWire.read(DisplayWire.move(3, 4, 5, 6, 10, 7, -20, 15));
        assertEquals(7, move.glideTicks);
        assertEquals(-20, move.pitch, 1e-6);
        assertEquals(15, move.roll, 1e-6);
        assertEquals(4, move.x, 1e-9);

        // an older server's messages: no API 11 fields, defaults
        DisplayWire.Message oldMove = DisplayWire.read(DisplayWire.move(3, 4, 5, 6, 10));
        assertEquals(0, oldMove.glideTicks);
        assertEquals(0, oldMove.pitch, 0);
        DisplayWire.Message oldShow = DisplayWire
            .read(DisplayWire.show(3, 1, 2, 3, 90, "ns:m", "", "a", 0, Collections.<byte[]>emptyList()));
        assertEquals(0, oldShow.vx, 0);

        // the new messages begin exactly like an API 8 MOVE (op, id, x, y, z, yaw: 33 bytes), then the API 9 ticks
        // (2) and the API 11 pitch and roll (8): an older client reads its part and skips the rest
        byte[] newBytes = DisplayWire.move(3, 4, 5, 6, 10, 7, -20, 15);
        assertEquals(33 + 2 + 8, newBytes.length);
        DisplayWire.Message api8 = DisplayWire.read(java.util.Arrays.copyOf(newBytes, 33));
        assertEquals(4, api8.x, 1e-9);
        assertEquals(0, api8.glideTicks, "an API 8 MOVE jumps");
        DisplayWire.Message api9 = DisplayWire.read(java.util.Arrays.copyOf(newBytes, 35));
        assertEquals(7, api9.ticks, "an API 9 MOVE glides, upright");
        assertEquals(0, api9.pitch, 0);

        DisplayWire.Message motion = DisplayWire.read(DisplayWire.motion(3, 1, 2, 3, 99, Double.NaN, -0.5));
        assertEquals(DisplayWire.MOTION, motion.op);
        assertEquals(DisplayWire.MAX_SPEED, motion.vx, 0, "clamped");
        assertEquals(0, motion.vy, 0, "not a number: still");
        assertEquals(-0.5, motion.vz, 1e-9);
        assertNull(DisplayWire.read(new byte[] { DisplayWire.MOTION, 0, 0, 0, 1, 0 }), "truncated");
    }

    @Test
    void glidesInStraightStepsAndTurnsTheShortWay() {
        DisplayMotion m = new DisplayMotion();
        m.jump(0, 64, 0, 170, 0, 0);
        m.glide(4, 64, 0, -170, 0, 0, 4);
        assertTrue(m.moving());
        m.tick();
        assertEquals(1, m.x(1), 1e-9);
        assertEquals(175F, m.yaw(1), 1e-4, "170 -> 190 (= -170), not back through 0");
        assertEquals(0.5, m.x(0.5F), 1e-9, "between ticks: interpolated");
        m.tick();
        m.tick();
        m.tick();
        assertEquals(4, m.x(1), 1e-9);
        assertEquals(190F, m.yaw(1), 1e-4);
        assertFalse(m.moving());
        m.tick();
        assertEquals(4, m.x(0), 1e-9, "stays");
    }

    @Test
    void fliesByItsMotionUntilToldOtherwise() {
        DisplayMotion m = new DisplayMotion();
        m.jump(0, 0, 0, 0, 0, 0);
        m.fly(10, 0, 0, 0.5, 0, -1);
        for (int i = 0; i < 4; i++) {
            m.tick();
        }
        assertEquals(12, m.x(1), 1e-9);
        assertEquals(-4, m.z(1), 1e-9);
        m.glide(0, 0, 0, 0, 0, 0, 0);
        assertFalse(m.moving(), "a jump stops the flight");
        assertEquals(0, m.x(0.3F), 1e-9);
    }

    @Test
    void theServerFliesItTooAndSendsOneMessage() {
        DisplayTracker<String> t = new DisplayTracker<>();
        DisplayTracker.State<String> d = t.create("w", 0, 64, 0, 0, "ns:drive", "", "voar", null, 48);
        List<DisplayTracker.Viewer<String>> ana = Collections
            .singletonList(new DisplayTracker.Viewer<>("ana", "w", 0, 64, 0, true));
        assertEquals(
            DisplayWire.SHOW,
            DisplayWire.read(
                t.update(ana)
                    .get(0).data).op);
        d.setMotion(1, 0, 0);
        List<DisplayTracker.Out<String>> out = t.update(ana);
        assertEquals(1, out.size());
        DisplayWire.Message m = DisplayWire.read(out.get(0).data);
        assertEquals(DisplayWire.MOTION, m.op);
        assertEquals(0, m.x, 1e-9, "from where it is");
        assertEquals(1, m.vx, 1e-9);
        for (int i = 0; i < 5; i++) {
            assertTrue(
                t.update(ana)
                    .isEmpty(),
                "no message while it flies");
        }
        assertEquals(6, d.position()[0], 1e-9, "the server moved it too");
        // a late viewer gets it where it is now, still flying
        List<DisplayTracker.Viewer<String>> both = Arrays
            .asList(ana.get(0), new DisplayTracker.Viewer<>("bia", "w", 6, 64, 0, true));
        DisplayWire.Message late = DisplayWire.read(
            t.update(both)
                .get(0).data);
        assertEquals(DisplayWire.SHOW, late.op);
        assertEquals(6, late.x, 1e-9);
        assertEquals(1, late.vx, 1e-9);
    }

    @Test
    void movingWithAGlideStopsTheFlightAndAnotherWorldJumps() {
        DisplayTracker<String> t = new DisplayTracker<>();
        DisplayTracker.State<String> d = t.create("w", 0, 64, 0, 0, "ns:espada", "", "voar", null, 48);
        List<DisplayTracker.Viewer<String>> ana = Collections
            .singletonList(new DisplayTracker.Viewer<>("ana", "w", 0, 64, 0, true));
        t.update(ana);
        d.setMotion(0.5, 0, 0);
        t.update(ana);
        d.moveTo("w", 3, 65, 0, 90, -45, 30, 5);
        List<DisplayTracker.Out<String>> out = t.update(ana);
        assertEquals(2, out.size());
        DisplayWire.Message move = DisplayWire.read(out.get(0).data);
        assertEquals(DisplayWire.MOVE, move.op);
        assertEquals(5, move.glideTicks);
        assertEquals(-45, move.pitch, 1e-6);
        assertEquals(30, move.roll, 1e-6);
        DisplayWire.Message stop = DisplayWire.read(out.get(1).data);
        assertEquals(DisplayWire.MOTION, stop.op);
        assertEquals(0, stop.vx, 0);
        assertEquals(3, d.position()[0], 1e-9);
        t.update(ana);
        assertEquals(3, d.position()[0], 1e-9, "stopped");

        d.moveTo("nether", 0, 64, 0, 0, 0, 0, 20);
        List<DisplayTracker.Viewer<String>> there = Collections
            .singletonList(new DisplayTracker.Viewer<>("ana", "nether", 0, 64, 0, true));
        out = t.update(there);
        DisplayWire.Message jump = DisplayWire.read(out.get(0).data);
        assertEquals(DisplayWire.MOVE, jump.op, "she went along: it moves for her");
        assertEquals(0, jump.glideTicks, "another world is always a jump");
    }
}
