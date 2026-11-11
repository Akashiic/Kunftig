package com.akashic.kunftig.display;

/**
 * One player against one display's {@link DeckShape} (API 12), once per tick, after the player's own move: carries the
 * player with the display (the point it stands on stays the same point of the display, however it moved and turned),
 * holds it on the highest floor under its feet, takes steps up and down, stops it at walls and under ceilings.
 * <p>
 * The player is an upright point with a radius and a height in the display's frame (the boxes grow by the radius);
 * floors are found along the world's vertical, so a player standing on a tilted deck does not slide. The deck only
 * exists for a player once it stands on it: someone swimming or flying by passes through, and lands only by coming
 * down on a floor from above (or stepping onto one from the ground beside it).
 * <p>
 * No Minecraft classes; one instance per player, client thread.
 */
public final class DeckWalker {

    /** The highest step taken up, and the farthest the feet follow a floor down, in blocks. */
    public static final double STEP = 0.6;
    /** Ticks in the air (a jump, a fall off an edge) before the display stops carrying the player. */
    public static final int MAX_AIR_TICKS = 40;
    /** A wall is slid along, not snagged on, when leaving it sideways takes this little. */
    private static final double SLIP = 0.15;
    private static final double EPS = 1e-4;
    /** Past this tilt (the world's up almost in the deck's plane) nobody stands. */
    private static final double MIN_UP = 0.35;

    private boolean tracked;
    private boolean aboard;
    private boolean grounded;
    private int air;
    private double ax;
    private double ay;
    private double az;

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
            int floor = floorAt(shape, free[0], free[1], ay - STEP, ay + STEP, radius);
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
            grounded = false;
            lx = c[0];
            ly = c[1];
            lz = c[2];
            int floor = rise <= EPS ? landing(shape, lx, ly, lz, radius, height) : -1;
            if (floor >= 0) {
                double top = shape.maxY[floor];
                double s = (top - ly) / up[1];
                double fx = lx + s * up[0];
                double fz = lz + s * up[2];
                if (!inside(shape, floor, fx, fz, radius)) {
                    fx = lx;
                    fz = lz;
                }
                double[] free = pushOut(shape, fx, fz, top + STEP, top + height, radius, ax, az, Double.NaN, height);
                lx = free[0];
                ly = top;
                lz = free[1];
                aboard = true;
                grounded = true;
                air = 0;
            } else if (aboard) {
                for (int i = 0; i < shape.size; i++) {
                    // a ceiling: the head was under it and would now be in it
                    if (ay + height <= shape.minY[i] + EPS && ly + height > shape.minY[i]
                        && inside(shape, i, lx, lz, radius)) {
                        ly = shape.minY[i] - height;
                        bumped = true;
                    }
                }
                double[] free = pushOut(shape, lx, lz, ly + EPS, ly + height, radius, ax, az, ay, height);
                lx = free[0];
                lz = free[1];
            }
        }
        if (aboard && !grounded && (worldGround || ++air > MAX_AIR_TICKS)) {
            aboard = false;
        }
        if (aboard) {
            double[] w = now.toWorld(lx, ly, lz);
            moved = true;
            x = w[0];
            y = w[1];
            z = w[2];
            turned = DisplayMotion.wrap(now.yaw - prev.yaw);
            ax = lx;
            ay = ly;
            az = lz;
        } else {
            // not carried: next tick the display has moved under it
            double[] a = now.toLocal(px, py, pz);
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

    /** The floor a falling player lands on this tick, or -1. */
    private int landing(DeckShape s, double x, double y, double z, double r, double height) {
        int best = -1;
        for (int i = 0; i < s.size; i++) {
            double top = s.maxY[i];
            if (y > top || (best >= 0 && top <= s.maxY[best]) || !inside(s, i, x, z, r)) {
                continue;
            }
            boolean crossed = ay >= top - EPS;
            // feet a little into a floor (a step up from the ground beside it, a deck that changed): only where
            // the player fits
            boolean shallow = top - y <= STEP && !blocked(s, x, z, top, r, height);
            if (crossed || shallow) {
                best = i;
            }
        }
        return best;
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
