package com.akashic.kunftig.blade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

/** combo_a1 (1-10) times out into combo_a1_end (10-21), then combo_a1_end2 (21-41), then standby (0-1, loop). */
class BladeTimelineTest {

    private static final BladeTimeline.Segment A1 = new BladeTimeline.Segment(
        1,
        10,
        false,
        BladeTimeline.Segment.duration(1, 10, 1, 0));
    private static final BladeTimeline.Segment A1_END = new BladeTimeline.Segment(
        10,
        21,
        false,
        BladeTimeline.Segment.duration(10, 21, 1, 0));
    private static final BladeTimeline.Segment A1_END2 = new BladeTimeline.Segment(
        21,
        41,
        false,
        BladeTimeline.Segment.duration(21, 41, 1, 0));
    private static final BladeTimeline.Segment STANDBY = new BladeTimeline.Segment(
        0,
        1,
        true,
        BladeTimeline.Segment.duration(0, 1, 1, 1000));

    @Test
    void durationsAreSlashBlades() {
        assertEquals(300, A1.durationMs, "(int)(9 * 33.33)");
        assertEquals(366, A1_END.durationMs);
        assertEquals(666, A1_END2.durationMs);
        assertEquals(1033, STANDBY.durationMs);
        assertEquals(416, BladeTimeline.Segment.duration(1923, 1928, 0.4, 0), "judgement_cut_slash, speed 0.4");
    }

    @Test
    void framesWalkTheChainAtRealTime() {
        BladeTimeline t = new BladeTimeline(Arrays.asList(A1, A1_END, A1_END2), STANDBY);
        assertEquals(1, t.frame(0, 2299), 1e-9);
        assertEquals(1 + 1.5, t.frame(50, 2299), 1e-9, "one tick = 1.5 frames");
        assertEquals(10, t.frame(300, 2299), 1e-9, "end of combo_a1");
        assertSame(A1, t.segment(300));
        assertSame(A1_END, t.segment(301));
        assertEquals(10 + 0.03, t.frame(301, 2299), 1e-9);
        assertEquals(20.98, t.frame(300 + 366, 2299), 1e-9, "durations are truncated to whole ms, as in SlashBlade");
        assertEquals(40.98, t.frame(300 + 366 + 666, 2299), 1e-9);
        // then standby, looping its single frame span
        assertSame(STANDBY, t.segment(2000));
        assertEquals(0.5, t.frame(300 + 366 + 666 + 50 / 3.0, 2299), 1e-6);
    }

    @Test
    void aSlowStateHoldsItsLastFrame() {
        BladeTimeline.Segment slow = new BladeTimeline.Segment(1923, 1928, false, 416);
        BladeTimeline t = new BladeTimeline(Arrays.asList(slow), STANDBY);
        assertEquals(1928, t.frame(200, 2299), 1e-9, "the motion is not slowed: 166 ms of frames, then held");
        assertEquals(1928, t.frame(416, 2299), 1e-9);
    }
}
