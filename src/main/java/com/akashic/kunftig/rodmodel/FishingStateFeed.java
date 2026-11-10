package com.akashic.kunftig.rodmodel;

import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * What 3D Futura Engine is told about each player's rod so his model fishes with it (futura3d API 4): the rod state
 * the server set ({@code KunftigRods}, from the {@code kunftig:rod} channel) and the fight's tension, as the external
 * states {@code fishing.state} and {@code fishing.tension}. Only the server's states: the draw, the cast and the line
 * coming back the model sees on its own, so the 3D rod's own inferred states are not sent. Only changes go out; a new
 * start of the same state (a second bite) clears and sets it, which restarts the model's clip. A state that plays
 * once and is already older than {@link #STALE_TICKS} when first told (a player who comes close long after a catch)
 * is told as {@code parada}, so the model does not play it late. No Minecraft classes.
 */
public final class FishingStateFeed {

    public static final String STATE = "fishing.state";
    public static final String TENSION = "fishing.tension";
    /** The state told instead of a stale one. */
    public static final String IDLE = "parada";
    /** Ticks after which a state that plays once is over for a viewer who hears of it only now. */
    public static final int STALE_TICKS = 20;

    /** Where the states go (futura3d's {@code setExternalState}). */
    public interface Sink {

        void set(String key, String value);
    }

    private static final class Last {

        /** The server's state and start last seen. */
        String raw;
        long start = Long.MIN_VALUE;
        /** What the model was told. */
        String told;
        int tension = -1;
    }

    private final Map<Object, Last> last = new WeakHashMap<>();

    /** Whether the model's fishing clip of this state loops (a fight goes on however long ago it started). */
    public static boolean loops(String state) {
        return "briga".equals(state) || "recolher".equals(state) || "fight".equals(state) || "reel".equals(state);
    }

    /**
     * @param player  the player (a weak key)
     * @param state   the server's rod state for him, or null when none is known (then everything is cleared)
     * @param start   the tick it started at (on the same clock as {@code now})
     * @param tension the fight's tension, 0 to 1
     */
    public void update(Object player, String state, long start, float tension, long now, Sink sink) {
        Last l = last.get(player);
        if (l == null) {
            if (state == null) {
                return;
            }
            l = new Last();
            last.put(player, l);
        }
        if (state == null) {
            if (l.told != null) {
                sink.set(STATE, null);
            }
            if (l.tension >= 0) {
                sink.set(TENSION, null);
            }
            last.remove(player);
            return;
        }
        if (!state.equals(l.raw) || start != l.start) {
            boolean firstHeard = l.raw == null;
            l.raw = state;
            l.start = start;
            String tell = firstHeard && !loops(state) && now - start > STALE_TICKS ? IDLE : state;
            if (tell.equals(l.told)) {
                if (!IDLE.equals(tell)) {
                    sink.set(STATE, null); // the same state started again: cleared and set, the clip starts over
                    sink.set(STATE, tell);
                }
            } else {
                sink.set(STATE, tell);
                l.told = tell;
            }
        }
        int q = RodWire.quantize(tension);
        if (q != l.tension) {
            l.tension = q;
            sink.set(TENSION, String.format(Locale.ROOT, "%.2f", q / (float) RodWire.TENSION_STEPS));
        }
    }

    /** Forgets every player (a new world). */
    public void clear() {
        last.clear();
    }
}
