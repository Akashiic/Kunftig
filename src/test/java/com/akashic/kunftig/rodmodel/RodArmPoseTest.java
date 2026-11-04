package com.akashic.kunftig.rodmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class RodArmPoseTest {

    @Test
    void standingStillHasNoSwing() {
        assertEquals(0F, RodArmPose.rightWalkSwing(3.7F, 0F, 1), 1e-6);
        assertEquals(0F, RodArmPose.leftWalkSwing(3.7F, 0F, 0), 1e-6);
    }

    @Test
    void armsSwingInOppositePhasesAndTheHoldingArmHalf() {
        // limbSwing 0: cos(0) = 1 for the left arm, cos(PI) = -1 for the right one; amount 1 -> 1 and -1 (held: -0.5)
        assertEquals(1F, RodArmPose.leftWalkSwing(0F, 1F, 0), 1e-6);
        assertEquals(-1F, RodArmPose.rightWalkSwing(0F, 1F, 0), 1e-6);
        assertEquals(-0.5F, RodArmPose.rightWalkSwing(0F, 1F, 1), 1e-6);
        assertEquals(0.5F, RodArmPose.leftWalkSwing(0F, 1F, 1), 1e-6);
    }

    @Test
    void followsVanillaFormula() {
        float swing = 5.25F, amount = 0.6F;
        float vanillaLeft = (float) Math.cos(swing * 0.6662F) * 2.0F * amount * 0.5F;
        float vanillaRight = (float) Math.cos(swing * 0.6662F + Math.PI) * 2.0F * amount * 0.5F * 0.5F;
        assertEquals(vanillaLeft, RodArmPose.leftWalkSwing(swing, amount, 0), 1e-6);
        assertEquals(vanillaRight, RodArmPose.rightWalkSwing(swing, amount, 1), 1e-6);
    }
}
