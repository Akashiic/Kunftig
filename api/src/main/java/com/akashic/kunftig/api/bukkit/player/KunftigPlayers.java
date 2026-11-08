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

        void setFlightLocked(Player player, boolean locked);

        boolean isFlightLocked(Player player);

        /** See {@link KunftigPlayers#pushVelocity}. */
        default void pushVelocity(Player player, double x, double y, double z) {
            player.setVelocity(new org.bukkit.util.Vector(x, y, z));
        }
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

    /**
     * Locks or frees the player's flight (API 9): while locked, a player who is not in creative cannot fly, whatever
     * gives the flight. On the server, at the end of each of the player's ticks (after rings, armour, baubles and
     * spells switched flying on), flying is switched off and the client told: every mod that flies through the player's
     * capabilities, like the creative flight (Extra Utilities' angel ring, Draconic Evolution's armour, Botania's tiara,
     * Ars Magica's flight...), and Bukkit's {@code setAllowFlight}, are covered. A Kunftig client also cuts upward
     * speed that does not come from a jump or a push (a hit, an explosion, {@code setVelocity}): jetpacks, gliders,
     * lift spells. The lock outlives the player's logout until freed; freeing it does not give flight back (the mods
     * switch it on again by themselves; a plugin that took Bukkit's {@code allowFlight} away gives it back).
     * <p>
     * A protection layer, not an anticheat: a client without Kunftig, or a modified one, can still move upward, so for
     * a rule like "no flying in this arena" also check that players do not stay in the air. Main thread only.
     *
     * @throws IllegalArgumentException if the player is not an online player of this server
     * @since API 9
     */
    public static void setFlightLocked(Player player, boolean locked) {
        Provider current = provider;
        if (current == null) {
            throw new IllegalStateException("KunftigPlayers is not available on this server");
        }
        current.setFlightLocked(player, locked);
    }

    /** Whether {@link #setFlightLocked} locked the player. @since API 9 */
    public static boolean isFlightLocked(Player player) {
        Provider current = provider;
        if (current == null) {
            throw new IllegalStateException("KunftigPlayers is not available on this server");
        }
        return current.isFlightLocked(player);
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

    /**
     * Sets the player's motion and sends it to his client <b>in this same tick</b>. Bukkit's {@code setVelocity} only
     * marks the velocity as changed: the entity tracker sends it on its next update of that player, every 2 ticks, and
     * a later position packet can lose it, so motion set every tick (a hover, a dash) reaches the client in jumps. The
     * client is authoritative for its own movement, so this is how a server moves a player smoothly. Each component is
     * limited to ±3.9 by the packet.
     *
     * @throws IllegalArgumentException if the player is not an online player of this server
     * @since API 11
     */
    public static void pushVelocity(Player player, double x, double y, double z) {
        Provider current = provider;
        if (current == null) {
            throw new IllegalStateException("KunftigPlayers is not available on this server");
        }
        current.pushVelocity(player, x, y, z);
    }
}
