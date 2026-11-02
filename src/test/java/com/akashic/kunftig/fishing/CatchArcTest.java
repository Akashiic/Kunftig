package com.akashic.kunftig.fishing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Issue #10: a 12x12 pond, water at y 71, the angler on a bank with feet at y 72, casting 4 to 6 blocks. */
class CatchArcTest {

    /** EntityFishHook.func_146034_e's vertical launch speed. */
    private static double vanillaVertical(double horizontal, double rise) {
        double distance = Math.sqrt(horizontal * horizontal + rise * rise);
        return rise * 0.1 + Math.sqrt(distance) * 0.08;
    }

    @Test
    void horizontalSpeedCoversTheDistanceAtTheArrivalTick() {
        double covered = 0;
        double speed = CatchArc.LAUNCH;
        for (int t = 1; t <= CatchArc.ARRIVAL_TICKS; t++) {
            covered += speed;
            speed *= CatchArc.DRAG;
            assertEquals(t == CatchArc.ARRIVAL_TICKS, covered >= 1.0, "tick " + t);
        }
    }

    @Test
    void vanillaFallsShortOfABankOneBlockUp() {
        double rise = 72.0 - 71.9; // feet above the floating hook
        for (double horizontal = 4; horizontal <= 6; horizontal++) {
            double height = CatchArc.heightAfter(vanillaVertical(horizontal, rise), CatchArc.ARRIVAL_TICKS);
            assertTrue(height < rise, horizontal + " blocks: arrives " + height + " below the feet line " + rise);
        }
    }

    @Test
    void theAimedArcArrivesInThePickupBox() {
        for (double rise : new double[] { -3, 0.1, 1.1, 4 }) {
            for (double horizontal = 2; horizontal <= 20; horizontal++) {
                double vanilla = vanillaVertical(horizontal, rise);
                double aimed = CatchArc.aimedVerticalSpeed(vanilla, rise);
                double height = CatchArc.heightAfter(aimed, CatchArc.ARRIVAL_TICKS);
                String where = "rise " + rise + ", " + horizontal + " blocks";
                assertTrue(aimed >= vanilla, where);
                if (aimed > vanilla) {
                    assertEquals(rise + CatchArc.ABOVE_FEET, height, 1e-9, where); // raised exactly to the target
                } else {
                    assertTrue(height >= rise + CatchArc.ABOVE_FEET, where); // vanilla was already high enough
                }
            }
        }
    }

    @Test
    void longCastsKeepVanillasArcAndCliffsAreBounded() {
        double vanilla = vanillaVertical(17, 0.1);
        assertEquals(vanilla, CatchArc.aimedVerticalSpeed(vanilla, 0.1), "vanilla already high enough");
        assertEquals(CatchArc.MAX_VERTICAL_SPEED, CatchArc.aimedVerticalSpeed(0.2, 40));
        assertTrue(
            CatchArc.verticalSpeedFor(10 + CatchArc.ABOVE_FEET, CatchArc.ARRIVAL_TICKS) < CatchArc.MAX_VERTICAL_SPEED);
    }

    @Test
    void minigameSwitchesAreOffByDefault() {
        FishingParameters parameters = new FishingParameters(FishingProfile.VANILLA_1_7_10);
        assertFalse(parameters.reelLocked());
        assertFalse(parameters.catchAimedAtAngler());
        parameters.setReelLocked(true);
        parameters.setCatchAimedAtAngler(true);
        assertTrue(parameters.reelLocked());
        assertTrue(parameters.catchAimedAtAngler());
    }
}
