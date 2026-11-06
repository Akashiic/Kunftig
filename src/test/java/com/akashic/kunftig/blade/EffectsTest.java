package com.akashic.kunftig.blade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

/** SlashBlade's effects without Minecraft: the wire, the concentration rank and the projectile motion. */
class EffectsTest {

    @Test
    void everyMessageRoundTrips() {
        EffectWire.JudgementCut j = (EffectWire.JudgementCut) EffectWire
            .read(EffectWire.judgementCut(new EffectWire.JudgementCut("ok", 1, 2, 3, 0, 0, 0xFF3333FF, 10)));
        assertEquals(0x3333FF, j.color);
        assertEquals(10, j.lifetime);
        assertEquals(2, j.y, 0);

        EffectWire.Projectile p = (EffectWire.Projectile) EffectWire.read(
            EffectWire.projectile(
                new EffectWire.Projectile(
                    "ok",
                    "espada",
                    7,
                    EffectWire.KIND_SWORD,
                    1,
                    2,
                    3,
                    0,
                    0,
                    3,
                    0,
                    0,
                    45,
                    0x123456,
                    0)));
        assertEquals("espada", p.effect);
        assertEquals(7, p.id);
        assertEquals(EffectWire.KIND_SWORD, p.kind);
        assertEquals(3, p.vz, 0);
        assertEquals(45, p.roll, 0);

        EffectWire.Move m = (EffectWire.Move) EffectWire
            .read(EffectWire.move(new EffectWire.Move(7, 4, 5, 6, 0.5, 0, 0, 90, -10)));
        assertEquals(7, m.id);
        assertEquals(0.5, m.vx, 0);
        assertEquals(-10, m.pitch, 0);

        EffectWire.Stick s = (EffectWire.Stick) EffectWire.read(EffectWire.stick(new EffectWire.Stick(7, 42, 200)));
        assertEquals(42, s.entityId);
        assertEquals(200, s.offsetYaw, 0);

        EffectWire.Remove r = (EffectWire.Remove) EffectWire.read(EffectWire.remove(new EffectWire.Remove(7, true)));
        assertTrue(r.burst);

        EffectWire.Lock l = (EffectWire.Lock) EffectWire.read(EffectWire.lock(new EffectWire.Lock("ok", -1, 0x3333FF)));
        assertEquals(-1, l.entityId);

        EffectWire.Rank k = (EffectWire.Rank) EffectWire.read(EffectWire.rank(new EffectWire.Rank("ok", 5000)));
        assertEquals(ConcentrationRank.MAX_POINTS, k.points, "clamped");
    }

    @Test
    void malformedMessagesAreDropped() {
        byte[] good = EffectWire.stick(new EffectWire.Stick(1, 2, 3));
        assertNull(EffectWire.read(Arrays.copyOf(good, good.length - 1)), "truncated");
        assertNull(EffectWire.read(Arrays.copyOf(good, good.length + 1)), "trailing bytes");
        assertNull(EffectWire.read(new byte[] { 99, 0, 0 }), "unknown operation");
        assertNull(
            EffectWire.read(EffectWire.move(new EffectWire.Move(1, Double.NaN, 0, 0, 0, 0, 0, 0, 0))),
            "not finite");
        byte[] drive = EffectWire.projectile(
            new EffectWire.Projectile("ok", "onda", 1, EffectWire.KIND_DRIVE, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 10));
        int kindAt = 1 + 2 + 2 + 2 + 4 + 4; // op, "ok", "onda", id
        drive[kindAt] = 9;
        assertNull(EffectWire.read(drive), "unknown kind");
        assertThrows(
            IllegalArgumentException.class,
            () -> EffectWire.lock(new EffectWire.Lock("Not Valid", 1, 0)),
            "bad namespace");
        assertInstanceOf(EffectWire.Slash.class, EffectWire.read(EffectWire.slash(slash())));
    }

    private static EffectWire.Slash slash() {
        return new EffectWire.Slash("ok", 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 10);
    }

    @Test
    void rankDecaysToTheFloorOfItsLevelAsSlashBlades() {
        ConcentrationRank r = new ConcentrationRank();
        r.sync(1200 + 150, 1000); // A, half way
        assertEquals(4, r.rank(1000));
        assertEquals(0.5F, r.progress(1000), 1e-6);
        assertEquals(1350 - 100, r.points(1100), "one point per tick");
        assertEquals(1200, r.points(5000), "never below its level's floor");
        assertEquals(4, r.rank(100000));

        r.sync(1700, 2000); // SSS
        assertEquals(7, r.rank(2000));
        assertEquals((1700 / 300F - 5.5F) / 0.5F, r.progress(2000), 1e-6, "SSS spans half a level");
        assertEquals(1500, r.points(100000), "S, SS and SSS fall back to S");
        assertEquals(5, r.rank(100000));

        r.sync(1580, 3000); // SS
        assertEquals(6, r.rank(3000));
        assertEquals((1580 / 300F - 5.25F) / 0.25F, r.progress(3000), 1e-5);

        r.reset();
        r.sync(299, 10);
        assertEquals(0, r.rank(10));
        assertFalse(r.visible(10), "no HUD without a rank");
    }

    @Test
    void rankHudTimesAsSlashBlades() {
        ConcentrationRank r = new ConcentrationRank();
        r.sync(330, 100); // NONE -> D: a rise
        assertTrue(r.showText(100));
        assertTrue(r.showText(119));
        assertFalse(r.showText(120));
        assertTrue(r.visible(219));
        assertFalse(r.visible(220), "6 seconds after the last change");
        r.sync(300, 130); // lower: no rise
        assertFalse(r.showText(131));
        r.add(-300, 140); // hurt: a whole level
        assertEquals(0, r.points(140));
        r.add(30, 150);
        assertEquals(30, r.points(150));
    }

    @Test
    void projectilesFlyLikeSummonedSwords() {
        EffectProjectile p = new EffectProjectile(
            new EffectWire.Projectile("ok", "espada", 1, EffectWire.KIND_SWORD, 0, 64, 0, 0, 0, 3, 0, 0, 0, 0, 0));
        p.tick(null);
        assertEquals(3, p.pos[2], 1e-9);
        assertEquals(2.97, p.motion[2], 1e-9, "x 0.99 per tick");
        assertEquals(0, p.yaw, 0);
        p.tick(null);
        assertEquals(5.97, p.pos[2], 1e-9);
        assertEquals(3, p.prevPos[2], 1e-9);

        // a sword turned by a MOVE eases a fifth of the way toward its flight per tick
        p.move(new EffectWire.Move(1, 0, 64, 0, 1, 0, 0, 0, 0));
        p.tick(null);
        assertEquals(0.2F * 90, p.yaw, 1e-4);

        // lodging: 0.05 short of the hit, then still
        EffectProjectile.BlockClip wall = (from, to) -> to[0] > 10 ? new double[] { 10, from[1], from[2] } : null;
        EffectProjectile s = new EffectProjectile(
            new EffectWire.Projectile("ok", "espada", 2, EffectWire.KIND_SWORD, 9, 64, 0, 3, 0, 0, 90, 0, 0, 0, 0));
        s.tick(wall);
        assertTrue(s.inGround);
        assertEquals(9 - 0.05, s.pos[0], 1e-9);
        s.tick(wall);
        assertEquals(9 - 0.05, s.pos[0], 1e-9);
        assertFalse(s.dead);

        // a drive ends at the block, or after its lifetime
        EffectProjectile d = new EffectProjectile(
            new EffectWire.Projectile("ok", "onda", 3, EffectWire.KIND_DRIVE, 9, 64, 0, 3, 0, 0, 90, 0, 0, 0, 10));
        d.tick(wall);
        assertTrue(d.dead);
        EffectProjectile e = new EffectProjectile(
            new EffectWire.Projectile("ok", "onda", 4, EffectWire.KIND_DRIVE, 0, 64, 0, 1, 0, 0, 90, 0, 0, 0, 10));
        assertEquals(0.75 - 1, -0.25, 0); // SlashBlade's fade: max(0, 0.75 - t^4)
        assertEquals(0.75, e.driveAlpha(), 1e-9);
        for (int i = 0; i < 10; i++) {
            e.tick(null);
        }
        assertFalse(e.dead);
        assertEquals(0, e.driveAlpha(), 0);
        e.tick(null);
        assertTrue(e.dead);
    }

    @Test
    void stuckSwordsWaitAndForgottenOnesExpire() {
        EffectProjectile p = new EffectProjectile(
            new EffectWire.Projectile("ok", "espada", 1, EffectWire.KIND_SWORD, 0, 64, 0, 0, 0, 3, 0, 0, 0, 0, 0));
        p.stick(new EffectWire.Stick(1, 5, 30));
        p.tick(null);
        assertEquals(0, p.pos[2], 0, "the client places stuck swords on their entity");
        for (int i = 0; i < EffectProjectile.MAX_AGE; i++) {
            p.tick(null);
        }
        assertTrue(p.dead);
    }

    @Test
    void indexReadsTheEffectNames() {
        BladeIndex i = new BladeIndex();
        i.add(
            "ok",
            "{\"_formato\":1,\"movimento_lamina\":\"m.vmd\",\"laminas\":{},\"efeitos\":{"
                + "\"julgamento\":{\"obj\":\"model/util/slashdim.obj\",\"textura\":\"model/util/slashdim.png\"},"
                + "\"rank\":{\"textura\":\"textures/gui/rank.png\"},"
                + "\"espada\":{\"textura\":\"model/util/ss.png\"}}}",
            "t");
        assertNotNull(i.effect("ok", BladeIndex.JUDGEMENT_CUT));
        assertEquals("ok:textures/gui/rank.png", i.effect("ok", BladeIndex.RANK).texture);
        assertNull(i.effect("ok", BladeIndex.RANK).obj);
        assertNull(i.effect("ok", BladeIndex.SWORD), "a model effect needs its obj");
        assertEquals(
            1,
            i.errors()
                .size(),
            i.errors()
                .toString());
    }
}
