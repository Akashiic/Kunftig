package com.akashic.kunftig.api;

import com.akashic.kunftig.api.bukkit.fishing.KunftigFishingBukkit;
import com.akashic.kunftig.api.bukkit.display.KunftigDisplays;
import com.akashic.kunftig.api.bukkit.hologram.KunftigHolograms;
import com.akashic.kunftig.api.bukkit.particle.KunftigParticles;
import com.akashic.kunftig.api.bukkit.player.KunftigPlayers;
import com.akashic.kunftig.api.bukkit.rod.KunftigRods;
import com.akashic.kunftig.api.bukkit.persistence.KunftigItems;

/**
 * Version and capabilities of the Kunftig API on this server.
 * <p>
 * Plugins compile against {@code kunftig-api} as {@code compileOnly} and must not shade it: at runtime the classes
 * come from the Kunftig mod in the server's {@code mods/} folder. To degrade gracefully when the mod is missing, check
 * {@code Class.forName("com.akashic.kunftig.api.KunftigApi")} before touching any other Kunftig class.
 */
public final class KunftigApi {

    /**
     * Incremented whenever the API gains something. 1: modern fishing ({@code KunftigFishEvent},
     * {@code ModernFishHook}) and item persistent data ({@code KunftigItems}). 2: particles
     * ({@code KunftigParticles}) and player ping ({@code KunftigPlayers}). 3: {@code ModernFishHook#retrieve} and lava
     * fishing ({@code ModernFishHook#setLavaFishing}). 4: item models ({@code KunftigItems#setModel}). 5:
     * {@code ModernFishHook#setReelLocked}, {@code setCatchAimedAtAngler} and {@code KunftigPlayers#isHotbarSwitch}.
     * 6: holograms ({@code KunftigHolograms}). 7: the state of 3D fishing rods ({@code KunftigRods}). 8: 3D models
     * in the world ({@code KunftigDisplays}). 9: display scale, hidden bones, state blends and glides
     * ({@code Display#setScale}, {@code setBoneVisible}, {@code teleport(Location, int)},
     * {@code KunftigDisplays#drawsExtendedDisplays}) and the flight lock ({@code KunftigPlayers#setFlightLocked}).
     */
    private static final int API_VERSION = 9;

    private KunftigApi() {}

    /** The API level of the installed Kunftig (a method, so it is never inlined into your plugin at compile time). */
    public static int apiVersion() {
        return API_VERSION;
    }

    /** Whether the modern fishing engine runs (mod installed, mixin applied, enabled in {@code kunftig.cfg}). */
    public static boolean isFishingAvailable() {
        return KunftigFishingBukkit.isAvailable();
    }

    /** Whether item persistent data is available (Bukkit hybrid with the mod installed). */
    public static boolean isItemDataAvailable() {
        return KunftigItems.isAvailable();
    }

    /** Whether {@code KunftigParticles} works (Bukkit hybrid with the mod installed). */
    public static boolean isParticlesAvailable() {
        return KunftigParticles.isAvailable();
    }

    /** Whether {@code KunftigHolograms} works (Bukkit hybrid with the mod installed; API 6). */
    public static boolean isHologramsAvailable() {
        return KunftigHolograms.isAvailable();
    }

    /** Whether {@code KunftigRods} works (Bukkit hybrid with the mod installed; API 7). */
    public static boolean isRodsAvailable() {
        return KunftigRods.isAvailable();
    }

    /** Whether {@code KunftigDisplays} works (Bukkit hybrid with the mod installed; API 8). */
    public static boolean isDisplaysAvailable() {
        return KunftigDisplays.isAvailable();
    }

    /** Whether {@code KunftigPlayers} works (Bukkit hybrid with the mod installed). */
    public static boolean isPlayersAvailable() {
        return KunftigPlayers.isAvailable();
    }
}
