package com.akashic.kunftig.api.bukkit.blade;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What a player's SlashBlade-style blade plays from now (API 11): a chain of combo states of the blade motion (each a
 * frame span of the VMD, looping or not, lasting some milliseconds before its timeout successor plays), what plays
 * when the chain is over (SlashBlade's standby), and optionally a clip of the player body motion. Built with
 * {@link #builder}; immutable.
 *
 * <pre>
 * // SlashBlade's combo_a1: 1-10, then combo_a1_end 10-21, then combo_a1_end2 21-41; the body clip 1-41
 * BladeMotion a1 = BladeMotion.builder()
 *     .state(1, 10, false, BladeMotion.duration(1, 10, 1, 0))
 *     .state(10, 21, false, BladeMotion.duration(10, 21, 1, 0))
 *     .state(21, 41, false, BladeMotion.duration(21, 41, 1, 0))
 *     .body(1, 41, true)
 *     .build();
 * </pre>
 */
public final class BladeMotion {

    /** One combo state of the chain. */
    public static final class State {

        private final int start;
        private final int end;
        private final boolean loop;
        private final int durationMs;

        State(int start, int end, boolean loop, int durationMs) {
            this.start = start;
            this.end = end;
            this.loop = loop;
            this.durationMs = durationMs;
        }

        public int getStart() {
            return start;
        }

        public int getEnd() {
            return end;
        }

        public boolean isLoop() {
            return loop;
        }

        public int getDurationMs() {
            return durationMs;
        }
    }

    /** SlashBlade's standby: frames 0-1, looping, 1033 ms. */
    public static final State STANDBY = new State(0, 1, true, duration(0, 1, 1, 1000));
    public static final int MAX_STATES = 16;

    private final List<State> chain;
    private final State idle;
    private final int bodyStart;
    private final int bodyEnd;
    private final boolean blendLegs;

    private BladeMotion(Builder b) {
        chain = Collections.unmodifiableList(new ArrayList<>(b.chain));
        idle = b.idle;
        bodyStart = b.bodyStart;
        bodyEnd = b.bodyEnd;
        blendLegs = b.blendLegs;
    }

    /** SlashBlade's state duration: {@code (int)(|end - start| * 1000/30 / speed) + timeoutMs}. */
    public static int duration(int start, int end, double speed, int timeoutMs) {
        return (int) (Math.abs(end - start) * (1000.0 / 30.0) / speed) + timeoutMs;
    }

    public static Builder builder() {
        return new Builder();
    }

    public List<State> getChain() {
        return chain;
    }

    public State getIdle() {
        return idle;
    }

    /** Whether a body clip plays. */
    public boolean hasBody() {
        return bodyEnd > bodyStart;
    }

    public int getBodyStart() {
        return bodyStart;
    }

    public int getBodyEnd() {
        return bodyEnd;
    }

    /** Whether the legs add the motion to vanilla walking (false in aerial moves: the motion only). */
    public boolean isBlendLegs() {
        return blendLegs;
    }

    /** Builds a {@link BladeMotion}. */
    public static final class Builder {

        private final List<State> chain = new ArrayList<>();
        private State idle = STANDBY;
        private int bodyStart;
        private int bodyEnd;
        private boolean blendLegs = true;

        private Builder() {}

        private static void frames(int start, int end) {
            if (start < 0 || end < 0 || start > 65535 || end > 65535) {
                throw new IllegalArgumentException("frames 0 to 65535: " + start + ", " + end);
            }
        }

        /** The next state of the chain. */
        public Builder state(int start, int end, boolean loop, int durationMs) {
            frames(start, end);
            if (chain.size() == MAX_STATES) {
                throw new IllegalArgumentException("at most " + MAX_STATES + " states");
            }
            chain.add(new State(start, end, loop, Math.max(0, durationMs)));
            return this;
        }

        /** What plays when the chain is over (default {@link #STANDBY}). */
        public Builder idle(int start, int end, boolean loop, int durationMs) {
            frames(start, end);
            idle = new State(start, end, loop, Math.max(0, durationMs));
            return this;
        }

        /** A clip of the player body motion, {@code (end - start) * 2/3} ticks long. */
        public Builder body(int start, int end, boolean blendLegsWithWalking) {
            frames(start, end);
            bodyStart = start;
            bodyEnd = end;
            blendLegs = blendLegsWithWalking;
            return this;
        }

        public BladeMotion build() {
            return new BladeMotion(this);
        }
    }
}
