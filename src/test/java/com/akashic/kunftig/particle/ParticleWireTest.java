package com.akashic.kunftig.particle;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.akashic.kunftig.api.particle.KunftigParticle;
import com.akashic.kunftig.api.particle.KunftigParticle.DataKind;

class ParticleWireTest {

    private static ParticlePlan plan(KunftigParticle particle, int count, double offset, double speed, int color) {
        return new ParticlePlan(
            particle,
            10,
            64,
            -5,
            count,
            offset,
            offset,
            offset,
            speed,
            color,
            -1,
            0,
            new Random(7));
    }

    @Test
    void everyParticleRoundTripsWithAndWithoutColorWithinTheNameLimit() {
        for (KunftigParticle particle : KunftigParticle.values()) {
            for (int color : new int[] { 0, 0xFF000000, 0xFFFFFFFF, 0xFF8E6D14 }) {
                String name = ParticleWire.encode(particle, color);
                assertTrue(name.length() <= ParticleWire.MAX_NAME_LENGTH, name);
                ParticleWire.Decoded decoded = ParticleWire.decode(name);
                assertNotNull(decoded, name);
                assertSame(particle, decoded.particle);
                assertEquals(color, decoded.color);
            }
        }
        assertEquals("kunftig:end_rod", ParticleWire.encode(KunftigParticle.END_ROD, 0));
        assertEquals("kunftig:falling_dust#ffaa5500", ParticleWire.encode(KunftigParticle.FALLING_DUST, 0xFFAA5500));
    }

    @Test
    void malformedOrFutureNamesDecodeToNothing() {
        for (String name : new String[] { null, "flame", "kunftig:", "kunftig:END_ROD", "kunftig:end_rod#ff",
            "kunftig:end_rod#gggggggg", "kunftig:end_rod#ffffffff0", "kunftig:sonic_boom", "kunftig:end_rod#",
            "kunftig:" + new String(new char[60]).replace('\0', 'a') }) {
            assertNull(ParticleWire.decode(name), String.valueOf(name));
        }
        assertFalse(ParticleWire.isKunftig("wake"));
        assertTrue(ParticleWire.isKunftig("kunftig:sonic_boom"));
    }

    @Test
    void vanillaNamesCarryBothIdsForItemsAndBlocks() {
        assertEquals("wake", ParticleWire.vanillaName(KunftigParticle.FISHING, -1, 0));
        assertEquals("iconcrack_349_2", ParticleWire.vanillaName(KunftigParticle.ITEM, 349, 2));
        assertEquals("blockcrack_9_0", ParticleWire.vanillaName(KunftigParticle.BLOCK, 9, 0));
        assertEquals("blockdust_12_1", ParticleWire.vanillaName(KunftigParticle.BLOCK_DUST, 12, 1));
        assertThrows(IllegalArgumentException.class, () -> ParticleWire.vanillaName(KunftigParticle.END_ROD, 0, 0));
    }

    @Test
    void colorsBecomeVelocityAndPureColorsSurviveReddust() {
        assertArrayEquals(
            new float[] { 1, 0.6666667F, 0 },
            ParticleWire.colorAsVelocity(KunftigParticle.DUST, 0xFFFFAA00));
        float[] green = ParticleWire.colorAsVelocity(KunftigParticle.DUST, 0xFF00FF00);
        assertTrue(green[0] > 0 && green[0] < 0.001F, "reddust turns red 0 into 1");
        assertEquals(0F, ParticleWire.colorAsVelocity(KunftigParticle.ENTITY_EFFECT, 0xFF00FF00)[0]);
    }

    @Test
    void modernParticlesReachKunftigClientsAsOnePacketAndOthersAsTheFallback() {
        ParticlePlan plan = plan(KunftigParticle.END_ROD, 8, 0.3, 0.01, 0);
        List<ParticlePlan.Packet> kunftig = plan.forKunftigClient();
        assertEquals(1, kunftig.size());
        assertEquals("kunftig:end_rod", kunftig.get(0).name);
        assertEquals(8, kunftig.get(0).count);
        assertEquals(0.3F, kunftig.get(0).offsetX);
        assertEquals(0.01F, kunftig.get(0).speed);

        List<ParticlePlan.Packet> vanilla = plan.forVanillaClient();
        assertEquals(1, vanilla.size());
        assertEquals("fireworksSpark", vanilla.get(0).name);
        assertEquals(8, vanilla.get(0).count);
        assertSame(vanilla, plan.forVanillaClient(), "computed once per effect");
    }

    @Test
    void plainVanillaParticlesAreTheSamePacketForEveryone() {
        ParticlePlan plan = plan(KunftigParticle.FISHING, 6, 0.2, 0.02, 0);
        assertSame(plan.forVanillaClient(), plan.forKunftigClient());
        assertEquals(
            "wake",
            plan.forVanillaClient()
                .get(0).name);

        ParticlePlan dust = plan(KunftigParticle.DUST, 5, 0.2, 1, 0);
        assertEquals(
            "reddust",
            dust.forKunftigClient()
                .get(0).name);
        assertEquals(
            5,
            dust.forKunftigClient()
                .get(0).count,
            "uncolored dust keeps vanilla's random colors");

        ParticlePlan item = new ParticlePlan(KunftigParticle.ITEM, 0, 0, 0, 3, 0, 0, 0, 0.1, 0, 349, 0, new Random());
        assertEquals(
            "iconcrack_349_0",
            item.forKunftigClient()
                .get(0).name);
    }

    @Test
    void coloredDustExpandsForVanillaClientsWithTheClientsSpread() {
        ParticlePlan plan = plan(KunftigParticle.DUST, 12, 0.4, 0.5, 0xFFFFAA00);
        List<ParticlePlan.Packet> kunftig = plan.forKunftigClient();
        assertEquals(1, kunftig.size());
        assertEquals("kunftig:dust#ffffaa00", kunftig.get(0).name);

        List<ParticlePlan.Packet> vanilla = plan.forVanillaClient();
        assertEquals(12, vanilla.size());
        Random expected = new Random(7);
        for (ParticlePlan.Packet packet : vanilla) {
            assertEquals("reddust", packet.name);
            assertEquals(0, packet.count);
            assertEquals(1.0F, packet.speed);
            assertEquals(1.0F, packet.offsetX);
            assertEquals(0.6666667F, packet.offsetY);
            assertEquals(0F, packet.offsetZ);
            assertEquals(10 + expected.nextGaussian() * 0.4F, packet.x, 1e-9);
            assertEquals(64 + expected.nextGaussian() * 0.4F, packet.y, 1e-9);
            assertEquals(-5 + expected.nextGaussian() * 0.4F, packet.z, 1e-9);
        }

        List<ParticlePlan.Packet> single = plan(KunftigParticle.DUST, 0, 0.4, 0.5, 0xFF0000FF).forVanillaClient();
        assertEquals(1, single.size());
        assertEquals(10, single.get(0).x);
        assertEquals(1.0F, single.get(0).offsetZ);
    }

    @Test
    void modernColorsAndDefaultTintsReachTheFallback() {
        List<ParticlePlan.Packet> fallingDust = plan(KunftigParticle.FALLING_DUST, 2, 0.1, 0, 0xFF336699)
            .forVanillaClient();
        assertEquals(2, fallingDust.size());
        assertEquals("reddust", fallingDust.get(0).name);
        assertEquals(0.2F, fallingDust.get(0).offsetX);

        List<ParticlePlan.Packet> cherry = plan(KunftigParticle.CHERRY_LEAVES, 3, 0.1, 0, 0).forVanillaClient();
        assertEquals(3, cherry.size());
        assertEquals(1.0F, cherry.get(0).offsetX);
        assertEquals(0xB7 / 255F, cherry.get(0).offsetY);

        List<ParticlePlan.Packet> honey = plan(KunftigParticle.DRIPPING_HONEY, 4, 0.1, 0, 0xFF00FF00)
            .forVanillaClient();
        assertEquals(1, honey.size());
        assertEquals("dripLava", honey.get(0).name);
        assertEquals(
            "kunftig:dripping_honey#ff00ff00",
            plan(KunftigParticle.DRIPPING_HONEY, 4, 0.1, 0, 0xFF00FF00).forKunftigClient()
                .get(0).name);
    }

    @Test
    void etFuturumTableCoversExactlyTheModernParticles() {
        for (KunftigParticle particle : KunftigParticle.values()) {
            EfrParticleTable.Entry entry = EfrParticleTable.get(particle);
            assertEquals(particle.isModern(), entry != null, particle.toString());
            if (entry != null) {
                assertEquals(
                    particle.dataKind() == DataKind.COLOR,
                    entry.shape == EfrParticleTable.Shape.POSITION_COLOR,
                    particle + " color parameter");
                assertTrue(entry.method.startsWith("spawn"), entry.method);
            }
        }
    }
}
