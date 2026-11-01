package com.akashic.kunftig.api.bukkit.particle;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import com.akashic.kunftig.api.particle.KunftigParticle;
import com.akashic.kunftig.api.particle.KunftigParticle.DataKind;

/**
 * One particle effect, in the shape of Paper's {@code ParticleBuilder}: set what you need and call {@link #spawn()}.
 * Reusable: {@code spawn()} can be called again after changing the location.
 *
 * <pre>
 * KunftigParticles.builder(KunftigParticle.END_ROD)
 *     .location(hookLocation)
 *     .count(8)
 *     .offset(0.3, 0.3, 0.3)
 *     .extra(0.01)
 *     .spawn();                          // everyone in the world within 16 blocks
 * </pre>
 *
 * Semantics are vanilla's (and modern Bukkit's): with {@code count > 0} each particle is placed at a gaussian offset
 * (standard deviation {@code offset}) and gets a random velocity scaled by {@code extra}; with {@code count == 0}
 * exactly one particle is spawned with velocity {@code offset * extra}.
 */
public final class ParticleBuilder {

    /** How far 1.7.10 sends and draws particles: the server sends within 16 blocks and the client culls past 16. */
    public static final double DEFAULT_RADIUS = 16;

    private KunftigParticle particle;
    private Location location;
    private int count = 1;
    private double offsetX;
    private double offsetY;
    private double offsetZ;
    private double extra = 1;
    private int color;
    private int dataId = -1;
    private int dataMeta;
    private double radius = DEFAULT_RADIUS;
    private List<Player> receivers;

    ParticleBuilder(KunftigParticle particle) {
        particle(particle);
    }

    public ParticleBuilder particle(KunftigParticle particle) {
        if (particle == null) {
            throw new IllegalArgumentException("particle is null");
        }
        if (this.particle != null && this.particle.dataKind() != particle.dataKind()) {
            color = 0;
            dataId = -1;
            dataMeta = 0;
        }
        this.particle = particle;
        return this;
    }

    public ParticleBuilder location(Location location) {
        if (location == null || location.getWorld() == null) {
            throw new IllegalArgumentException("location and its world must not be null");
        }
        this.location = location.clone();
        return this;
    }

    /** Number of particles; 0 spawns one particle whose velocity is {@code offset * extra}. Default 1. */
    public ParticleBuilder count(int count) {
        if (count < 0) {
            throw new IllegalArgumentException("count must not be negative: " + count);
        }
        this.count = count;
        return this;
    }

    /** Spread (count &gt; 0) or direction (count 0) on each axis. Default 0. */
    public ParticleBuilder offset(double x, double y, double z) {
        this.offsetX = x;
        this.offsetY = y;
        this.offsetZ = z;
        return this;
    }

    /** Speed. Default 1, as in Bukkit's {@code spawnParticle}: use 0 for particles that should stay in place. */
    public ParticleBuilder extra(double extra) {
        this.extra = extra;
        return this;
    }

    /**
     * Color of a {@link DataKind#COLOR} particle ({@code DUST}, {@code ENTITY_EFFECT}, {@code FALLING_DUST},
     * {@code DRIPPING_HONEY}...), as {@code 0xRRGGBB}.
     *
     * @throws IllegalArgumentException if the particle takes no color
     */
    public ParticleBuilder color(int rgb) {
        requireKind(DataKind.COLOR);
        this.color = 0xFF000000 | (rgb & 0xFFFFFF);
        return this;
    }

    public ParticleBuilder color(int red, int green, int blue) {
        for (int channel : new int[] { red, green, blue }) {
            if (channel < 0 || channel > 255) {
                throw new IllegalArgumentException("color channels must be 0-255");
            }
        }
        return color(red << 16 | green << 8 | blue);
    }

    /**
     * Item of {@link KunftigParticle#ITEM} by numeric id (as in 1.7.10: {@code Material#getId()}) and damage.
     * The server replaces ids it does not know with stone (1), as Crucible does: 1.7.10 clients fail on unknown ids.
     */
    public ParticleBuilder item(int id, int damage) {
        requireKind(DataKind.ITEM);
        return ids(id, damage);
    }

    /** Block of {@link KunftigParticle#BLOCK} or {@link KunftigParticle#BLOCK_DUST} by numeric id and metadata. */
    public ParticleBuilder block(int id, int metadata) {
        requireKind(DataKind.BLOCK);
        if (metadata < 0 || metadata > 15) {
            throw new IllegalArgumentException("block metadata must be 0-15: " + metadata);
        }
        return ids(id, metadata);
    }

    private ParticleBuilder ids(int id, int meta) {
        if (id <= 0 || id > 31999 || meta < 0 || meta > 32767) {
            throw new IllegalArgumentException("invalid id/data " + id + ":" + meta);
        }
        this.dataId = id;
        this.dataMeta = meta;
        return this;
    }

    private void requireKind(DataKind kind) {
        if (particle.dataKind() != kind) {
            throw new IllegalArgumentException(particle + " takes " + particle.dataKind() + ", not " + kind);
        }
    }

    /** Send to every player in the location's world within {@code radius} blocks. Default {@link #DEFAULT_RADIUS}. */
    public ParticleBuilder receivers(double radius) {
        if (!(radius > 0)) {
            throw new IllegalArgumentException("radius must be positive: " + radius);
        }
        this.radius = radius;
        this.receivers = null;
        return this;
    }

    /** Send only to these players (any distance, any world: the client still culls particles past 16 blocks). */
    public ParticleBuilder receivers(Collection<? extends Player> players) {
        if (players == null) {
            throw new IllegalArgumentException("players is null");
        }
        this.receivers = Collections.unmodifiableList(new ArrayList<Player>(players));
        return this;
    }

    public ParticleBuilder receivers(Player... players) {
        return receivers(Arrays.asList(players));
    }

    /**
     * Sends the effect. Main thread only.
     *
     * @throws IllegalStateException if no location was set, an {@code ITEM}/{@code BLOCK} particle has no data, or
     *                               Kunftig particles are not available
     */
    public ParticleBuilder spawn() {
        if (location == null) {
            throw new IllegalStateException("no location set");
        }
        if ((particle.dataKind() == DataKind.ITEM || particle.dataKind() == DataKind.BLOCK) && dataId < 0) {
            throw new IllegalStateException(particle + " needs " + particle.dataKind() + " data");
        }
        KunftigParticles.provider()
            .spawn(this);
        return this;
    }

    public KunftigParticle particle() {
        return particle;
    }

    /** A copy of the location, or null. */
    public Location location() {
        return location == null ? null : location.clone();
    }

    public int count() {
        return count;
    }

    public double offsetX() {
        return offsetX;
    }

    public double offsetY() {
        return offsetY;
    }

    public double offsetZ() {
        return offsetZ;
    }

    public double extra() {
        return extra;
    }

    /** ARGB color set with {@link #color}, or 0 for the particle's default. */
    public int color() {
        return color;
    }

    /** Item/block id, or -1. */
    public int dataId() {
        return dataId;
    }

    public int dataMeta() {
        return dataMeta;
    }

    public double radius() {
        return radius;
    }

    /** Explicit receivers, or null to use {@link #radius()}. */
    public List<Player> receivers() {
        return receivers;
    }
}
