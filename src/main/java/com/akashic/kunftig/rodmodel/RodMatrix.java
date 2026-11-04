package com.akashic.kunftig.rodmodel;

/**
 * A 4x4 matrix in OpenGL's layout (column-major), composed like the GL matrix stack: each operation multiplies on the
 * right, so {@code identity().translate(..).rotate(..)} equals {@code glTranslatef(..); glRotatef(..)}. Angles in
 * degrees, rotations right-handed as {@code glRotatef}. Mutable, not thread-safe. No Minecraft classes.
 */
public final class RodMatrix {

    private final float[] m = new float[16];

    private RodMatrix() {
        m[0] = m[5] = m[10] = m[15] = 1;
    }

    public static RodMatrix identity() {
        return new RodMatrix();
    }

    /** A copy of a column-major array of 16. */
    public static RodMatrix of(float[] columnMajor) {
        RodMatrix r = new RodMatrix();
        System.arraycopy(columnMajor, 0, r.m, 0, 16);
        return r;
    }

    public RodMatrix copy() {
        return of(m);
    }

    /** The values, column-major (for {@code glMultMatrix}). */
    public float[] values() {
        return m.clone();
    }

    public float get(int row, int column) {
        return m[column * 4 + row];
    }

    public RodMatrix translate(float x, float y, float z) {
        RodMatrix t = new RodMatrix();
        t.m[12] = x;
        t.m[13] = y;
        t.m[14] = z;
        return multiply(t);
    }

    public RodMatrix scale(float x, float y, float z) {
        RodMatrix s = new RodMatrix();
        s.m[0] = x;
        s.m[5] = y;
        s.m[10] = z;
        return multiply(s);
    }

    public RodMatrix rotateX(float degrees) {
        return rotate(degrees, 0);
    }

    public RodMatrix rotateY(float degrees) {
        return rotate(degrees, 1);
    }

    public RodMatrix rotateZ(float degrees) {
        return rotate(degrees, 2);
    }

    private RodMatrix rotate(float degrees, int axis) {
        if (degrees == 0) {
            return this;
        }
        double rad = Math.toRadians(degrees);
        float c = (float) Math.cos(rad);
        float s = (float) Math.sin(rad);
        RodMatrix r = new RodMatrix();
        int a = (axis + 1) % 3;
        int b = (axis + 2) % 3;
        // In the plane (a, b): a' = c*a - s*b, b' = s*a + c*b.
        r.m[a * 4 + a] = c;
        r.m[b * 4 + a] = -s;
        r.m[a * 4 + b] = s;
        r.m[b * 4 + b] = c;
        return multiply(r);
    }

    /** {@code this = this * o}. */
    public RodMatrix multiply(RodMatrix o) {
        float[] out = new float[16];
        for (int col = 0; col < 4; col++) {
            for (int row = 0; row < 4; row++) {
                float sum = 0;
                for (int k = 0; k < 4; k++) {
                    sum += m[k * 4 + row] * o.m[col * 4 + k];
                }
                out[col * 4 + row] = sum;
            }
        }
        System.arraycopy(out, 0, m, 0, 16);
        return this;
    }

    /** The inverse (a new matrix); the identity for a singular one. */
    public RodMatrix inverse() {
        float[] a = m;
        float[] inv = new float[16];
        inv[0] = a[5] * a[10] * a[15] - a[5] * a[11] * a[14]
            - a[9] * a[6] * a[15]
            + a[9] * a[7] * a[14]
            + a[13] * a[6] * a[11]
            - a[13] * a[7] * a[10];
        inv[4] = -a[4] * a[10] * a[15] + a[4] * a[11] * a[14]
            + a[8] * a[6] * a[15]
            - a[8] * a[7] * a[14]
            - a[12] * a[6] * a[11]
            + a[12] * a[7] * a[10];
        inv[8] = a[4] * a[9] * a[15] - a[4] * a[11] * a[13]
            - a[8] * a[5] * a[15]
            + a[8] * a[7] * a[13]
            + a[12] * a[5] * a[11]
            - a[12] * a[7] * a[9];
        inv[12] = -a[4] * a[9] * a[14] + a[4] * a[10] * a[13]
            + a[8] * a[5] * a[14]
            - a[8] * a[6] * a[13]
            - a[12] * a[5] * a[10]
            + a[12] * a[6] * a[9];
        inv[1] = -a[1] * a[10] * a[15] + a[1] * a[11] * a[14]
            + a[9] * a[2] * a[15]
            - a[9] * a[3] * a[14]
            - a[13] * a[2] * a[11]
            + a[13] * a[3] * a[10];
        inv[5] = a[0] * a[10] * a[15] - a[0] * a[11] * a[14]
            - a[8] * a[2] * a[15]
            + a[8] * a[3] * a[14]
            + a[12] * a[2] * a[11]
            - a[12] * a[3] * a[10];
        inv[9] = -a[0] * a[9] * a[15] + a[0] * a[11] * a[13]
            + a[8] * a[1] * a[15]
            - a[8] * a[3] * a[13]
            - a[12] * a[1] * a[11]
            + a[12] * a[3] * a[9];
        inv[13] = a[0] * a[9] * a[14] - a[0] * a[10] * a[13]
            - a[8] * a[1] * a[14]
            + a[8] * a[2] * a[13]
            + a[12] * a[1] * a[10]
            - a[12] * a[2] * a[9];
        inv[2] = a[1] * a[6] * a[15] - a[1] * a[7] * a[14]
            - a[5] * a[2] * a[15]
            + a[5] * a[3] * a[14]
            + a[13] * a[2] * a[7]
            - a[13] * a[3] * a[6];
        inv[6] = -a[0] * a[6] * a[15] + a[0] * a[7] * a[14]
            + a[4] * a[2] * a[15]
            - a[4] * a[3] * a[14]
            - a[12] * a[2] * a[7]
            + a[12] * a[3] * a[6];
        inv[10] = a[0] * a[5] * a[15] - a[0] * a[7] * a[13]
            - a[4] * a[1] * a[15]
            + a[4] * a[3] * a[13]
            + a[12] * a[1] * a[7]
            - a[12] * a[3] * a[5];
        inv[14] = -a[0] * a[5] * a[14] + a[0] * a[6] * a[13]
            + a[4] * a[1] * a[14]
            - a[4] * a[2] * a[13]
            - a[12] * a[1] * a[6]
            + a[12] * a[2] * a[5];
        inv[3] = -a[1] * a[6] * a[11] + a[1] * a[7] * a[10]
            + a[5] * a[2] * a[11]
            - a[5] * a[3] * a[10]
            - a[9] * a[2] * a[7]
            + a[9] * a[3] * a[6];
        inv[7] = a[0] * a[6] * a[11] - a[0] * a[7] * a[10]
            - a[4] * a[2] * a[11]
            + a[4] * a[3] * a[10]
            + a[8] * a[2] * a[7]
            - a[8] * a[3] * a[6];
        inv[11] = -a[0] * a[5] * a[11] + a[0] * a[7] * a[9]
            + a[4] * a[1] * a[11]
            - a[4] * a[3] * a[9]
            - a[8] * a[1] * a[7]
            + a[8] * a[3] * a[5];
        inv[15] = a[0] * a[5] * a[10] - a[0] * a[6] * a[9]
            - a[4] * a[1] * a[10]
            + a[4] * a[2] * a[9]
            + a[8] * a[1] * a[6]
            - a[8] * a[2] * a[5];
        float det = a[0] * inv[0] + a[1] * inv[4] + a[2] * inv[8] + a[3] * inv[12];
        RodMatrix r = new RodMatrix();
        if (det == 0 || !Float.isFinite(det)) {
            return r;
        }
        for (int i = 0; i < 16; i++) {
            r.m[i] = inv[i] / det;
        }
        return r;
    }

    /** An angle in radians brought to [-PI, PI): the same rotation, the short way from 0. */
    public static float wrap(float radians) {
        double twoPi = Math.PI * 2;
        double r = (radians + Math.PI) % twoPi;
        if (r < 0) {
            r += twoPi;
        }
        return (float) (r - Math.PI);
    }

    /** The point {@code (x, y, z, 1)} transformed. */
    public float[] transform(float x, float y, float z) {
        return new float[] { m[0] * x + m[4] * y + m[8] * z + m[12], m[1] * x + m[5] * y + m[9] * z + m[13],
            m[2] * x + m[6] * y + m[10] * z + m[14] };
    }
}
