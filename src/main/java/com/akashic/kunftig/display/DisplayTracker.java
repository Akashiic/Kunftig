package com.akashic.kunftig.display;

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
 * Who sees which display: run once per server tick with the players online, it returns the messages to send
 * ({@link DisplayWire}). The same rules as the holograms' tracker: a viewer is its connection object {@code C} (a
 * player who relogs or respawns gets every display again), it sees a display while it listens to
 * {@link DisplayWire#CHANNEL}, is in its world and within range, and it stops seeing it {@link #HYSTERESIS} blocks
 * beyond. Each display remembers the tick its state started, so a late viewer gets the state with the time already
 * played. No Minecraft classes; main thread only.
 *
 * @param <C> the connection (player entity) type
 */
public final class DisplayTracker<C> {

    /** Extra blocks before a shown display is hidden again. */
    public static final double HYSTERESIS = 4;

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

    /** A message for one connection. */
    public static final class Out<C> {

        public final C connection;
        public final byte[] data;

        Out(C connection, byte[] data) {
            this.connection = connection;
            this.data = data;
        }
    }

    /** One display's state; changed by the API object, read here. */
    public static final class State<C> {

        final int id;
        final double range;
        final String model;
        final String variant;
        String world;
        double x;
        double y;
        double z;
        float yaw;
        String state;
        long stateStart;
        List<byte[]> items;
        boolean removed;
        boolean stateChanged;
        boolean itemsChanged;
        boolean moved;
        final Set<C> shown = Collections.newSetFromMap(new IdentityHashMap<>());

        State(int id, String world, double x, double y, double z, float yaw, String model, String variant, String state,
            List<byte[]> items, double range, long now) {
            this.id = id;
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.model = model;
            this.variant = variant;
            this.state = state;
            this.stateStart = now;
            this.items = DisplayWire.clean(items);
            this.range = range;
        }

        public int id() {
            return id;
        }

        public String model() {
            return model;
        }

        public String variant() {
            return variant;
        }

        public synchronized String state() {
            return state;
        }

        /** Plays {@code newState} from the next tick (the same state again restarts it). */
        public synchronized void setState(String newState, long now) {
            state = newState;
            stateStart = now;
            stateChanged = true;
        }

        public synchronized List<byte[]> items() {
            return items;
        }

        public synchronized void setItems(List<byte[]> newItems) {
            items = DisplayWire.clean(newItems);
            itemsChanged = true;
        }

        public synchronized void moveTo(String newWorld, double nx, double ny, double nz, float nyaw) {
            if (!newWorld.equals(world) || nx != x || ny != y || nz != z || nyaw != yaw) {
                world = newWorld;
                x = nx;
                y = ny;
                z = nz;
                yaw = nyaw;
                moved = true;
            }
        }

        public synchronized void remove() {
            removed = true;
        }

        public synchronized boolean isRemoved() {
            return removed;
        }

        public synchronized String world() {
            return world;
        }

        public synchronized double[] position() {
            return new double[] { x, y, z, yaw };
        }

        public double range() {
            return range;
        }
    }

    private final Map<Integer, State<C>> displays = new LinkedHashMap<>();
    private int nextId = 1;
    private long tick;

    /** A new display (sent from the next {@link #update}); its state starts now. */
    public State<C> create(String world, double x, double y, double z, float yaw, String model, String variant,
        String state, List<byte[]> items, double range) {
        State<C> s = new State<>(nextId++, world, x, y, z, yaw, model, variant, state, items, range, tick);
        displays.put(s.id, s);
        return s;
    }

    /** The current tick (for {@link State#setState}). */
    public long tick() {
        return tick;
    }

    public int size() {
        return displays.size();
    }

    /** The messages that bring every viewer up to date; then the clock advances one tick. */
    public List<Out<C>> update(Collection<Viewer<C>> viewers) {
        List<Out<C>> out = new ArrayList<>();
        Map<C, Viewer<C>> online = new IdentityHashMap<>();
        for (Viewer<C> v : viewers) {
            online.put(v.connection, v);
        }
        for (Iterator<State<C>> it = displays.values()
            .iterator(); it.hasNext();) {
            State<C> d = it.next();
            synchronized (d) {
                if (d.removed) {
                    for (C c : d.shown) {
                        if (online.containsKey(c)) {
                            out.add(new Out<>(c, DisplayWire.hide(d.id)));
                        }
                    }
                    d.shown.clear();
                    it.remove();
                    continue;
                }
                int elapsed = (int) Math.min(Integer.MAX_VALUE, tick - d.stateStart);
                d.shown.removeIf(c -> !online.containsKey(c));
                for (Iterator<C> s = d.shown.iterator(); s.hasNext();) {
                    C c = s.next();
                    if (!sees(online.get(c), d, d.range + HYSTERESIS)) {
                        out.add(new Out<>(c, DisplayWire.hide(d.id)));
                        s.remove();
                        continue;
                    }
                    if (d.moved) {
                        out.add(new Out<>(c, DisplayWire.move(d.id, d.x, d.y, d.z, d.yaw)));
                    }
                    if (d.itemsChanged) {
                        out.add(new Out<>(c, DisplayWire.items(d.id, d.items)));
                    }
                    if (d.stateChanged) {
                        out.add(new Out<>(c, DisplayWire.state(d.id, d.state, elapsed)));
                    }
                }
                d.moved = false;
                d.itemsChanged = false;
                d.stateChanged = false;
                for (Viewer<C> v : viewers) {
                    if (!d.shown.contains(v.connection) && sees(v, d, d.range)) {
                        out.add(
                            new Out<>(
                                v.connection,
                                DisplayWire
                                    .show(d.id, d.x, d.y, d.z, d.yaw, d.model, d.variant, d.state, elapsed, d.items)));
                        d.shown.add(v.connection);
                    }
                }
            }
        }
        tick++;
        return out;
    }

    private static <C> boolean sees(Viewer<C> v, State<C> d, double range) {
        if (v == null || !v.listens || !d.world.equals(v.world)) {
            return false;
        }
        double dx = v.x - d.x;
        double dy = v.y - d.y;
        double dz = v.z - d.z;
        return dx * dx + dy * dy + dz * dz <= range * range;
    }
}
