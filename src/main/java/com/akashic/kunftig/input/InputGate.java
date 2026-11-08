package com.akashic.kunftig.input;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * What the server lets through of a client's input (API 11). A client is never trusted: an action reaches plugins only
 * when a plugin bound it for that player, its sequence is newer than the last one accepted, and the player has not
 * gone over the rate limit (a token bucket: {@link #BURST} messages at once, refilled by {@link #PER_TICK} per server
 * tick). Also remembers which bound actions each player holds down, so a plugin can ask. No Minecraft classes; main
 * thread only.
 *
 * @param <K> what identifies a player (his UUID)
 */
public final class InputGate<K> {

    /** Messages a client may send at once. */
    public static final int BURST = 20;
    /** Messages a client may send per server tick, on average (20 per second: a key down and up 10 times). */
    public static final double PER_TICK = 1.0;

    private static final class Entry {

        Set<String> bound = Collections.emptySet();
        final Set<String> held = new LinkedHashSet<>();
        boolean hasSequence;
        int lastSequence;
        double tokens = BURST;
        long refilledAt;
        int dropped;
        int keys;
        final long[] lastPress = new long[InputWire.KEY_COUNT];

        Entry() {
            java.util.Arrays.fill(lastPress, -1L);
        }
    }

    private final Map<K, Entry> entries = new HashMap<>();

    /**
     * The actions a player's client should report from now (unknown names are left out); an empty set stops them.
     *
     * @return the clean set, as the client is told
     */
    public Set<String> bind(K player, Collection<String> actions) {
        Set<String> clean = new LinkedHashSet<>();
        if (actions != null) {
            for (String a : actions) {
                if (InputWire.isAction(a)) {
                    clean.add(a);
                }
            }
        }
        Entry e = entries.computeIfAbsent(player, k -> new Entry());
        e.bound = Collections.unmodifiableSet(clean);
        e.held.retainAll(clean);
        if (!clean.contains(InputWire.KEYS_ACTION)) {
            e.keys = 0;
        }
        return e.bound;
    }

    /** The actions bound for a player (empty when none). */
    public Set<String> bound(K player) {
        Entry e = entries.get(player);
        return e == null ? Collections.<String>emptySet() : e.bound;
    }

    /** Whether a bound action is held down by the player, as his last accepted message says. */
    public boolean isHeld(K player, String action) {
        Entry e = entries.get(player);
        return e != null && e.held.contains(action);
    }

    /** Messages dropped for this player so far (rate limit, old sequence, action not bound). */
    public int dropped(K player) {
        Entry e = entries.get(player);
        return e == null ? 0 : e.dropped;
    }

    /** Forgets a player (who left). */
    public void remove(K player) {
        entries.remove(player);
    }

    /**
     * Forgets what a player's client said (he reconnected or respawned: new connection, new sequence), keeping what is
     * bound.
     */
    public void resetConnection(K player) {
        Entry e = entries.get(player);
        if (e != null) {
            e.hasSequence = false;
            e.held.clear();
            e.keys = 0;
            e.tokens = BURST;
        }
    }

    public Collection<K> players() {
        return Collections.unmodifiableSet(entries.keySet());
    }

    /** The rate limit and the sequence, shared by every message of a connection. */
    private static boolean admit(Entry e, int sequence, long now) {
        e.tokens = Math.min(BURST, e.tokens + Math.max(0, now - e.refilledAt) * PER_TICK);
        e.refilledAt = now;
        if (e.tokens < 1) {
            e.dropped++;
            return false;
        }
        e.tokens -= 1; // every message costs, even one dropped below: a flood is a flood
        if (e.hasSequence && sequence - e.lastSequence <= 0) {
            e.dropped++;
            return false; // a repeat or an old message
        }
        e.hasSequence = true;
        e.lastSequence = sequence;
        return true;
    }

    /**
     * Whether a KEYS message gets through at server tick {@code now} (only while {@link InputWire#KEYS_ACTION} is
     * bound); one that does updates the held keys and the tick each newly held key went down.
     *
     * @return the keys held before, or -1 when dropped
     */
    public int acceptKeys(K player, InputWire.Keys keys, long now) {
        Entry e = entries.get(player);
        if (keys == null || e == null || !admit(e, keys.sequence, now)) {
            return -1;
        }
        if (!e.bound.contains(InputWire.KEYS_ACTION)) {
            e.dropped++;
            return -1;
        }
        int old = e.keys;
        int pressed = keys.keys & ~old;
        for (int b = 0; b < InputWire.KEY_COUNT; b++) {
            if ((pressed & (1 << b)) != 0) {
                e.lastPress[b] = now;
            }
        }
        e.keys = keys.keys;
        return old;
    }

    /** The keys a player holds, as his last accepted KEYS message says (0 when keys are not bound). */
    public int keys(K player) {
        Entry e = entries.get(player);
        return e == null || !e.bound.contains(InputWire.KEYS_ACTION) ? 0 : e.keys;
    }

    /** The server tick a key ({@link InputWire#KEY_FORWARD} ...) last went down, or -1. */
    public long lastPress(K player, int key) {
        Entry e = entries.get(player);
        int b = Integer.numberOfTrailingZeros(key);
        return e == null || b >= InputWire.KEY_COUNT ? -1 : e.lastPress[b];
    }

    /**
     * Whether a message from a player gets through at server tick {@code now}. A message that gets through updates
     * what the player holds down; one that does not changes nothing but the counters.
     */
    public boolean accept(K player, InputWire.Action action, long now) {
        if (action == null) {
            return false;
        }
        Entry e = entries.get(player);
        if (e == null || !admit(e, action.sequence, now)) {
            return false; // nothing bound for this player (no plugin asked for his input), a flood or an old message
        }
        if (!e.bound.contains(action.action)) {
            e.dropped++;
            return false;
        }
        if (action.pressed) {
            e.held.add(action.action);
        } else {
            e.held.remove(action.action);
        }
        return true;
    }
}
