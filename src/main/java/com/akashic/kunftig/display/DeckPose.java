package com.akashic.kunftig.display;

/**
 * Where a display is and how it is turned, as the renderer draws it: {@code world = origin + Ry(-yaw) Rx(pitch)
 * Rz(roll) local}, right-handed, degrees. Turns points between the world and the display's frame. No Minecraft
 * classes; immutable.
 */
public final class DeckPose {

    public final double x;
    public final double y;
    public final double z;
    public final float yaw;
    /** Row-major rotation, local to world. */
    private final double m00;
    private final double m01;
    private final double m02;
    private final double m10;
    private final double m11;
    private final double m12;
    private final double m20;
    private final double m21;
    private final double m22;

    public DeckPose(double x, double y, double z, float yaw, float pitch, float roll) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        double a = Math.toRadians(-yaw);
        double b = Math.toRadians(pitch);
        double c = Math.toRadians(roll);
        double ca = Math.cos(a), sa = Math.sin(a);
        double cb = Math.cos(b), sb = Math.sin(b);
        double cc = Math.cos(c), sc = Math.sin(c);
        // Rx(b) Rz(c)
        double r00 = cc, r01 = -sc, r02 = 0;
        double r10 = cb * sc, r11 = cb * cc, r12 = -sb;
        double r20 = sb * sc, r21 = sb * cc, r22 = cb;
        // Ry(a) on the left
        m00 = ca * r00 + sa * r20;
        m01 = ca * r01 + sa * r21;
        m02 = ca * r02 + sa * r22;
        m10 = r10;
        m11 = r11;
        m12 = r12;
        m20 = -sa * r00 + ca * r20;
        m21 = -sa * r01 + ca * r21;
        m22 = -sa * r02 + ca * r22;
    }

    /** A point of the display's frame in the world. */
    public double[] toWorld(double lx, double ly, double lz) {
        return new double[] { x + m00 * lx + m01 * ly + m02 * lz, y + m10 * lx + m11 * ly + m12 * lz,
            z + m20 * lx + m21 * ly + m22 * lz };
    }

    /** A point of the world in the display's frame. */
    public double[] toLocal(double wx, double wy, double wz) {
        double dx = wx - x, dy = wy - y, dz = wz - z;
        return new double[] { m00 * dx + m10 * dy + m20 * dz, m01 * dx + m11 * dy + m21 * dz,
            m02 * dx + m12 * dy + m22 * dz };
    }

    /** The world's up in the display's frame (a unit vector). */
    public double[] up() {
        return new double[] { m10, m11, m12 };
    }
}
