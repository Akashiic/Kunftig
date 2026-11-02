package com.akashic.kunftig.api.bukkit.player;

import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerItemHeldEvent;

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

    /** Hotbar slots are 0 to 8; anything else in a {@link PlayerItemHeldEvent} is not a real switch. */
    public static final int HOTBAR_SIZE = 9;

    /**
     * Whether a {@link PlayerItemHeldEvent} is the player really switching hotbar slots. With Backhand (off hand mod),
     * every right click on the server produces {@code 0 -> 36 -> 0}: the client switches to the off hand slot (36) and
     * back to try the off hand item, Backhand makes the server accept slot 36, and Crucible fires the event for each.
     * Those are not switches. Works without the mod (pure Bukkit). To know whether the item in hand changed, compare it
     * a tick later instead.
     */
    public static boolean isHotbarSwitch(PlayerItemHeldEvent event) {
        return isHotbarSwitch(event.getPreviousSlot(), event.getNewSlot());
    }

    /** {@link #isHotbarSwitch(PlayerItemHeldEvent)} by slots. */
    public static boolean isHotbarSwitch(int previousSlot, int newSlot) {
        return previousSlot != newSlot && previousSlot >= 0
            && previousSlot < HOTBAR_SIZE
            && newSlot >= 0
            && newSlot < HOTBAR_SIZE;
    }

    /** Internal: called by the mod. */
    public static void install(Provider implementation) {
        provider = implementation;
    }
}
