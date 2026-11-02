package com.akashic.kunftig.api.particle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.akashic.kunftig.api.KunftigApi;
import com.akashic.kunftig.api.bukkit.particle.KunftigParticles;
import com.akashic.kunftig.api.bukkit.particle.ParticleBuilder;
import com.akashic.kunftig.api.bukkit.player.KunftigPlayers;
import com.akashic.kunftig.api.particle.KunftigParticle.DataKind;

class ParticleApiTest {

    /** Every constant of org.bukkit.Particle on production Crucible (1.7.10-staging-67b53034). */
    private static final List<String> CRUCIBLE_PARTICLES = Arrays.asList(
        "EXPLOSION_NORMAL", "EXPLOSION_LARGE", "EXPLOSION_HUGE", "FIREWORKS_SPARK", "WATER_BUBBLE", "WATER_SPLASH",
        "WATER_WAKE", "SUSPENDED", "SUSPENDED_DEPTH", "CRIT", "CRIT_MAGIC", "SMOKE_NORMAL", "SMOKE_LARGE", "SPELL",
        "SPELL_INSTANT", "SPELL_MOB", "SPELL_MOB_AMBIENT", "SPELL_WITCH", "DRIP_WATER", "DRIP_LAVA", "VILLAGER_ANGRY",
        "VILLAGER_HAPPY", "TOWN_AURA", "NOTE", "PORTAL", "ENCHANTMENT_TABLE", "FLAME", "LAVA", "FOOTSTEP", "CLOUD",
        "REDSTONE", "SNOWBALL", "SNOW_SHOVEL", "SLIME", "HEART", "BARRIER", "ITEM_CRACK", "BLOCK_CRACK", "BLOCK_DUST",
        "WATER_DROP", "ITEM_TAKE", "MOB_APPEARANCE", "DRAGON_BREATH", "END_ROD", "DAMAGE_INDICATOR", "SWEEP_ATTACK",
        "FALLING_DUST", "TOTEM", "SPIT");

    /** Crucible's MinecraftParticles names, i.e. what 1.7.10 itself can draw. */
    private static final Set<String> VANILLA_1_7_10_NAMES = new HashSet<>(Arrays.asList(
        "explode", "largeexplode", "hugeexplosion", "fireworksSpark", "bubble", "splash", "wake", "suspended",
        "depthsuspend", "crit", "magicCrit", "smoke", "largesmoke", "spell", "instantSpell", "mobSpell",
        "mobSpellAmbient", "witchMagic", "dripWater", "dripLava", "angryVillager", "happyVillager", "townaura", "note",
        "portal", "enchantmenttable", "flame", "lava", "footstep", "cloud", "reddust", "snowballpoof", "snowshovel",
        "slime", "heart", "iconcrack_", "blockcrack_", "blockdust_"));

    @AfterEach
    void uninstall() {
        KunftigParticles.install(null);
        KunftigPlayers.install(null);
    }

    private static <T> T stub(Class<T> type) {
        return type.cast(
            Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[] { type },
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "equals":
                            return proxy == args[0];
                        case "hashCode":
                            return System.identityHashCode(proxy);
                        case "toString":
                            return type.getSimpleName() + "@stub";
                        default:
                            throw new UnsupportedOperationException(method.getName());
                    }
                }));
    }

    @Test
    void vanillaParticlesAreExactly1710sAndModernOnesFallBackToThem() {
        Set<String> seen = new HashSet<>();
        for (KunftigParticle particle : KunftigParticle.values()) {
            if (particle.isModern()) {
                assertNull(particle.vanillaName());
                assertFalse(particle.fallback().isModern(), particle + " falls back to a modern particle");
                assertTrue(
                    particle.fallback().dataKind() == DataKind.NONE || particle.fallback().dataKind() == DataKind.COLOR,
                    particle + " falls back to a particle that needs ids");
            } else {
                assertSame(particle, particle.fallback());
                assertTrue(VANILLA_1_7_10_NAMES.contains(particle.vanillaName()), particle.vanillaName());
                assertEquals(0, particle.defaultColor());
                assertTrue(seen.add(particle.vanillaName()), "duplicate " + particle.vanillaName());
            }
            if (particle.defaultColor() != 0) {
                assertEquals(0xFF, particle.defaultColor() >>> 24, particle + " default color must be opaque");
            }
        }
        assertEquals(VANILLA_1_7_10_NAMES, seen);
    }

    @Test
    void everyCrucibleParticleNameResolvesExceptTheUndrawableOnes() {
        Set<String> undrawable = new HashSet<>(Arrays.asList(
            "BARRIER", "WATER_DROP", "ITEM_TAKE", "MOB_APPEARANCE", "DRAGON_BREATH", "SWEEP_ATTACK", "TOTEM", "SPIT"));
        for (String name : CRUCIBLE_PARTICLES) {
            KunftigParticle particle = KunftigParticle.fromName(name);
            if (undrawable.contains(name)) {
                assertNull(particle, name);
            } else {
                assertNotNull(particle, name);
            }
        }
        assertSame(KunftigParticle.FISHING, KunftigParticle.fromName("water_wake"));
        assertSame(KunftigParticle.DUST, KunftigParticle.fromName("REDSTONE"));
        assertSame(KunftigParticle.FISHING, KunftigParticle.fromName("fishing"));
        assertSame(KunftigParticle.END_ROD, KunftigParticle.fromName("END_ROD"));
        assertNull(KunftigParticle.fromName(null));
        assertNull(KunftigParticle.fromName("nope"));
    }

    @Test
    void builderValidatesLikeBukkit() {
        ParticleBuilder dust = KunftigParticles.builder(KunftigParticle.DUST);
        assertEquals(0xFFFFAA00, dust.color(0xFFAA00).color());
        assertEquals(0xFF0A141E, dust.color(10, 20, 30).color());
        assertThrows(IllegalArgumentException.class, () -> dust.color(256, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> dust.item(1, 0));
        assertThrows(IllegalArgumentException.class, () -> KunftigParticles.builder(KunftigParticle.FLAME).color(1));
        assertThrows(IllegalArgumentException.class, () -> KunftigParticles.builder(KunftigParticle.END_ROD).color(1));
        assertThrows(IllegalArgumentException.class, () -> KunftigParticles.builder(KunftigParticle.BLOCK).block(1, 16));
        assertThrows(IllegalArgumentException.class, () -> KunftigParticles.builder(KunftigParticle.ITEM).item(0, 0));
        assertThrows(IllegalArgumentException.class, () -> dust.count(-1));
        assertThrows(IllegalArgumentException.class, () -> dust.receivers(0));
        assertThrows(IllegalArgumentException.class, () -> dust.location(new Location(null, 0, 0, 0)));
        assertThrows(IllegalArgumentException.class, () -> KunftigParticles.builder(null));

        // Changing to a particle with other data drops the old data.
        ParticleBuilder changed = KunftigParticles.builder(KunftigParticle.FALLING_DUST).color(0x123456);
        assertEquals(0, changed.particle(KunftigParticle.ITEM).color());
        assertEquals(0xFF123456, changed.particle(KunftigParticle.ITEM).particle(KunftigParticle.DUST).color(0x123456).color());
    }

    @Test
    void defaultsMatchBukkitsSpawnParticle() {
        ParticleBuilder builder = KunftigParticles.builder(KunftigParticle.FLAME);
        assertEquals(1, builder.count());
        assertEquals(1.0, builder.extra());
        assertEquals(16.0, builder.radius());
        assertNull(builder.receivers());
        assertEquals(-1, builder.dataId());
    }

    @Test
    void spawnNeedsLocationDataAndTheMod() {
        World world = stub(World.class);
        Location location = new Location(world, 1, 2, 3);
        assertThrows(IllegalStateException.class, () -> KunftigParticles.builder(KunftigParticle.FLAME).spawn());
        assertThrows(
            IllegalStateException.class,
            () -> KunftigParticles.builder(KunftigParticle.ITEM).location(location).spawn());
        IllegalStateException missing = assertThrows(
            IllegalStateException.class,
            () -> KunftigParticles.spawn(location, KunftigParticle.FLAME, 1));
        assertTrue(missing.getMessage().contains("not available"));
        assertFalse(KunftigApi.isParticlesAvailable());
    }

    @Test
    void shortcutsReachTheProviderWithBukkitSemantics() {
        List<ParticleBuilder> spawned = new ArrayList<>();
        Player player = stub(Player.class);
        KunftigParticles.install(new KunftigParticles.Provider() {

            @Override
            public void spawn(ParticleBuilder effect) {
                spawned.add(effect);
            }

            @Override
            public boolean drawsModernParticles(Player ignored) {
                return true;
            }
        });
        World world = stub(World.class);
        Location location = new Location(world, 1, 2, 3);
        KunftigParticles.spawn(location, KunftigParticle.FISHING, 6, 0.2, 0, 0.2, 0.02);
        KunftigParticles.spawn(location, KunftigParticle.FLAME, 3);
        KunftigParticles.builder(KunftigParticle.BLOCK).location(location).block(2, 0).receivers(player).spawn();
        location.setX(99); // the builder keeps its own copy

        assertEquals(3, spawned.size());
        ParticleBuilder wake = spawned.get(0);
        assertEquals(KunftigParticle.FISHING, wake.particle());
        assertEquals(6, wake.count());
        assertEquals(0.2, wake.offsetX());
        assertEquals(0.02, wake.extra());
        assertEquals(1.0, wake.location().getX());
        assertEquals(1.0, spawned.get(1).extra());
        assertEquals(Collections.singletonList(player), spawned.get(2).receivers());
        assertEquals(2, spawned.get(2).dataId());
        assertTrue(KunftigParticles.drawsModernParticles(player));
        assertTrue(KunftigApi.isParticlesAvailable());
        assertEquals(4, KunftigApi.apiVersion());
    }

    @Test
    void pingNeedsTheMod() {
        Player player = stub(Player.class);
        assertFalse(KunftigApi.isPlayersAvailable());
        assertThrows(IllegalStateException.class, () -> KunftigPlayers.getPing(player));
        KunftigPlayers.install(p -> 42);
        assertEquals(42, KunftigPlayers.getPing(player));
    }
}
