package com.akashic.kunftig.api.bukkit.fishing;

import org.bukkit.entity.FishHook;

/**
 * Entry point of Kunftig's modern fishing for Bukkit plugins on Thermos/Crucible (requires the Kunftig mod in the
 * server's {@code mods/} folder). Main thread only.
 *
 * <pre>
 * 
 * &#64;EventHandler
 * public void onFish(PlayerFishEvent event) { // Bukkit's own event, state FISHING
 *     if (event.getState() == PlayerFishEvent.State.FISHING) {
 *         KunftigFishingBukkit.of(event.getHook())
 *             .setWaitTime(40, 120);
 *     }
 * }
 *
 * &#64;EventHandler
 * public void onBite(KunftigFishEvent event) { // LURED, BITE, FAILED_ATTEMPT, REEL_IN
 *     if (event.getState() == KunftigFishEvent.State.BITE) {
 *         event.getHook()
 *             .setBiteWindow(100); // five seconds for a minigame
 *     }
 * }
 * </pre>
 */
public final class KunftigFishingBukkit {

    /** Implemented inside the mod; installed when the server starts. */
    public interface Provider {

        ModernFishHook of(FishHook hook);

        boolean isActive();
    }

    private static volatile Provider provider;

    private KunftigFishingBukkit() {}

    /** Whether the fishing engine runs on this server (mod installed, mixin applied, enabled in its config). */
    public static boolean isAvailable() {
        Provider current = provider;
        return current != null && current.isActive();
    }

    /**
     * The 1.21.1 view of a hook.
     *
     * @throws IllegalStateException if Kunftig fishing is not available ({@link #isAvailable()})
     */
    public static ModernFishHook of(FishHook hook) {
        Provider current = provider;
        if (current == null || !current.isActive()) {
            throw new IllegalStateException("Kunftig fishing is not available on this server");
        }
        return current.of(hook);
    }

    /** Internal: called by the mod. */
    public static void install(Provider implementation) {
        provider = implementation;
    }
}
