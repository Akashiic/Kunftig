package com.akashic.kunftig.rodmodel;

/**
 * Which state one player's 3D rod is in, and since when (client ticks). The client infers some states on its own
 * (issue #13): {@code sacar} when a rod comes to the hand, {@code lancar} when the line goes out, {@code parada} when
 * the line comes back from a looping state, and (API 9) the next attack of a combo when the local player swings
 * ({@code ao_atacar}); the others come from the server ({@code KunftigRods}). The latest event wins, except that the
 * server confirming a predicted attack does not restart it; a state that does not loop goes back to {@code parada}
 * when it ends. No Minecraft classes; client thread.
 */
public final class RodTimeline {

    /** What the timeline needs to know about the states of the rod in hand. */
    public interface Clips {

        boolean has(String state);

        boolean loops(String state);

        /** Length in client ticks, at least 1. */
        int lengthTicks(String state);

        /**
         * The state an attack starts {@code elapsedTicks} into {@code state} ({@code ao_atacar}), or null: the client
         * waits for the server.
         */
        default String attack(String state, long elapsedTicks) {
            return null;
        }
    }

    /**
     * Client ticks a predicted state waits for the server to confirm it: a state from the server that matches one
     * predicted within this many ticks is the same attack and does not restart the animation (20: 1 s of ping).
     */
    public static final int PREDICTION_TICKS = 20;
    /** Predictions kept at once (a combo clicked faster than the round trip). */
    private static final int MAX_PREDICTIONS = 8;

    private final java.util.ArrayDeque<String> predicted = new java.util.ArrayDeque<>();
    private final java.util.ArrayDeque<Long> predictedAt = new java.util.ArrayDeque<>();

    private String held;
    private boolean seen;
    private boolean lineOut;
    private long lineSince;
    private String state;
    private long start;
    private String previous;
    private long previousStart;
    private float tension;
    private long lastSeen;

    /** The current state, or null without a 3D rod in the hand. */
    public String state() {
        return state;
    }

    /** The client tick the current state started at. */
    public long start() {
        return start;
    }

    /** The state before the current one (for blending into it), or null. */
    public String previous() {
        return previous;
    }

    /** The client tick the previous state started at. */
    public long previousStart() {
        return previousStart;
    }

    /** The fight's tension, 0 to 1. */
    public float tension() {
        return tension;
    }

    /** Seconds into the current state. */
    public double seconds(long now, float partialTicks) {
        return Math.max(0, now - start + partialTicks) / 20.0;
    }

    /** Whether the player's line was out at the last {@link #observe}. */
    public boolean lineOut() {
        return lineOut;
    }

    /** The client tick the line last went out or came back (for blending the arms between the two poses). */
    public long lineSince() {
        return lineSince;
    }

    /** The last tick {@link #observe} saw the player. */
    public long lastSeen() {
        return lastSeen;
    }

    /**
     * Once per client tick for each player in the world.
     *
     * @param heldModel the id of the 3D rod in the player's main hand (without suffixes), or null
     * @param line      whether the player's line is out ({@code fishEntity})
     */
    public void observe(long now, String heldModel, boolean line, Clips clips) {
        lastSeen = now;
        boolean first = !seen;
        seen = true;
        if (heldModel == null) {
            held = null;
            state = null;
            lineOut = line;
            predicted.clear();
            predictedAt.clear();
            return;
        }
        if (!heldModel.equals(held)) {
            held = heldModel;
            predicted.clear();
            predictedAt.clear();
            lineOut = line;
            if (first && state != null) {
                return; // the server's state arrived before the player did
            }
            begin(first || !clips.has(RodModelDef.DRAW) ? RodModelDef.IDLE : RodModelDef.DRAW, now);
            return;
        }
        if (state == null || !clips.has(state)) {
            begin(RodModelDef.IDLE, now);
        } else if (!clips.loops(state)) {
            int length = clips.lengthTicks(state);
            if (now - start >= length) {
                begin(RodModelDef.IDLE, start + length);
            }
        }
        if (line != lineOut) {
            lineSince = now;
        }
        if (line && !lineOut) {
            cast(now, clips);
        } else if (!line && lineOut && clips.loops(state) && !RodModelDef.IDLE.equals(state)) {
            begin(RodModelDef.IDLE, now); // the fight or the reeling ended with the line
        }
        lineOut = line;
    }

    /** Marks the timeline as seen (a server message about a player not spawned here yet). */
    public void touch(long now) {
        lastSeen = Math.max(lastSeen, now);
    }

    /** The local player just cast (before the server spawns the bobber). */
    public void localCast(long now, Clips clips) {
        if (held != null) {
            cast(now, clips);
        }
    }

    private void cast(long now, Clips clips) {
        if (!clips.has(RodModelDef.CAST)) {
            return;
        }
        if (RodModelDef.CAST.equals(state) && now - start < clips.lengthTicks(RodModelDef.CAST)) {
            return; // the local click already started it; the bobber appearing is the same cast
        }
        begin(RodModelDef.CAST, now);
    }

    /** A state from the server (or the debug command), started at {@code startTick}. */
    public void server(String newState, long startTick) {
        begin(newState, startTick);
    }

    /**
     * The local player attacked (a swing, the same click the server sees as an arm swing). Starts the state the
     * current one's {@code ao_atacar} names, if the attack falls in its window, and remembers it as predicted.
     *
     * @return whether a state was started
     */
    public boolean localAttack(long now, Clips clips) {
        expire(now);
        if (held == null || state == null) {
            return false;
        }
        String next = clips.attack(state, now - start);
        if (next == null || !clips.has(next)) {
            return false;
        }
        begin(next, now);
        if (predicted.size() == MAX_PREDICTIONS) {
            predicted.poll();
            predictedAt.poll();
        }
        predicted.add(next);
        predictedAt.add(now);
        return true;
    }

    /**
     * A state from the server at client tick {@code now}, started at {@code startTick}. If it is one the client
     * predicted ({@link #localAttack}) and still waits for, it is that attack confirmed: the animation already
     * playing goes on (along with any later prediction). Anything else is the server's word and replaces what plays.
     */
    public void fromServer(String newState, long startTick, long now) {
        expire(now);
        if (predicted.contains(newState)) {
            while (!predicted.isEmpty()) {
                predictedAt.poll();
                if (newState.equals(predicted.poll())) {
                    return; // confirmed; older predictions were confirmed before or skipped by the server
                }
            }
        }
        predicted.clear();
        predictedAt.clear();
        begin(newState, startTick);
    }

    /** States predicted and not yet confirmed (oldest first; for tests and the debug command). */
    public java.util.List<String> pending() {
        return new java.util.ArrayList<>(predicted);
    }

    private void expire(long now) {
        while (!predictedAt.isEmpty() && now - predictedAt.peek() > PREDICTION_TICKS) {
            predictedAt.poll();
            predicted.poll();
        }
    }

    public void setTension(float value) {
        tension = Float.isNaN(value) ? 0 : Math.max(0, Math.min(1, value));
    }

    private void begin(String newState, long at) {
        previous = state;
        previousStart = start;
        state = newState;
        start = at;
    }
}
