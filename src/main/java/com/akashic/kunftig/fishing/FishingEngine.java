package com.akashic.kunftig.fishing;

import java.util.Random;

/**
 * The "a fish is coming" state machine of a bobbing hook, run once per server tick while the hook is in water.
 * <p>
 * It is the same machine in 1.7.10 and 1.21.1 (1.7.10's anonymous {@code field_146040_ay}, {@code field_146038_az}
 * and {@code field_146045_ax} are 1.21's {@code timeUntilLured}, {@code timeUntilHooked} and {@code nibble}):
 *
 * <pre>
 * WAITING  (wait > 0)      counts down by "speed"; random splash particles; at 0 the fish is LURED
 * LURED    (approach > 0)  the fish swims toward the bobber; at 0 it BITES (splash, bobber dips)
 * BITING   (nibble > 0)    the window to reel in and catch it; at 0 it escapes (FAILED_ATTEMPT) and waiting restarts
 * </pre>
 *
 * With {@link FishingProfile#VANILLA_1_7_10} and listeners that allow everything, it consumes the random generator in
 * exactly the order 1.7.10 does, so fishing behaves exactly like vanilla. The additions are 1.21.1's: configurable
 * ranges (Bukkit {@code FishHook}), the rain/sky/lure switches, Paper's lure fix, and the LURED, BITE and
 * FAILED_ATTEMPT events, with the cancellation semantics of CraftBukkit/Paper 1.21.1.
 * <p>
 * Free of Minecraft types so it can be tested on its own; {@link Hook} is implemented by {@code EntityFishHook}.
 */
public final class FishingEngine {

    /** The hook as the engine sees it. Effects must consume {@link #random()} like 1.7.10's code does. */
    public interface Hook {

        Random kunftig$random();

        FishingParameters kunftig$parameters();

        int kunftig$waitTime();

        void kunftig$setWaitTime(int ticks);

        int kunftig$approachTime();

        void kunftig$setApproachTime(int ticks);

        int kunftig$biteWindow();

        void kunftig$setBiteWindow(int ticks);

        float kunftig$fishAngle();

        void kunftig$setFishAngle(float degrees);

        /** Level of the Lure enchantment on the owner's rod. */
        int kunftig$lureLevel();

        /** Rain falls on the block above the hook. */
        boolean kunftig$isRainingAbove();

        /** The block above the hook sees the sky. */
        boolean kunftig$canSeeSkyAbove();

        /** Random splash around the bobber while waiting (consumes random only when spawning). */
        void kunftig$waitingSplash();

        /** Bubbles/wake of the fish approaching at {@link #kunftig$fishAngle()}, {@link #kunftig$approachTime()}. */
        void kunftig$approachEffects();

        /** The bite: bobber pulled down, splash sound, bubbles and wake. */
        void kunftig$biteEffects();

        /** The bobber twitching while the fish nibbles. */
        void kunftig$nibbleTug();
    }

    /** Decides on the events. Return false to cancel. */
    public interface Listener {

        Listener ALLOW_ALL = new Listener() {

            @Override
            public boolean lured(Hook hook) {
                return true;
            }

            @Override
            public boolean bite(Hook hook) {
                return true;
            }

            @Override
            public void failedAttempt(Hook hook) {}
        };

        /**
         * A fish appeared and starts swimming toward the bobber. The lure time and angle are already rolled and may be
         * changed here. Cancel: it does not come, waiting restarts.
         */
        boolean lured(Hook hook);

        /**
         * The fish bites. Cancel: no splash, no bite, waiting restarts (the fish got away unseen). A listener may set
         * the bite window here; otherwise it is rolled from the parameters.
         */
        boolean bite(Hook hook);

        /** The bite window ran out without the player reeling in. Informational. */
        void failedAttempt(Hook hook);
    }

    private FishingEngine() {}

    /** One tick of a hook that is in water, on the server. */
    public static void tick(Hook hook, Listener listener) {
        Random random = hook.kunftig$random();
        FishingParameters parameters = hook.kunftig$parameters();

        int speed = 1;
        if (parameters.rainInfluenced() && random.nextFloat() < 0.25F && hook.kunftig$isRainingAbove()) {
            speed++;
        }
        if (parameters.skyInfluenced() && random.nextFloat() < 0.5F && !hook.kunftig$canSeeSkyAbove()) {
            speed--;
        }

        if (hook.kunftig$biteWindow() > 0) {
            hook.kunftig$setBiteWindow(hook.kunftig$biteWindow() - 1);
            if (hook.kunftig$biteWindow() <= 0) {
                hook.kunftig$setWaitTime(0);
                hook.kunftig$setApproachTime(0);
                listener.failedAttempt(hook);
            }
        } else if (hook.kunftig$approachTime() > 0) {
            hook.kunftig$setApproachTime(hook.kunftig$approachTime() - speed);
            if (hook.kunftig$approachTime() <= 0) {
                if (!listener.bite(hook)) {
                    // Approach and bite window are both <= 0: the next tick rolls a new wait.
                    return;
                }
                hook.kunftig$biteEffects();
                // A listener may have set the window itself (e.g. a minigame wanting more time): keep it. Otherwise
                // roll it here, after the effects, which is where vanilla consumes the random generator for it.
                if (hook.kunftig$biteWindow() <= 0) {
                    hook.kunftig$setBiteWindow(nextInt(random, parameters.minBiteWindow(), parameters.maxBiteWindow()));
                }
            } else {
                hook.kunftig$setFishAngle((float) (hook.kunftig$fishAngle() + random.nextGaussian() * 4.0D));
                hook.kunftig$approachEffects();
            }
        } else if (hook.kunftig$waitTime() > 0) {
            hook.kunftig$setWaitTime(hook.kunftig$waitTime() - speed);
            int wait = hook.kunftig$waitTime();
            float splashChance = 0.15F;
            if (wait < 20) {
                splashChance = (float) (splashChance + (20 - wait) * 0.05D);
            } else if (wait < 40) {
                splashChance = (float) (splashChance + (40 - wait) * 0.02D);
            } else if (wait < 60) {
                splashChance = (float) (splashChance + (60 - wait) * 0.01D);
            }
            if (random.nextFloat() < splashChance) {
                hook.kunftig$waitingSplash();
            }
            if (wait <= 0) {
                hook.kunftig$setFishAngle(nextFloat(random, parameters.minLureAngle(), parameters.maxLureAngle()));
                hook.kunftig$setApproachTime(nextInt(random, parameters.minLureTime(), parameters.maxLureTime()));
                if (!listener.lured(hook)) {
                    hook.kunftig$setApproachTime(0);
                }
            }
        } else {
            hook.kunftig$setWaitTime(rollWaitTime(random, parameters, hook.kunftig$lureLevel()));
        }

        if (hook.kunftig$biteWindow() > 0) {
            hook.kunftig$nibbleTug();
        }
    }

    /**
     * A new wait: uniform in [min, max], minus 100 ticks per Lure level. Like Paper, when the reduction reaches the
     * maximum the wait becomes 1 tick instead of going to zero or below, which in vanilla re-rolls forever (a Lure
     * level that high would otherwise never catch anything).
     */
    public static int rollWaitTime(Random random, FishingParameters parameters, int lureLevel) {
        int wait = nextInt(random, parameters.minWaitTime(), parameters.maxWaitTime());
        if (parameters.applyLure()) {
            int reduction = lureLevel * 20 * 5;
            wait -= reduction >= parameters.maxWaitTime() ? wait - 1 : reduction;
        }
        return wait;
    }

    /** Same as 1.7.10's {@code MathHelper.getRandomIntegerInRange} / 1.21's {@code Mth.nextInt}. */
    static int nextInt(Random random, int min, int max) {
        return min >= max ? min : random.nextInt(max - min + 1) + min;
    }

    /** Same as 1.7.10's {@code MathHelper.randomFloatClamp} / 1.21's {@code Mth.nextFloat}. */
    static float nextFloat(Random random, float min, float max) {
        return min >= max ? min : random.nextFloat() * (max - min) + min;
    }
}
