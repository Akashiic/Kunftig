package com.akashic.kunftig.fishing;

/**
 * Default fishing timings for newly cast hooks. Plugins and mods can still change every value per hook.
 * <p>
 * {@link #VANILLA_1_7_10} keeps 1.7.10's numbers, so installing Kunftig changes nothing until asked to.
 * {@link #MODERN_1_21_1} uses 1.21.1's: the wait, lure and angle ranges documented as the defaults of Bukkit's
 * {@code FishHook} (100-600, 20-80, 0-360), the 20-40 tick bite window, and treasure only in open water (1.16+).
 */
public enum FishingProfile {

    VANILLA_1_7_10(100, 900, 20, 80, 10, 30, false),
    MODERN_1_21_1(100, 600, 20, 80, 20, 40, true);

    final int minWait;
    final int maxWait;
    final int minLure;
    final int maxLure;
    final int minBiteWindow;
    final int maxBiteWindow;
    final boolean treasureRequiresOpenWater;

    FishingProfile(int minWait, int maxWait, int minLure, int maxLure, int minBiteWindow, int maxBiteWindow,
        boolean treasureRequiresOpenWater) {
        this.minWait = minWait;
        this.maxWait = maxWait;
        this.minLure = minLure;
        this.maxLure = maxLure;
        this.minBiteWindow = minBiteWindow;
        this.maxBiteWindow = maxBiteWindow;
        this.treasureRequiresOpenWater = treasureRequiresOpenWater;
    }
}
