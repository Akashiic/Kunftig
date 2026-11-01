package com.akashic.kunftig.fishing.api;

import net.minecraft.entity.projectile.EntityFishHook;

/**
 * Forge-side listener for the fishing states 1.7.10 does not report. Called on the server thread. Implement only
 * what you need; return false to cancel (where cancelling is possible).
 * <p>
 * Bukkit plugins on Thermos/Crucible use {@code com.akashic.kunftig.api.bukkit.fishing.KunftigFishEvent} instead.
 */
public interface FishingListener {

    /** A fish appeared (1.21 {@code LURED}). Cancel: it does not come, the hook waits again. */
    default boolean onLured(EntityFishHook hook) {
        return true;
    }

    /** The fish bites (1.21 {@code BITE}). Cancel: no splash and no bite, the hook waits again. */
    default boolean onBite(EntityFishHook hook) {
        return true;
    }

    /** The bite window ran out (1.21 {@code FAILED_ATTEMPT}: "fails to catch a bite due to poor timing"). */
    default void onFailedAttempt(EntityFishHook hook) {}

    /**
     * The player reels in with nothing on the hook (1.21 {@code REEL_IN}; Bukkit 1.7 calls this FAILED_ATTEMPT).
     * Cancel: the hook stays in the water.
     */
    default boolean onReelIn(EntityFishHook hook) {
        return true;
    }
}
