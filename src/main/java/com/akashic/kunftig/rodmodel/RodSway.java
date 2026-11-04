package com.akashic.kunftig.rodmodel;

/**
 * The 3D rod's inertia in first person ({@code balanco}, issue #13): turning the camera, stepping sideways, jumping or
 * falling leaves the rod behind for a moment, and it springs back with a small overshoot. Two damped springs per axis
 * of eye space: the whole rod around the grip (stiffer), and the bend of the rod (softer and less damped, so the tip
 * whips). Vanilla's own sway (the arm following the camera at a tenth of the lag) and the view bobbing stay; this is
 * on top of them. Pure math, ticked 20 times per second and interpolated for the frame; no Minecraft classes.
 * <p>
 * The springs give how far the tip lags, in degrees: up (negative: down) and left (negative: right), as seen from the
 * camera. {@link #eyeRotation} turns that into a rotation of eye space (X right, Y up, looking down -Z) about the axis
 * that moves the tip that way, from the rod's own direction: a rod held up on the screen swings sideways about the
 * view axis, one pointing ahead about the vertical axis.
 */
public final class RodSway {

    /** Degrees of lag per degree of camera turn in one tick, and the cap (degrees). */
    static final float ROOT_TURN = 0.22F;
    static final float ROOT_MAX = 8F;
    static final float BEND_TURN = 0.35F;
    static final float BEND_MAX = 14F;
    /** Degrees of lag per block per tick of movement (sideways, vertical). */
    static final float ROOT_STEP = 22F;
    static final float BEND_STEP = 30F;
    static final float ROOT_FALL = 10F;
    static final float BEND_FALL = 16F;

    /** Spring stiffness and damping per tick: root ~0.5 s period, bend ~0.65 s and livelier. */
    static final float ROOT_K = 0.35F;
    static final float ROOT_C = 0.45F;
    static final float BEND_K = 0.22F;
    static final float BEND_C = 0.22F;

    private static final float RAD = (float) (Math.PI / 180);

    /** One damped spring, in degrees. */
    static final class Spring {

        final float k;
        final float c;
        float value;
        float previous;
        float speed;

        Spring(float k, float c) {
            this.k = k;
            this.c = c;
        }

        void step(float target) {
            previous = value;
            speed += k * (target - value) - c * speed;
            value += speed;
        }

        float at(float partial) {
            return previous + (value - previous) * partial;
        }

        void reset() {
            value = previous = speed = 0;
        }
    }

    /** {up, left} of the whole rod and of the bend. */
    private final Spring[] root = { new Spring(ROOT_K, ROOT_C), new Spring(ROOT_K, ROOT_C) };
    private final Spring[] bend = { new Spring(BEND_K, BEND_C), new Spring(BEND_K, BEND_C) };

    /**
     * One client tick.
     *
     * @param turnYaw   degrees the camera turned this tick (positive: to the right)
     * @param turnPitch degrees the camera pitched this tick (positive: down, Minecraft's sign)
     * @param right     blocks moved this tick to the camera's right
     * @param up        blocks moved this tick upwards
     * @param strength  the manifest's {@code forca} (0 turns it off)
     */
    public void tick(float turnYaw, float turnPitch, float right, float up, float strength) {
        float s = Math.max(0, strength);
        // Turning right or stepping right leaves the tip to the left; looking up (pitch decreasing) or going up
        // leaves it low.
        root[0].step(s * clamp(ROOT_TURN * turnPitch - ROOT_FALL * up, ROOT_MAX));
        root[1].step(s * clamp(ROOT_TURN * turnYaw + ROOT_STEP * right, ROOT_MAX));
        bend[0].step(s * clamp(BEND_TURN * turnPitch - BEND_FALL * up, BEND_MAX));
        bend[1].step(s * clamp(BEND_TURN * turnYaw + BEND_STEP * right, BEND_MAX));
    }

    /** How far the whole rod lags about the grip: {up, left}, radians. */
    public float[] root(float partial) {
        return vector(root, partial);
    }

    /** How far the bend lags: {up, left}, radians, shared among the bending bones. */
    public float[] bend(float partial) {
        return vector(bend, partial);
    }

    /** Back at rest (the rod just came to the hand). */
    public void reset() {
        for (int i = 0; i < 2; i++) {
            root[i].reset();
            bend[i].reset();
        }
    }

    /** Whether the rod is (nearly) at rest: nothing to add to the draw. */
    public boolean resting() {
        for (int i = 0; i < 2; i++) {
            if (Math.abs(root[i].value) + Math.abs(root[i].speed) + Math.abs(bend[i].value) + Math.abs(bend[i].speed)
                > 1e-3F) {
                return false;
            }
        }
        return true;
    }

    private static float[] vector(Spring[] s, float partial) {
        return new float[] { s[0].at(partial) * RAD, s[1].at(partial) * RAD };
    }

    /**
     * The eye-space rotation vector that moves the tip of a rod pointing along {@code rod} (eye space, any length)
     * {@code lag[0]} radians up and {@code lag[1]} radians to the left: about {@code rod x direction}, so the tip moves
     * along the direction (the part of it across the rod). A direction along the rod moves nothing.
     */
    public static float[] eyeRotation(float[] rod, float[] lag) {
        float[] out = new float[3];
        addAbout(out, rod, 0, 1, 0, lag[0]);
        addAbout(out, rod, -1, 0, 0, lag[1]);
        return out;
    }

    private static void addAbout(float[] out, float[] r, float dx, float dy, float dz, float angle) {
        float ax = r[1] * dz - r[2] * dy;
        float ay = r[2] * dx - r[0] * dz;
        float az = r[0] * dy - r[1] * dx;
        float len = (float) Math.sqrt(ax * ax + ay * ay + az * az);
        float rlen = (float) Math.sqrt(r[0] * r[0] + r[1] * r[1] + r[2] * r[2]);
        if (!(len > 1e-4F * rlen)) {
            return;
        }
        out[0] += ax / len * angle;
        out[1] += ay / len * angle;
        out[2] += az / len * angle;
    }

    private static float clamp(float v, float max) {
        return Math.max(-max, Math.min(max, v));
    }

    /** {@code a - b} in degrees, wrapped to -180..180 (yaw crosses 360 freely). */
    public static float turn(float a, float b) {
        float d = (a - b) % 360F;
        if (d >= 180F) {
            d -= 360F;
        } else if (d < -180F) {
            d += 360F;
        }
        return d;
    }

    /**
     * A rotation vector of eye space expressed in a bone's frame, given the bone frame's linear part in eye space
     * ({@code m}, row-major 3x3, uniform scale, possibly mirrored as GeckoLib's models are): rotating the bone by the
     * result about its pivot rotates it in eye space by {@code eye}. Small angles: the components go to the bone's
     * X, Y and Z rotations.
     */
    public static float[] toLocal(float[] m, float[] eye) {
        float det = m[0] * (m[4] * m[8] - m[5] * m[7]) - m[1] * (m[3] * m[8] - m[5] * m[6])
            + m[2] * (m[3] * m[7] - m[4] * m[6]);
        float scale = (float) Math.cbrt(Math.abs(det));
        if (!(scale > 1e-6F)) {
            return new float[3];
        }
        // Q = m / scale is orthogonal; Q R(a, t) Q^T = R(Q a, det(Q) t), so the local vector is det(Q) Q^T eye.
        float sign = det < 0 ? -1 : 1;
        float f = sign / scale;
        return new float[] { f * (m[0] * eye[0] + m[3] * eye[1] + m[6] * eye[2]),
            f * (m[1] * eye[0] + m[4] * eye[1] + m[7] * eye[2]), f * (m[2] * eye[0] + m[5] * eye[1] + m[8] * eye[2]) };
    }
}
