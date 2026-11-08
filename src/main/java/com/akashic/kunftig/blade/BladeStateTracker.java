package com.akashic.kunftig.blade;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The blade states the server set and who has been told: run once per server tick, it returns the messages to send
 * ({@link BladeWire}). The player always hears his own state; others hear it while in the same world within
 * {@link #RANGE} blocks, and get it again (with the time already played) when they come back. A viewer is its
 * connection object {@code C}: a player who relogs or respawns has a new one and is told again. No Minecraft classes;
 * main thread only.
 *
 * @param <K> what identifies a player across respawns (his UUID)
 * @param <C> the connection (player entity) type
 */
public final class BladeStateTracker<K, C> {

    public static final double RANGE = 64;
    public static final double HYSTERESIS = 8;

    /** A player as seen this tick. */
    public static final class Viewer<C> {

        final C connection;
        final String world;
        final double x;
        final double y;
        final double z;
        final boolean listens;

        public Viewer(C connection, String world, double x, double y, double z, boolean listens) {
            this.connection = connection;
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.listens = listens;
        }
    }

    /** A player with a blade state, as seen this tick. */
    public static final class Holder {

        final int entityId;
        final String world;
        final double x;
        final double y;
        final double z;

        public Holder(int entityId, String world, double x, double y, double z) {
            this.entityId = entityId;
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    public static final class Out<C> {

        public final C connection;
        public final byte[] data;

        Out(C connection, byte[] data) {
            this.connection = connection;
            this.data = data;
        }
    }

    private static final class Entry<C> {

        List<BladeTimeline.Segment> chain = Collections.emptyList();
        BladeTimeline.Segment idle;
        BladeWire.Body body;
        long start;
        boolean charged;
        boolean stateChanged;
        boolean chargeChanged;
        int entityId = Integer.MIN_VALUE;
        final Set<C> told = Collections.newSetFromMap(new IdentityHashMap<>());
    }

    private final Map<K, Entry<C>> entries = new LinkedHashMap<>();

    /** A new chain from tick {@code now} (sent from the next {@link #update}). */
    public void play(K holder, List<BladeTimeline.Segment> chain, BladeTimeline.Segment idle, BladeWire.Body body,
        long now) {
        Entry<C> e = entries.computeIfAbsent(holder, k -> new Entry<>());
        e.chain = Collections.unmodifiableList(new ArrayList<>(chain));
        e.idle = idle;
        e.body = body;
        e.start = now;
        e.stateChanged = true;
    }

    public void setCharged(K holder, boolean charged) {
        Entry<C> e = entries.computeIfAbsent(holder, k -> new Entry<>());
        if (e.charged != charged) {
            e.charged = charged;
            e.chargeChanged = true;
        }
    }

    public void remove(K holder) {
        entries.remove(holder);
    }

    public int size() {
        return entries.size();
    }

    /** The messages that bring every viewer up to date; holders missing from {@code holders} are forgotten. */
    public List<Out<C>> update(Collection<Viewer<C>> viewers, Map<K, Holder> holders, long now) {
        List<Out<C>> out = new ArrayList<>();
        Map<C, Boolean> online = new IdentityHashMap<>();
        for (Viewer<C> v : viewers) {
            online.put(v.connection, Boolean.TRUE);
        }
        for (Iterator<Map.Entry<K, Entry<C>>> it = entries.entrySet()
            .iterator(); it.hasNext();) {
            Map.Entry<K, Entry<C>> me = it.next();
            Entry<C> e = me.getValue();
            Holder h = holders.get(me.getKey());
            if (h == null) {
                it.remove();
                continue;
            }
            if (h.entityId != e.entityId) {
                e.entityId = h.entityId;
                e.told.clear();
            }
            e.told.removeIf(c -> !online.containsKey(c));
            for (Viewer<C> v : viewers) {
                boolean told = e.told.contains(v.connection);
                if (!hears(v, h, told ? RANGE + HYSTERESIS : RANGE)) {
                    e.told.remove(v.connection);
                    continue;
                }
                if (!told || e.stateChanged) {
                    if (e.idle != null) {
                        int elapsed = (int) Math.min(Integer.MAX_VALUE, now - e.start);
                        out.add(
                            new Out<>(
                                v.connection,
                                BladeWire.state(h.entityId, elapsed, e.chain, e.idle, e.body, elapsed, e.charged)));
                    } else if (e.charged || e.chargeChanged) {
                        out.add(new Out<>(v.connection, BladeWire.charge(h.entityId, e.charged)));
                    }
                    e.told.add(v.connection);
                } else if (e.chargeChanged) {
                    out.add(new Out<>(v.connection, BladeWire.charge(h.entityId, e.charged)));
                }
            }
            e.stateChanged = false;
            e.chargeChanged = false;
        }
        return out;
    }

    private static boolean hears(Viewer<?> v, Holder h, double range) {
        if (!v.listens || !h.world.equals(v.world)) {
            return false;
        }
        double dx = v.x - h.x;
        double dy = v.y - h.y;
        double dz = v.z - h.z;
        return dx * dx + dy * dy + dz * dz <= range * range;
    }
}
