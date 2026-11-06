package com.akashic.kunftig.api.bukkit.blade;

import java.util.regex.Pattern;

import org.bukkit.Location;

/**
 * SlashBlade-style effects (API 9), drawn by clients with Kunftig from the models and textures a resource pack
 * declares under {@code efeitos} in its {@code kunftig/blades.json}. Effects are fire-and-forget: they play on the
 * clients within 64 blocks and need no update. Requires the Kunftig mod on the server.
 *
 * <pre>
 * // SlashBlade's doSlash: at the player's eyes * 0.75 + look * 0.3, the player's yaw, pitch 0
 * KunftigEffects.slash("overkill", at, player.getLocation().getYaw(), -10f, 0x3333FF, rankLevel, 1f);
 * </pre>
 */
public final class KunftigEffects {

    public static final Pattern NAMESPACE = Pattern.compile("[a-z0-9_.-]{1,64}");

    /** Implemented inside the mod; installed when the server starts. */
    public interface Provider {

        void slash(String namespace, Location at, float yaw, float pitch, float roll, float rotationOffset, int color,
            float rank, float baseSize, int lifetime);
    }

    private static volatile Provider provider;

    private KunftigEffects() {}

    public static boolean isAvailable() {
        return provider != null;
    }

    /**
     * A slash arc (SlashBlade's {@code EntitySlashEffect}): 270 degrees of a ring about 4 blocks across, sweeping 135
     * degrees and fading over 10 ticks, in the colour (RGB) and with the passes of the concentration rank level
     * ({@code rank}: 0 none, 1 D ... 5 S, 5.25 SS, 5.5 SSS; below C it is grey). Roll 0: horizontal, left to right;
     * 180: right to left.
     */
    public static void slash(String namespace, Location at, float yaw, float roll, int color, float rank,
        float baseSize) {
        slash(namespace, at, yaw, 0, roll, 0, color, rank, baseSize, 10);
    }

    /** {@link #slash(String, Location, float, float, int, float, float)} with every parameter. */
    public static void slash(String namespace, Location at, float yaw, float pitch, float roll, float rotationOffset,
        int color, float rank, float baseSize, int lifetime) {
        if (namespace == null || !NAMESPACE.matcher(namespace)
            .matches()) {
            throw new IllegalArgumentException("namespace must match " + NAMESPACE + ": " + namespace);
        }
        if (at == null || at.getWorld() == null) {
            throw new IllegalArgumentException("location with a world required");
        }
        Provider p = provider;
        if (p == null) {
            throw new IllegalStateException("KunftigEffects is not available on this server");
        }
        p.slash(namespace, at, yaw, pitch, roll, rotationOffset, color, rank, baseSize, lifetime);
    }

    /** Internal: called by the mod. */
    public static void install(Provider implementation) {
        provider = implementation;
    }
}
