package com.akashic.kunftig.blade;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The names of SlashBlade's combo states by the first frame of their blade motion, so a player model can play the
 * animation it carries for each ({@code slashblade:combo_a1} in a YSM-style model of 3D Futura Engine). The server
 * sends frame spans, not names ({@link BladeWire}): a chain's first state names the whole chain (SlashBlade's
 * {@code combo_a1} times out into {@code combo_a1_end} and {@code combo_a1_end2}; the model's {@code combo_a1} clip
 * covers all of them). No Minecraft classes.
 *
 * <p>
 * {@link #DEFAULTS} follows SlashBlade: Resharped's combo states in its {@code combostate/motion.vmd} (each family in
 * its block of a hundred frames). A resource pack with another motion names its own in {@code blades.json}
 * ({@code "combos": {"combo_a1": 1, ...}}, see {@link BladeIndex}); a pack's names replace these for its motion.
 */
public final class SlashBladeCombos {

    /**
     * First frame of each root combo state in SlashBlade: Resharped's motion. {@code combo_a1} (1), {@code combo_a2}
     * (100), {@code combo_a3} (200), {@code combo_a4} (500), {@code aerial_rave_a1} (1100), {@code aerial_rave_b3}
     * (1400) and {@code judgement_cut} (1900) are the spans Kunftig's own harness compares with SlashBlade; the others
     * follow the same layout and are to be checked against SlashBlade's registry.
     */
    public static final Map<Integer, String> DEFAULTS;

    static {
        Map<Integer, String> m = new LinkedHashMap<>();
        m.put(1, "combo_a1");
        m.put(100, "combo_a2");
        m.put(200, "combo_a3");
        m.put(400, "combo_c");
        m.put(500, "combo_a4");
        m.put(700, "combo_b1");
        m.put(800, "combo_a4ex");
        m.put(900, "combo_a5ex");
        m.put(1100, "aerial_rave_a1");
        m.put(1200, "aerial_rave_a2");
        m.put(1300, "aerial_rave_a3");
        m.put(1400, "aerial_rave_b3");
        m.put(1500, "aerial_rave_b4");
        m.put(1600, "upperslash");
        m.put(1700, "upperslash_jump");
        m.put(1900, "judgement_cut");
        m.put(2000, "rapid_slash");
        m.put(2100, "rising_star");
        m.put(2200, "void_slash");
        DEFAULTS = Collections.unmodifiableMap(m);
    }

    private SlashBladeCombos() {}

    /**
     * The combo state a chain starting at {@code startFrame} plays, from the pack's table when it has one (it replaces
     * the defaults), else {@link #DEFAULTS}; null when unnamed. Upstream's air rule: SlashBlade does not tell the
     * Judgement Cut in the air apart, YSM does ({@code judgement_cut_slash_air}).
     *
     * @param pack     the pack's names for this motion, or null
     * @param onGround whether the player stands on the ground
     */
    public static String name(Map<Integer, String> pack, int startFrame, boolean onGround) {
        Map<Integer, String> table = pack != null && !pack.isEmpty() ? pack : DEFAULTS;
        String name = table.get(startFrame);
        if (name == null) {
            return null;
        }
        if (!onGround) {
            if (name.equals("judgement_cut")) {
                return "judgement_cut_slash_air";
            }
            if (name.equals("judgement_cut_slash_just2")) {
                return "judgement_cut_slash_air_just2";
            }
        }
        return name;
    }

    /** A combo name a pack may declare: {@code [a-z0-9_]} with an optional {@code namespace:}, at most 64. */
    public static boolean validName(String name) {
        return name != null && name.matches("([a-z0-9_]{1,32}:)?[a-z0-9_]{1,64}");
    }
}
