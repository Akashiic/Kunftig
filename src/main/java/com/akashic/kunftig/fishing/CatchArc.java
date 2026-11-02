package com.akashic.kunftig.fishing;

/**
 * The flight of a caught item, 1.7.10 physics (the same launch formula as 1.21.1). Vanilla launches with
 * {@code motion = delta * 0.1}, plus {@code sqrt(distance) * 0.08} upwards, and {@code EntityItem} then applies
 * {@code motionY -= 0.04}, moves, and multiplies by 0.98 every tick. With the hook about level with the angler's feet,
 * the item is back at its start height after ~10 ticks and ~4.6 of 5 blocks: from water one block below a bank it hits
 * the bank and falls in (issue #10).
 * <p>
 * The horizontal speed always covers the distance after {@link #ARRIVAL_TICKS} ticks, whatever the distance, so the
 * aimed arc only changes the vertical speed: the one that puts the item {@link #ABOVE_FEET} above the angler's feet
 * at that tick, never lower than vanilla's. No Minecraft classes here.
 */
public final class CatchArc {

    static final double GRAVITY = 0.03999999910593033D;
    static final double DRAG = 0.9800000190734863D;
    /** Vanilla's launch factor: the item gets a tenth of the distance as speed. */
    static final double LAUNCH = 0.1D;
    /** Height above the feet the item aims for: inside the pickup box (player box grown by 0.5 vertically). */
    public static final double ABOVE_FEET = 0.6D;
    /** Upper bound of the aimed vertical speed (a 10-block cliff needs ~1.2). */
    static final double MAX_VERTICAL_SPEED = 1.5D;
    /** Tick at which vanilla's horizontal speed has covered the whole distance. */
    public static final int ARRIVAL_TICKS = arrivalTicks();

    private CatchArc() {}

    private static int arrivalTicks() {
        double covered = 0;
        double speed = LAUNCH;
        int ticks = 0;
        while (covered < 1.0D) {
            covered += speed;
            speed *= DRAG;
            ticks++;
        }
        return ticks;
    }

    /** Vertical start speed after which the item is {@code rise} higher than its start after {@code ticks} ticks. */
    static double verticalSpeedFor(double rise, int ticks) {
        // y(n) = vy0 * sum_{k<n} DRAG^k - GRAVITY * sum_{k=1..n} sum_{j<k} DRAG^j (subtract, move, then drag)
        double speedWeight = 0;
        double gravityWeight = 0;
        double dragPower = 1;
        double gravityAccumulated = 0;
        for (int k = 0; k < ticks; k++) {
            speedWeight += dragPower;
            gravityAccumulated += dragPower;
            gravityWeight += gravityAccumulated;
            dragPower *= DRAG;
        }
        return (rise + GRAVITY * gravityWeight) / speedWeight;
    }

    /**
     * The vertical speed that lands the item at the angler: {@code feetRise} is the angler's feet minus the item's
     * start
     * height. Never below {@code vanillaSpeed}, never above {@link #MAX_VERTICAL_SPEED} (unless vanilla is).
     */
    public static double aimedVerticalSpeed(double vanillaSpeed, double feetRise) {
        double aimed = Math.min(MAX_VERTICAL_SPEED, verticalSpeedFor(feetRise + ABOVE_FEET, ARRIVAL_TICKS));
        return Math.max(vanillaSpeed, aimed);
    }

    /** Height relative to the start after {@code ticks} ticks of free flight (for tests and docs). */
    static double heightAfter(double verticalSpeed, int ticks) {
        double y = 0;
        double vy = verticalSpeed;
        for (int t = 0; t < ticks; t++) {
            vy -= GRAVITY;
            y += vy;
            vy *= DRAG;
        }
        return y;
    }
}
