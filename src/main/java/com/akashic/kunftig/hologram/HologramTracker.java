package com.akashic.kunftig.hologram;

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
 * Who sees which hologram: run once per server tick with the players online, it returns the messages to send
 * ({@link HologramWire}). A viewer is identified by its connection object {@code C} (the server's player entity): a
 * player who relogs or respawns has a new one and gets every hologram again, as his client forgot them.
 * <p>
 * A player sees a hologram while he listens to {@link HologramWire#CHANNEL}, is in its world and within its view range;
 * once shown, it stays until he is {@link #HYSTERESIS} blocks beyond the range (no flicker at the edge). No Minecraft
 * classes; main thread only.
 *
 * @param <C> the connection (player entity) type
 */
public final class HologramTracker<C> {

    /** Extra blocks before a shown hologram is hidden again. */
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

    /** One hologram's state; changed by the API object, read here. */
    public static final class State<C> {

        final int id;
        final double range;
        String world;
        double x;
        double y;
        double z;
        List<String> lines;
        boolean removed;
        boolean linesChanged;
        boolean moved;
        final Set<C> shown = Collections.newSetFromMap(new IdentityHashMap<>());

        State(int id, String world, double x, double y, double z, List<String> lines, double range) {
            this.id = id;
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.lines = HologramWire.clean(lines);
            this.range = range;
        }

        public int id() {
            return id;
        }

        public synchronized List<String> lines() {
            return lines;
        }

        public synchronized void setLines(List<String> newLines) {
            List<String> clean = HologramWire.clean(newLines);
            if (!clean.equals(lines)) {
                lines = clean;
                linesChanged = true;
            }
        }

        public synchronized void moveTo(String newWorld, double nx, double ny, double nz) {
            if (!newWorld.equals(world) || nx != x || ny != y || nz != z) {
                world = newWorld;
                x = nx;
                y = ny;
                z = nz;
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
            return new double[] { x, y, z };
        }

        public double range() {
            return range;
        }
    }

    private final Map<Integer, State<C>> holograms = new LinkedHashMap<>();
    private int nextId = 1;

    /** A new hologram (sent from the next {@link #update}). */
    public State<C> create(String world, double x, double y, double z, List<String> lines, double range) {
        State<C> s = new State<>(nextId++, world, x, y, z, lines, range);
        holograms.put(s.id, s);
        return s;
    }

    public int size() {
        return holograms.size();
    }

    /** The messages that bring every viewer up to date. */
    public List<Out<C>> update(Collection<Viewer<C>> viewers) {
        List<Out<C>> out = new ArrayList<>();
        Map<C, Viewer<C>> online = new IdentityHashMap<>();
        for (Viewer<C> v : viewers) {
            online.put(v.connection, v);
        }
        for (Iterator<State<C>> it = holograms.values()
            .iterator(); it.hasNext();) {
            State<C> h = it.next();
            synchronized (h) {
                if (h.removed) {
                    for (C c : h.shown) {
                        if (online.containsKey(c)) {
                            out.add(new Out<>(c, HologramWire.hide(h.id)));
                        }
                    }
                    h.shown.clear();
                    it.remove();
                    continue;
                }
                // Gone (offline, relogged, respawned): the client already forgot it.
                h.shown.removeIf(c -> !online.containsKey(c));
                for (Iterator<C> s = h.shown.iterator(); s.hasNext();) {
                    C c = s.next();
                    if (!sees(online.get(c), h, h.range + HYSTERESIS)) {
                        out.add(new Out<>(c, HologramWire.hide(h.id)));
                        s.remove();
                    } else if (h.moved) {
                        out.add(new Out<>(c, HologramWire.move(h.id, h.x, h.y, h.z)));
                    } else if (h.linesChanged) {
                        out.add(new Out<>(c, HologramWire.lines(h.id, h.lines)));
                    }
                }
                if (h.moved && h.linesChanged) {
                    for (C c : h.shown) {
                        out.add(new Out<>(c, HologramWire.lines(h.id, h.lines)));
                    }
                }
                h.moved = false;
                h.linesChanged = false;
                for (Viewer<C> v : viewers) {
                    if (!h.shown.contains(v.connection) && sees(v, h, h.range)) {
                        out.add(new Out<>(v.connection, HologramWire.show(h.id, h.x, h.y, h.z, h.lines)));
                        h.shown.add(v.connection);
                    }
                }
            }
        }
        return out;
    }

    private static <C> boolean sees(Viewer<C> v, State<C> h, double range) {
        if (v == null || !v.listens || !h.world.equals(v.world)) {
            return false;
        }
        double dx = v.x - h.x;
        double dy = v.y - h.y;
        double dz = v.z - h.z;
        return dx * dx + dy * dy + dz * dz <= range * range;
    }
}
