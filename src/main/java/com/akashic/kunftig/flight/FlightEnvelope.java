package com.akashic.kunftig.flight;

/**
 * The client side of the flight lock, as pure arithmetic: tick by tick, the upward speed a player may have without
 * flying. Leaving the ground with any speed (a jump, a jump boost) is fine, and so is a sudden push from outside (a
 * hit, an explosion, a launch by a plugin): from there the speed may only fall as vanilla gravity makes it fall. A
 * jetpack's thrust, a glider's slow descent or a spell's lift go over that curve and are cut back to it. In water, in
 * lava, on a ladder or riding something nothing is cut. No Minecraft classes.
 * <p>
 * Call {@link #next} at the start of each movement tick (after the items' ticks added their thrust, before the move).
 */
public final class FlightEnvelope {

    /** Vanilla 1.7.10 player gravity and air drag, per tick. */
    public static final double GRAVITY = 0.08;
    public static final double DRAG = 0.98;
    /** A jump in upward speed bigger than this in one tick is a push from outside, not thrust. */
    public static final double IMPULSE = 0.3;

    private boolean wasOnGround = true;
    private double last;
    private double lastRaw;
    private double allowed;

    /**
     * The upward speed to use this tick.
     *
     * @param onGround whether the player stands on something now
     * @param motionY  the vertical speed the game has now
     * @param exempt   in water, lava, on a ladder or riding: not cut
     */
    public double next(boolean onGround, double motionY, boolean exempt) {
        if (exempt || onGround) {
            wasOnGround = true;
            last = motionY;
            lastRaw = motionY;
            allowed = motionY;
            return motionY;
        }
        double expected = (last - GRAVITY) * DRAG;
        // a push jumps above both the curve and what the game had last tick (a glider holding the fall does not)
        boolean pushed = motionY - expected > IMPULSE && motionY - lastRaw > IMPULSE;
        if (wasOnGround || pushed) {
            allowed = motionY; // left the ground, or pushed from outside
        } else {
            allowed = (allowed - GRAVITY) * DRAG;
        }
        double out = motionY > allowed ? allowed : motionY;
        wasOnGround = false;
        last = out;
        lastRaw = motionY;
        return out;
    }

    /** Starts over (the lock was set again, a new world). */
    public void reset() {
        wasOnGround = true;
        last = 0;
        lastRaw = 0;
        allowed = 0;
    }
}
