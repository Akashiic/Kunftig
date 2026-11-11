package com.akashic.kunftig.api.bukkit.display;

import java.util.List;
import java.util.regex.Pattern;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * 3D models in the world (API 8) on Thermos/Crucible 1.7.10: GeckoLib models from the players' resource pack, drawn by
 * Kunftig clients, animated on the client. Requires the Kunftig mod on the server.
 *
 * <pre>
 * Display box = KunftigDisplays.spawn(bobber, "akashicfishing:caixa_da_sorte", "epico", "abrir", rewards);
 * // ... the animation plays on the clients; 7.6 s later:
 * box.remove();
 * </pre>
 *
 * The server sends each display to the players within its view range who listen to the display channel (Kunftig
 * clients), with the time its state has been playing, and takes it away when they leave the range, the world or the
 * server. Displays do not survive a restart. A client without the model (another pack) or without GeckoLib draws
 * nothing: check {@link #drawsDisplays} to give those players something else (particles, a hologram).
 */
public final class KunftigDisplays {

    /** Default view range in blocks. */
    public static final double DEFAULT_VIEW_RANGE = 48;
    /** Item stacks per display. */
    public static final int MAX_ITEMS = 8;
    /** A model: {@code namespace:path}, lower case. */
    public static final Pattern MODEL_NAME = Pattern.compile("[a-z0-9_.-]{1,64}:[a-z0-9_./-]{1,128}");
    /** A state or variant name: a-z, 0-9 and _, up to 32 characters (the variant may also be empty). */
    public static final Pattern STATE_NAME = Pattern.compile("[a-z0-9_]{1,32}");

    /** Implemented inside the mod; installed when the server starts. */
    public interface Provider {

        Display spawn(Location location, String model, String variant, String state, List<ItemStack> items,
            double viewRange);

        boolean drawsDisplays(Player player);

        boolean drawsExtendedDisplays(Player player);

        boolean walksOnDisplays(Player player);
    }

    private static volatile Provider provider;

    private KunftigDisplays() {}

    public static boolean isAvailable() {
        return provider != null;
    }

    static Provider provider() {
        Provider current = provider;
        if (current == null) {
            throw new IllegalStateException("Kunftig displays are not available on this server");
        }
        return current;
    }

    /** A display seen from {@link #DEFAULT_VIEW_RANGE} blocks. */
    public static Display spawn(Location location, String model, String variant, String state,
        List<ItemStack> items) {
        return spawn(location, model, variant, state, items, DEFAULT_VIEW_RANGE);
    }

    /**
     * @param location  the model's origin; the yaw turns it
     * @param model     {@code namespace:id} of {@code kunftig/displays.json}
     * @param variant   a texture variant of the manifest, or null / {@code ""} for the default texture
     * @param state     the state to play from now
     * @param items     up to {@link #MAX_ITEMS} stacks for the item bones (null for none)
     * @param viewRange blocks (1 to 128)
     */
    public static Display spawn(Location location, String model, String variant, String state, List<ItemStack> items,
        double viewRange) {
        if (location == null || location.getWorld() == null) {
            throw new IllegalArgumentException("location with a world required");
        }
        if (model == null || !MODEL_NAME.matcher(model)
            .matches()) {
            throw new IllegalArgumentException("model must be namespace:id: " + model);
        }
        if (variant != null && !variant.isEmpty()
            && !STATE_NAME.matcher(variant)
                .matches()) {
            throw new IllegalArgumentException("variant must match " + STATE_NAME + ": " + variant);
        }
        if (state == null || !STATE_NAME.matcher(state)
            .matches()) {
            throw new IllegalArgumentException("state must match " + STATE_NAME + ": " + state);
        }
        if (items != null && items.size() > MAX_ITEMS) {
            throw new IllegalArgumentException("at most " + MAX_ITEMS + " items");
        }
        if (!(viewRange >= 1 && viewRange <= 128)) {
            throw new IllegalArgumentException("view range must be 1..128: " + viewRange);
        }
        return provider().spawn(location, model, variant == null ? "" : variant, state, items, viewRange);
    }

    /** Whether this player's client draws displays (Kunftig with the display channel; GeckoLib and the pack too). */
    public static boolean drawsDisplays(Player player) {
        return provider().drawsDisplays(player);
    }

    /**
     * Whether the player's client also draws the API 9 parts of displays: the model scale ({@code escala} and
     * {@link Display#setScale}), bones hidden and shown ({@code ocultos} and {@link Display#setBoneVisible}), the
     * blend between states ({@code transicao}) and glides ({@link Display#teleport(Location, int)}). A client that
     * {@link #drawsDisplays draws displays} but not these draws them at the manifest's base size, with every bone,
     * and jumps between states and places: for a model built small and scaled up (like a boss), treat it as a client
     * that cannot draw it.
     *
     * @since API 9
     */
    public static boolean drawsExtendedDisplays(Player player) {
        return provider().drawsExtendedDisplays(player);
    }

    /**
     * Whether the player's client makes it walk on displays ({@link Display#setCollision}). The others fall through:
     * keep them off a ship, or give them something else to stand on.
     *
     * @since API 12
     */
    public static boolean walksOnDisplays(Player player) {
        return provider().walksOnDisplays(player);
    }

    /** Internal: called by the mod. */
    public static void install(Provider implementation) {
        provider = implementation;
    }
}
