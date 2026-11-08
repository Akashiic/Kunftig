package com.akashic.kunftig.rodmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** API 11: a katana's combo starts on the client at the click ({@code ao_atacar}); the server confirms it. */
class AttackPredictionTest {

    private static final String KATANA = "overkill:katana/basica";

    private static final String MANIFEST = "{\"_formato\": 1, \"modelos\": {\"katana/basica\": {"
        + "\"geo\": \"overkill:geo/katana.geo.json\", \"animacoes\": \"overkill:animations/katana.animation.json\","
        + "\"textura\": \"overkill:textures/katana.png\", \"itens\": [\"minecraft:golden_sword\"],"
        + "\"display\": {\"primeira_pessoa\": {\"rotation\": [0,0,0], \"translation\": [0,0,0], \"scale\": 1}},"
        + "\"estados\": {"
        + "\"parada\": {\"modelo\": \"animation.katana.parada\", \"laco\": true, \"ao_atacar\": \"golpe_1\"},"
        + "\"sacar\": {\"modelo\": \"animation.katana.sacar\", \"ao_atacar\": {\"estado\": \"iaido\"}},"
        + "\"iaido\": {\"modelo\": \"animation.katana.iaido\"},"
        + "\"golpe_1\": {\"modelo\": \"animation.katana.golpe_1\", \"ao_atacar\": {\"estado\": \"golpe_2\", \"de\": 4, \"ate\": 14}},"
        + "\"golpe_2\": {\"modelo\": \"animation.katana.golpe_2\", \"ao_atacar\": {\"estado\": \"golpe_3\", \"de\": 4}},"
        + "\"golpe_3\": {\"modelo\": \"animation.katana.golpe_3\"}"
        + "}}}}";

    /** Lengths in ticks (negative: loops) and the manifest's ao_atacar. */
    private static RodTimeline.Clips clips(RodModelDef rod) {
        Map<String, Integer> lengths = new HashMap<>();
        lengths.put("parada", -40);
        lengths.put("sacar", 10);
        lengths.put("iaido", 12);
        lengths.put("golpe_1", 16);
        lengths.put("golpe_2", 16);
        lengths.put("golpe_3", 20);
        return new RodTimeline.Clips() {

            @Override
            public boolean has(String state) {
                return lengths.containsKey(state);
            }

            @Override
            public boolean loops(String state) {
                return lengths.getOrDefault(state, 1) < 0;
            }

            @Override
            public int lengthTicks(String state) {
                return Math.abs(lengths.getOrDefault(state, 1));
            }

            @Override
            public String attack(String state, long elapsed) {
                RodModelDef.Clips c = rod.states.get(state);
                return c == null || c.attack == null || !c.attack.accepts(elapsed) ? null : c.attack.next;
            }
        };
    }

    private static RodModelDef katana() {
        RodModelIndex index = new RodModelIndex();
        index.add("overkill", MANIFEST, "overkill:" + RodModelIndex.PATH);
        assertEquals(Collections.emptyList(), index.errors());
        return index.models()
            .get(KATANA);
    }

    @Test
    void manifestReadsAttacks() {
        RodModelDef rod = katana();
        assertEquals("golpe_1", rod.states.get("parada").attack.next);
        assertEquals(-1, rod.states.get("parada").attack.to);
        RodModelDef.Attack second = rod.states.get("golpe_1").attack;
        assertEquals("golpe_2", second.next);
        assertFalse(second.accepts(3));
        assertTrue(second.accepts(4));
        assertTrue(second.accepts(14));
        assertFalse(second.accepts(15));
        assertNull(rod.states.get("golpe_3").attack);
    }

    @Test
    void badAttacksAreReported() {
        for (String bad : new String[] { "\"voar\"", "{\"estado\": \"golpe_1\", \"de\": 5, \"ate\": 2}",
            "{\"estado\": \"Golpe\"}", "{\"de\": 1}", "{\"estado\": \"golpe_1\", \"de\": 1.5}", "3" }) {
            RodModelIndex index = new RodModelIndex();
            index.add("overkill", MANIFEST.replace("\"ao_atacar\": \"golpe_1\"", "\"ao_atacar\": " + bad), "t");
            assertTrue(
                index.models()
                    .isEmpty(),
                bad);
            assertEquals(
                1,
                index.errors()
                    .size(),
                bad + " " + index.errors());
        }
    }

    @Test
    void aClickStartsTheComboAndTheServerConfirmsWithoutRestarting() {
        RodModelDef rod = katana();
        RodTimeline.Clips clips = clips(rod);
        RodTimeline t = new RodTimeline();
        t.observe(100, KATANA, false, clips); // first seen: idle
        assertEquals("parada", t.state());

        assertTrue(t.localAttack(105, clips));
        assertEquals("golpe_1", t.state());
        assertEquals(105, t.start());
        // the server's golpe_1 arrives 3 ticks later (it started 1 tick ago on the server): the same attack
        t.fromServer("golpe_1", 107, 108);
        assertEquals("golpe_1", t.state());
        assertEquals(105, t.start(), "confirmed: not restarted");
        assertTrue(
            t.pending()
                .isEmpty());

        // too early for the next attack (window from 4 ticks)
        assertFalse(t.localAttack(107, clips));
        assertEquals("golpe_1", t.state());
        // in the window, and faster than the round trip: two predictions waiting
        assertTrue(t.localAttack(110, clips));
        assertTrue(t.localAttack(115, clips));
        assertEquals("golpe_3", t.state());
        assertEquals(Arrays.asList("golpe_2", "golpe_3"), t.pending());
        t.fromServer("golpe_2", 111, 113);
        assertEquals("golpe_3", t.state(), "a later prediction goes on");
        t.fromServer("golpe_3", 116, 118);
        assertEquals("golpe_3", t.state());
        assertEquals(115, t.start());
        // the last attack has no follow-up; it ends and idles
        assertFalse(t.localAttack(120, clips));
        t.observe(135, KATANA, false, clips);
        assertEquals("parada", t.state());
        assertEquals(135, t.start());
    }

    @Test
    void theServerCorrectsWhatItDidNotAccept() {
        RodModelDef rod = katana();
        RodTimeline.Clips clips = clips(rod);
        RodTimeline t = new RodTimeline();
        t.observe(0, KATANA, false, clips);
        t.localAttack(10, clips);
        t.localAttack(16, clips); // golpe_2 predicted
        // the server only saw one attack in time and sends golpe_1, then nothing more
        t.fromServer("golpe_1", 11, 13);
        assertEquals("golpe_2", t.state(), "golpe_1 confirmed, golpe_2 still waits");
        // the server starts golpe_1 again (a new combo it counted from idle): not predicted, so it wins
        t.fromServer("golpe_1", 30, 31);
        assertEquals("golpe_1", t.state());
        assertEquals(30, t.start());
        assertTrue(
            t.pending()
                .isEmpty());
    }

    @Test
    void predictionsExpireAndAnotherItemForgetsThem() {
        RodModelDef rod = katana();
        RodTimeline.Clips clips = clips(rod);
        RodTimeline t = new RodTimeline();
        t.observe(0, KATANA, false, clips);
        t.localAttack(10, clips);
        // the confirmation never came; much later the server's golpe_1 is a new attack and restarts
        t.fromServer("golpe_1", 10 + RodTimeline.PREDICTION_TICKS + 5, 10 + RodTimeline.PREDICTION_TICKS + 6);
        assertEquals(10 + RodTimeline.PREDICTION_TICKS + 5, t.start());

        t.localAttack(100, clips);
        t.observe(101, null, false, clips);
        assertTrue(
            t.pending()
                .isEmpty());
        assertFalse(t.localAttack(102, clips), "nothing in the hand");
    }

    @Test
    void attackingWhileDrawingIsAnIaido() {
        RodModelDef rod = katana();
        RodTimeline.Clips clips = clips(rod);
        RodTimeline t = new RodTimeline();
        t.observe(0, KATANA, false, clips);
        t.observe(1, null, false, clips);
        t.observe(2, KATANA, false, clips);
        assertEquals("sacar", t.state());
        assertTrue(t.localAttack(4, clips));
        assertEquals("iaido", t.state());
    }
}
