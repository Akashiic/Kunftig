package com.akashic.kunftig.api.bukkit.particle;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import com.akashic.kunftig.api.particle.KunftigParticle;

/**
 * Particles for everyone nearby, and modern particles, on Thermos/Crucible 1.7.10. Requires the Kunftig mod on the
 * server.
 * <ul>
 * <li>Production Crucible only has {@code Player#spawnParticle}; this sends to every player in the world within a
 * radius (16 blocks by default, the 1.7.10 particle range).</li>
 * <li>Modern particles ({@link KunftigParticle#isModern()}: {@code END_ROD}, {@code SOUL_FIRE_FLAME},
 * {@code FALLING_DUST}...) are drawn by clients with Kunftig and Et Futurum Requiem; everyone else sees a 1.7.10
 * fallback. Crucible's own {@code spawnParticle(Particle.END_ROD, ...)} sends {@code flame} instead.</li>
 * <li>Colors work for {@code DUST}, {@code ENTITY_EFFECT} and {@code AMBIENT_ENTITY_EFFECT} on vanilla clients too.</li>
 * </ul>
 *
 * <pre>
 * KunftigParticles.spawn(location, KunftigParticle.FISHING, 6, 0.2, 0, 0.2, 0.02);
 * KunftigParticles.builder(KunftigParticle.DUST).location(location).color(0xFFAA00).count(12).offset(.4, .4, .4).spawn();
 * </pre>
 */
public final class KunftigParticles {

    /** Implemented inside the mod; installed when the server starts. */
    public interface Provider {

        void spawn(ParticleBuilder effect);

        boolean drawsModernParticles(Player player);
    }

    private static volatile Provider provider;

    private KunftigParticles() {}

    public static boolean isAvailable() {
        return provider != null;
    }

    static Provider provider() {
        Provider current = provider;
        if (current == null) {
            throw new IllegalStateException("Kunftig particles are not available on this server");
        }
        return current;
    }

    /** A builder for {@code particle} (count 1, no offset, extra 1, radius 16). */
    public static ParticleBuilder builder(KunftigParticle particle) {
        return new ParticleBuilder(particle);
    }

    /**
     * Same as Bukkit's {@code World#spawnParticle(particle, location, count)}: every player within 16 blocks, no
     * offset, extra (speed) 1.
     */
    public static void spawn(Location location, KunftigParticle particle, int count) {
        builder(particle).location(location)
            .count(count)
            .spawn();
    }

    /** Same as {@code World#spawnParticle(particle, location, count, offsetX, offsetY, offsetZ, extra)} of Bukkit. */
    public static void spawn(Location location, KunftigParticle particle, int count, double offsetX, double offsetY,
        double offsetZ, double extra) {
        builder(particle).location(location)
            .count(count)
            .offset(offsetX, offsetY, offsetZ)
            .extra(extra)
            .spawn();
    }

    /** Whether this player's client draws modern particles itself (Kunftig mod with the particle bridge). */
    public static boolean drawsModernParticles(Player player) {
        return provider().drawsModernParticles(player);
    }

    /** Internal: called by the mod. */
    public static void install(Provider implementation) {
        provider = implementation;
    }
}
