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
 * played. A display with a motion (API 9) moves by it every tick here and on the clients, so no message is needed
 * while it flies. No Minecraft classes; main thread only.
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
        String variant;
        boolean variantChanged;
        String world;
        double x;
        double y;
        double z;
        float yaw;
        float pitch;
        float roll;
        double vx;
        double vy;
        double vz;
        int glide;
        String state;
        long stateStart;
        List<byte[]> items;
        float[] scale = { 1, 1, 1 };
        Map<String, Boolean> bones = new LinkedHashMap<>();
        boolean removed;
        boolean stateChanged;
        boolean itemsChanged;
        boolean moved;
        boolean motionChanged;
        boolean scaleChanged;
        boolean bonesChanged;
        /** API 12: the boxes players walk on ({@link DeckShape#pack}); empty for none. */
        short[] deck = new short[0];
        boolean deckChanged;
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

        public synchronized String variant() {
            return variant;
        }

        /** Another texture variant where it stands (API 12); {@code ""} for the default texture. */
        public synchronized void setVariant(String newVariant) {
            String v = newVariant == null ? "" : newVariant;
            if (!v.equals(variant)) {
                variant = v;
                variantChanged = true;
            }
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
            moveTo(newWorld, nx, ny, nz, nyaw, pitch, roll, 0);
        }

        /** Moves it, keeping the pitch and roll; viewers glide there over {@code ticks} (0: at once). */
        public synchronized void moveTo(String newWorld, double nx, double ny, double nz, float nyaw, int ticks) {
            moveTo(newWorld, nx, ny, nz, nyaw, pitch, roll, ticks);
        }

        /**
         * Moves it (and stops its flight): viewers in the same world glide there over {@code glideTicks} (0: jump);
         * the full rotation, in degrees.
         */
        public synchronized void moveTo(String newWorld, double nx, double ny, double nz, float nyaw, float npitch,
            float nroll, int glideTicks) {
            boolean stop = vx != 0 || vy != 0 || vz != 0;
            if (stop) {
                vx = 0;
                vy = 0;
                vz = 0;
                motionChanged = true;
            }
            if (!newWorld.equals(world) || nx != x
                || ny != y
                || nz != z
                || nyaw != yaw
                || npitch != pitch
                || nroll != roll) {
                // another world is always a jump: viewers there see it appear
                glide = newWorld.equals(world) ? Math.max(0, Math.min(DisplayWire.MAX_GLIDE, glideTicks)) : 0;
                world = newWorld;
                x = nx;
                y = ny;
                z = nz;
                yaw = nyaw;
                pitch = npitch;
                roll = nroll;
                moved = true;
            }
        }

        /** The scale on the model's X, Y and Z, on top of the manifest's (1, 1, 1 by default). */
        public synchronized void setScale(float sx, float sy, float sz) {
            float[] next = { DisplayWire.clampScale(sx), DisplayWire.clampScale(sy), DisplayWire.clampScale(sz) };
            if (next[0] != scale[0] || next[1] != scale[1] || next[2] != scale[2]) {
                scale = next;
                scaleChanged = true;
            }
        }

        public synchronized float[] scale() {
            return scale.clone();
        }

        /** Shows or hides a bone (and everything under it), whatever the manifest says. */
        public synchronized void setBone(String bone, boolean visible) {
            Boolean old = bones.get(bone);
            if (old == null || old != visible) {
                if (old == null && bones.size() >= DisplayWire.MAX_BONES) {
                    throw new IllegalStateException("at most " + DisplayWire.MAX_BONES + " bones set per display");
                }
                bones.put(bone, visible);
                bonesChanged = true;
            }
        }

        /** Every bone back to the manifest's default. */
        public synchronized void resetBones() {
            if (!bones.isEmpty()) {
                bones.clear();
                bonesChanged = true;
            }
        }

        public synchronized Map<String, Boolean> bones() {
            return Collections.unmodifiableMap(new LinkedHashMap<>(bones));
        }

        /** The boxes players walk on (API 12), as {@link DeckShape#pack} gives them; null or empty for none. */
        public synchronized void setDeck(short[] packed) {
            short[] next = packed == null ? new short[0] : packed.clone();
            if (DeckShape.of(next) == null) {
                throw new IllegalArgumentException("malformed deck boxes");
            }
            if (!java.util.Arrays.equals(next, deck)) {
                deck = next;
                deckChanged = true;
            }
        }

        public synchronized short[] deck() {
            return deck.clone();
        }

        /** Flies from where it is, {@code vx, vy, vz} blocks per tick, on the server and on every client (0: stops). */
        public synchronized void setMotion(double nvx, double nvy, double nvz) {
            double a = DisplayWire.speed(nvx);
            double b = DisplayWire.speed(nvy);
            double c = DisplayWire.speed(nvz);
            if (a != vx || b != vy || c != vz) {
                vx = a;
                vy = b;
                vz = c;
                motionChanged = true;
            }
        }

        /** Blocks per tick. */
        public synchronized double[] motion() {
            return new double[] { vx, vy, vz };
        }

        /** Yaw, pitch and roll in degrees. */
        public synchronized float[] rotation() {
            return new float[] { yaw, pitch, roll };
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
                        out.add(new Out<>(c, DisplayWire.move(d.id, d.x, d.y, d.z, d.yaw, d.glide, d.pitch, d.roll)));
                    }
                    if (d.motionChanged) {
                        out.add(new Out<>(c, DisplayWire.motion(d.id, d.x, d.y, d.z, d.vx, d.vy, d.vz)));
                    }
                    if (d.scaleChanged) {
                        out.add(new Out<>(c, DisplayWire.scale(d.id, d.scale)));
                    }
                    if (d.bonesChanged) {
                        out.add(new Out<>(c, DisplayWire.bones(d.id, d.bones)));
                    }
                    if (d.variantChanged) {
                        out.add(new Out<>(c, DisplayWire.variant(d.id, d.variant)));
                    }
                    if (d.deckChanged) {
                        out.add(new Out<>(c, DisplayWire.deck(d.id, d.deck)));
                    }
                    if (d.itemsChanged) {
                        out.add(new Out<>(c, DisplayWire.items(d.id, d.items)));
                    }
                    if (d.stateChanged) {
                        out.add(new Out<>(c, DisplayWire.state(d.id, d.state, elapsed)));
                    }
                }
                d.moved = false;
                d.motionChanged = false;
                d.scaleChanged = false;
                d.bonesChanged = false;
                d.deckChanged = false;
                d.variantChanged = false;
                d.glide = 0;
                d.itemsChanged = false;
                d.stateChanged = false;
                for (Viewer<C> v : viewers) {
                    if (!d.shown.contains(v.connection) && sees(v, d, d.range)) {
                        out.add(
                            new Out<>(
                                v.connection,
                                DisplayWire.show(
                                    d.id,
                                    d.x,
                                    d.y,
                                    d.z,
                                    d.yaw,
                                    d.pitch,
                                    d.roll,
                                    d.vx,
                                    d.vy,
                                    d.vz,
                                    d.model,
                                    d.variant,
                                    d.state,
                                    elapsed,
                                    d.items,
                                    d.scale,
                                    d.bones)));
                        if (d.deck.length > 0) {
                            out.add(new Out<>(v.connection, DisplayWire.deck(d.id, d.deck)));
                        }
                        d.shown.add(v.connection);
                    }
                }
                // a flying display moves on its own, here as on the clients (after this tick's messages)
                d.x += d.vx;
                d.y += d.vy;
                d.z += d.vz;
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
