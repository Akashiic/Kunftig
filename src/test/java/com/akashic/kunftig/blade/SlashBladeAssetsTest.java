package com.akashic.kunftig.blade;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Checks the engine against SlashBlade's own files, which are not in this repository (art of SlashBlade: All Rights
 * Reserved): runs only with {@code -Dkunftig.sbAssets=<assets/slashblade folder>} ({@code ./gradlew :test
 * -PsbAssets=<folder>}). The expected values come from the study (docs of Overkill, fidelidade/estudo/02, section 3.4).
 */
@EnabledIfSystemProperty(named = "kunftig.sbAssets", matches = ".+")
class SlashBladeAssetsTest {

    private static File dir() {
        return new File(System.getProperty("kunftig.sbAssets"));
    }

    @Test
    void theBladeRestsAtTheLeftHipAndSwingsRightInComboA1() throws Exception {
        Vmd motion = Vmd.parse(Files.readAllBytes(new File(dir(), "combostate/motion.vmd").toPath()), "motion.vmd");
        assertEquals("BladeHolder", motion.model());
        assertEquals(2299, motion.maxFrame());
        for (int frame : new int[] { 0, 1, 41 }) {
            double[] a = BladeHolder.blade(motion, frame);
            double[] b = BladeHolder.sheath(motion, frame);
            assertArrayEquals(
                new double[] { -0.522, 0.796, -0.547 },
                BladeHolder.toFrame(a, 0, 0, 0),
                2e-3,
                "A " + frame);
            assertArrayEquals(BladeHolder.toFrame(a, 0, 0, 0), BladeHolder.toFrame(b, 0, 0, 0), 1e-6, "B " + frame);
            assertArrayEquals(new double[] { 0.089, 0.663, 1.639 }, BladeHolder.toFrame(a, -291, 0, 0), 2e-3);
            assertArrayEquals(new double[] { -0.61, 0.815, -0.862 }, BladeHolder.toFrame(a, 42, 0, 0), 2e-3);
        }
        double[] a10 = BladeHolder.blade(motion, 10);
        assertArrayEquals(new double[] { 0.413, 1.844, 0.661 }, BladeHolder.toFrame(a10, 0, 0, 0), 2e-3);
        assertArrayEquals(new double[] { 2.638, 1.913, 0.2 }, BladeHolder.toFrame(a10, -291, 0, 0), 2e-3);
    }

    @Test
    void theBladeModelHasSlashBladesGroups() throws Exception {
        ObjModel m = ObjModel.parse(
            new String(Files.readAllBytes(new File(dir(), "model/blade.obj").toPath()), StandardCharsets.UTF_8),
            "blade.obj");
        for (String g : new String[] { "blade", "blade_damaged", "blade_fragment", "sheath", "effect", "item_blade",
            "item_damaged", "item_bladens" }) {
            assertTrue(m.has(g), g);
        }
        float[] b = m.bounds("blade");
        assertEquals(-290.96, b[0], 0.01);
        assertEquals(41.74, b[3], 0.01);
        for (ObjModel.Group g : m.groups()) {
            for (ObjModel.Face f : g.faces) {
                assertEquals(3, f.size(), "all triangles");
            }
        }
    }

    private static float deg(float r) {
        return (float) Math.toDegrees(r);
    }

    @Test
    void thePlayerMotionGivesTheStudysSanityPoses() throws Exception {
        Vmd motion = Vmd.parse(Files.readAllBytes(new File(dir(), "model/pa/player_motion.vmd").toPath()), "player");
        assertEquals("Alex", motion.model());
        BodyPose p0 = BodyPose.at(motion, 0);
        assertEquals(7.4, deg(p0.rotation[BodyPose.RIGHT_ARM][2]), 0.15);
        assertEquals(-4.6, deg(p0.rotation[BodyPose.LEFT_ARM][2]), 0.15);
        BodyPose p6 = BodyPose.at(motion, 6);
        assertEquals(31.0, deg(p6.rotation[BodyPose.RIGHT_ARM][0]), 0.15);
        assertEquals(29.3, deg(p6.rotation[BodyPose.RIGHT_ARM][1]), 0.15);
        assertEquals(59.9, deg(p6.rotation[BodyPose.RIGHT_ARM][2]), 0.15);
        assertArrayEquals(new float[] { 1.22F, 2.23F, 0.54F }, p6.offset[BodyPose.RIGHT_ARM], 0.02F);
        assertEquals(19.4, deg(p6.rotation[BodyPose.LEFT_ARM][0]), 0.15);
        assertEquals(8.4, deg(p6.rotation[BodyPose.LEFT_ARM][1]), 0.15);
        assertEquals(-73.6, deg(p6.rotation[BodyPose.LEFT_ARM][2]), 0.15);
        assertEquals(-11.7, deg(p6.bodyRotation[1]), 0.15);
        assertArrayEquals(new float[] { 0.038F, -0.1F, -0.038F }, p6.bodyPosition, 0.003F);
        assertEquals(-28.5, deg(p6.rotation[BodyPose.HEAD][1]), 0.15);
        BodyPose p11 = BodyPose.at(motion, 11);
        assertEquals(-135, deg(p11.rotation[BodyPose.LEFT_ARM][0]), 0.5);
        assertEquals(-16, deg(p11.rotation[BodyPose.LEFT_ARM][1]), 0.5);
        assertEquals(-72, deg(p11.rotation[BodyPose.LEFT_ARM][2]), 0.5);
        assertEquals(-65.5, deg(p11.bodyRotation[1]), 0.15);
    }
}
