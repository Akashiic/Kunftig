package com.akashic.kunftig.blade;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Which frame of the blade motion plays, SlashBlade's way ({@code LayerMainBlade}): the time since the state started
 * walks down the chain of timeouts (each state lasts {@link Segment#durationMs}, then its timeout successor plays),
 * and within the state reached the time is looped or clamped to the state's frame span, then added to its start
 * frame. The chain ends in the idle state (looping). The motion's own speed is never slowed: a state's speed only
 * stretches how long it lasts (it holds its last frame). No Minecraft classes.
 */
public final class BladeTimeline {

    /** One state of the chain: its frames in the motion, whether it loops, and how long it lasts. */
    public static final class Segment {

        public final int start;
        public final int end;
        public final boolean loop;
        /** {@code (int)(|end - start| * 1000/30 / speed) + timeout}, SlashBlade's {@code getTimeoutMS}. */
        public final int durationMs;

        public Segment(int start, int end, boolean loop, int durationMs) {
            this.start = start;
            this.end = end;
            this.loop = loop;
            this.durationMs = Math.max(0, durationMs);
        }

        /** A state's duration as SlashBlade computes it. */
        public static int duration(int start, int end, double speed, int timeoutMs) {
            return (int) (Math.abs(end - start) * Vmd.MS_PER_FRAME / speed) + timeoutMs;
        }
    }

    private final List<Segment> chain;
    private final Segment idle;

    /**
     * @param chain the state just entered and its timeout successors, in order
     * @param idle  what plays when the chain is over (SlashBlade's standby: frames 0-1, looping)
     */
    public BladeTimeline(List<Segment> chain, Segment idle) {
        this.chain = Collections.unmodifiableList(new ArrayList<>(chain));
        this.idle = idle;
    }

    public List<Segment> chain() {
        return chain;
    }

    public Segment idle() {
        return idle;
    }

    /** The motion frame {@code elapsedMs} after the chain started, in a motion whose last frame is {@code maxFrame}. */
    public double frame(double elapsedMs, int maxFrame) {
        double t = Math.max(0, elapsedMs);
        Segment s = idle;
        for (Segment c : chain) {
            if (t <= c.durationMs) {
                s = c;
                break;
            }
            t -= c.durationMs;
        }
        double maxMs = maxFrame * Vmd.MS_PER_FRAME;
        double span = Math.min(maxMs, Math.abs(s.end - s.start) * Vmd.MS_PER_FRAME);
        if (s.loop && span > 0) {
            t %= span;
        }
        t = Math.min(span, t);
        t += s.start * Vmd.MS_PER_FRAME;
        return t / Vmd.MS_PER_FRAME;
    }

    /** The segment playing {@code elapsedMs} after the chain started (the idle one once it is over). */
    public Segment segment(double elapsedMs) {
        double t = Math.max(0, elapsedMs);
        for (Segment c : chain) {
            if (t <= c.durationMs) {
                return c;
            }
            t -= c.durationMs;
        }
        return idle;
    }
}
