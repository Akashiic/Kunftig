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

    /**
     * Reels in as if the angler had right-clicked with the rod, like Paper's {@code FishHook#retrieve(EquipmentSlot)}
     * (1.7.10 has no off hand). During a bite the fish is caught, with the same cancellable {@code PlayerFishEvent}
     * {@code CAUGHT_FISH} (change the item there as usual) and the item flies to the angler; a hooked entity is
     * pulled; otherwise it is {@code REEL_IN} ({@link KunftigFishEvent}, cancellable) or {@code IN_GROUND}. The hook
     * is removed unless an event was cancelled. With {@code applyRodDamage}, the rod in hand takes vanilla's damage
     * (and is removed if it breaks) and the angler swings for everyone else; the angler's own client is not told to
     * swing, because it would echo that as a left click ({@code PlayerAnimationEvent}). Main thread. API level 3.
     *
     * @return the rod damage of this retrieval as vanilla computes it (1 for a fish, 2 in the ground, 3 for an
     *         entity...), whether applied or not
     * @throws IllegalStateException if the hook is gone or is not its angler's current hook
     */
    int retrieve(boolean applyRodDamage);

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

    // ---------------------------------------------------------------- Kunftig extensions: lava

    /**
     * Fishing in lava: the hook floats on lava and the whole cycle runs there (states, events, bite window, loot),
     * with flames and lava pops instead of wakes and splashes. The hook and the caught item are fire-proof, and the
     * item starts above the surface so it flies to the angler. Water keeps working. Off by default (unless
     * {@code fishing.lava} is on in {@code kunftig.cfg}); set it on {@code PlayerFishEvent} {@code FISHING}. Kunftig
     * extension. In lava, {@link #isInOpenWater()} means open lava.
     */
    void setLavaFishing(boolean lavaFishing);

    boolean isLavaFishing();

    /** The hook is floating in lava. Kunftig extension. */
    boolean isInLava();

    // ---------------------------------------------------------------- Kunftig extensions: range (API level 10)

    /**
     * How far the hook may get from its angler before it is removed (vanilla: 32 blocks), clamped to 4..128. For
     * casts at far targets, such as a boss's weak points. Clients draw a hook up to 64 blocks away and the server
     * tracks it to 64 by default, so past that the hook still works but may not be seen. Main thread. API level 10.
     */
    void setMaxDistance(double blocks);

    /** The hook's range in blocks (32 unless changed). API level 10. */
    double getMaxDistance();

    // ---------------------------------------------------------------- Kunftig extensions: minigames (API level 5)

    /**
     * Locks reeling in: while locked, every way of reeling in (right click in the air or on a block, the Backhand off
     * hand, other mods) does nothing, without rod damage; the hook stays and the bite goes on. For minigames between
     * {@code BITE} and the catch, instead of cancelling clicks. {@link #retrieve} ignores the lock (it is the plugin
     * asking). The lock belongs to the hook and goes away with it. Kunftig extension.
     */
    void setReelLocked(boolean locked);

    boolean isReelLocked();

    /**
     * Raises the caught item's arc so it lands at the angler. Vanilla's arc (the same in 1.21.1) comes back to the
     * hook's height after ~10 ticks and ~90% of the distance, so from water a block below a bank the item hits the
     * bank and falls in. Never lower than vanilla's. Off by default ({@code fishing.catchAim} in {@code kunftig.cfg});
     * set it on {@code FISHING}. Applies to {@link #retrieve} too. Kunftig extension.
     */
    void setCatchAimedAtAngler(boolean aimed);

    boolean isCatchAimedAtAngler();
}
