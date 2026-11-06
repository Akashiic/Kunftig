package com.akashic.kunftig.blade;

/**
 * The MMD math SlashBlade uses (NyMMD, MIT, nyatla; as bundled in SlashBlade: Resharped). Matrices are 4x4 doubles in
 * NyMMD's row-vector convention ({@code v' = v·M}, translation in row 3); a matrix is stored row by row
 * ({@code m00, m01, m02, m03, m10, ..., m33}), which is exactly the column-major float[16] that
 * {@code glMultMatrix} expects for the same transform. Quaternions are {x, y, z, w}. No Minecraft classes.
 */
public final class Mmd {

    private Mmd() {}

    /** The identity. */
    public static double[] identity() {
        double[] m = new double[16];
        m[0] = m[5] = m[10] = m[15] = 1;
        return m;
    }

    /** NyMMD's {@code QuaternionToMatrix}: the rotation of {@code q} with translation {@code t} in row 3. */
    public static double[] matrix(double[] q, double[] t) {
        double x2 = q[0] * q[0] * 2.0;
        double y2 = q[1] * q[1] * 2.0;
        double z2 = q[2] * q[2] * 2.0;
        double xy = q[0] * q[1] * 2.0;
        double yz = q[1] * q[2] * 2.0;
        double zx = q[2] * q[0] * 2.0;
        double xw = q[0] * q[3] * 2.0;
        double yw = q[1] * q[3] * 2.0;
        double zw = q[2] * q[3] * 2.0;
        double[] m = new double[16];
        m[0] = 1.0 - y2 - z2;
        m[1] = xy + zw;
        m[2] = zx - yw;
        m[4] = xy - zw;
        m[5] = 1.0 - z2 - x2;
        m[6] = yz + xw;
        m[8] = zx + yw;
        m[9] = yz - xw;
        m[10] = 1.0 - x2 - y2;
        m[12] = t[0];
        m[13] = t[1];
        m[14] = t[2];
        m[15] = 1;
        return m;
    }

    /** {@code a·b} (row-vector convention: apply {@code a}, then {@code b}). */
    public static double[] mul(double[] a, double[] b) {
        double[] r = new double[16];
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                double s = 0;
                for (int k = 0; k < 4; k++) {
                    s += a[i * 4 + k] * b[k * 4 + j];
                }
                r[i * 4 + j] = s;
            }
        }
        return r;
    }

    /** A point through a matrix ({@code p·M}). */
    public static double[] apply(double[] m, double x, double y, double z) {
        return new double[] { x * m[0] + y * m[4] + z * m[8] + m[12], x * m[1] + y * m[5] + z * m[9] + m[13],
            x * m[2] + y * m[6] + z * m[10] + m[14] };
    }

    /** NyMMD's {@code QuaternionSlerp}, which is a normalized lerp along the short way (not a true slerp). */
    public static double[] nlerp(double[] a, double[] b, double t) {
        double dot = a[0] * b[0] + a[1] * b[1] + a[2] * b[2] + a[3] * b[3];
        double t0 = 1.0 - t;
        double s = dot < 0 ? -t : t;
        double[] q = { a[0] * t0 + b[0] * s, a[1] * t0 + b[1] * s, a[2] * t0 + b[2] * s, a[3] * t0 + b[3] * s };
        return normalize(q);
    }

    public static double[] normalize(double[] q) {
        double inv = 1.0 / Math.sqrt(q[0] * q[0] + q[1] * q[1] + q[2] * q[2] + q[3] * q[3]);
        return new double[] { q[0] * inv, q[1] * inv, q[2] * inv, q[3] * inv };
    }

    /**
     * ZYX Euler angles (radians) {ex, ey, ez} of a quaternion, with {@code R = Rz(ez)·Ry(ey)·Rx(ex)} in the
     * column-vector convention, as SlashBlade's player animation takes them ({@code VmdAnimation}).
     */
    public static double[] eulerZYX(double[] q) {
        double x = q[0];
        double y = q[1];
        double z = q[2];
        double w = q[3];
        double m00 = 1 - 2 * (y * y + z * z);
        double m01 = 2 * (x * y + w * z);
        double m02 = 2 * (x * z - w * y);
        double m12 = 2 * (y * z + w * x);
        double m22 = 1 - 2 * (x * x + y * y);
        double ez = Math.atan2(m01, m00);
        double ey = Math.asin(Math.max(-1, Math.min(1, -m02)));
        double ex = Math.atan2(m12, m22);
        return new double[] { ex, ey, ez };
    }

    /** The matrix as the float[16] {@code glMultMatrix} takes (same order). */
    public static float[] gl(double[] m) {
        float[] f = new float[16];
        for (int i = 0; i < 16; i++) {
            f[i] = (float) m[i];
        }
        return f;
    }
}
