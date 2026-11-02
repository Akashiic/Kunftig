package com.akashic.kunftig.fishing;

import java.util.Random;

/**
 * How the bobber floats since 1.16 ({@code FishingHook#tick}, state BOBBING), for 1.7.10. No Minecraft classes here.
 * <p>
 * 1.7.10 pushes the hook up or down by {@code 0.04 * (2f - 1)}, where {@code f} is the fraction (in steps of 0.2) of
 * the hook's five slices that are in water counted up to the <i>top of the block</i>. It has no brake on entry: a
 * strong cast sinks up to ~2.4 blocks and takes ~2.5 s to come back, then sits still. The modern bobber:
 * <ul>
 * <li>brakes on entering the fluid: motion times {@code (0.3, 0.2, 0.3)};</li>
 * <li>while bobbing, pulls the vertical speed toward the fluid surface of its block like a random spring:
 * {@code d = y + vy - (blockY + height)} (nudged by 0.1 when {@code |d| < 0.01}),
 * {@code vy -= d * random * 0.2}, and slows horizontally by 0.9; {@code height} is the drawn height of the fluid in
 * that block (8/9 for a source under air, 1 under more fluid), so it floats where the water is drawn and keeps a gentle
 * bob;</li>
 * <li>falls by 0.03 per tick out of the fluid, and loses 8% of its speed every tick (0.92).</li>
 * </ul>
 * In 1.7.10's tick the hook has already moved when this runs, so the adjustment applies to the next move.
 */
public final class ModernBobber {

    public static final double ENTRY_BRAKE_HORIZONTAL = 0.3D;
    public static final double ENTRY_BRAKE_VERTICAL = 0.2D;
    public static final double SPRING = 0.2D;
    public static final double DEAD_ZONE = 0.01D;
    public static final double NUDGE = 0.1D;
    public static final double BOBBING_HORIZONTAL = 0.9D;
    public static final double GRAVITY = 0.03D;
    public static final double DRAG = 0.92D;

    private ModernBobber() {}

    /** The velocity after one tick; {@code velocity} ({x, y, z}) is changed in place and returned. */
    public static double[] step(double[] velocity, double y, Double surface, boolean enteringFluid, Random random) {
        boolean inFluid = surface != null;
        if (enteringFluid) {
            velocity[0] *= ENTRY_BRAKE_HORIZONTAL;
            velocity[1] *= ENTRY_BRAKE_VERTICAL;
            velocity[2] *= ENTRY_BRAKE_HORIZONTAL;
        } else if (inFluid) {
            double offset = y + velocity[1] - surface;
            if (Math.abs(offset) < DEAD_ZONE) {
                offset += Math.signum(offset) * NUDGE;
            }
            velocity[0] *= BOBBING_HORIZONTAL;
            velocity[1] -= offset * random.nextFloat() * SPRING;
            velocity[2] *= BOBBING_HORIZONTAL;
        }
        if (!inFluid) {
            velocity[1] -= GRAVITY;
        }
        velocity[0] *= DRAG;
        velocity[1] *= DRAG;
        velocity[2] *= DRAG;
        return velocity;
    }

    /**
     * The drawn top of a fluid block: 1 under more fluid, else {@code 1 - (meta + 1) / 9} as 1.7.10's renderer and
     * {@code BlockLiquid#getLiquidHeightPercent} draw it (8/9 for a source).
     */
    public static double surfaceHeight(int metadata, boolean fluidAbove) {
        if (fluidAbove) {
            return 1.0D;
        }
        int level = metadata >= 8 ? 0 : metadata;
        return 1.0D - (level + 1) / 9.0D;
    }
}
