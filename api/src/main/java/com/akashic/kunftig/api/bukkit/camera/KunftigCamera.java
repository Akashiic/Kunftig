package com.akashic.kunftig.api.bukkit.camera;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

/**
 * Lock-on (API 9): the player's client keeps turning his view toward an entity, smoothly, while he can still move the
 * mouse against it, and can mark the target. The view the server receives follows, so aiming by the player's look
 * hits the target. Requires the Kunftig mod on the server and on the client ({@link #followsLockOn}).
 *
 * <pre>
 * KunftigCamera.lockOn(player, zombie, 0.35f, 24, true);
 * KunftigCamera.release(player);
 * </pre>
 *
 * The client lets go by itself when the target is gone, dead or farther than the distance given; the plugin is not
 * told, so it should release or pick another target when it sees the same. Main thread.
 */
public final class KunftigCamera {

    /** Implemented inside the mod; installed when the server starts. */
    public interface Provider {

        void lockOn(Player player, Entity target, float strength, float maxDistance, boolean marker);

        void release(Player player);

        Entity getTarget(Player player);

        boolean followsLockOn(Player player);
    }

    private static volatile Provider provider;

    private KunftigCamera() {}

    public static boolean isAvailable() {
        return provider != null;
    }

    private static Provider provider() {
        Provider current = provider;
        if (current == null) {
            throw new IllegalStateException("KunftigCamera is not available on this server");
        }
        return current;
    }

    /**
     * Locks the player's view on {@code target}.
     *
     * @param strength    how much of the way the view turns per tick, 0.01 to 1 (1 snaps; 0.35 is firm but smooth)
     * @param maxDistance blocks (1 to 64) beyond which the client lets go
     * @param marker      whether the client marks the target
     */
    public static void lockOn(Player player, Entity target, float strength, float maxDistance, boolean marker) {
        if (player == null || target == null || target.equals(player)) {
            throw new IllegalArgumentException("player and another entity required");
        }
        if (!target.getWorld()
            .equals(player.getWorld())) {
            throw new IllegalArgumentException("target in another world");
        }
        provider().lockOn(player, target, strength, maxDistance, marker);
    }

    public static void release(Player player) {
        if (player == null) {
            throw new IllegalArgumentException("player required");
        }
        provider().release(player);
    }

    /** The entity last locked on for the player by the server, or null. */
    public static Entity getTarget(Player player) {
        return provider().getTarget(player);
    }

    /** Whether this player's client follows a lock-on (Kunftig 9 or newer). */
    public static boolean followsLockOn(Player player) {
        return provider().followsLockOn(player);
    }

    /** Internal: called by the mod. */
    public static void install(Provider implementation) {
        provider = implementation;
    }
}
