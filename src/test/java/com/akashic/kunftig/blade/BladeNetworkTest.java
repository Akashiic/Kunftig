package com.akashic.kunftig.blade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class BladeNetworkTest {

    private static final BladeTimeline.Segment A1 = new BladeTimeline.Segment(1, 10, false, 300);
    private static final BladeTimeline.Segment STANDBY = new BladeTimeline.Segment(0, 1, true, 1033);

    @Test
    void stateRoundTrips() {
        BladeWire.Message m = BladeWire
            .read(BladeWire.state(42, 7, Arrays.asList(A1), STANDBY, new BladeWire.Body(1100, 1132, false), 7, true));
        assertEquals(BladeWire.STATE, m.op);
        assertEquals(42, m.entityId);
        assertEquals(7, m.elapsedTicks);
        assertEquals(1, m.chain.size());
        assertEquals(300, m.chain.get(0).durationMs);
        assertTrue(STANDBY.loop == m.idle.loop && m.idle.end == 1);
        assertEquals(1100, m.body.start);
        assertFalse(m.body.blendLegs);
        assertEquals(21, m.body.spanTicks(), "(1132-1100)*2/3");
        assertTrue(m.charged);
        BladeWire.Message c = BladeWire.read(BladeWire.charge(42, false));
        assertEquals(BladeWire.CHARGE, c.op);
        assertFalse(c.charged);
        assertNull(BladeWire.read(new byte[] { 9 }));
        BladeWire.Message noBody = BladeWire
            .read(BladeWire.state(1, 0, Collections.<BladeTimeline.Segment>emptyList(), STANDBY, null, 0, false));
        assertNull(noBody.body);
    }

    @Test
    void lateViewersJoinMidChain() {
        BladeStateTracker<String, String> t = new BladeStateTracker<>();
        Map<String, BladeStateTracker.Holder> holders = Collections
            .singletonMap("ana", new BladeStateTracker.Holder(5, "w", 0, 64, 0));
        List<BladeStateTracker.Viewer<String>> ana = Collections
            .singletonList(new BladeStateTracker.Viewer<>("ana", "w", 0, 64, 0, true));
        t.play("ana", Arrays.asList(A1), STANDBY, null, 100);
        List<BladeStateTracker.Out<String>> out = t.update(ana, holders, 100);
        assertEquals(1, out.size());
        assertEquals(0, BladeWire.read(out.get(0).data).elapsedTicks);
        assertTrue(
            t.update(ana, holders, 101)
                .isEmpty());
        List<BladeStateTracker.Viewer<String>> both = Arrays
            .asList(ana.get(0), new BladeStateTracker.Viewer<>("bia", "w", 10, 64, 0, true));
        out = t.update(both, holders, 104);
        assertEquals(1, out.size());
        assertEquals("bia", out.get(0).connection);
        assertEquals(4, BladeWire.read(out.get(0).data).elapsedTicks);
        // far away: not told
        List<BladeStateTracker.Viewer<String>> far = Arrays
            .asList(ana.get(0), new BladeStateTracker.Viewer<>("caio", "w", 500, 64, 0, true));
        t.play("ana", Arrays.asList(A1), STANDBY, null, 105);
        out = t.update(far, holders, 105);
        assertEquals(1, out.size(), "only ana, who is herself in range");
        t.setCharged("ana", true);
        out = t.update(ana, holders, 106);
        assertEquals(BladeWire.CHARGE, BladeWire.read(out.get(0).data).op);
        assertEquals(
            0,
            t.update(ana, Collections.<String, BladeStateTracker.Holder>emptyMap(), 107)
                .size());
        assertEquals(0, t.size(), "a holder who left is forgotten");
    }

    @Test
    void indexReadsBlades() {
        BladeIndex i = new BladeIndex();
        i.add(
            "overkill",
            "{\"_formato\":1,\"movimento_lamina\":\"combostate/motion.vmd\",\"laminas\":{"
                + "\"yamato\":{\"obj\":\"model/yamato.obj\",\"textura\":\"overkill:model/yamato.png\",\"porte\":\"KATANA\",\"itens\":[\"minecraft:golden_sword\"],\"quebrada\":\"overkill:broken\"},"
                + "\"ruim\":{\"obj\":\"x.obj\"},"
                + "\"porte\":{\"obj\":\"a.obj\",\"textura\":\"a.png\",\"porte\":\"VOAR\"}}}",
            "t");
        BladeIndex.Blade y = i.get("overkill:yamato");
        assertEquals("overkill:model/yamato.obj", y.obj);
        assertEquals("overkill:combostate/motion.vmd", y.bladeMotion);
        assertNull(y.playerMotion);
        assertEquals(BladeIndex.Carry.KATANA, y.carry);
        assertEquals("overkill:broken", y.brokenKey);
        assertEquals(
            1,
            i.blades()
                .size());
        assertEquals(
            2,
            i.errors()
                .size(),
            i.errors()
                .toString());
        assertTrue(
            i.items()
                .contains("minecraft:golden_sword"));
        BladeIndex bad = new BladeIndex();
        bad.add("x", "{\"_formato\":2}", "t");
        assertEquals(
            1,
            bad.errors()
                .size());
    }
}
