package com.akashic.kunftig.blade;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

class BladeEngineTest {

    private static final double S45 = Math.sin(Math.PI / 8);
    private static final double C45 = Math.cos(Math.PI / 8);

    @Test
    void objReadsGroupsFlipsVAndInsetsUv() throws Exception {
        String obj = "# Created by Metasequoia\n" + "v 0.0 0.0 0.0\nv 1.0 0.0 0.0\nv 0.0 1.0 0.0\nv 1 1 0\n"
            + "vt 0.0 0.0\nvt 1.0 0.0\nvt 0.0 1.0\n"
            + "vn 0.0 0.0 1.0\n"
            + "f 1/1/1 2/2/1 3/3/1\n"
            + "g blade\n"
            + "f 1/1/1 2/2/1 3/3/1\n"
            + "g sheath\n"
            + "f 2//1 4//1 3//1\n"
            + "g blade\n"
            + "f 1 2 4\n";
        ObjModel m = ObjModel.parse(obj, "t.obj");
        assertEquals(Arrays.asList("Default", "blade", "sheath"), m.names());
        assertEquals(
            2,
            m.groups("BLADE")
                .size(),
            "same name twice: both drawn, case ignored");
        ObjModel.Face f = m.groups("blade")
            .get(0).faces.get(0);
        assertArrayEquals(new float[] { 0, 1 }, f.uvs[0], 0, "v flipped");
        assertArrayEquals(new float[] { 0, 0 }, f.uvs[2], 0);
        // uv pulled toward the centre (1/3, 2/3) by 0.0005
        assertArrayEquals(new float[] { 0.0005F, 1 - 0.0005F }, f.uv(0, 1, 1, 0, 0), 1e-7F);
        assertArrayEquals(new float[] { 1 - 0.0005F, 1 - 0.0005F }, f.uv(1, 1, 1, 0, 0), 1e-7F);
        // the operator scales and offsets before the inset
        assertArrayEquals(new float[] { 0.5005F, 2 - 0.0005F }, f.uv(0, 1, 2, 0.5F, 0), 1e-6F);
        assertArrayEquals(new float[] { 0, 0, 1 }, f.normal(0), 0);
        ObjModel.Face noVt = m.groups("sheath")
            .get(0).faces.get(0);
        assertArrayEquals(new float[] { 0, 0 }, noVt.uv(0, 1, 1, 0, 0), 0);
        ObjModel.Face bare = m.groups("blade")
            .get(1).faces.get(0);
        assertArrayEquals(new float[] { 0, 0, 1 }, bare.normal(1), 1e-6F, "face normal fallback");
        assertArrayEquals(new float[] { 0, 0, 0, 1, 1, 0 }, m.bounds(null), 0);
        assertThrows(ObjModel.FormatException.class, () -> ObjModel.parse("f 1 2 3\n", "x"));
        assertThrows(ObjModel.FormatException.class, () -> ObjModel.parse("v 1 2\n", "x"));
    }

    @Test
    void vmdSamplesWithLerpAndNlerpAndHoldsPastTheEnd() throws Exception {
        // a 90 degree turn about Y over 10 frames, and a move of 10 units in X
        Vmd v = Vmd.parse(
            new VmdWriter("BladeHolder").key("JointA1", 10, 0, 0, 0, 0, 0, 0, 1)
                .key("JointA1", 20, 10, 0, 0, 0, Math.sin(Math.PI / 4), 0, Math.cos(Math.PI / 4))
                .bytes(),
            "t.vmd");
        assertEquals("BladeHolder", v.model());
        assertEquals(20, v.maxFrame());
        double[] mid = v.sample("JointA1", 15);
        assertEquals(5, mid[0], 1e-6);
        // nlerp halfway between identity and 90 degrees is 45 degrees (exact for two keys symmetric about it)
        assertEquals(S45, mid[4], 1e-6);
        assertEquals(C45, mid[6], 1e-6);
        assertArrayEquals(v.sample("JointA1", 10), v.sample("JointA1", 3), 1e-12, "before the first key: the first");
        assertArrayEquals(v.sample("JointA1", 20), v.sample("JointA1", 99), 1e-12, "past the last: the last");
        assertArrayEquals(new double[] { 0, 0, 0, 0, 0, 0, 1 }, v.sample("nobody", 5), 0, "unkeyed: rest");
        assertThrows(Vmd.FormatException.class, () -> Vmd.parse(new byte[10], "x"));
    }

    @Test
    void nlerpTakesTheShortWay() {
        double[] a = { 0, 0, 0, 1 };
        double[] minusIdentity = { 0, 0, 0, -1 };
        double[] q = Mmd.nlerp(a, minusIdentity, 0.5);
        assertEquals(1, Math.abs(q[3]), 1e-12, "q and -q are the same rotation: no spin through zero");
    }

    @Test
    void theHardpointChainMultipliesChildFirst() throws Exception {
        // JointA1 moves 8 units up; hardpointA turns 90 degrees about Y: a point at x=16 OBJ units ends in front
        Vmd v = Vmd.parse(
            new VmdWriter("BladeHolder").key("JointA1", 0, 0, 8, 0, 0, 0, 0, 1)
                .key("hardpointA", 0, 0, 0, 0, 0, Math.sin(Math.PI / 4), 0, Math.cos(Math.PI / 4))
                .bytes(),
            "t.vmd");
        double[] m = BladeHolder.blade(v, 0);
        double[] origin = BladeHolder.toFrame(m, 0, 0, 0);
        assertArrayEquals(new double[] { 0, 1, 0 }, origin, 1e-9, "8 MMD units = 1 block up");
        double[] p = BladeHolder.toFrame(m, 16, 0, 0);
        assertEquals(1, p[1], 1e-9);
        assertEquals(1.0 / 8, Math.hypot(p[0], p[2]), 1e-9, "16 OBJ units = 1 MMD unit = 1/8 block");
        double[] sheath = BladeHolder.sheath(v, 0);
        assertArrayEquals(Mmd.identity(), sheath, 1e-12, "chain B unkeyed: identity");
    }

    @Test
    void eulerZYXRoundTrips() {
        double ex = 0.3;
        double ey = -0.4;
        double ez = 1.1;
        // q = qz * qy * qx (column vectors: X applied first)
        double[] qx = { Math.sin(ex / 2), 0, 0, Math.cos(ex / 2) };
        double[] qy = { 0, Math.sin(ey / 2), 0, Math.cos(ey / 2) };
        double[] qz = { 0, 0, Math.sin(ez / 2), Math.cos(ez / 2) };
        double[] q = qmul(qz, qmul(qy, qx));
        double[] e = Mmd.eulerZYX(q);
        assertArrayEquals(new double[] { ex, ey, ez }, e, 1e-9);
        assertTrue(Mmd.gl(Mmd.identity())[15] == 1F);
    }

    private static double[] qmul(double[] a, double[] b) {
        return new double[] { a[3] * b[0] + a[0] * b[3] + a[1] * b[2] - a[2] * b[1],
            a[3] * b[1] - a[0] * b[2] + a[1] * b[3] + a[2] * b[0],
            a[3] * b[2] + a[0] * b[1] - a[1] * b[0] + a[2] * b[3],
            a[3] * b[3] - a[0] * b[0] - a[1] * b[1] - a[2] * b[2] };
    }
}
