package com.akashic.kunftig.particle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import com.akashic.kunftig.api.particle.KunftigParticle;
import com.akashic.kunftig.api.particle.KunftigParticle.DataKind;

/**
 * The particle packets one effect turns into, for the two kinds of client. Computed once per effect and shared by
 * every receiver; no Minecraft classes, so it is unit tested directly.
 * <ul>
 * <li><b>Kunftig clients</b> get one packet: a {@link ParticleWire} name when the client must do something vanilla
 * cannot (modern particle, or a color), otherwise the plain vanilla packet.</li>
 * <li><b>Other clients</b> get the vanilla fallback. A color needs one packet per particle: 1.7.10 colors
 * {@code reddust}/{@code mobSpell} through the velocity of a single particle (count 0), so the spread is computed
 * here, the way the client would.</li>
 * </ul>
 */
public final class ParticlePlan {

    /** One {@code S2APacketParticles}. */
    public static final class Packet {

        public final String name;
        public final double x;
        public final double y;
        public final double z;
        public final float offsetX;
        public final float offsetY;
        public final float offsetZ;
        public final float speed;
        public final int count;

        Packet(String name, double x, double y, double z, float offsetX, float offsetY, float offsetZ, float speed,
            int count) {
            if (name.length() > ParticleWire.MAX_NAME_LENGTH) {
                throw new IllegalArgumentException("particle name too long: " + name);
            }
            this.name = name;
            this.x = x;
            this.y = y;
            this.z = z;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.offsetZ = offsetZ;
            this.speed = speed;
            this.count = count;
        }

        @Override
        public String toString() {
            return name + " @"
                + x
                + ","
                + y
                + ","
                + z
                + " o="
                + offsetX
                + ","
                + offsetY
                + ","
                + offsetZ
                + " s="
                + speed
                + " n="
                + count;
        }
    }

    private final KunftigParticle particle;
    private final double x;
    private final double y;
    private final double z;
    private final int count;
    private final float offsetX;
    private final float offsetY;
    private final float offsetZ;
    private final float speed;
    private final int color;
    private final int dataId;
    private final int dataMeta;
    private final Random random;

    private List<Packet> forKunftig;
    private List<Packet> forVanilla;

    /**
     * @param color  ARGB color given by the plugin, or 0 for the particle's default
     * @param dataId item/block id already validated by the server ({@code ITEM}/{@code BLOCK} particles only)
     */
    public ParticlePlan(KunftigParticle particle, double x, double y, double z, int count, double offsetX,
        double offsetY, double offsetZ, double speed, int color, int dataId, int dataMeta, Random random) {
        this.particle = particle;
        this.x = x;
        this.y = y;
        this.z = z;
        this.count = count;
        this.offsetX = (float) offsetX;
        this.offsetY = (float) offsetY;
        this.offsetZ = (float) offsetZ;
        this.speed = (float) speed;
        this.color = color;
        this.dataId = dataId;
        this.dataMeta = dataMeta;
        this.random = random;
    }

    /** Whether a Kunftig client needs the Kunftig name (vanilla cannot express this effect in one packet). */
    boolean needsKunftigName() {
        return particle.isModern() || particle.dataKind() == DataKind.COLOR && color != 0;
    }

    public List<Packet> forKunftigClient() {
        if (forKunftig == null) {
            forKunftig = needsKunftigName()
                ? Collections.singletonList(
                    new Packet(ParticleWire.encode(particle, color), x, y, z, offsetX, offsetY, offsetZ, speed, count))
                : forVanillaClient();
        }
        return forKunftig;
    }

    public List<Packet> forVanillaClient() {
        if (forVanilla == null) {
            forVanilla = Collections.unmodifiableList(vanilla());
        }
        return forVanilla;
    }

    private List<Packet> vanilla() {
        KunftigParticle target = particle.fallback();
        int tint = ParticleWire.fallbackColor(particle, color);
        if (target.dataKind() == DataKind.COLOR && tint != 0) {
            float[] rgb = ParticleWire.colorAsVelocity(target, tint);
            int particles = Math.max(count, 1);
            List<Packet> packets = new ArrayList<>(particles);
            for (int i = 0; i < particles; i++) {
                // Same spread the client applies for count > 0 (gaussian * offset); count 0 is one exact particle.
                double px = count > 0 ? x + random.nextGaussian() * offsetX : x;
                double py = count > 0 ? y + random.nextGaussian() * offsetY : y;
                double pz = count > 0 ? z + random.nextGaussian() * offsetZ : z;
                packets.add(new Packet(target.vanillaName(), px, py, pz, rgb[0], rgb[1], rgb[2], 1.0F, 0));
            }
            return packets;
        }
        return Collections.singletonList(
            new Packet(
                ParticleWire.vanillaName(target, dataId, dataMeta),
                x,
                y,
                z,
                offsetX,
                offsetY,
                offsetZ,
                speed,
                count));
    }
}
