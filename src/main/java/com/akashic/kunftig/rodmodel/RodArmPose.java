package com.akashic.kunftig.rodmodel;

/**
 * The part of vanilla's arm pose ({@code ModelBiped#setRotationAngles}, 1.7.10) that a rod held with both hands takes
 * back: the walking swing. Vanilla swings the arms in opposite phases while walking, and the right arm, holding an
 * item,
 * only half as much; with both hands on the rod, the hands would let go of it. Pure math, so it is tested here.
 */
public final class RodArmPose {

    /** {@code limbSwing} frequency of vanilla's arm swing. */
    static final float SWING_FREQUENCY = 0.6662F;

    private RodArmPose() {}

    /** The walking swing vanilla put in {@code bipedRightArm.rotateAngleX} (radians). */
    public static float rightWalkSwing(float limbSwing, float limbSwingAmount, int heldItemRight) {
        float swing = (float) Math.cos(limbSwing * SWING_FREQUENCY + Math.PI) * 2.0F * limbSwingAmount * 0.5F;
        return heldItemRight != 0 ? swing * 0.5F : swing;
    }

    /** The walking swing vanilla put in {@code bipedLeftArm.rotateAngleX} (radians). */
    public static float leftWalkSwing(float limbSwing, float limbSwingAmount, int heldItemLeft) {
        float swing = (float) Math.cos(limbSwing * SWING_FREQUENCY) * 2.0F * limbSwingAmount * 0.5F;
        return heldItemLeft != 0 ? swing * 0.5F : swing;
    }
}
