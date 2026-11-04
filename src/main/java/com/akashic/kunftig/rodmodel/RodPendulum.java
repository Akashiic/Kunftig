package com.akashic.kunftig.rodmodel;

/**
 * The line hanging from a 3D rod's tip ({@code linha_pendurada}) as a pendulum in world space: the top follows the
 * tip, gravity pulls the line down, and when the tip moves (walking, turning, the rod's own animation) the line lags
 * and swings back to the vertical, damped. Pure math, stepped once per frame with the frame's time and the tip's
 * world position (blocks); no Minecraft classes.
 */
public final class RodPendulum {

    /** Blocks per second squared (the blocks are meters). */
    static final double GRAVITY = 9.8;
    /** Fraction of the swing speed lost per second. */
    static final double DAMPING = 2.2;
    /** Longest step simulated at once; longer frames are split. */
    static final double MAX_STEP = 1.0 / 120;
    /** A frame gap longer than this (seconds) starts over at rest (a teleport, the rod put away). */
    static final double RESET_GAP = 0.25;
    /** A tip that moves farther than this in one frame (blocks) teleported: the line starts over at rest. */
    static final double TELEPORT = 1.0;
    /** Cap on the tip's acceleration (blocks/s²): a camera jump must not fling the line. */
    static final double MAX_ACCELERATION = 30;
    /** Frames shorter than this (seconds) do not step: a second draw in the same frame must not read as a jolt. */
    static final double MIN_STEP = 0.002;
    /** How much of each new speed reading is taken (the rest is the running estimate): smooths the tip's jitter. */
    static final double SPEED_SMOOTHING = 0.5;

    private final double[] dir = { 0, -1, 0 };
    private final double[] speed = new double[3];
    private double[] lastTip;
    private double[] lastTipSpeed;
    private final double[] lastAccel = new double[3];
    private boolean started;

    /**
     * One frame.
     *
     * @param dt     seconds since the last frame
     * @param tip    the line's top in world space (blocks)
     * @param length the line's length (blocks), more than 0
     * @return the line's direction from the top, a unit vector in world space (it may be shared: copy to keep)
     */
    public double[] update(double dt, double[] tip, double length) {
        if (!started || dt > RESET_GAP || !(length > 0)) {
            reset(tip);
            return dir;
        }
        if (dt < MIN_STEP) {
            return dir;
        }
        double jx = tip[0] - lastTip[0], jy = tip[1] - lastTip[1], jz = tip[2] - lastTip[2];
        if (jx * jx + jy * jy + jz * jz > TELEPORT * TELEPORT) {
            reset(tip);
            return dir;
        }
        double[] tipSpeed = new double[3];
        double[] accel = new double[3];
        for (int i = 0; i < 3; i++) {
            double reading = (tip[i] - lastTip[i]) / dt;
            tipSpeed[i] = lastTipSpeed == null ? reading
                : lastTipSpeed[i] + SPEED_SMOOTHING * (reading - lastTipSpeed[i]);
            double reading2 = lastTipSpeed == null ? 0 : (tipSpeed[i] - lastTipSpeed[i]) / dt;
            accel[i] = lastAccel[i] + SPEED_SMOOTHING * (reading2 - lastAccel[i]);
        }
        System.arraycopy(accel, 0, lastAccel, 0, 3);
        clamp(accel, MAX_ACCELERATION);
        int steps = Math.max(1, (int) Math.ceil(dt / MAX_STEP));
        double h = dt / steps;
        for (int s = 0; s < steps; s++) {
            // The end of the line in the frame of the moving top: gravity minus the top's acceleration, only the part
            // across the line (the line does not stretch), over the length; damped.
            double gx = -accel[0];
            double gy = -GRAVITY - accel[1];
            double gz = -accel[2];
            double along = gx * dir[0] + gy * dir[1] + gz * dir[2];
            for (int i = 0; i < 3; i++) {
                double g = i == 0 ? gx : i == 1 ? gy : gz;
                speed[i] += h * ((g - along * dir[i]) / length - DAMPING * speed[i]);
            }
            for (int i = 0; i < 3; i++) {
                dir[i] += h * speed[i];
            }
            normalize(dir);
            double radial = speed[0] * dir[0] + speed[1] * dir[1] + speed[2] * dir[2];
            for (int i = 0; i < 3; i++) {
                speed[i] -= radial * dir[i];
            }
        }
        lastTip = tip.clone();
        lastTipSpeed = tipSpeed;
        return dir;
    }

    /** Straight down and still, with the top at {@code tip}. */
    public void reset(double[] tip) {
        dir[0] = 0;
        dir[1] = -1;
        dir[2] = 0;
        speed[0] = speed[1] = speed[2] = 0;
        lastTip = tip == null ? null : tip.clone();
        lastTipSpeed = null;
        lastAccel[0] = lastAccel[1] = lastAccel[2] = 0;
        started = tip != null;
    }

    /** How far from the vertical the line is now, in radians. */
    public double angleFromVertical() {
        return Math.acos(Math.max(-1, Math.min(1, -dir[1])));
    }

    private static void clamp(double[] v, double max) {
        double len = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        if (len > max) {
            for (int i = 0; i < 3; i++) {
                v[i] *= max / len;
            }
        }
    }

    private static void normalize(double[] v) {
        double len = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        if (len < 1e-9) {
            v[0] = 0;
            v[1] = -1;
            v[2] = 0;
            return;
        }
        for (int i = 0; i < 3; i++) {
            v[i] /= len;
        }
    }

    /**
     * The rotation, as Euler angles {x, y, z} in radians for {@code rotateZ(z); rotateY(y); rotateX(x)} (GeckoLib's
     * order, applied in the bone's frame), that turns the unit vector {@code from} onto the unit vector {@code to}
     * (both in that frame), by the shortest arc.
     */
    public static float[] eulerTurning(double[] from, double[] to) {
        double ax = from[1] * to[2] - from[2] * to[1];
        double ay = from[2] * to[0] - from[0] * to[2];
        double az = from[0] * to[1] - from[1] * to[0];
        double sin = Math.sqrt(ax * ax + ay * ay + az * az);
        double cos = from[0] * to[0] + from[1] * to[1] + from[2] * to[2];
        if (sin < 1e-9) {
            if (cos > 0) {
                return new float[3];
            }
            // opposite: half a turn about any axis across "from"
            double[] other = Math.abs(from[0]) < 0.9 ? new double[] { 1, 0, 0 } : new double[] { 0, 1, 0 };
            ax = from[1] * other[2] - from[2] * other[1];
            ay = from[2] * other[0] - from[0] * other[2];
            az = from[0] * other[1] - from[1] * other[0];
            sin = Math.sqrt(ax * ax + ay * ay + az * az);
            cos = -1;
            ax /= sin;
            ay /= sin;
            az /= sin;
            sin = 0;
        } else {
            ax /= sin;
            ay /= sin;
            az /= sin;
        }
        double t = 1 - cos;
        // Rodrigues: R = cos I + sin [a]x + (1 - cos) a a^T
        double r00 = cos + t * ax * ax;
        double r10 = t * ax * ay + sin * az;
        double r20 = t * ax * az - sin * ay;
        double r21 = t * ay * az + sin * ax;
        double r22 = cos + t * az * az;
        // R = Rz(c) Ry(b) Rx(a)
        double b = Math.asin(Math.max(-1, Math.min(1, -r20)));
        double a = Math.atan2(r21, r22);
        double c = Math.atan2(r10, r00);
        return new float[] { (float) a, (float) b, (float) c };
    }
}
