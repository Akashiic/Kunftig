package com.akashic.kunftig.rodmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class RodTimelineTest {

    private static final String ROD = "akashicfishing:vara/vara-solar";

    /** The states of the rod pack: lengths in ticks, negative for loops. */
    private static final RodTimeline.Clips CLIPS = new RodTimeline.Clips() {

        private final Map<String, Integer> lengths = new HashMap<>();

        {
            lengths.put("parada", -80);
            lengths.put("sacar", 18);
            lengths.put("lancar", 18);
            lengths.put("mordida", 14);
            lengths.put("briga", -40);
            lengths.put("recolher", -12);
            lengths.put("captura", 32);
        }

        @Override
        public boolean has(String state) {
            return lengths.containsKey(state);
        }

        @Override
        public boolean loops(String state) {
            Integer l = lengths.get(state);
            return l != null && l < 0;
        }

        @Override
        public int lengthTicks(String state) {
            return Math.abs(lengths.getOrDefault(state, 1));
        }
    };

    @Test
    void aPlayerFirstSeenWithARodIsIdleThenDrawsOnSwitch() {
        RodTimeline t = new RodTimeline();
        t.observe(100, ROD, false, CLIPS);
        assertEquals("parada", t.state());
        t.observe(101, null, false, CLIPS);
        assertNull(t.state());
        t.observe(102, ROD, false, CLIPS);
        assertEquals("sacar", t.state());
        assertEquals(102, t.start());
        // Ends 18 ticks later, then idles from where it ended.
        t.observe(119, ROD, false, CLIPS);
        assertEquals("sacar", t.state());
        t.observe(120, ROD, false, CLIPS);
        assertEquals("parada", t.state());
        assertEquals(120, t.start());
        // Another rod also draws.
        t.observe(130, "akashicfishing:vara/vara", false, CLIPS);
        assertEquals("sacar", t.state());
    }

    @Test
    void castingOnceWhetherTheClickOrTheBobberComesFirst() {
        RodTimeline t = new RodTimeline();
        t.observe(0, ROD, false, CLIPS);
        t.localCast(10, CLIPS);
        assertEquals("lancar", t.state());
        // The bobber arrives 3 ticks later (ping): same cast, no restart.
        t.observe(13, ROD, true, CLIPS);
        assertEquals(10, t.start());
        // Then idle with the line out.
        t.observe(28, ROD, true, CLIPS);
        assertEquals("parada", t.state());
        // Another player: only the bobber tells.
        RodTimeline other = new RodTimeline();
        other.observe(0, ROD, false, CLIPS);
        other.observe(5, ROD, true, CLIPS);
        assertEquals("lancar", other.state());
        assertEquals(5, other.start());
    }

    @Test
    void serverStatesWinUntilTheyEndOrTheLineComesBack() {
        RodTimeline t = new RodTimeline();
        t.observe(0, ROD, false, CLIPS);
        t.observe(1, ROD, true, CLIPS);
        t.server("mordida", 40);
        t.observe(41, ROD, true, CLIPS);
        assertEquals("mordida", t.state());
        t.server("briga", 50);
        t.setTension(0.7F);
        t.observe(200, ROD, true, CLIPS);
        assertEquals("briga", t.state());
        assertEquals(0.7F, t.tension());
        // The fish got away and the line came back without the server saying so.
        t.observe(201, ROD, false, CLIPS);
        assertEquals("parada", t.state());
        // A catch keeps playing while the line comes back, then idles.
        t.observe(210, ROD, true, CLIPS);
        t.server("captura", 220);
        t.observe(221, ROD, false, CLIPS);
        assertEquals("captura", t.state());
        t.observe(252, ROD, false, CLIPS);
        assertEquals("parada", t.state());
    }

    @Test
    void remembersThePreviousStateForBlending() {
        RodTimeline t = new RodTimeline();
        t.observe(0, ROD, false, CLIPS);
        t.server("briga", 10);
        t.server("captura", 50);
        assertEquals("briga", t.previous());
        assertEquals(10, t.previousStart());
        t.observe(82, ROD, false, CLIPS);
        assertEquals("parada", t.state());
        assertEquals("captura", t.previous());
    }

    @Test
    void aServerStateBeforeThePlayerAppearsIsKept() {
        RodTimeline t = new RodTimeline();
        t.server("briga", 5);
        t.observe(6, ROD, true, CLIPS);
        assertEquals("briga", t.state());
        assertEquals(5, t.start());
    }

    @Test
    void unknownStatesIdleAndTensionIsClamped() {
        RodTimeline t = new RodTimeline();
        t.observe(0, ROD, false, CLIPS);
        t.server("danca", 1);
        t.observe(2, ROD, false, CLIPS);
        assertEquals("parada", t.state());
        t.setTension(3);
        assertEquals(1F, t.tension());
        t.setTension(Float.NaN);
        assertEquals(0F, t.tension());
        assertEquals(0.5, new RodTimeline().seconds(10, 0), 1e-9);
    }
}
