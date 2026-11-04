package com.akashic.kunftig.api.bukkit.rod;

import java.util.regex.Pattern;

import org.bukkit.entity.Player;

/**
 * The state of a player's 3D fishing rod, for the clients that draw it (Kunftig with GeckoLib and a resource pack that
 * declares the rod in {@code kunftig/item_models_3d.json}). Requires the Kunftig mod on the server.
 *
 * <pre>
 * KunftigRods.setState(player, KunftigRods.BITE);    // KunftigFishEvent BITE
 * KunftigRods.setState(player, KunftigRods.FIGHT);   // the minigame starts...
 * KunftigRods.setTension(player, 0.8f);              // ...every tick of it (sent only when it changes)
 * KunftigRods.setState(player, KunftigRods.CATCH);   // won; or IDLE when the fish got away
 * </pre>
 *
 * The client plays {@link #DRAW}, {@link #CAST} and {@link #IDLE} on its own (the rod comes to the hand, the line goes
 * out, the line comes back); the server sets what the client cannot know. The latest of both wins; a state that does
 * not loop ({@link #BITE}, {@link #CATCH}) plays once and goes back to {@link #IDLE}. The player and everyone within
 * 96 blocks with Kunftig see it; someone who comes closer later sees the state from where it is. States are the names
 * of the manifest's {@code estados}: those below, or any other the pack declares.
 */
public final class KunftigRods {

    /** The rod in the hand, no fish (loops). */
    public static final String IDLE = "parada";
    /** The rod just came to the hand (the client plays it on its own). */
    public static final String DRAW = "sacar";
    /** The cast (the client plays it on its own). */
    public static final String CAST = "lancar";
    /** A fish bit (once). */
    public static final String BITE = "mordida";
    /** The fight: the rod bends with {@link #setTension} (loops). */
    public static final String FIGHT = "briga";
    /** Reeling in without a fish (loops). */
    public static final String REEL = "recolher";
    /** The fight was won (once). */
    public static final String CATCH = "captura";

    /** What a state name may be: a-z, 0-9 and _, up to 32 characters. */
    public static final Pattern STATE_NAME = Pattern.compile("[a-z0-9_]{1,32}");

    /** Implemented inside the mod; installed when the server starts. */
    public interface Provider {

        void setState(Player player, String state);

        void setTension(Player player, float tension);

        String getState(Player player);

        boolean drawsRodModels(Player player);
    }

    private static volatile Provider provider;

    private KunftigRods() {}

    public static boolean isAvailable() {
        return provider != null;
    }

    private static Provider provider() {
        Provider current = provider;
        if (current == null) {
            throw new IllegalStateException("KunftigRods is not available on this server");
        }
        return current;
    }

    /**
     * Sets the state of the player's rod from now; setting the same state again restarts it (a second bite).
     *
     * @throws IllegalArgumentException for a state that is not a valid name
     */
    public static void setState(Player player, String state) {
        if (player == null) {
            throw new IllegalArgumentException("player required");
        }
        if (state == null || !STATE_NAME.matcher(state)
            .matches()) {
            throw new IllegalArgumentException("invalid rod state (a-z 0-9 _, up to 32): " + state);
        }
        provider().setState(player, state);
    }

    /**
     * The fight's tension, 0 (slack) to 1 (about to break): the {@link #FIGHT} animation bends the rod with it
     * ({@code query.tensao}). Call it as often as you like; it goes out at most once per tick, in hundredths, only
     * when it changed.
     */
    public static void setTension(Player player, float tension) {
        if (player == null) {
            throw new IllegalArgumentException("player required");
        }
        provider().setTension(player, Float.isNaN(tension) ? 0 : Math.max(0, Math.min(1, tension)));
    }

    /** The state last set by the server for the player, or null. */
    public static String getState(Player player) {
        return provider().getState(player);
    }

    /** Whether this player's client draws 3D rods (Kunftig with GeckoLib, listening to the rod channel). */
    public static boolean drawsRodModels(Player player) {
        return provider().drawsRodModels(player);
    }

    /** Internal: called by the mod. */
    public static void install(Provider implementation) {
        provider = implementation;
    }
}
