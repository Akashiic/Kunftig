package com.akashic.kunftig.display;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

/** API 12: players walk on displays. */
class DeckTest {

    private static final double R = 0.3;
    private static final double H = 1.8;

    /**
     * A deck 8 wide and 20 long with its floor at y = 4, a wall 1 high along each side, a block 2 high across the
     * stern (z from -10 to -6), a crate and a beam overhead.
     */
    private static DeckShape ship() {
        return DeckShape.of(
            DeckShape.pack(
                new double[][] { { -4, 3.5, -10, 4, 4, 10 }, // floor
                    { -4, 4, -10, -3.75, 5, 10 }, { 3.75, 4, -10, 4, 5, 10 }, // bulwarks
                    { -4, 4, -10, 4, 6, -6 }, // the raised deck, solid
                    { -2, 4, 2, -1, 4.75, 3 }, // crate
                    { -4, 6.25, 6, 4, 6.75, 6.5 } })); // beam: 2.25 over the floor
    }

    /** Stairs done properly: three steps of 0.5 going up towards -z, on x 1..3. */
    private static DeckShape stairs() {
        return DeckShape.of(
            DeckShape.pack(
                new double[][] { { -4, 3.5, -10, 4, 4, 10 }, { -4, 4, -10, 4, 6, -6 }, { 1, 4, -4.5, 3, 4.5, -4 },
                    { 1, 4, -5, 3, 5, -4.5 }, { 1, 4, -5.5, 3, 5.5, -5 }, { 1, 4, -6, 3, 6, -5.5 } }));
    }

    /** A player as vanilla moves it: walking speed along the world, gravity, a jump. */
    private static final class Sim {

        final DeckWalker walker = new DeckWalker();
        final DeckShape shape;
        DeckPose pose;
        double x;
        double y;
        double z;
        double vy;
        boolean ground;

        Sim(DeckShape shape, DeckPose pose, double[] localFeet) {
            this.shape = shape;
            this.pose = pose;
            double[] w = pose.toWorld(localFeet[0], localFeet[1], localFeet[2]);
            x = w[0];
            y = w[1];
            z = w[2];
        }

        /** One tick: the player's own move ({@code dx, dz} in the world), then the display goes to {@code next}. */
        void tick(double dx, double dz, boolean jump, DeckPose next) {
            if (jump && ground) {
                vy = 0.42;
            }
            x += dx;
            z += dz;
            y += vy;
            vy = (vy - 0.08) * 0.98;
            walker.step(shape, pose, next, x, y, z, R, H, false);
            if (walker.moved) {
                x = walker.x;
                y = walker.y;
                z = walker.z;
            }
            ground = walker.grounded();
            if (ground && vy < 0 || walker.bumped && vy > 0) {
                vy = 0;
            }
            pose = next;
        }

        double[] local() {
            return pose.toLocal(x, y, z);
        }
    }

    private static DeckPose still() {
        return new DeckPose(100, 60, -40, 0, 0, 0);
    }

    /** Lands a player on the floor at a local point. */
    private static Sim standing(DeckShape shape, DeckPose pose, double lx, double lz) {
        Sim s = new Sim(shape, pose, new double[] { lx, 4.5, lz });
        for (int i = 0; i < 12; i++) {
            s.tick(0, 0, false, pose);
        }
        assertTrue(s.walker.grounded(), "landed");
        assertEquals(4, s.local()[1], 1e-9);
        return s;
    }

    @Test
    void poseTurnsLikeTheRenderer() {
        // yaw 90: the display's front (+Z) points west, as an entity's
        double[] w = new DeckPose(10, 20, 30, 90, 0, 0).toWorld(0, 0, 1);
        assertArrayEquals(new double[] { 9, 20, 30 }, w, 1e-9);
        // positive pitch tips the front down; positive roll lifts the +X side
        assertTrue(new DeckPose(0, 0, 0, 0, 20, 0).toWorld(0, 0, 1)[1] < 0);
        assertTrue(new DeckPose(0, 0, 0, 0, 0, 20).toWorld(1, 0, 0)[1] > 0);
        DeckPose p = new DeckPose(-3, 64, 12, 37, -11, 23);
        double[] l = p.toLocal(5, 70, -2);
        assertArrayEquals(new double[] { 5, 70, -2 }, p.toWorld(l[0], l[1], l[2]), 1e-9);
        double[] up = p.up();
        assertEquals(1, up[0] * up[0] + up[1] * up[1] + up[2] * up[2], 1e-12);
        double[] a = p.toWorld(0, 0, 0);
        double[] b = p.toWorld(up[0], up[1], up[2]);
        assertArrayEquals(new double[] { 0, 1, 0 }, new double[] { b[0] - a[0], b[1] - a[1], b[2] - a[2] }, 1e-9);
    }

    @Test
    void shapesPackAndTravel() {
        short[] packed = DeckShape.pack(new double[][] { { -1.5, 0, -2, 1.5, 0.25, 2 }, { 0, 0.25, 0, 0.5, 3, 0.5 } });
        DisplayWire.Message m = DisplayWire.read(DisplayWire.deck(7, packed));
        assertNotNull(m);
        assertEquals(DisplayWire.DECK, m.op);
        assertEquals(7, m.id);
        assertEquals(2, m.deck.size());
        assertArrayEquals(packed, m.deck.packed());
        assertEquals(Math.sqrt(0.25 + 9 + 0.25), m.deck.radius(), 1e-9);
        // no boxes takes the deck away
        assertTrue(DisplayWire.read(DisplayWire.deck(7, null)).deck.isEmpty());
        // malformed: a max below a min, a count that does not match
        assertNull(DeckShape.of(new short[] { 0, 0, 0, -1, 0, 0 }));
        assertNull(DeckShape.of(new short[5]));
        byte[] cut = DisplayWire.deck(7, packed);
        assertNull(DisplayWire.read(java.util.Arrays.copyOf(cut, cut.length - 3)));
        assertThrows(IllegalArgumentException.class, () -> DeckShape.pack(new double[][] { { 0, 0, 0, 2000, 1, 1 } }));
        assertThrows(
            IllegalArgumentException.class,
            () -> DeckShape.pack(new double[][] { { 0, 0, 0, 1, Double.NaN, 1 } }));
        assertThrows(IllegalArgumentException.class, () -> DeckShape.pack(new double[DeckShape.MAX_BOXES + 1][]));
    }

    @Test
    void trackerSendsTheDeckToNewViewersAndOnChange() {
        DisplayTracker<String> t = new DisplayTracker<>();
        DisplayTracker.State<String> d = t
            .create("w", 0, 64, 0, 0, "ns:navio", "", "parado", Collections.emptyList(), 48);
        short[] packed = DeckShape.pack(new double[][] { { -1, 0, -1, 1, 1, 1 } });
        d.setDeck(packed);
        List<DisplayTracker.Viewer<String>> viewers = new ArrayList<>();
        viewers.add(new DisplayTracker.Viewer<>("ana", "w", 1, 64, 1, true));
        List<DisplayTracker.Out<String>> out = t.update(viewers);
        assertEquals(2, out.size());
        assertEquals(DisplayWire.SHOW, out.get(0).data[0]);
        assertEquals(DisplayWire.DECK, out.get(1).data[0]);
        assertTrue(
            t.update(viewers)
                .isEmpty());
        d.setDeck(packed); // the same again: nothing to send
        assertTrue(
            t.update(viewers)
                .isEmpty());
        d.setDeck(null);
        out = t.update(viewers);
        assertEquals(1, out.size());
        assertTrue(DisplayWire.read(out.get(0).data).deck.isEmpty());
        assertThrows(IllegalArgumentException.class, () -> d.setDeck(new short[] { 1, 2, 3 }));
    }

    @Test
    void bonesTurnAndLateViewersGetThemWhereTheyAre() {
        DisplayWire.Message m = DisplayWire.read(DisplayWire.pose(4, "porta", 0, -95, 0, 12));
        assertEquals(DisplayWire.POSE, m.op);
        assertEquals("porta", m.bone);
        assertArrayEquals(new float[] { 0, -95, 0 }, m.turn, 0);
        assertEquals(12, m.ticks);
        assertNull(DisplayWire.read(DisplayWire.pose(4, "", 0, 0, 0, 0)));
        DisplayTracker<String> t = new DisplayTracker<>();
        DisplayTracker.State<String> d = t
            .create("w", 0, 64, 0, 0, "ns:navio", "", "parado", Collections.emptyList(), 48);
        List<DisplayTracker.Viewer<String>> viewers = new ArrayList<>();
        viewers.add(new DisplayTracker.Viewer<>("ana", "w", 1, 64, 1, true));
        t.update(viewers);
        d.setPose("porta", 0, 0, 0, 5); // already there: nothing
        assertTrue(
            t.update(viewers)
                .isEmpty());
        d.setPose("porta", 0, -95, 0, 12);
        List<DisplayTracker.Out<String>> out = t.update(viewers);
        assertEquals(1, out.size());
        assertEquals(12, DisplayWire.read(out.get(0).data).ticks);
        assertTrue(
            t.update(viewers)
                .isEmpty());
        viewers.add(new DisplayTracker.Viewer<>("bia", "w", 1, 64, 1, true));
        out = t.update(viewers);
        assertEquals(2, out.size()); // SHOW, then the door where it is, at once
        DisplayWire.Message late = DisplayWire.read(out.get(1).data);
        assertEquals(-95, late.turn[1], 0);
        assertEquals(0, late.ticks);
        assertArrayEquals(new float[] { 0, -95, 0 }, d.pose("porta"), 0);
    }

    @Test
    void variantChangesInPlace() {
        DisplayWire.Message m = DisplayWire.read(DisplayWire.variant(9, "pintado"));
        assertEquals(DisplayWire.VARIANT, m.op);
        assertEquals(9, m.id);
        assertEquals("pintado", m.variant);
        DisplayTracker<String> t = new DisplayTracker<>();
        DisplayTracker.State<String> d = t
            .create("w", 0, 64, 0, 0, "ns:navio", "cru", "parado", Collections.emptyList(), 48);
        List<DisplayTracker.Viewer<String>> viewers = new ArrayList<>();
        viewers.add(new DisplayTracker.Viewer<>("ana", "w", 1, 64, 1, true));
        t.update(viewers);
        d.setVariant("cru");
        assertTrue(
            t.update(viewers)
                .isEmpty());
        d.setVariant("pintado");
        List<DisplayTracker.Out<String>> out = t.update(viewers);
        assertEquals(1, out.size());
        assertEquals("pintado", DisplayWire.read(out.get(0).data).variant);
        // a late viewer gets it in the SHOW
        viewers.add(new DisplayTracker.Viewer<>("bia", "w", 1, 64, 1, true));
        out = t.update(viewers);
        assertEquals(1, out.size());
        assertEquals("pintado", DisplayWire.read(out.get(0).data).variant);
        d.setVariant(null);
        assertEquals(
            "",
            DisplayWire.read(
                t.update(viewers)
                    .get(0).data).variant);
    }

    @Test
    void standsStillOnADeckThatHeavesRollsPitchesTurnsAndSails() {
        DeckShape shape = ship();
        Sim s = standing(shape, still(), 2.5, 5);
        double[] spot = s.local();
        for (int i = 1; i <= 400; i++) {
            double t = i / 20.0;
            DeckPose next = new DeckPose(
                100 + 0.2 * i,
                60 + 1.2 * Math.sin(t * 1.3),
                -40 - 0.1 * i,
                (float) (i * 0.9),
                (float) (9 * Math.sin(t * 0.9)),
                (float) (22 * Math.sin(t * 1.1 + 1)));
            s.tick(0, 0, false, next);
            assertTrue(s.walker.grounded(), "tick " + i);
            // the same spot of the deck, to the hair: no sliding down the slope, no sinking
            assertArrayEquals(spot, s.local(), 1e-7, "tick " + i);
            assertEquals(0.9f, s.walker.turned, 1e-3f);
        }
    }

    @Test
    void fallsOntoADeckComingUpAndIsNotCaughtFromBelowOrBeside() {
        DeckShape shape = ship();
        // from 3 blocks above, the deck rising 0.3 a tick: lands, does not tunnel
        Sim s = new Sim(shape, still(), new double[] { 0, 7, 5 });
        DeckPose p = still();
        for (int i = 0; i < 30; i++) {
            p = new DeckPose(100, p.y + 0.3, -40, 0, 0, 0);
            s.tick(0, 0, false, p);
        }
        assertTrue(s.walker.grounded());
        assertEquals(4, s.local()[1], 1e-9);
        // under the floor (swimming inside the hull): passes
        Sim under = new Sim(shape, still(), new double[] { 0, 2, 5 });
        for (int i = 0; i < 10; i++) {
            under.tick(0, 0, false, still());
        }
        assertFalse(under.walker.aboard());
        // falling past the side, outside the hull
        Sim beside = new Sim(shape, still(), new double[] { 6, 8, 5 });
        for (int i = 0; i < 30; i++) {
            beside.tick(0, 0, false, still());
        }
        assertFalse(beside.walker.aboard());
        assertTrue(beside.y < 60);
    }

    @Test
    void wallsStopAndLetSlide() {
        DeckShape shape = ship();
        Sim s = standing(shape, still(), 2.5, 5);
        for (int i = 0; i < 40; i++) {
            s.tick(0.2, 0.05, false, still()); // into the +x bulwark, drifting forward
        }
        double[] l = s.local();
        assertEquals(3.75 - R, l[0], 1e-3); // stopped at the wall, a radius away
        assertEquals(5 + 40 * 0.05, l[2], 1e-6); // and slid along it
        assertTrue(s.walker.grounded());
        // the raised deck is a wall from the main deck (2 high), away from the steps
        Sim b = standing(shape, still(), -2, 0);
        for (int i = 0; i < 60; i++) {
            b.tick(0, -0.2, false, still());
        }
        assertEquals(-6 + R, b.local()[2], 1e-3);
        assertEquals(4, b.local()[1], 1e-9);
    }

    @Test
    void wallsHoldOnARollingDeck() {
        DeckShape shape = ship();
        Sim s = standing(shape, still(), 2.5, 5);
        for (int i = 1; i <= 200; i++) {
            DeckPose next = new DeckPose(100, 60, -40, 0, 0, (float) (20 * Math.sin(i / 14.0)));
            s.tick(0.15, 0, false, next);
            assertTrue(s.local()[0] <= 3.75 - R + 1e-3, "through the bulwark at tick " + i);
            assertTrue(s.walker.grounded(), "tick " + i);
        }
    }

    @Test
    void stepsUpAndDownStairsAndWalksOffTheUpperDeck() {
        DeckShape shape = stairs();
        Sim s = standing(shape, still(), 2, 0);
        for (int i = 0; i < 60; i++) {
            s.tick(0, -0.1, false, still());
            assertTrue(s.walker.grounded(), "up, tick " + i);
        }
        assertEquals(6, s.local()[1], 1e-9); // on the raised deck
        for (int i = 0; i < 60; i++) {
            s.tick(0, 0.1, false, still());
            assertTrue(s.walker.grounded(), "down, tick " + i);
        }
        assertEquals(4, s.local()[1], 1e-9);
        // off the edge of the raised deck where there are no stairs: a fall of 2, then the main deck
        Sim f = standing(shape, still(), -2, 0);
        f.y += 2.5;
        f.z -= 8; // put on the raised deck
        f.walker.reset();
        for (int i = 0; i < 12; i++) {
            f.tick(0, 0, false, still());
        }
        assertEquals(6, f.local()[1], 1e-9);
        boolean flew = false;
        for (int i = 0; i < 60; i++) {
            f.tick(0, 0.1, false, still());
            flew |= !f.walker.grounded();
        }
        assertTrue(flew);
        assertTrue(f.walker.grounded());
        assertEquals(4, f.local()[1], 1e-9);
    }

    @Test
    void jumpsAreCarriedAndLandOnCrates() {
        DeckShape shape = ship();
        Sim s = standing(shape, still(), 0, 0);
        DeckPose p = still();
        double top = 0;
        for (int i = 0; i < 40; i++) {
            p = new DeckPose(p.x + 0.5, p.y, p.z, 0, 0, 0); // sailing fast
            s.tick(0, 0, i == 0, p);
            top = Math.max(top, s.local()[1]);
        }
        assertTrue(top > 5.1 && top < 5.4, "jump height " + (top - 4));
        assertTrue(s.walker.grounded());
        assertEquals(0, s.local()[0], 1e-6); // came down where it went up
        // the crate (0.75 high) is a wall on foot and a floor after a jump
        Sim c = standing(shape, still(), -1.5, 0.5);
        for (int i = 0; i < 20; i++) {
            c.tick(0, 0.1, false, still());
        }
        assertEquals(2 - R, c.local()[2], 1e-3);
        for (int i = 0; i < 20; i++) {
            c.tick(0, i < 10 ? 0.1 : 0, i == 0, still());
        }
        assertEquals(4.75, c.local()[1], 1e-9);
        assertTrue(c.walker.grounded());
    }

    @Test
    void theHeadMeetsABeam() {
        DeckShape shape = ship();
        Sim s = standing(shape, still(), 0, 6.25); // under the beam: room to stand, not to jump
        double top = 0;
        for (int i = 0; i < 20; i++) {
            s.tick(0, 0, i == 0, still());
            top = Math.max(top, s.local()[1] + H);
        }
        assertEquals(6.25, top, 1e-9);
        assertTrue(s.walker.grounded());
    }

    @Test
    void walkingOffTheShipLetsGo() {
        DeckShape shape = DeckShape.of(DeckShape.pack(new double[][] { { -2, 3.5, -2, 2, 4, 2 } }));
        Sim s = standing(shape, still(), 0, 0);
        DeckPose p = still();
        for (int i = 0; i < 120; i++) {
            p = new DeckPose(p.x, p.y, p.z + 0.3, 0, 0, 0);
            s.tick(0.1, 0, false, p);
        }
        assertFalse(s.walker.aboard());
        assertTrue(s.z < p.z - 10, "left behind by the ship");
    }

    @Test
    void othersAreDrawnOnTheFloorUnderThem() {
        DeckShape shape = ship();
        DeckPose p = new DeckPose(100, 60, -40, 30, 5, 15);
        double[] w = p.toWorld(1, 4, 3);
        assertEquals(w[1], DeckWalker.surface(shape, p, w[0], w[1] + 0.2, w[2], R, 0.4), 1e-9);
        assertEquals(w[1], DeckWalker.surface(shape, p, w[0], w[1] - 0.3, w[2], R, 0.4), 1e-9);
        assertTrue(Double.isNaN(DeckWalker.surface(shape, p, w[0], w[1] + 1, w[2], R, 0.4)));
        assertTrue(Double.isNaN(DeckWalker.surface(shape, p, w[0] + 30, w[1], w[2], R, 0.4)));
    }
}
