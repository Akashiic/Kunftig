package com.akashic.kunftig.blade;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * What 3D Futura Engine is told about each player's blade so his model plays SlashBlade's animations: whether the
 * main hand holds a blade and which combo state plays, as external states (futura3d API 3,
 * {@code slashblade.held} and {@code slashblade.combo}). Only changes are sent; a new chain whose first state has the
 * name of the one before is cleared and set again, which restarts the model's clip. No Minecraft classes.
 */
public final class ModelStateFeed {

    public static final String HELD = "slashblade.held";
    public static final String COMBO = "slashblade.combo";
    public static final String MAIN_HAND = "mainhand";

    /** Where the states go (futura3d's {@code setExternalState}). */
    public interface Sink {

        void set(String key, String value);
    }

    private static final class Last {

        boolean held;
        String combo;
        int serial;
    }

    private final Map<Object, Last> last = new WeakHashMap<>();

    /**
     * @param player the player (a weak key)
     * @param held   whether his main hand holds a blade
     * @param combo  the combo state his chain started with while it plays, or null
     * @param serial changes with every chain started ({@code BladeClient.chainSerial})
     */
    public void update(Object player, boolean held, String combo, int serial, Sink sink) {
        Last l = last.get(player);
        if (l == null) {
            l = new Last();
            last.put(player, l);
        }
        if (!held) {
            if (l.held) {
                sink.set(HELD, null);
                l.held = false;
            }
            if (l.combo != null) {
                sink.set(COMBO, null);
                l.combo = null;
            }
            return;
        }
        if (!l.held) {
            sink.set(HELD, MAIN_HAND);
            l.held = true;
        }
        if (combo == null) {
            if (l.combo != null) {
                sink.set(COMBO, null);
                l.combo = null;
            }
            return;
        }
        if (combo.equals(l.combo) && serial != l.serial) {
            sink.set(COMBO, null); // the same combo again: cleared and set, so the clip starts over
            l.combo = null;
        }
        if (!combo.equals(l.combo)) {
            sink.set(COMBO, combo);
            l.combo = combo;
        }
        l.serial = serial;
    }

    /** Forgets every player (a new world). */
    public void clear() {
        last.clear();
    }
}
