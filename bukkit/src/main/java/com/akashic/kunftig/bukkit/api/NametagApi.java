package com.akashic.kunftig.bukkit.api;

import org.bukkit.entity.Player;

import com.akashic.kunftig.bukkit.nametag.NametagSpec;

/**
 * Kunftig nametag API for other plugins. Main thread only. Obtain it with {@link KunftigNametags#api()}.
 * <p>
 * Two ways to integrate:
 * <ul>
 * <li>let Kunftig manage the player's team, with {@link #setOverride} layered above config groups and
 * {@code /kunftig set};</li>
 * <li>keep your own teams and only attach the long/RGB version to them with {@link #setTeamStyle}: vanilla clients
 * keep seeing your team's prefix, Kunftig clients see the style.</li>
 * </ul>
 * Specs are built with {@link NametagSpec#builder()}, which accepts MiniMessage (with legacy {@code &} codes) or
 * JSON text components.
 */
public interface NametagApi {

    /** Whether the player's client completed the Kunftig handshake. */
    boolean isKunftigClient(Player player);

    /** Highest-priority layer for a player; fields left {@code null} fall through to lower layers. */
    void setOverride(Player player, NametagSpec spec);

    void clearOverride(Player player);

    /** Attaches a style to a team managed by another plugin (by team name, on every scoreboard). */
    void setTeamStyle(String teamName, NametagSpec style);

    void removeTeamStyle(String teamName);
}
