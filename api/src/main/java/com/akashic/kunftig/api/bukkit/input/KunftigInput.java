package com.akashic.kunftig.api.bukkit.input;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Keys of the client that reach the server (API 11): a Kunftig client has keys of its own in Controls (category
 * Kunftig) for the actions below, and reports the ones a plugin bound for that player, pressed and released, with the
 * movement keys held at that moment. Vanilla 1.7.10 only tells the server about clicks; a hack'n'slash needs more
 * (special, dodge, lock-on). Requires the Kunftig mod on the server and on the client.
 *
 * <pre>
 * public void onEnable() {
 *     KunftigInput.register(this);                           // start receiving input (once)
 * }
 * KunftigInput.bind(player, KunftigInput.SPECIAL, KunftigInput.LOCK_ON);   // e.g. when a blade comes to the hand
 * KunftigInput.bind(player);                                 // nothing (when it leaves)
 *
 * &#64;EventHandler
 * public void onInput(KunftigInputEvent event) {
 *     if (event.getAction().equals(KunftigInput.SPECIAL) &amp;&amp; event.isPressed() &amp;&amp; event.isBack()) { ... }
 * }
 * </pre>
 *
 * The client is never trusted: an action reaches {@link KunftigInputEvent} only when it is bound for that player, newer
 * than the last one, and within a rate limit (about 20 messages per second, 20 at once). Players without Kunftig never
 * send anything ({@link #sendsInput}); give them another way (a command, a click combination).
 */
public final class KunftigInput {

    /** A special move: a slash art, a skill (default key R). */
    public static final String SPECIAL = "especial";
    /** A second skill (default key F). */
    public static final String SKILL = "habilidade";
    /** A dodge or dash (default key Left Alt). */
    public static final String DODGE = "esquiva";
    /** Lock on a target (default key Z). */
    public static final String LOCK_ON = "travar";
    /**
     * Not a key: bound, the client reports every held key ({@link #getKeys}, {@link KunftigKeysEvent}) whenever they
     * change, the way SlashBlade's clients do while a blade is in the hand.
     */
    public static final String KEYS = "teclas";
    /** Held keys ({@link #getKeys}). */
    public static final int KEY_FORWARD = 1;
    public static final int KEY_BACK = 1 << 1;
    public static final int KEY_LEFT = 1 << 2;
    public static final int KEY_RIGHT = 1 << 3;
    public static final int KEY_JUMP = 1 << 4;
    public static final int KEY_SNEAK = 1 << 5;
    public static final int KEY_SPRINT = 1 << 6;
    /** The use button (right). */
    public static final int KEY_USE = 1 << 7;
    /** The attack button (left). */
    public static final int KEY_ATTACK = 1 << 8;
    /** The pick block button (middle). */
    public static final int KEY_PICK = 1 << 9;
    public static final int KEY_SPECIAL = 1 << 10;
    public static final int KEY_SKILL = 1 << 11;
    public static final int KEY_DODGE = 1 << 12;
    public static final int KEY_LOCK_ON = 1 << 13;

    /** Every action a Kunftig client has a key for. */
    public static final List<String> ACTIONS = Collections
        .unmodifiableList(Arrays.asList(SPECIAL, SKILL, DODGE, LOCK_ON));

    /** Implemented inside the mod; installed when the server starts. */
    public interface Provider {

        void register(Plugin plugin);

        void bind(Player player, Set<String> actions);

        Set<String> getBound(Player player);

        boolean isHeld(Player player, String action);

        int getKeys(Player player);

        long getLastPress(Player player, int key);

        long getTick();

        boolean sendsInput(Player player);
    }

    private static volatile Provider provider;

    private KunftigInput() {}

    public static boolean isAvailable() {
        return provider != null;
    }

    private static Provider provider() {
        Provider current = provider;
        if (current == null) {
            throw new IllegalStateException("KunftigInput is not available on this server");
        }
        return current;
    }

    /**
     * Starts receiving input on the server's behalf: Bukkit only hands plugin messages to a plugin, so one plugin must
     * lend itself (any of them; calling it from several plugins is fine, and if the one in use is disabled the next one
     * takes over). Call it in {@code onEnable}.
     */
    public static void register(Plugin plugin) {
        if (plugin == null) {
            throw new IllegalArgumentException("plugin required");
        }
        provider().register(plugin);
    }

    /**
     * The actions this player's client reports from now on, replacing those bound before (none: it reports nothing).
     * Keys held when an action is unbound are not released with an event.
     *
     * @throws IllegalArgumentException for a name that is neither one of {@link #ACTIONS} nor {@link #KEYS}
     */
    public static void bind(Player player, String... actions) {
        bind(player, Arrays.asList(actions == null ? new String[0] : actions));
    }

    /** {@link #bind(Player, String...)} with a collection. */
    public static void bind(Player player, Collection<String> actions) {
        if (player == null) {
            throw new IllegalArgumentException("player required");
        }
        Set<String> clean = new java.util.LinkedHashSet<>();
        if (actions != null) {
            for (String a : actions) {
                if (!ACTIONS.contains(a) && !KEYS.equals(a)) {
                    throw new IllegalArgumentException(
                        "unknown input action: " + a + " (one of " + ACTIONS + ", or " + KEYS + ")");
                }
                clean.add(a);
            }
        }
        provider().bind(player, Collections.unmodifiableSet(clean));
    }

    /** The actions bound for the player (empty when none). */
    public static Set<String> getBound(Player player) {
        return provider().getBound(player);
    }

    /** Whether the player holds a bound action's key down, as his client last said. */
    public static boolean isHeld(Player player, String action) {
        return provider().isHeld(player, action);
    }

    /** The keys the player holds ({@link #KEY_FORWARD} ...), while {@link #KEYS} is bound; 0 otherwise. */
    public static int getKeys(Player player) {
        return provider().getKeys(player);
    }

    /** The server tick ({@link #getTick}) {@code key} last went down, or -1. */
    public static long getLastPress(Player player, int key) {
        return provider().getLastPress(player, key);
    }

    /** Kunftig's server tick counter (the clock of {@link #getLastPress}). */
    public static long getTick() {
        return provider().getTick();
    }

    /** Whether this player's client can report input (Kunftig 9 or newer, listening to the input channel). */
    public static boolean sendsInput(Player player) {
        return provider().sendsInput(player);
    }

    /** Internal: called by the mod. */
    public static void install(Provider implementation) {
        provider = implementation;
    }
}
