package com.akashic.kunftig.api.bukkit.fishing;

import org.bukkit.entity.Entity;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;

/**
 * Bukkit 1.21.1's {@code org.bukkit.entity.FishHook} on Thermos/Crucible 1.7.10: same method names, same
 * semantics, same defaults (see the profile in {@code config/kunftig.cfg}), same validation. Code written against
 * 1.21.1 ports by replacing {@code event.getHook()} with {@code KunftigFishingBukkit.of(event.getHook())}.
 * <p>
 * The deprecated bite chance is not mirrored: it never did anything on 1.7.10 either (Crucible stores it but no
 * code reads it). Kunftig extensions are marked as such. Main thread only.
 */
public interface ModernFishHook {

    /** 1.21.1's {@code FishHook.HookState}. */
    enum HookState {
        /** Flying, or stuck in a block. */
        UNHOOKED,
        /** Attached to an entity. */
        HOOKED_ENTITY,
        /** Has landed in water. */
        BOBBING
    }

    /** The underlying Bukkit entity. Kunftig extension. */
    FishHook getEntity();

    /** The player holding the rod. Kunftig extension. */
    Player getOwner();

    // ---------------------------------------------------------------- wait (until a fish appears), before lure

    int getMinWaitTime();

    void setMinWaitTime(int minWaitTime);

    int getMaxWaitTime();

    void setMaxWaitTime(int maxWaitTime);

    void setWaitTime(int min, int max);

    // ---------------------------------------------------------------- lure (the fish swimming to the bobber)

    int getMinLureTime();

    void setMinLureTime(int minLureTime);

    int getMaxLureTime();

    void setMaxLureTime(int maxLureTime);

    void setLureTime(int min, int max);

    float getMinLureAngle();

    void setMinLureAngle(float minLureAngle);

    float getMaxLureAngle();

    void setMaxLureAngle(float maxLureAngle);

    void setLureAngle(float min, float max);

    /** Whether the Lure enchantment reduces the wait (100 ticks per level). */
    boolean getApplyLure();

    void setApplyLure(boolean applyLure);

    /** Rain above the hook speeds fishing up (25% shorter on average). */
    boolean isRainInfluenced();

    void setRainInfluenced(boolean rainInfluenced);

    /** No sky above the hook slows fishing down (50% longer on average). */
    boolean isSkyInfluenced();

    void setSkyInfluenced(boolean skyInfluenced);

    // ---------------------------------------------------------------- state

    /** 1.16+ open water: a 5x4x5 area of water, air and lily pads. Treasure needs it when the rule is on. */
    boolean isInOpenWater();

    HookState getState();

    /** Ticks until a fish appears (0 or less: none is waiting to appear). */
    int getWaitTime();

    void setWaitTime(int ticks);

    /** Ticks the appeared fish still swims before biting; 0 once it has bitten. */
    int getTimeUntilBite();

    /** @throws IllegalArgumentException if {@code ticks < 1} (1 = bites on the next tick) */
    void setTimeUntilBite(int ticks);

    /** Starts over: nothing approaching or biting, a fresh wait rolled with the current settings and lure. */
    void resetFishingState();

    Entity getHookedEntity();

    /** @param entity the entity to hook, or null to unhook */
    void setHookedEntity(Entity entity);

    /** Pulls the hooked entity toward the angler; false if nothing is hooked. */
    boolean pullHookedEntity();

    // ---------------------------------------------------------------- Kunftig extensions: the bite window

    /** A fish is on the hook: reeling in now catches it. Kunftig extension. */
    boolean isBiting();

    /** Ticks left to reel in (0 = no bite). Kunftig extension, 1.21's {@code nibble}. */
    int getBiteWindow();

    /**
     * Lengthen or shorten the current bite (e.g. while a minigame runs). 1 makes the fish escape on the next tick
     * with {@code FAILED_ATTEMPT}; 0 ends the bite silently and waiting restarts. Kunftig extension.
     */
    void setBiteWindow(int ticks);

    int getMinBiteWindow();

    int getMaxBiteWindow();

    /** Range of future bite windows (vanilla 1.7.10: 10-30, 1.21.1: 20-40). Kunftig extension. */
    void setBiteWindowRange(int min, int max);

    /** Treasure only in open water (1.16+). Kunftig extension. */
    boolean getTreasureRequiresOpenWater();

    void setTreasureRequiresOpenWater(boolean value);
}
