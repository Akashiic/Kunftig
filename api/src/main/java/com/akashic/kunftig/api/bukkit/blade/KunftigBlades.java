package com.akashic.kunftig.api.bukkit.blade;

import org.bukkit.entity.Player;

/**
 * SlashBlade-style blades (API 11): clients with Kunftig draw the blade a player holds as SlashBlade does (the OBJ the
 * resource pack declares in {@code kunftig/blades.json} for the item's model, moved by the blade rig's VMD, the sheath
 * at the hip, the body posed by the player motion), and the server tells what plays. Players without Kunftig see the
 * vanilla item. Requires the Kunftig mod on the server.
 *
 * <pre>
 * KunftigBlades.play(player, comboA1);            // from now, for the player and everyone around
 * KunftigBlades.setCharged(player, true);         // the charge glow on the sheath
 * </pre>
 *
 * A player who comes closer later sees the chain from where it is. Main thread.
 */
public final class KunftigBlades {

    /** Implemented inside the mod; installed when the server starts. */
    public interface Provider {

        void play(Player player, BladeMotion motion);

        void setCharged(Player player, boolean charged);

        boolean drawsBlades(Player player);
    }

    private static volatile Provider provider;

    private KunftigBlades() {}

    public static boolean isAvailable() {
        return provider != null;
    }

    private static Provider provider() {
        Provider current = provider;
        if (current == null) {
            throw new IllegalStateException("KunftigBlades is not available on this server");
        }
        return current;
    }

    public static void play(Player player, BladeMotion motion) {
        if (player == null || motion == null) {
            throw new IllegalArgumentException("player and motion required");
        }
        provider().play(player, motion);
    }

    public static void setCharged(Player player, boolean charged) {
        if (player == null) {
            throw new IllegalArgumentException("player required");
        }
        provider().setCharged(player, charged);
    }

    /** Whether this player's client draws SlashBlade-style blades (Kunftig 9 or newer). */
    public static boolean drawsBlades(Player player) {
        return provider().drawsBlades(player);
    }

    /** Internal: called by the mod. */
    public static void install(Provider implementation) {
        provider = implementation;
    }
}
