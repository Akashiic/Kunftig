package com.akashic.kunftig.fishing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Test;

/**
 * The bobber in a pond: water blocks y = -3..0 (source blocks, air above), drawn surface at 8/9. Each tick runs in
 * 1.7.10's order (move, then the new velocity), the order the mixin applies it in.
 */
class ModernBobberTest {

    private static final double SURFACE = 8.0 / 9.0;
    private static final int DEPTH = 3;

    /** Drawn fluid surface of the block holding {@code y}, or null out of the water. */
    private static Double surfaceAt(double y) {
        int block = (int) Math.floor(y);
        if (block < -DEPTH || block > 0) {
            return null;
        }
        return block + ModernBobber.surfaceHeight(0, block < 0);
    }

    /** Heights of the hook's bottom after a cast entering the water at {@code entrySpeed}, modern physics. */
    private static double[] modern(double entrySpeed, long seed, int ticks) {
        Random random = new Random(seed);
        double[] velocity = { 0, entrySpeed, 0 };
        double y = 1.5;
        boolean wasInFluid = false;
        double[] heights = new double[ticks];
        for (int t = 0; t < ticks; t++) {
            y += velocity[1];
            Double surface = surfaceAt(y);
            ModernBobber.step(velocity, y, surface, surface != null && !wasInFluid, random);
            wasInFluid = surface != null;
            heights[t] = y;
        }
        return heights;
    }

    /** 1.7.10's buoyancy: five slices counted in water up to the block top, push 0.04 * (2f - 1). */
    private static double[] legacy(double entrySpeed, int ticks) {
        double velocity = entrySpeed;
        double y = 1.5;
        double[] heights = new double[ticks];
        for (int t = 0; t < ticks; t++) {
            y += velocity;
            double fraction = 0;
            for (int j = 0; j < 5; j++) {
                double sliceBottom = y + 0.25 * j / 5;
                if (sliceBottom <= 1.0 && sliceBottom >= -DEPTH) {
                    fraction += 0.2;
                }
            }
            velocity += 0.04 * (fraction * 2 - 1);
            if (fraction > 0) {
                velocity *= 0.8 * 0.9;
            }
            velocity *= 0.92;
            heights[t] = y;
        }
        return heights;
    }

    private static double min(double[] values) {
        double min = Double.MAX_VALUE;
        for (double value : values) {
            min = Math.min(min, value);
        }
        return min;
    }

    @Test
    void surfaceHeightsAreTheDrawnOnes() {
        assertEquals(8.0 / 9.0, ModernBobber.surfaceHeight(0, false), 1e-12);
        assertEquals(1.0 / 9.0, ModernBobber.surfaceHeight(7, false), 1e-12);
        assertEquals(8.0 / 9.0, ModernBobber.surfaceHeight(8, false), 1e-12, "falling water counts as full");
        assertEquals(1.0, ModernBobber.surfaceHeight(3, true), 1e-12);
    }

    @Test
    void theEntryBrakeHalvesTheDiveOfAStrongCast() {
        for (double entry : new double[] { -0.8, -1.2 }) {
            double legacyDive = SURFACE - min(legacy(entry, 160));
            for (long seed = 0; seed < 20; seed++) {
                double modernDive = SURFACE - min(modern(entry, seed, 160));
                assertTrue(modernDive < legacyDive * 0.6, entry + "/" + seed + ": " + modernDive + " vs " + legacyDive);
            }
        }
        assertTrue(SURFACE - min(legacy(-1.2, 160)) > 2.0, "1.7.10 sinks over two blocks");
    }

    @Test
    void itFloatsAtTheDrawnSurfaceWithAGentleBob() {
        for (long seed = 0; seed < 20; seed++) {
            double[] heights = modern(-0.5, seed, 400);
            double sum = 0;
            double low = Double.MAX_VALUE;
            double high = -Double.MAX_VALUE;
            for (int t = 200; t < 400; t++) {
                sum += heights[t];
                low = Math.min(low, heights[t]);
                high = Math.max(high, heights[t]);
            }
            double mean = sum / 200;
            assertEquals(SURFACE, mean, 0.05, "seed " + seed);
            assertTrue(high - low > 0.01, "it keeps bobbing");
            assertTrue(high - low < 0.25, "but gently: " + (high - low));
        }
    }

    @Test
    void outOfTheWaterItFallsAndOnEntryItBrakes() {
        double[] velocity = { 1, 0, 1 };
        ModernBobber.step(velocity, 5, null, false, new Random(1));
        assertEquals(1 * 0.92, velocity[0], 1e-12);
        assertEquals(-0.03 * 0.92, velocity[1], 1e-12);

        double[] entering = { 1, -1, 1 };
        ModernBobber.step(entering, 0.5, SURFACE, true, new Random(1));
        assertEquals(0.3 * 0.92, entering[0], 1e-12);
        assertEquals(-0.2 * 0.92, entering[1], 1e-12);
    }

    @Test
    void clientsFollowTheServerAndStartVanilla() {
        ModernBobberMode.reset();
        assertFalse(ModernBobberMode.client());
        ModernBobberMode.receive(new byte[] { ModernBobberMode.MODERN });
        assertTrue(ModernBobberMode.client());
        ModernBobberMode.reset();
        assertFalse(ModernBobberMode.client(), "a new connection starts vanilla");
        ModernBobberMode.receive(new byte[0]);
        assertFalse(ModernBobberMode.client());
    }
}
