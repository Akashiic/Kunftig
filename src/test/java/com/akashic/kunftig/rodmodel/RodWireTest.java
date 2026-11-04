package com.akashic.kunftig.rodmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class RodWireTest {

    @Test
    void roundTrips() {
        RodWire.Message s = RodWire.read(RodWire.state(42, "briga", 300, 0.456F));
        assertEquals(RodWire.STATE, s.op);
        assertEquals(42, s.entityId);
        assertEquals("briga", s.state);
        assertEquals(255, s.elapsedTicks);
        assertEquals(0.46F, s.tension, 1e-6);
        RodWire.Message t = RodWire.read(RodWire.tension(-7, 2));
        assertEquals(RodWire.TENSION, t.op);
        assertEquals(-7, t.entityId);
        assertNull(t.state);
        assertEquals(1F, t.tension);
        assertTrue(RodWire.CHANNEL.length() <= 16);
    }

    @Test
    void theApiSpeaksTheSameStates() {
        assertEquals(RodWire.STATE_NAME.pattern(), com.akashic.kunftig.api.bukkit.rod.KunftigRods.STATE_NAME.pattern());
        assertEquals(RodModelDef.IDLE, com.akashic.kunftig.api.bukkit.rod.KunftigRods.IDLE);
        assertEquals(RodModelDef.DRAW, com.akashic.kunftig.api.bukkit.rod.KunftigRods.DRAW);
        assertEquals(RodModelDef.CAST, com.akashic.kunftig.api.bukkit.rod.KunftigRods.CAST);
    }

    @Test
    void ignoresWhatItDoesNotUnderstand() {
        assertNull(RodWire.read(null));
        assertNull(RodWire.read(new byte[] { 0, 0, 0 }));
        assertNull(RodWire.read(new byte[] { 9, 0, 0, 0, 1, 0 }));
        byte[] good = RodWire.state(1, "parada", 0, 0);
        assertNull(RodWire.read(Arrays.copyOf(good, good.length - 1)));
        assertThrows(IllegalArgumentException.class, () -> RodWire.state(1, "Briga!", 0, 0));
        assertEquals(0, RodWire.quantize(Float.NaN));
        assertEquals(0, RodWire.quantize(-1));
    }

    private static final class Conn {

        final String name;

        Conn(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private static List<String> sent(List<RodStateTracker.Out<Conn>> out) {
        List<String> s = new ArrayList<>();
        for (RodStateTracker.Out<Conn> o : out) {
            RodWire.Message m = RodWire.read(o.data);
            s.add(
                o.connection + ":"
                    + (m.op == RodWire.STATE ? m.state + "@" + m.elapsedTicks : "t")
                    + "/"
                    + Math.round(m.tension * 100));
        }
        return s;
    }

    @Test
    void trackerTellsTheAnglerAndThoseNearbyOnce() {
        RodStateTracker<String, Conn> tracker = new RodStateTracker<>();
        Conn angler = new Conn("a");
        Conn near = new Conn("n");
        Conn far = new Conn("f");
        Conn vanilla = new Conn("v");
        Map<String, RodStateTracker.Angler> anglers = new HashMap<>();
        anglers.put("A", new RodStateTracker.Angler(7, "w", 0, 64, 0));
        List<RodStateTracker.Viewer<Conn>> viewers = new ArrayList<>(
            Arrays.asList(
                new RodStateTracker.Viewer<>(angler, "w", 0, 64, 0, true),
                new RodStateTracker.Viewer<>(near, "w", 50, 64, 0, true),
                new RodStateTracker.Viewer<>(far, "w", 200, 64, 0, true),
                new RodStateTracker.Viewer<>(vanilla, "w", 1, 64, 0, false)));

        // Nothing set: nothing sent.
        assertTrue(
            tracker.update(viewers, anglers, 0)
                .isEmpty());
        tracker.setState("A", "briga", 10);
        tracker.setTension("A", 0.3F);
        assertEquals(Arrays.asList("a:briga@0/30", "n:briga@0/30"), sent(tracker.update(viewers, anglers, 10)));
        assertTrue(
            tracker.update(viewers, anglers, 11)
                .isEmpty());
        // Tension: once per tick, only when it changed in hundredths.
        tracker.setTension("A", 0.5F);
        tracker.setTension("A", 0.6F);
        assertEquals(Arrays.asList("a:t/60", "n:t/60"), sent(tracker.update(viewers, anglers, 12)));
        tracker.setTension("A", 0.601F);
        assertTrue(
            tracker.update(viewers, anglers, 13)
                .isEmpty());
        // Someone walking in hears the whole state, with how long it has been running.
        viewers.set(2, new RodStateTracker.Viewer<>(far, "w", 90, 64, 0, true));
        assertEquals(Collections.singletonList("f:briga@4/60"), sent(tracker.update(viewers, anglers, 14)));
        // A new state goes to everyone who hears.
        tracker.setState("A", "captura", 20);
        assertEquals(
            Arrays.asList("a:captura@0/60", "n:captura@0/60", "f:captura@0/60"),
            sent(tracker.update(viewers, anglers, 20)));
    }

    @Test
    void trackerForgetsAnglersAndRetellsAfterRespawn() {
        RodStateTracker<String, Conn> tracker = new RodStateTracker<>();
        Conn me = new Conn("me");
        Map<String, RodStateTracker.Angler> anglers = new HashMap<>();
        anglers.put("A", new RodStateTracker.Angler(7, "w", 0, 0, 0));
        List<RodStateTracker.Viewer<Conn>> viewers = Collections
            .singletonList(new RodStateTracker.Viewer<>(me, "w", 0, 0, 0, true));
        tracker.setState("A", "mordida", 0);
        assertEquals(
            1,
            tracker.update(viewers, anglers, 0)
                .size());
        // Respawned: a new entity id for every client.
        anglers.put("A", new RodStateTracker.Angler(8, "w", 0, 0, 0));
        assertEquals(Collections.singletonList("me:mordida@5/0"), sent(tracker.update(viewers, anglers, 5)));
        // Offline: forgotten.
        anglers.clear();
        assertTrue(
            tracker.update(viewers, anglers, 6)
                .isEmpty());
        assertEquals(0, tracker.size());
        assertNull(tracker.state("A"));
        assertThrows(IllegalArgumentException.class, () -> tracker.setState("A", "", 0));
    }
}
