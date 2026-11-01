package com.akashic.kunftig.api.bukkit.player;

import org.bukkit.entity.Player;

/**
 * Player data that Bukkit 1.7.10 does not expose. Requires the Kunftig mod on the server.
 */
public final class KunftigPlayers {

    /** Implemented inside the mod; installed when the server starts. */
    public interface Provider {

        int getPing(Player player);
    }

    private static volatile Provider provider;

    private KunftigPlayers() {}

    public static boolean isAvailable() {
        return provider != null;
    }

    /**
     * The player's latency in milliseconds, as in Bukkit 1.17+'s {@code Player#getPing()} and the tab list: a moving
     * average updated on each keep-alive ({@code (ping * 3 + sample) / 4}, sampled every few seconds), so it reacts to
     * changes over several samples. 0 right after joining. Useful to widen timing windows for laggy players (e.g. a
     * minigame on the bite).
     *
     * @throws IllegalArgumentException if the player is not an online player of this server
     */
    public static int getPing(Player player) {
        Provider current = provider;
        if (current == null) {
            throw new IllegalStateException("KunftigPlayers is not available on this server");
        }
        return current.getPing(player);
    }

    /** Internal: called by the mod. */
    public static void install(Provider implementation) {
        provider = implementation;
    }
}
