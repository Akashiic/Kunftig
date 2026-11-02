package com.akashic.kunftig.fishing;

/**
 * The per-hook settings that 1.21.1's Bukkit {@code FishHook} exposes (plus the bite window and the treasure rule),
 * with the same validation. Mutable; owned by one hook; server thread only.
 */
public final class FishingParameters {

    private int minWait;
    private int maxWait;
    private int minLure;
    private int maxLure;
    private float minLureAngle;
    private float maxLureAngle;
    private int minBiteWindow;
    private int maxBiteWindow;
    private boolean applyLure = true;
    private boolean rainInfluenced = true;
    private boolean skyInfluenced = true;
    private boolean treasureRequiresOpenWater;
    private boolean lavaFishing;
    private boolean catchAimedAtAngler;
    private boolean reelLocked;

    public FishingParameters(FishingProfile profile) {
        this(profile, false);
    }

    /** @param lavaFishing whether hooks also float and catch fish in lava (see {@link #setLavaFishing}) */
    public FishingParameters(FishingProfile profile, boolean lavaFishing) {
        this.lavaFishing = lavaFishing;
        minWait = profile.minWait;
        maxWait = profile.maxWait;
        minLure = profile.minLure;
        maxLure = profile.maxLure;
        minLureAngle = 0.0F;
        maxLureAngle = 360.0F;
        minBiteWindow = profile.minBiteWindow;
        maxBiteWindow = profile.maxBiteWindow;
        treasureRequiresOpenWater = profile.treasureRequiresOpenWater;
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    // --- wait: ticks until a fish appears ("lured"), before lure is applied

    public int minWaitTime() {
        return minWait;
    }

    public int maxWaitTime() {
        return maxWait;
    }

    public void setMinWaitTime(int value) {
        check(value >= 0 && value <= maxWait, "The minimum wait time should be between 0 and the maximum wait time.");
        minWait = value;
    }

    public void setMaxWaitTime(int value) {
        check(value >= 0 && value >= minWait, "The maximum wait time should be at least 0 and the minimum wait time.");
        maxWait = value;
    }

    public void setWaitTime(int min, int max) {
        check(min >= 0 && max >= 0 && min <= max, "The minimum/maximum wait time should be at least 0 and min <= max.");
        minWait = min;
        maxWait = max;
    }

    // --- lure: ticks the fish swims toward the bobber before biting

    public int minLureTime() {
        return minLure;
    }

    public int maxLureTime() {
        return maxLure;
    }

    public void setMinLureTime(int value) {
        check(value >= 0 && value <= maxLure, "The minimum lure time should be between 0 and the maximum lure time.");
        minLure = value;
    }

    public void setMaxLureTime(int value) {
        check(value >= 0 && value >= minLure, "The maximum lure time should be at least 0 and the minimum lure time.");
        maxLure = value;
    }

    public void setLureTime(int min, int max) {
        check(min >= 0 && max >= 0 && min <= max, "The minimum/maximum lure time should be at least 0 and min <= max.");
        minLure = min;
        maxLure = max;
    }

    // --- angle the fish comes from, in degrees

    public float minLureAngle() {
        return minLureAngle;
    }

    public float maxLureAngle() {
        return maxLureAngle;
    }

    public void setMinLureAngle(float value) {
        check(
            value >= 0 && value <= maxLureAngle && value <= 360,
            "The minimum lure angle should be between 0 and the maximum lure angle (at most 360).");
        minLureAngle = value;
    }

    public void setMaxLureAngle(float value) {
        check(
            value <= 360 && value >= minLureAngle && value >= 0,
            "The maximum lure angle should be between the minimum lure angle and 360.");
        maxLureAngle = value;
    }

    public void setLureAngle(float min, float max) {
        check(min >= 0 && max <= 360 && min <= max, "The lure angles should be within 0-360 and min <= max.");
        minLureAngle = min;
        maxLureAngle = max;
    }

    // --- bite window: ticks the player has to reel in after the bite (Kunftig extension)

    public int minBiteWindow() {
        return minBiteWindow;
    }

    public int maxBiteWindow() {
        return maxBiteWindow;
    }

    public void setBiteWindow(int min, int max) {
        check(min >= 1 && min <= max, "The bite window should be at least 1 tick and min <= max.");
        minBiteWindow = min;
        maxBiteWindow = max;
    }

    // --- switches

    public boolean applyLure() {
        return applyLure;
    }

    public void setApplyLure(boolean value) {
        applyLure = value;
    }

    public boolean rainInfluenced() {
        return rainInfluenced;
    }

    public void setRainInfluenced(boolean value) {
        rainInfluenced = value;
    }

    public boolean skyInfluenced() {
        return skyInfluenced;
    }

    public void setSkyInfluenced(boolean value) {
        skyInfluenced = value;
    }

    public boolean treasureRequiresOpenWater() {
        return treasureRequiresOpenWater;
    }

    public void setTreasureRequiresOpenWater(boolean value) {
        treasureRequiresOpenWater = value;
    }

    // --- lava (Kunftig extension)

    public boolean lavaFishing() {
        return lavaFishing;
    }

    /**
     * Lets the hook float in lava and run the whole bite cycle there, with fire effects instead of water ones. The
     * hook and the caught item become fire-proof. Water keeps working as usual.
     */
    public void setLavaFishing(boolean value) {
        lavaFishing = value;
    }

    // --- caught item aimed at the angler (Kunftig extension, issue #10)

    public boolean catchAimedAtAngler() {
        return catchAimedAtAngler;
    }

    /**
     * Raises the caught item's arc just enough to land it at the angler's feet ({@link CatchArc}); vanilla's arc falls
     * short when the angler stands above the water. Never lower than vanilla's.
     */
    public void setCatchAimedAtAngler(boolean value) {
        catchAimedAtAngler = value;
    }

    // --- reel lock (Kunftig extension, issue #9)

    public boolean reelLocked() {
        return reelLocked;
    }

    /** While locked, reeling in by any path does nothing (the hook stays, the bite goes on); retrieve() ignores it. */
    public void setReelLocked(boolean value) {
        reelLocked = value;
    }
}
