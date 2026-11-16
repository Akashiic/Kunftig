package com.akashic.kunftig.display;

/**
 * One player against one display's {@link DeckShape} (API 12), once per tick, after the player's own move: carries the
 * player with the display (the point it stands on stays the same point of the display, however it moved and turned),
 * holds it on the highest floor under its feet, takes steps up and down, stops it at walls and under ceilings.
 * <p>
 * The player is an upright point with a radius and a height in the display's frame (the boxes grow by the radius);
 * floors are found along the world's vertical, so a player standing on a tilted deck does not slide.
 * <p>
 * The boxes are solid from every side and for everyone: a player who is not standing on the display (jumping,
 * falling, flying, swimming, arriving from the ground beside it) is swept through its own move in the display's
 * frame and stopped at the first face it meets, floor, wall or ceiling, and lands on a floor it comes down on. A
 * display that moves into a player pushes it out. Only a player found deep inside a box (put there by a teleport,
 * or by a deck that changed) is let out freely.
 * <p>
 * No Minecraft classes; one instance per player, client thread.
 */
public final class DeckWalker {

    /** The highest step taken up, in blocks. */
    public static final double STEP = 0.6;
    /**
     * The farthest the feet follow a floor down in one tick, in blocks: more than a step, so a steep stair taken at a
     * run (and boxes that travel rounded to 1/32 block) does not leave the player in the air between two treads.
     */
    public static final double DROP = 0.75;
    /** The deepest a box may be inside the player and still push it out; deeper, the player is let out freely. */
    private static final double PUSH = 0.6;
    /** Ticks in the air (a jump, a fall off an edge) before the display stops carrying the player. */
    public static final int MAX_AIR_TICKS = 40;
    /** A wall is slid along, not snagged on, when leaving it sideways takes this little. */
    private static final double SLIP = 0.15;
    private static final double EPS = 1e-4;
    /** Farther than this in one tick is not a move: the player was put somewhere else. */
    private static final double TELEPORT = 4;
    /** Past this tilt (the world's up almost in the deck's plane) nobody stands. */
    private static final double MIN_UP = 0.35;

    private boolean tracked;
    private boolean aboard;
    private boolean grounded;
    private int air;
    private double ax;
    private double ay;
    private double az;
    /** Boxes the player is deep inside this tick: they do not stop it. */
    private boolean[] deep = new boolean[0];
    private boolean landed;
    private boolean touched;

    /** After {@link #step}: whether the player must be put at {@link #x}, {@link #y}, {@link #z}. */
    public boolean moved;
    public double x;
    public double y;
    public double z;
    /** After {@link #step}: degrees the display turned under the player this tick (0 when not aboard). */
    public float turned;
    /** After {@link #step}: the head met a ceiling. */
    public boolean bumped;

    /** Forgets the player (it left, the display went away, it flies or rides). */
    public void reset() {
        tracked = false;
        aboard = false;
        grounded = false;
        air = 0;
    }

    /** Carried by the display: standing on it, or in the air since at most {@link #MAX_AIR_TICKS}. */
    public boolean aboard() {
        return aboard;
    }

    /** Standing on one of the display's floors. */
    public boolean grounded() {
        return aboard && grounded;
    }

    /**
     * One tick.
     *
     * @param shape       the display's boxes
     * @param prev        the display's pose at the end of the last tick (the one the player just moved against)
     * @param now         its pose at the end of this tick
     * @param px          the player's feet in the world after its own move
     * @param py          idem
     * @param pz          idem
     * @param radius      half the player's width
     * @param height      the player's height
     * @param worldGround whether the world's blocks hold the player up
     */
    public void step(DeckShape shape, DeckPose prev, DeckPose now, double px, double py, double pz, double radius,
        double height, boolean worldGround) {
        step(shape, prev, now, px, py, pz, radius, height, worldGround, false);
    }

    /**
     * One tick.
     *
     * @param flying whether the player flies (creative flight and the like): it is stopped by the boxes and lands
     *               on a floor it comes down on, but is not carried through the air by the display
     */
    public void step(DeckShape shape, DeckPose prev, DeckPose now, double px, double py, double pz, double radius,
        double height, boolean worldGround, boolean flying) {
        moved = false;
        turned = 0;
        bumped = false;
        double[] up = prev.up();
        if (shape == null || shape.isEmpty() || up[1] < MIN_UP) {
            reset();
            return;
        }
        double[] c = prev.toLocal(px, py, pz);
        if (!tracked) {
            tracked = true;
            aboard = false;
            grounded = false;
            air = 0;
            ax = c[0];
            ay = c[1];
            az = c[2];
        }
        double jx = c[0] - ax;
        double jy = c[1] - ay;
        double jz = c[2] - az;
        if (jx * jx + jy * jy + jz * jz > TELEPORT * TELEPORT) {
            // moved by something else (a teleport, a respawn): nothing of where it was says where it may be now
            aboard = false;
            grounded = false;
            air = 0;
            ax = c[0];
            ay = c[1];
            az = c[2];
        }
        // how far the player went up in the world this tick
        double rise = (c[0] - ax) * up[0] + (c[1] - ay) * up[1] + (c[2] - az) * up[2];
        double lx;
        double ly;
        double lz;
        if (aboard && grounded && rise <= EPS) {
            // walking: the vertical through the player, where it meets the level it stood on
            double s = (ay - c[1]) / up[1];
            double hx = c[0] + s * up[0];
            double hz = c[2] + s * up[2];
            double[] free = pushOut(shape, hx, hz, ay + STEP, ay + height, radius, ax, az, Double.NaN, height);
            int floor = floorAt(shape, free[0], free[1], ay - DROP, ay + STEP, radius);
            if (floor >= 0) {
                double top = shape.maxY[floor];
                double s2 = (top - c[1]) / up[1];
                double fx = c[0] + s2 * up[0] + (free[0] - hx);
                double fz = c[2] + s2 * up[2] + (free[1] - hz);
                if (!inside(shape, floor, fx, fz, radius) || blocked(shape, fx, fz, top, radius, height)) {
                    fx = free[0];
                    fz = free[1];
                }
                lx = fx;
                ly = top;
                lz = fz;
                air = 0;
            } else {
                grounded = false; // walked off an edge
                lx = c[0] + (free[0] - hx);
                ly = c[1];
                lz = c[2] + (free[1] - hz);
            }
        } else {
            // not standing on it: the boxes are solid from every side
            grounded = false;
            double[] p = { ax, ay, az };
            sweep(shape, p, c[0] - ax, c[1] - ay, c[2] - az, radius, height, worldGround && !flying);
            lx = p[0];
            ly = p[1];
            lz = p[2];
            if (landed) {
                aboard = true;
                grounded = true;
                air = 0;
            }
        }
        boolean touch = !grounded && touched;
        if (aboard && !grounded && (worldGround || flying || ++air > MAX_AIR_TICKS)) {
            aboard = false;
        }
        if (aboard || touch) {
            // carried by the display, or held against one of its faces this tick
            double[] w = now.toWorld(lx, ly, lz);
            moved = true;
            x = w[0];
            y = w[1];
            z = w[2];
            turned = aboard ? DisplayMotion.wrap(now.yaw - prev.yaw) : 0;
            ax = lx;
            ay = ly;
            az = lz;
        } else {
            // not carried: the display has moved, maybe into the player
            double[] a = now.toLocal(px, py, pz);
            settle(shape, a, radius, height);
            if (touched) {
                double[] w = now.toWorld(a[0], a[1], a[2]);
                moved = true;
                x = w[0];
                y = w[1];
                z = w[2];
                if (landed) {
                    aboard = true;
                    grounded = true;
                    air = 0;
                }
            }
            ax = a[0];
            ay = a[1];
            az = a[2];
        }
    }

    /**
     * The world height of the floor nearest to a point along the vertical, within {@code reach} blocks above or below
     * it, or NaN: where to draw someone else who stands on the display.
     */
    public static double surface(DeckShape shape, DeckPose pose, double wx, double wy, double wz, double radius,
        double reach) {
        double[] up = pose.up();
        if (shape == null || shape.isEmpty() || up[1] < MIN_UP) {
            return Double.NaN;
        }
        double[] c = pose.toLocal(wx, wy, wz);
        double best = Double.NaN;
        for (int i = 0; i < shape.size; i++) {
            double s = (shape.maxY[i] - c[1]) / up[1];
            if (Math.abs(s) > reach || Math.abs(s) >= Math.abs(best)) {
                continue;
            }
            if (inside(shape, i, c[0] + s * up[0], c[2] + s * up[2], radius)) {
                best = s;
            }
        }
        return wy + best;
    }

    private static boolean inside(DeckShape s, int i, double x, double z, double r) {
        return x > s.minX[i] - r && x < s.maxX[i] + r && z > s.minZ[i] - r && z < s.maxZ[i] + r;
    }

    /** The highest floor with its top in {@code lo..hi} under a point, or -1. */
    private static int floorAt(DeckShape s, double x, double z, double lo, double hi, double r) {
        int best = -1;
        for (int i = 0; i < s.size; i++) {
            double top = s.maxY[i];
            if (top >= lo && top <= hi && (best < 0 || top > s.maxY[best]) && inside(s, i, x, z, r)) {
                best = i;
            }
        }
        return best;
    }

    private static boolean over(DeckShape s, int i, double x, double z, double r) {
        return x + r > s.minX[i] + EPS && x - r < s.maxX[i] - EPS && z + r > s.minZ[i] + EPS && z - r < s.maxZ[i] - EPS;
    }

    private static boolean level(DeckShape s, int i, double feet, double height) {
        return feet + height > s.minY[i] + EPS && feet < s.maxY[i] - EPS;
    }

    /**
     * Pushes {@code p} (the feet) out of the boxes it is a little inside, least way out first: a display that moved
     * into the player, a floor come up under it. The boxes it is deep inside are marked in {@link #deep} and left
     * alone. Sets {@link #landed}, {@link #bumped} and {@link #touched}.
     */
    private void settle(DeckShape s, double[] p, double r, double height) {
        landed = false;
        touched = false;
        if (deep.length < s.size) {
            deep = new boolean[s.size];
        }
        java.util.Arrays.fill(deep, 0, s.size, false);
        for (int pass = 0; pass < 3; pass++) {
            boolean again = false;
            for (int i = 0; i < s.size; i++) {
                if (deep[i] || !over(s, i, p[0], p[2], r) || !level(s, i, p[1], height)) {
                    continue;
                }
                double[] d = { s.maxY[i] - p[1], p[1] + height - s.minY[i], p[0] + r - s.minX[i],
                    s.maxX[i] - (p[0] - r), p[2] + r - s.minZ[i], s.maxZ[i] - (p[2] - r) };
                int k = 0;
                for (int j = 1; j < 6; j++) {
                    if (d[j] < d[k]) {
                        k = j;
                    }
                }
                if (d[k] > PUSH) {
                    deep[i] = true;
                    continue;
                }
                if (k == 0) {
                    p[1] = s.maxY[i];
                    landed = true;
                } else if (k == 1) {
                    p[1] = s.minY[i] - height;
                    bumped = true;
                } else if (k == 2) {
                    p[0] = s.minX[i] - r - EPS;
                } else if (k == 3) {
                    p[0] = s.maxX[i] + r + EPS;
                } else if (k == 4) {
                    p[2] = s.minZ[i] - r - EPS;
                } else {
                    p[2] = s.maxZ[i] + r + EPS;
                }
                touched = true;
                again = true;
            }
            if (!again) {
                break;
            }
        }
    }

    /**
     * A free body against the boxes, in the display's frame: {@code p} (the feet, where the player was) is first
     * pushed out of the boxes the display moved into it, then taken through its move, down or up first and then
     * along each horizontal axis, stopping at the first face in the way. Sets {@link #landed} (it came to rest on a
     * floor), {@link #bumped} (the head met a ceiling) and {@link #touched} (any face held it).
     *
     * @param stepUp whether a low box met sideways is stepped onto (a player walking in from the ground beside)
     */
    private void sweep(DeckShape s, double[] p, double dx, double dy, double dz, double r, double height,
        boolean stepUp) {
        settle(s, p, r, height);
        // down or up
        if (dy < 0) {
            double to = p[1] + dy;
            for (int i = 0; i < s.size; i++) {
                if (!deep[i] && s.maxY[i] <= p[1] + EPS && s.maxY[i] > to && over(s, i, p[0], p[2], r)) {
                    to = s.maxY[i];
                    landed = true;
                    touched = true;
                }
            }
            p[1] = to;
        } else if (dy > 0) {
            landed = false;
            double to = p[1] + dy;
            for (int i = 0; i < s.size; i++) {
                if (!deep[i] && s.minY[i] >= p[1] + height - EPS
                    && s.minY[i] - height < to
                    && over(s, i, p[0], p[2], r)) {
                    to = s.minY[i] - height;
                    bumped = true;
                    touched = true;
                }
            }
            p[1] = to;
        }
        // along each horizontal axis
        for (int axis = 0; axis < 4; axis += 2) {
            double d = axis == 0 ? dx : dz;
            if (d == 0) {
                continue;
            }
            double[] lo = axis == 0 ? s.minX : s.minZ;
            double[] hi = axis == 0 ? s.maxX : s.maxZ;
            double from = p[axis];
            double to = from + d;
            int hit = -1;
            for (int i = 0; i < s.size; i++) {
                if (deep[i] || !level(s, i, p[1], height)) {
                    continue;
                }
                boolean beside = axis == 0 ? p[2] + r > s.minZ[i] + EPS && p[2] - r < s.maxZ[i] - EPS
                    : p[0] + r > s.minX[i] + EPS && p[0] - r < s.maxX[i] - EPS;
                if (!beside) {
                    continue;
                }
                if (d > 0 && lo[i] - r >= from - EPS && lo[i] - r - EPS < to) {
                    to = lo[i] - r - EPS;
                    hit = i;
                } else if (d < 0 && hi[i] + r <= from + EPS && hi[i] + r + EPS > to) {
                    to = hi[i] + r + EPS;
                    hit = i;
                }
            }
            if (hit >= 0 && stepUp && s.maxY[hit] - p[1] <= STEP) {
                // a low box met on foot: stepped onto, where the player fits
                double top = s.maxY[hit];
                double nx = axis == 0 ? from + d : p[0];
                double nz = axis == 0 ? p[2] : from + d;
                boolean fits = true;
                for (int i = 0; i < s.size && fits; i++) {
                    fits = deep[i] || !over(s, i, nx, nz, r) || !level(s, i, top, height);
                }
                if (fits) {
                    p[1] = top;
                    to = from + d;
                    landed = true;
                }
            }
            if (hit >= 0) {
                touched = true;
            }
            p[axis] = to;
        }
    }

    /** Whether a player standing at this height would be inside a box too high to step on. */
    private static boolean blocked(DeckShape s, double x, double z, double feet, double r, double height) {
        for (int i = 0; i < s.size; i++) {
            if (s.maxY[i] > feet + STEP && s.minY[i] < feet + height && inside(s, i, x, z, r)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Moves a point out of the boxes that reach above {@code minTop} and below {@code head}: back the way it came,
     * or sideways when that takes very little (sliding along a wall).
     *
     * @param fromY the height the player came from, to let it land on boxes it was above and pass under those it
     *              was below; NaN on the ground
     */
    private static double[] pushOut(DeckShape s, double x, double z, double minTop, double head, double r, double fromX,
        double fromZ, double fromY, double height) {
        for (int pass = 0; pass < 4; pass++) {
            boolean moved = false;
            for (int i = 0; i < s.size; i++) {
                if (s.maxY[i] <= minTop || s.minY[i] >= head || !inside(s, i, x, z, r)) {
                    continue;
                }
                if (!Double.isNaN(fromY) && (fromY >= s.maxY[i] - EPS || fromY + height <= s.minY[i] + EPS)) {
                    continue;
                }
                double x0 = s.minX[i] - r;
                double x1 = s.maxX[i] + r;
                double z0 = s.minZ[i] - r;
                double z1 = s.maxZ[i] + r;
                double[] depth = { x - x0, x1 - x, z - z0, z1 - z };
                boolean[] from = { fromX <= x0 + EPS, fromX >= x1 - EPS, fromZ <= z0 + EPS, fromZ >= z1 - EPS };
                boolean any = from[0] || from[1] || from[2] || from[3];
                int side = -1;
                for (int k = 0; k < 4; k++) {
                    if ((!any || from[k] || depth[k] <= SLIP) && (side < 0 || depth[k] < depth[side])) {
                        side = k;
                    }
                }
                if (side == 0) {
                    x = x0 - EPS;
                } else if (side == 1) {
                    x = x1 + EPS;
                } else if (side == 2) {
                    z = z0 - EPS;
                } else {
                    z = z1 + EPS;
                }
                moved = true;
            }
            if (!moved) {
                break;
            }
        }
        return new double[] { x, z };
    }
}
