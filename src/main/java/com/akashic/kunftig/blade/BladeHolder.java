package com.akashic.kunftig.blade;

/**
 * SlashBlade's invisible blade rig ({@code model/bladeholder.pmd}): nine bones, all at the origin, no IK, animated by
 * {@code combostate/motion.vmd}. {@code hardpointA} places the blade, {@code hardpointB} the sheath. Since every rest
 * position is 0, a hardpoint's skinning matrix is the product of the local VMD transforms down its chain
 * ({@code center -> JointX1 -> JointX2 -> JointX3 -> hardpointX}), in NyMMD's row-vector order (child first). No
 * Minecraft classes.
 */
public final class BladeHolder {

    public static final String CENTER = "センター";
    public static final String[] CHAIN_A = { "JointA1", "JointA2", "JointA3", "hardpointA" };
    public static final String[] CHAIN_B = { "JointB1", "JointB2", "JointB3", "hardpointB" };

    /** Blocks per MMD unit: SlashBlade maps the MMD neck height of 12 units to 1.5 blocks. */
    public static final double BLOCKS_PER_MMD = 1.5 / 12.0;
    /** OBJ units per MMD unit inside the hardpoint (the OBJ is scaled by 1/16 there): 1 OBJ unit = 1/128 block. */
    public static final double OBJ_PER_MMD = 16;

    private BladeHolder() {}

    /** The blade's matrix ({@code hardpointA}) at {@code frame}. */
    public static double[] blade(Vmd motion, double frame) {
        return chain(motion, frame, CHAIN_A);
    }

    /** The sheath's matrix ({@code hardpointB}) at {@code frame}. */
    public static double[] sheath(Vmd motion, double frame) {
        return chain(motion, frame, CHAIN_B);
    }

    private static double[] chain(Vmd motion, double frame, String[] chain) {
        double[] m = motion.local(CENTER, frame);
        for (String bone : chain) {
            m = Mmd.mul(motion.local(bone, frame), m); // L_child · L_parent
        }
        return m;
    }

    /**
     * Where an OBJ point of the blade lands in the holder's frame F (blocks; Y up, feet at 0, front -Z, right +X), at
     * the player scale 1 (SlashBlade then scales by 0.9375 with the player model): {@code (1/8)·Sx·M·Sx·(v/16)}.
     */
    public static double[] toFrame(double[] hardpoint, double objX, double objY, double objZ) {
        double[] p = Mmd.apply(hardpoint, -objX / OBJ_PER_MMD, objY / OBJ_PER_MMD, objZ / OBJ_PER_MMD);
        return new double[] { -p[0] * BLOCKS_PER_MMD, p[1] * BLOCKS_PER_MMD, p[2] * BLOCKS_PER_MMD };
    }
}
