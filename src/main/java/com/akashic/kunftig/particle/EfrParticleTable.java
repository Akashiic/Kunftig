package com.akashic.kunftig.particle;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

import com.akashic.kunftig.api.particle.KunftigParticle;

/**
 * Which static method of Et Futurum Requiem's {@code ganymedes01.etfuturum.client.particle.CustomParticles} draws
 * each modern particle, and its shape. Checked against Et Futurum Requiem 2.6.64-GTNH with {@code javap}; every
 * method returns the spawned {@code EntityFX} (already added to the effect renderer).
 */
public final class EfrParticleTable {

    public static final String CLASS = "ganymedes01.etfuturum.client.particle.CustomParticles";
    public static final String MOD_ID = "etfuturum";

    /** Parameters after {@code (World, double x, double y, double z)}. */
    public enum Shape {
        /** Nothing more; Kunftig sets the velocity on the returned particle when the effect has one. */
        POSITION,
        /** {@code int argb}. */
        POSITION_COLOR,
        /** {@code double vx, double vy, double vz}. */
        POSITION_VELOCITY
    }

    public static final class Entry {

        public final String method;
        public final Shape shape;

        Entry(String method, Shape shape) {
            this.method = method;
            this.shape = shape;
        }
    }

    private static final Map<KunftigParticle, Entry> TABLE;

    static {
        Map<KunftigParticle, Entry> table = new EnumMap<>(KunftigParticle.class);
        table.put(KunftigParticle.END_ROD, new Entry("spawnEndRodParticle", Shape.POSITION));
        table.put(KunftigParticle.SOUL_FIRE_FLAME, new Entry("spawnSoulFlame", Shape.POSITION_VELOCITY));
        table.put(KunftigParticle.FALLING_DUST, new Entry("spawnFallingDustParticle", Shape.POSITION_COLOR));
        table.put(KunftigParticle.DRIPPING_HONEY, new Entry("spawnDrippingParticle", Shape.POSITION_COLOR));
        table.put(KunftigParticle.DRIPPING_OBSIDIAN_TEAR, new Entry("spawnDrippingParticle", Shape.POSITION_COLOR));
        table.put(KunftigParticle.CHERRY_LEAVES, new Entry("spawnCherryLeaf", Shape.POSITION));
        table.put(KunftigParticle.CRIMSON_SPORE, new Entry("spawnCrimsonSpore", Shape.POSITION));
        table.put(KunftigParticle.WARPED_SPORE, new Entry("spawnWarpedSpore", Shape.POSITION));
        table.put(KunftigParticle.ASH, new Entry("spawnAshParticle", Shape.POSITION));
        table.put(KunftigParticle.BUBBLE_COLUMN_UP, new Entry("spawnBubbleColumnUp", Shape.POSITION_VELOCITY));
        table.put(KunftigParticle.CURRENT_DOWN, new Entry("spawnWaterCurrentDown", Shape.POSITION));
        table.put(KunftigParticle.FALLING_NECTAR, new Entry("spawnBeeNectarParticle", Shape.POSITION));
        table.put(KunftigParticle.DAMAGE_INDICATOR, new Entry("spawnBlackHeartParticle", Shape.POSITION));
        table.put(KunftigParticle.WAX_ON, new Entry("spawnCopperWaxOnParticle", Shape.POSITION));
        table.put(KunftigParticle.WAX_OFF, new Entry("spawnCopperWaxOffParticle", Shape.POSITION));
        table.put(KunftigParticle.SCRAPE, new Entry("spawnCopperScrapeParticle", Shape.POSITION));
        TABLE = Collections.unmodifiableMap(table);
    }

    /** Velocity Et Futurum gives its own bubble columns; used when the effect has none. */
    public static final double BUBBLE_COLUMN_RISE = 0.04;

    private EfrParticleTable() {}

    /** The entry for a modern particle, or null. */
    public static Entry get(KunftigParticle particle) {
        return TABLE.get(particle);
    }

    public static Map<KunftigParticle, Entry> all() {
        return TABLE;
    }
}
