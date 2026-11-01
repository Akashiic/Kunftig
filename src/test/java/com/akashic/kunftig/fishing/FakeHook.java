package com.akashic.kunftig.fishing;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** In-memory hook: records effects and events, lets tests control weather and sky. */
final class FakeHook implements FishingEngine.Hook, FishingEngine.Listener {

    final Random random;
    final FishingParameters parameters;
    final List<String> log = new ArrayList<String>();
    int wait;
    int approach;
    int biteWindow;
    float angle;
    int lureLevel;
    boolean raining;
    boolean sky = true;
    boolean cancelLured;
    boolean cancelBite;
    int windowSetOnBite;
    int approachSetOnLured;

    FakeHook(long seed, FishingProfile profile) {
        random = new Random(seed);
        parameters = new FishingParameters(profile);
    }

    /** Ticks until something is logged that starts with {@code prefix}; returns the tick count. */
    int tickUntil(String prefix, int maxTicks) {
        for (int t = 1; t <= maxTicks; t++) {
            int before = log.size();
            FishingEngine.tick(this, this);
            for (int i = before; i < log.size(); i++) {
                if (log.get(i)
                    .startsWith(prefix)) {
                    return t;
                }
            }
        }
        throw new AssertionError("no '" + prefix + "' within " + maxTicks + " ticks: " + log);
    }

    @Override
    public Random kunftig$random() {
        return random;
    }

    @Override
    public FishingParameters kunftig$parameters() {
        return parameters;
    }

    @Override
    public int kunftig$waitTime() {
        return wait;
    }

    @Override
    public void kunftig$setWaitTime(int ticks) {
        wait = ticks;
    }

    @Override
    public int kunftig$approachTime() {
        return approach;
    }

    @Override
    public void kunftig$setApproachTime(int ticks) {
        approach = ticks;
    }

    @Override
    public int kunftig$biteWindow() {
        return biteWindow;
    }

    @Override
    public void kunftig$setBiteWindow(int ticks) {
        biteWindow = ticks;
    }

    @Override
    public float kunftig$fishAngle() {
        return angle;
    }

    @Override
    public void kunftig$setFishAngle(float degrees) {
        angle = degrees;
    }

    @Override
    public int kunftig$lureLevel() {
        return lureLevel;
    }

    @Override
    public boolean kunftig$isRainingAbove() {
        return raining;
    }

    @Override
    public boolean kunftig$canSeeSkyAbove() {
        return sky;
    }

    @Override
    public void kunftig$waitingSplash() {
        log.add("splash");
    }

    @Override
    public void kunftig$approachEffects() {
        log.add("approach");
    }

    @Override
    public void kunftig$biteEffects() {
        log.add("biteEffects");
    }

    @Override
    public void kunftig$nibbleTug() {
        log.add("tug");
    }

    @Override
    public boolean lured(FishingEngine.Hook hook) {
        log.add("LURED approach=" + approach + " angle=" + angle);
        if (approachSetOnLured > 0) {
            approach = approachSetOnLured;
        }
        return !cancelLured;
    }

    @Override
    public boolean bite(FishingEngine.Hook hook) {
        log.add("BITE");
        if (windowSetOnBite > 0) {
            biteWindow = windowSetOnBite;
        }
        return !cancelBite;
    }

    @Override
    public void failedAttempt(FishingEngine.Hook hook) {
        log.add("FAILED_ATTEMPT");
    }
}
