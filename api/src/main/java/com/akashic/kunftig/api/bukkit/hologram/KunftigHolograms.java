package com.akashic.kunftig.api.bukkit.hologram;

import java.util.List;

import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Holograms (floating text) on Thermos/Crucible 1.7.10. Requires the Kunftig mod on the server; players see them when
 * their client has Kunftig too.
 *
 * <pre>
 * Hologram h = KunftigHolograms.create(location, Arrays.asList("§6Amuleto da Sorte", "§a+25% de sorte", "§77h restantes"));
 * h.setLines(...);   // the viewers see the new text at once
 * h.remove();        // gone for everyone
 * </pre>
 *
 * The server sends each hologram to the players within its view range (and only those listening to the hologram
 * channel), and takes it away when they leave the range, the world or the server. Holograms do not survive a restart:
 * a plugin that wants one to stay creates it again on enable.
 */
public final class KunftigHolograms {

    /** Default view range in blocks. */
    public static final double DEFAULT_VIEW_RANGE = 32;
    /** Lines per hologram. */
    public static final int MAX_LINES = 16;
    /** Characters per line (color codes included). */
    public static final int MAX_LINE_LENGTH = 256;

    /** Implemented inside the mod; installed when the server starts. */
    public interface Provider {

        Hologram create(Location location, List<String> lines, double viewRange);

        boolean drawsHolograms(Player player);
    }

    private static volatile Provider provider;

    private KunftigHolograms() {}

    public static boolean isAvailable() {
        return provider != null;
    }

    static Provider provider() {
        Provider current = provider;
        if (current == null) {
            throw new IllegalStateException("Kunftig holograms are not available on this server");
        }
        return current;
    }

    /** A hologram seen from {@link #DEFAULT_VIEW_RANGE} blocks. */
    public static Hologram create(Location location, List<String> lines) {
        return create(location, lines, DEFAULT_VIEW_RANGE);
    }

    /**
     * @param location  the center of the top line
     * @param lines     top first; at most {@link #MAX_LINES}, each cut at {@link #MAX_LINE_LENGTH}
     * @param viewRange blocks (1 to 128)
     */
    public static Hologram create(Location location, List<String> lines, double viewRange) {
        if (location == null || location.getWorld() == null) {
            throw new IllegalArgumentException("location with a world required");
        }
        if (!(viewRange >= 1 && viewRange <= 128)) {
            throw new IllegalArgumentException("view range must be 1..128: " + viewRange);
        }
        return provider().create(location, lines, viewRange);
    }

    /** Whether this player's client draws holograms (Kunftig mod with the hologram channel). */
    public static boolean drawsHolograms(Player player) {
        return provider().drawsHolograms(player);
    }

    /** Internal: called by the mod. */
    public static void install(Provider implementation) {
        provider = implementation;
    }
}
