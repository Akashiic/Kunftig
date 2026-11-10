package com.akashic.kunftig.blade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/** Combo names for the player models of 3D Futura Engine: from the chain's first frame to the states it is told. */
class ModelCombosTest {

    @Test
    void slashBladesNamesByFirstFrame() {
        assertEquals("combo_a1", SlashBladeCombos.name(null, 1, true));
        assertEquals("combo_a2", SlashBladeCombos.name(null, 100, true));
        assertEquals("combo_a3", SlashBladeCombos.name(null, 200, true));
        assertEquals("aerial_rave_a1", SlashBladeCombos.name(null, 1100, false));
        assertEquals("judgement_cut", SlashBladeCombos.name(null, 1900, true));
        assertEquals("judgement_cut_slash_air", SlashBladeCombos.name(null, 1900, false), "upstream's air rule");
        assertNull(SlashBladeCombos.name(null, 10, true), "combo_a1_end: not the first state of a chain");
        assertNull(SlashBladeCombos.name(null, 0, true), "standby");
        assertTrue(SlashBladeCombos.validName("combo_a1"));
        assertTrue(SlashBladeCombos.validName("slashblade_addon:fire_spiral"));
        assertFalse(SlashBladeCombos.validName("Combo"));
        assertFalse(SlashBladeCombos.validName("a b"));
    }

    @Test
    void aPackNamesItsOwnMotion() {
        BladeIndex i = new BladeIndex();
        i.add(
            "overkill",
            "{\"_formato\":1,\"movimento_lamina\":\"combostate/motion.vmd\","
                + "\"combos\":{\"combo_a1\":1,\"slashblade_addon:fire_spiral\":3000,\"Ruim\":5,\"frac\":2.5},"
                + "\"laminas\":{\"yamato\":{\"obj\":\"y.obj\",\"textura\":\"y.png\",\"itens\":[\"minecraft:golden_sword\"]},"
                + "\"outra\":{\"obj\":\"y.obj\",\"textura\":\"y.png\",\"movimento_lamina\":\"other.vmd\"}}}",
            "t");
        assertEquals(
            2,
            i.errors()
                .size(),
            i.errors()
                .toString());
        BladeIndex.Blade y = i.get("overkill:yamato");
        assertEquals("combo_a1", i.comboName(y, 1, true));
        assertEquals("slashblade_addon:fire_spiral", i.comboName(y, 3000, true));
        assertNull(i.comboName(y, 100, true), "the pack's table replaces SlashBlade's");
        // a blade with its own motion and no table: SlashBlade's names
        assertEquals("combo_a2", i.comboName(i.get("overkill:outra"), 100, true));
        assertEquals("combo_a2", i.comboName(null, 100, true));
        BladeIndex bad = new BladeIndex();
        bad.add("x", "{\"_formato\":1,\"movimento_lamina\":\"m.vmd\",\"combos\":[1],\"laminas\":{}}", "t");
        assertEquals(
            1,
            bad.errors()
                .size());
    }

    @Test
    void theFeedSendsOnlyChanges() {
        ModelStateFeed feed = new ModelStateFeed();
        Object p = new Object();
        List<String> calls = new ArrayList<>();
        ModelStateFeed.Sink sink = (k, v) -> calls.add(k + "=" + v);
        feed.update(p, false, null, 0, sink);
        assertTrue(calls.isEmpty(), "nothing held, nothing said");
        feed.update(p, true, null, 0, sink);
        feed.update(p, true, null, 0, sink);
        assertEquals(java.util.Arrays.asList("slashblade.held=mainhand"), calls);
        calls.clear();
        feed.update(p, true, "combo_a1", 1, sink);
        feed.update(p, true, "combo_a1", 1, sink);
        assertEquals(java.util.Arrays.asList("slashblade.combo=combo_a1"), calls);
        calls.clear();
        // a new chain with the same first state: cleared and set again (the model's clip restarts)
        feed.update(p, true, "combo_a1", 2, sink);
        assertEquals(java.util.Arrays.asList("slashblade.combo=null", "slashblade.combo=combo_a1"), calls);
        calls.clear();
        // another combo: just set
        feed.update(p, true, "combo_a2", 3, sink);
        assertEquals(java.util.Arrays.asList("slashblade.combo=combo_a2"), calls);
        calls.clear();
        // the chain is over
        feed.update(p, true, null, 3, sink);
        assertEquals(java.util.Arrays.asList("slashblade.combo=null"), calls);
        calls.clear();
        // the blade put away mid-combo
        feed.update(p, true, "combo_a1", 4, sink);
        calls.clear();
        feed.update(p, false, "combo_a1", 4, sink);
        assertEquals(java.util.Arrays.asList("slashblade.held=null", "slashblade.combo=null"), calls);
        // players are kept apart
        calls.clear();
        feed.update(new Object(), true, null, 0, sink);
        assertEquals(java.util.Arrays.asList("slashblade.held=mainhand"), calls);
    }
}
