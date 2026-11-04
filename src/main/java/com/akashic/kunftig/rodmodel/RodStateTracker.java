package com.akashic.kunftig.rodmodel;

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
 * The rod states the server set ({@code KunftigRods}) and who has been told: run once per server tick, it returns the
 * messages to send ({@link RodWire}). The angler always hears his own state; other players hear it while they are in
 * the same world within {@link #RANGE} blocks (and get the whole state again when they come back). A viewer is
 * identified by its connection object {@code C} (the server's player entity): a player who relogs or respawns has a
 * new one and is told again. Tension goes out at most once per tick, only when it changed in hundredths. No Minecraft
 * classes; main thread only.
 *
 * @param <K> what identifies an angler across respawns (his UUID)
 * @param <C> the connection (player entity) type
 */
public final class RodStateTracker<K, C> {

    /** Blocks within which other players hear an angler's rod. */
    public static final double RANGE = 96;
    /** Extra blocks before a viewer stops being kept up to date. */
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

    /** An angler with a state, as seen this tick. */
    public static final class Angler {

        final int entityId;
        final String world;
        final double x;
        final double y;
        final double z;

        public Angler(int entityId, String world, double x, double y, double z) {
            this.entityId = entityId;
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    /** A message for one connection. */
    public static final class Out<C> {

        public final C connection;
        public final byte[] data;

        Out(C connection, byte[] data) {
            this.connection = connection;
            this.data = data;
        }
    }

    private static final class Entry<C> {

        String state;
        long start;
        int tension;
        boolean stateChanged;
        boolean tensionChanged;
        int entityId = Integer.MIN_VALUE;
        final Set<C> told = Collections.newSetFromMap(new IdentityHashMap<>());
    }

    private final Map<K, Entry<C>> entries = new LinkedHashMap<>();

    /** Sets an angler's state from tick {@code now} (sent from the next {@link #update}). */
    public void setState(K angler, String state, long now) {
        if (state == null || !RodWire.STATE_NAME.matcher(state)
            .matches()) {
            throw new IllegalArgumentException("invalid rod state: " + state);
        }
        Entry<C> e = entries.computeIfAbsent(angler, k -> new Entry<>());
        e.state = state;
        e.start = now;
        e.stateChanged = true;
    }

    /** Sets an angler's tension (0 to 1); sent only when it changed in hundredths. */
    public void setTension(K angler, float tension) {
        Entry<C> e = entries.computeIfAbsent(angler, k -> new Entry<>());
        int q = RodWire.quantize(tension);
        if (q != e.tension) {
            e.tension = q;
            e.tensionChanged = true;
        }
    }

    /** The state set for an angler, or null. */
    public String state(K angler) {
        Entry<C> e = entries.get(angler);
        return e == null ? null : e.state;
    }

    /** Forgets an angler (who left, for instance). */
    public void remove(K angler) {
        entries.remove(angler);
    }

    public Collection<K> anglers() {
        return Collections.unmodifiableSet(entries.keySet());
    }

    public int size() {
        return entries.size();
    }

    /**
     * The messages that bring every viewer up to date.
     *
     * @param anglers the anglers online this tick; those missing are forgotten
     */
    public List<Out<C>> update(Collection<Viewer<C>> viewers, Map<K, Angler> anglers, long now) {
        List<Out<C>> out = new ArrayList<>();
        Map<C, Boolean> online = new IdentityHashMap<>();
        for (Viewer<C> v : viewers) {
            online.put(v.connection, Boolean.TRUE);
        }
        for (Iterator<Map.Entry<K, Entry<C>>> it = entries.entrySet()
            .iterator(); it.hasNext();) {
            Map.Entry<K, Entry<C>> me = it.next();
            Entry<C> e = me.getValue();
            Angler a = anglers.get(me.getKey());
            if (a == null) {
                it.remove();
                continue;
            }
            if (a.entityId != e.entityId) {
                e.entityId = a.entityId; // respawned or changed world: a new entity for every client
                e.told.clear();
            }
            if (e.state != null) {
                e.told.removeIf(c -> !online.containsKey(c));
                float tension = e.tension / (float) RodWire.TENSION_STEPS;
                for (Viewer<C> v : viewers) {
                    boolean told = e.told.contains(v.connection);
                    if (!hears(v, a, told ? RANGE + HYSTERESIS : RANGE)) {
                        if (told) {
                            e.told.remove(v.connection);
                        }
                        continue;
                    }
                    if (!told || e.stateChanged) {
                        out.add(new Out<>(v.connection, RodWire.state(a.entityId, e.state, now - e.start, tension)));
                        e.told.add(v.connection);
                    } else if (e.tensionChanged) {
                        out.add(new Out<>(v.connection, RodWire.tension(a.entityId, tension)));
                    }
                }
            }
            e.stateChanged = false;
            e.tensionChanged = false;
        }
        return out;
    }

    private static boolean hears(Viewer<?> v, Angler a, double range) {
        if (!v.listens || !a.world.equals(v.world)) {
            return false;
        }
        double dx = v.x - a.x;
        double dy = v.y - a.y;
        double dz = v.z - a.z;
        return dx * dx + dy * dy + dz * dz <= range * range;
    }
}
