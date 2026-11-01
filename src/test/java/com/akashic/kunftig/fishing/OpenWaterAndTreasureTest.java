package com.akashic.kunftig.fishing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Test;

class OpenWaterAndTreasureTest {

    /** A lake: water below surfaceY, air from surfaceY up, with optional overrides. */
    private static OpenWater.Probe lake(final int surfaceY, final int[] solid) {
        return (x, y, z) -> {
            if (solid != null && x == solid[0] && y == solid[1] && z == solid[2]) {
                return OpenWater.Type.INVALID;
            }
            return y < surfaceY ? OpenWater.Type.INSIDE_WATER : OpenWater.Type.ABOVE_WATER;
        };
    }

    @Test
    void bobberOnAnOpenLakeIsOpenWater() {
        // Hook block y=63 at the surface: layers 62 and 63 water, 64 and 65 air.
        assertTrue(OpenWater.isOpenWater(lake(64, null), 0, 63, 0));
    }

    @Test
    void aBlockInTheAreaBreaksIt() {
        assertFalse(OpenWater.isOpenWater(lake(64, new int[] { 2, 64, -2 }), 0, 63, 0), "solid block above");
        assertFalse(OpenWater.isOpenWater(lake(64, new int[] { -1, 62, 1 }), 0, 63, 0), "solid block below");
        assertTrue(OpenWater.isOpenWater(lake(64, new int[] { 3, 64, 0 }), 0, 63, 0), "outside the 5x5 is ignored");
    }

    @Test
    void layersMustGoFromWaterToAir() {
        // Air below water (an overhang of water) is not open water.
        OpenWater.Probe inverted = (x, y, z) -> y < 63 ? OpenWater.Type.ABOVE_WATER : OpenWater.Type.INSIDE_WATER;
        assertFalse(OpenWater.isOpenWater(inverted, 0, 63, 0));
        // The lowest layer cannot be air.
        assertFalse(OpenWater.isOpenWater(lake(62, null), 0, 63, 0));
        // All four layers water (deep under the surface) is fine.
        assertTrue(OpenWater.isOpenWater(lake(100, null), 0, 63, 0));
    }

    @Test
    void treasureIntervalsMatchForge() {
        assertEquals(0.1F, TreasureRule.junkChance(0, 0), 1e-6);
        assertEquals(0.05F, TreasureRule.treasureChance(0, 0), 1e-6);
        assertEquals(0.0F, TreasureRule.junkChance(10, 10), 0.0F);
        assertEquals(0.08F, TreasureRule.treasureChance(3, 0), 1e-6);
    }

    @Test
    void remapRemovesTreasureAndRenormalises() {
        for (int luck = 0; luck <= 3; luck++) {
            for (int lure = 0; lure <= 3; lure++) {
                float junk = TreasureRule.junkChance(luck, lure);
                float treasure = TreasureRule.treasureChance(luck, lure);
                Random random = new Random(luck * 31 + lure);
                int n = 400_000, junkHits = 0, treasureHits = 0;
                for (int i = 0; i < n; i++) {
                    float chance = random.nextFloat();
                    float remapped = TreasureRule.withoutTreasure(chance, luck, lure);
                    assertTrue(remapped >= 0.0F && remapped < 1.0F);
                    if (chance < junk || chance >= junk + treasure) {
                        assertEquals(chance, remapped, 0.0F, "outside treasure untouched");
                    }
                    if (remapped < junk) {
                        junkHits++;
                    } else if (remapped < junk + treasure) {
                        treasureHits++;
                    }
                }
                assertEquals(0, treasureHits, "no treasure outside open water");
                double expectedJunk = junk / (1.0 - treasure);
                assertEquals(expectedJunk, (double) junkHits / n, 0.003, "luck " + luck + " lure " + lure);
            }
        }
    }
}
