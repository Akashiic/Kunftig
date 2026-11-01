package com.akashic.kunftig.fishing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Test;

class FishingEngineTest {

    @Test
    void fullCycleWaitLureBiteEscape() {
        FakeHook hook = new FakeHook(42, FishingProfile.VANILLA_1_7_10);
        FishingEngine.tick(hook, hook);
        assertTrue(hook.wait >= 100 && hook.wait <= 900, "first tick rolls the wait: " + hook.wait);

        hook.tickUntil("LURED", 1000);
        assertTrue(hook.approach >= 20 && hook.approach <= 80, "lure time in range: " + hook.approach);
        assertTrue(hook.angle >= 0 && hook.angle < 360);

        int beforeBite = hook.log.size();
        hook.tickUntil("BITE", 100);
        assertTrue(
            hook.log.subList(beforeBite, hook.log.size())
                .contains("approach"),
            "fish swims toward bobber");
        assertTrue(hook.log.contains("biteEffects"), "splash after an allowed bite");
        assertTrue(hook.biteWindow >= 10 && hook.biteWindow <= 30, "1.7.10 window: " + hook.biteWindow);

        int window = hook.biteWindow;
        int ticks = hook.tickUntil("FAILED_ATTEMPT", 100);
        assertEquals(window, ticks, "the window lasts exactly its ticks");
        assertEquals(0, hook.wait);
        assertEquals(0, hook.approach);

        FishingEngine.tick(hook, hook);
        assertTrue(hook.wait > 0, "waiting restarts after the escape");
    }

    @Test
    void modernProfileUsesModernRanges() {
        for (long seed = 0; seed < 300; seed++) {
            FakeHook hook = new FakeHook(seed, FishingProfile.MODERN_1_21_1);
            FishingEngine.tick(hook, hook);
            assertTrue(hook.wait >= 100 && hook.wait <= 600, "wait " + hook.wait);
            hook.tickUntil("BITE", 2000);
            assertTrue(hook.biteWindow >= 20 && hook.biteWindow <= 40, "window " + hook.biteWindow);
        }
    }

    @Test
    void cancellingLuredRestartsWaitingWithoutApproach() {
        FakeHook hook = new FakeHook(7, FishingProfile.VANILLA_1_7_10);
        hook.cancelLured = true;
        hook.tickUntil("LURED", 1000);
        assertEquals(0, hook.approach, "the fish does not come");
        FishingEngine.tick(hook, hook);
        assertTrue(hook.wait > 0, "a new wait was rolled");
        assertFalse(hook.log.contains("approach"));
    }

    @Test
    void cancellingBiteSkipsTheSplashAndRestarts() {
        FakeHook hook = new FakeHook(9, FishingProfile.VANILLA_1_7_10);
        hook.cancelBite = true;
        hook.tickUntil("BITE", 2000);
        assertFalse(hook.log.contains("biteEffects"), "no visible bite");
        assertEquals(0, hook.biteWindow, "nothing to catch");
        FishingEngine.tick(hook, hook);
        assertTrue(hook.wait > 0, "waiting restarts on the next tick");
    }

    @Test
    void nibbleTugsTheBobberEveryTickOfTheWindow() {
        FakeHook hook = new FakeHook(11, FishingProfile.VANILLA_1_7_10);
        hook.tickUntil("BITE", 2000);
        int window = hook.biteWindow;
        hook.log.clear();
        hook.tickUntil("FAILED_ATTEMPT", 100);
        long tugs = hook.log.stream()
            .filter("tug"::equals)
            .count();
        assertEquals(window - 1, tugs, "tugs while the window is still open after the tick");
    }

    @Test
    void listenersCanChangeTimingsInsideTheEvents() {
        FakeHook hook = new FakeHook(13, FishingProfile.VANILLA_1_7_10);
        hook.approachSetOnLured = 4;
        hook.windowSetOnBite = 200; // e.g. a minigame needing ten seconds
        hook.tickUntil("LURED", 1000);
        assertEquals(4, hook.approach, "lure time set in LURED is kept");
        assertEquals(4, hook.tickUntil("BITE", 100));
        assertEquals(200, hook.biteWindow, "bite window set in BITE is not overwritten by the roll");
        assertTrue(hook.log.contains("biteEffects"));
        assertEquals(200, hook.tickUntil("FAILED_ATTEMPT", 300));
    }

    @Test
    void observingListenersDoNotChangeTheRandomSequence() {
        // Same seed: one run with listeners that only observe, one with no listener logic at all. The sequence of
        // states must be identical, i.e. events never consume randomness or shift the window roll.
        FakeHook a = new FakeHook(21, FishingProfile.VANILLA_1_7_10);
        FakeHook b = new FakeHook(21, FishingProfile.VANILLA_1_7_10);
        for (int t = 0; t < 3000; t++) {
            FishingEngine.tick(a, a);
            FishingEngine.tick(b, FishingEngine.Listener.ALLOW_ALL);
            assertEquals(
                a.wait + "/" + a.approach + "/" + a.biteWindow,
                b.wait + "/" + b.approach + "/" + b.biteWindow);
        }
    }

    @Test
    void endingABiteFromOutsideFollowsTheDocumentedRules() {
        FakeHook escape = new FakeHook(17, FishingProfile.VANILLA_1_7_10);
        escape.tickUntil("BITE", 2000);
        escape.biteWindow = 1;
        assertEquals(1, escape.tickUntil("FAILED_ATTEMPT", 5), "window 1: escapes next tick with the event");

        FakeHook silent = new FakeHook(17, FishingProfile.VANILLA_1_7_10);
        silent.tickUntil("BITE", 2000);
        silent.biteWindow = 0;
        silent.log.clear();
        FishingEngine.tick(silent, silent);
        assertFalse(silent.log.contains("FAILED_ATTEMPT"), "window 0: ends silently");
        assertTrue(silent.wait > 0, "and waiting restarts");
    }

    @Test
    void customRangesAreHonoured() {
        FakeHook hook = new FakeHook(3, FishingProfile.VANILLA_1_7_10);
        hook.parameters.setWaitTime(5, 5);
        hook.parameters.setLureTime(3, 3);
        hook.parameters.setLureAngle(90, 90);
        hook.parameters.setBiteWindow(50, 50);
        assertEquals(1 + 5, hook.tickUntil("LURED", 100), "1 roll tick + 5 wait ticks");
        assertEquals(90.0F, hook.angle);
        assertEquals(3, hook.tickUntil("BITE", 100));
        assertEquals(50, hook.biteWindow);
    }

    @Test
    void rainSpeedsUpAndNoSkySlowsDown() {
        long rainy = 0, clear = 0, covered = 0;
        for (long seed = 0; seed < 400; seed++) {
            rainy += ticksToBite(seed, true, true);
            clear += ticksToBite(seed, false, true);
            covered += ticksToBite(seed, false, false);
        }
        assertTrue(rainy < clear, "rain is faster: " + rainy + " vs " + clear);
        assertTrue(covered > clear, "no sky is slower: " + covered + " vs " + clear);
    }

    @Test
    void influencesCanBeSwitchedOff() {
        FakeHook hook = new FakeHook(5, FishingProfile.VANILLA_1_7_10);
        hook.parameters.setRainInfluenced(false);
        hook.parameters.setSkyInfluenced(false);
        hook.raining = true;
        hook.sky = false;
        hook.parameters.setWaitTime(10, 10);
        assertEquals(11, hook.tickUntil("LURED", 100), "speed stays exactly 1");
    }

    private static long ticksToBite(long seed, boolean raining, boolean sky) {
        FakeHook hook = new FakeHook(seed, FishingProfile.VANILLA_1_7_10);
        hook.raining = raining;
        hook.sky = sky;
        return hook.tickUntil("BITE", 5000);
    }

    @Test
    void lureReducesTheWaitAndNeverLoopsForever() {
        FishingParameters parameters = new FishingParameters(FishingProfile.MODERN_1_21_1);
        Random random = new Random(1);
        for (int i = 0; i < 1000; i++) {
            int wait = FishingEngine.rollWaitTime(random, parameters, 3);
            assertTrue(wait >= -200 && wait <= 300, "lure III: " + wait);
        }
        for (int i = 0; i < 1000; i++) {
            assertEquals(1, FishingEngine.rollWaitTime(random, parameters, 6), "Paper's fix: lure >= max wait -> 1");
        }
        parameters.setApplyLure(false);
        for (int i = 0; i < 1000; i++) {
            int wait = FishingEngine.rollWaitTime(random, parameters, 10);
            assertTrue(wait >= 100 && wait <= 600, "lure ignored: " + wait);
        }
    }

    @Test
    void parameterValidationMatchesBukkit() {
        FishingParameters parameters = new FishingParameters(FishingProfile.MODERN_1_21_1);
        assertThrows(IllegalArgumentException.class, () -> parameters.setMinWaitTime(700));
        assertThrows(IllegalArgumentException.class, () -> parameters.setMaxWaitTime(50));
        assertThrows(IllegalArgumentException.class, () -> parameters.setWaitTime(10, 5));
        assertThrows(IllegalArgumentException.class, () -> parameters.setLureTime(-1, 5));
        assertThrows(IllegalArgumentException.class, () -> parameters.setLureAngle(10, 400));
        assertThrows(IllegalArgumentException.class, () -> parameters.setMaxLureAngle(-1));
        assertThrows(IllegalArgumentException.class, () -> parameters.setBiteWindow(0, 5));
        parameters.setWaitTime(0, 0);
        assertEquals(0, parameters.maxWaitTime());
    }

    @Test
    void randomHelpersMatchMinecraft() {
        Random a = new Random(99), b = new Random(99);
        for (int i = 0; i < 100; i++) {
            assertEquals(b.nextInt(80 - 20 + 1) + 20, FishingEngine.nextInt(a, 20, 80));
            assertEquals(b.nextFloat() * 360.0F, FishingEngine.nextFloat(a, 0.0F, 360.0F));
        }
        assertEquals(7, FishingEngine.nextInt(a, 7, 7), "degenerate range consumes nothing");
    }
}
