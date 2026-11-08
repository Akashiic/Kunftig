package com.akashic.kunftig.blade;

/**
 * The player's body pose from SlashBlade's {@code model/pa/player_motion.vmd} (rig {@code alex.pmd}), converted as
 * SlashBlade's PlayerAnimator path does ({@code VmdAnimation.get3DTransform}): each limb bone's local quaternion
 * becomes ZYX Euler angles for the matching vanilla model part with signs {@code (-x, +y, -z)}, and its VMD position
 * becomes a pivot offset in model pixels {@code (x, -y, z) * 2}; the {@code body} bone moves the whole model (angles
 * {@code (+x, -y, -z)}, position {@code p / 8} blocks, X not mirrored, as in SlashBlade). The angle order Z, Y, X is
 * the
 * same as 1.7.10's {@code ModelRenderer}, so the angles go straight to {@code rotateAngleX/Y/Z}. No Minecraft classes.
 */
public final class BodyPose {

    public static final int HEAD = 0;
    public static final int TORSO = 1;
    public static final int RIGHT_ARM = 2;
    public static final int LEFT_ARM = 3;
    public static final int RIGHT_LEG = 4;
    public static final int LEFT_LEG = 5;
    /** MMD bone of each part, in the order above. */
    public static final String[] BONES = { "head", "torso", "right arm", "left arm", "right leg", "left leg" };
    public static final String BODY = "body";
    /** Height (blocks) of the pivot the whole-body rotation turns about. */
    public static final float BODY_PIVOT = 0.7F;

    /** Per part: rotation x, y, z (radians). */
    public final float[][] rotation = new float[6][3];
    /** Per part: pivot offset x, y, z (model pixels), added to the vanilla pivot. */
    public final float[][] offset = new float[6][3];
    /** The whole body: rotation x, y, z (radians) and position x, y, z (blocks). */
    public final float[] bodyRotation = new float[3];
    public final float[] bodyPosition = new float[3];

    /** The pose at {@code frame} of a player motion. */
    public static BodyPose at(Vmd motion, double frame) {
        BodyPose p = new BodyPose();
        for (int i = 0; i < BONES.length; i++) {
            double[] s = motion.sample(BONES[i], frame);
            double[] e = Mmd.eulerZYX(new double[] { s[3], s[4], s[5], s[6] });
            p.rotation[i][0] = (float) -e[0];
            p.rotation[i][1] = (float) e[1];
            p.rotation[i][2] = (float) -e[2];
            p.offset[i][0] = (float) (s[0] * 2);
            p.offset[i][1] = (float) (-s[1] * 2);
            p.offset[i][2] = (float) (s[2] * 2);
        }
        double[] b = motion.sample(BODY, frame);
        double[] e = Mmd.eulerZYX(new double[] { b[3], b[4], b[5], b[6] });
        p.bodyRotation[0] = (float) e[0];
        p.bodyRotation[1] = (float) -e[1];
        p.bodyRotation[2] = (float) -e[2];
        p.bodyPosition[0] = (float) (b[0] / 8);
        p.bodyPosition[1] = (float) (b[1] / 8);
        p.bodyPosition[2] = (float) (b[2] / 8);
        return p;
    }
}
