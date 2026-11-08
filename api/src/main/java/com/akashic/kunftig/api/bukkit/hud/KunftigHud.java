package com.akashic.kunftig.api.bukkit.hud;

import org.bukkit.entity.Player;

/**
 * Elements on one player's screen (API 11), drawn over the vanilla HUD by a Kunftig client: a style rank, a combo
 * counter, a charge bar. 1.7.10 has only the scoreboard sidebar and the boss bar for this. Requires the Kunftig mod on
 * the server and on the client; players without it see nothing ({@link #drawsHud}).
 *
 * <pre>
 * KunftigHud.show(player, HudElement.builder("overkill:combo").anchor(HudElement.Anchor.RIGHT).text("§e12 hits").build());
 * KunftigHud.remove(player, "overkill:combo");
 * </pre>
 *
 * Showing an element again with the same id replaces it (and restarts its duration); an element identical to the
 * one on screen is not sent again, so calling this every tick is cheap. Elements do not survive a reconnection. Main
 * thread.
 */
public final class KunftigHud {

    /** Implemented inside the mod; installed when the server starts. */
    public interface Provider {

        void show(Player player, HudElement element);

        void remove(Player player, String id);

        void clear(Player player);

        boolean drawsHud(Player player);
    }

    private static volatile Provider provider;

    private KunftigHud() {}

    public static boolean isAvailable() {
        return provider != null;
    }

    private static Provider provider() {
        Provider current = provider;
        if (current == null) {
            throw new IllegalStateException("KunftigHud is not available on this server");
        }
        return current;
    }

    public static void show(Player player, HudElement element) {
        if (player == null || element == null) {
            throw new IllegalArgumentException("player and element required");
        }
        provider().show(player, element);
    }

    public static void remove(Player player, String id) {
        if (player == null || id == null || !HudElement.ID.matcher(id)
            .matches()) {
            throw new IllegalArgumentException("player and a valid id required: " + id);
        }
        provider().remove(player, id);
    }

    /** Removes every element of every plugin from the player's screen. */
    public static void clear(Player player) {
        if (player == null) {
            throw new IllegalArgumentException("player required");
        }
        provider().clear(player);
    }

    /** Whether this player's client draws the HUD (Kunftig 9 or newer). */
    public static boolean drawsHud(Player player) {
        return provider().drawsHud(player);
    }

    /** Internal: called by the mod. */
    public static void install(Provider implementation) {
        provider = implementation;
    }
}
