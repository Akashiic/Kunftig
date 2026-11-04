package com.akashic.kunftig.rodmodel;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RodSwayTest {

    private static final float DEG = (float) (180 / Math.PI);

    @Test
    void turningRightLeavesTheTipLeftThenItSpringsBackPastTheMiddle() {
        RodSway s = new RodSway();
        for (int i = 0; i < 6; i++) {
            s.tick(15, 0, 0, 0, 1); // turning right, 15 degrees per tick
        }
        float lagging = s.root(1)[1] * DEG;
        assertTrue(lagging > 1.5F, "a vara fica para trás (ponta à esquerda): " + lagging);
        assertTrue(s.bend(1)[1] * DEG > lagging, "a ponta curva mais que o cabo gira");
        float least = Float.MAX_VALUE;
        for (int i = 0; i < 40; i++) {
            s.tick(0, 0, 0, 0, 1);
            least = Math.min(least, s.bend(1)[1] * DEG);
        }
        assertTrue(least < 0, "parou de girar: a ponta passa do meio (balanço) " + least);
        for (int i = 0; i < 200; i++) {
            s.tick(0, 0, 0, 0, 1);
        }
        assertTrue(s.resting(), "e volta ao repouso");
    }

    @Test
    void lookingUpOrJumpingLeavesTheTipLow() {
        RodSway s = new RodSway();
        for (int i = 0; i < 4; i++) {
            s.tick(0, -10, 0, 0, 1); // pitch decreasing: looking up
        }
        assertTrue(s.root(1)[0] < 0);
        RodSway j = new RodSway();
        for (int i = 0; i < 4; i++) {
            j.tick(0, 0, 0, 0.4F, 1);
        }
        assertTrue(j.bend(1)[0] < 0);
    }

    @Test
    void steppingRightLeavesTheTipLeft() {
        RodSway s = new RodSway();
        for (int i = 0; i < 8; i++) {
            s.tick(0, 0, 0.2F, 0, 1);
        }
        assertTrue(s.root(1)[1] > 0);
        assertTrue(s.bend(1)[1] > 0);
    }

    @Test
    void theLagMovesTheTipThatWayWhereverTheRodPoints() {
        float up = 0.1F;
        float left = 0.2F;
        // held up on the screen, pointing ahead, and tilted ahead-up-right
        for (float[] rod : new float[][] { { 0, 1, 0 }, { 0, 0, -1 }, { 0.3F, 0.8F, -0.5F } }) {
            float[] w = RodSway.eyeRotation(rod, new float[] { 0, left });
            float[] d = cross(w, rod);
            assertTrue(d[0] < 0, "à esquerda: " + d[0]);
            assertEquals(0, dot(d, rod), 1e-5, "sem esticar a vara");
            float[] v = RodSway.eyeRotation(rod, new float[] { up, 0 });
            float[] e = cross(v, rod);
            if (Math.abs(rod[1]) < 0.99F) {
                assertTrue(e[1] > 0, "para cima: " + e[1]);
            }
        }
        // the up of a rod held straight up moves nothing (it would only stretch it)
        assertArrayEquals(new float[3], RodSway.eyeRotation(new float[] { 0, 1, 0 }, new float[] { up, 0 }));
    }

    private static float[] cross(float[] a, float[] b) {
        return new float[] { a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0] };
    }

    private static float dot(float[] a, float[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    @Test
    void aWildTurnStaysWithinItsCapAndStrengthZeroIsStill() {
        RodSway s = new RodSway();
        float most = 0;
        for (int i = 0; i < 60; i++) {
            s.tick(i < 20 ? 180 : 0, 90, 5, 5, 1);
            most = Math.max(most, Math.abs(s.root(1)[1] * DEG));
        }
        assertTrue(most < RodSway.ROOT_MAX * 1.6F, "teto do giro: " + most);
        RodSway off = new RodSway();
        off.tick(40, 40, 1, 1, 0);
        assertTrue(off.resting());
    }

    @Test
    void framesInterpolateBetweenTicks() {
        RodSway s = new RodSway();
        s.tick(20, 0, 0, 0, 1);
        s.tick(20, 0, 0, 0, 1);
        float a = s.root(0)[1];
        float b = s.root(1)[1];
        assertEquals((a + b) / 2, s.root(0.5F)[1], 1e-6);
    }

    @Test
    void turnWrapsAround360() {
        assertEquals(20, RodSway.turn(10, 350), 1e-4);
        assertEquals(-20, RodSway.turn(350, 10), 1e-4);
        assertEquals(5, RodSway.turn(725, 720), 1e-4);
    }

    @Test
    void theLocalRotationIsTheEyeRotationThroughAnyFrame() {
        float[] eye = { 0.03F, -0.05F, 0.02F };
        // a rotated, scaled frame; the same mirrored on X (GeckoLib's models)
        RodMatrix rotated = RodMatrix.identity()
            .rotateY(35)
            .rotateX(-66)
            .rotateZ(12)
            .scale(0.42F, 0.42F, 0.42F);
        RodMatrix mirrored = rotated.copy()
            .scale(-1, 1, 1);
        for (RodMatrix frame : new RodMatrix[] { RodMatrix.identity(), rotated, mirrored }) {
            float[] m = linear(frame);
            float[] local = RodSway.toLocal(m, eye);
            // frame * R(local) * frame^-1 must be R(eye) (small angles: compare the rotation's effect on vectors)
            float[] r = mul(mul(m, rotation(local)), inverse(m));
            float[] expected = rotation(eye);
            for (int i = 0; i < 9; i++) {
                assertEquals(expected[i], r[i], 2e-3, "elemento " + i);
            }
        }
    }

    @Test
    void aDegenerateFrameGivesNothing() {
        assertArrayEquals(new float[3], RodSway.toLocal(new float[9], new float[] { 1, 1, 1 }));
    }

    private static float[] linear(RodMatrix m) {
        float[] out = new float[9];
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                out[r * 3 + c] = m.get(r, c);
            }
        }
        return out;
    }

    /** Small-angle rotation for a rotation vector, built as Z, then Y, then X (the order the renderer applies). */
    private static float[] rotation(float[] v) {
        return mul(mul(rz(v[2]), ry(v[1])), rx(v[0]));
    }

    private static float[] rx(float a) {
        float c = (float) Math.cos(a), s = (float) Math.sin(a);
        return new float[] { 1, 0, 0, 0, c, -s, 0, s, c };
    }

    private static float[] ry(float a) {
        float c = (float) Math.cos(a), s = (float) Math.sin(a);
        return new float[] { c, 0, s, 0, 1, 0, -s, 0, c };
    }

    private static float[] rz(float a) {
        float c = (float) Math.cos(a), s = (float) Math.sin(a);
        return new float[] { c, -s, 0, s, c, 0, 0, 0, 1 };
    }

    private static float[] mul(float[] a, float[] b) {
        float[] o = new float[9];
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                o[r * 3 + c] = a[r * 3] * b[c] + a[r * 3 + 1] * b[3 + c] + a[r * 3 + 2] * b[6 + c];
            }
        }
        return o;
    }

    private static float[] inverse(float[] m) {
        float det = m[0] * (m[4] * m[8] - m[5] * m[7]) - m[1] * (m[3] * m[8] - m[5] * m[6])
            + m[2] * (m[3] * m[7] - m[4] * m[6]);
        return new float[] { (m[4] * m[8] - m[5] * m[7]) / det, (m[2] * m[7] - m[1] * m[8]) / det,
            (m[1] * m[5] - m[2] * m[4]) / det, (m[5] * m[6] - m[3] * m[8]) / det, (m[0] * m[8] - m[2] * m[6]) / det,
            (m[2] * m[3] - m[0] * m[5]) / det, (m[3] * m[7] - m[4] * m[6]) / det, (m[1] * m[6] - m[0] * m[7]) / det,
            (m[0] * m[4] - m[1] * m[3]) / det };
    }
}
