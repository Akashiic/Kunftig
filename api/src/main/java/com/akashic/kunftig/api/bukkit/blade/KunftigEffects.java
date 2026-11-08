package com.akashic.kunftig.api.bukkit.blade;

import java.util.regex.Pattern;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * SlashBlade-style effects (API 11), drawn by clients with Kunftig from the models and textures a resource pack
 * declares under {@code efeitos} in its {@code kunftig/blades.json} ({@code corte}, {@code julgamento}, {@code onda},
 * {@code espada}, {@code mira}, {@code rank}). They play on the clients within 64 blocks. Slashes and Judgement Cuts are
 * fire-and-forget. Drives and summoned swords ({@link Projectile}) fly on the clients the way SlashBlade's do, each
 * tick: position += velocity, velocity *= 0.99, no gravity, yaw and pitch easing toward the velocity, a drive ending
 * and a sword lodging at the first block. A plugin moving its own copy the same way only sends what differs: a new
 * motion ({@link Projectile#move}), a sword sticking in an entity, the end. The lock-on reticle and the rank HUD go to
 * their player only. Main thread only. Requires the Kunftig mod on the server.
 *
 * <pre>
 * // SlashBlade's doSlash: at the player's eyes * 0.75 + look * 0.3, the player's yaw, pitch 0
 * KunftigEffects.slash("overkill", at, player.getLocation().getYaw(), -10f, 0x3333FF, rankLevel, 1f);
 * </pre>
 */
public final class KunftigEffects {

    public static final Pattern NAMESPACE = Pattern.compile("[a-z0-9_.-]{1,64}");

    /** Effect names of {@code efeitos}. */
    public static final String SLASH = "corte";
    public static final String JUDGEMENT_CUT = "julgamento";
    public static final String DRIVE = "onda";
    public static final String SWORD = "espada";
    /** Projectile kinds: a drive fades over its lifetime and ends at blocks; a sword is opaque and lodges in them. */
    public static final int KIND_DRIVE = 0;
    public static final int KIND_SWORD = 1;
    /** SlashBlade's concentration rank points: 300 per level, at most 1799. */
    public static final int MAX_RANK_POINTS = 1799;

    /** A drive or summoned sword on the clients. */
    public interface Projectile {

        int getId();

        /**
         * A new position and velocity (blocks per tick), with yaw and pitch toward the velocity; the clients go on from
         * there. Also frees a stuck or lodged sword.
         */
        void move(Location at, Vector velocity);

        /**
         * {@link #move(Location, Vector)} with an explicit rotation, in SlashBlade's projectile convention: yaw
         * {@code atan2(x, z)} in degrees of the direction the sword points (so not Bukkit's yaw), pitch
         * {@code atan2(y, horizontal)}. For swords held still or turning on their own (orbits): velocity zero.
         */
        void move(Location at, Vector velocity, float yaw, float pitch);

        /** A sword stuck in {@code target}: it follows its middle and turns with it, {@code offsetYaw} apart. */
        void stick(Entity target, float offsetYaw);

        /** The end; {@code burst}: shattering (crits) as SlashBlade's {@code burst()}. Sounds are the plugin's. */
        void remove(boolean burst);
    }

    /** Implemented inside the mod; installed when the server starts. */
    public interface Provider {

        void slash(String namespace, Location at, float yaw, float pitch, float roll, float rotationOffset, int color,
            float rank, float baseSize, int lifetime);

        void judgementCut(String namespace, Location at, float yaw, float pitch, int color, int lifetime);

        Projectile projectile(String namespace, String effect, int kind, Location at, Vector velocity, float yaw,
            float pitch, float roll, int color, float lifetime);

        void lockOn(Player owner, String namespace, Entity target, int color);

        void rank(Player owner, String namespace, int points);

        void hits(Player owner, String namespace, int hits);
    }

    private static volatile Provider provider;

    private KunftigEffects() {}

    public static boolean isAvailable() {
        return provider != null;
    }

    /**
     * A slash arc (SlashBlade's {@code EntitySlashEffect}): 270 degrees of a ring about 4 blocks across, sweeping 135
     * degrees and fading over 10 ticks, in the colour (RGB) and with the passes of the concentration rank level
     * ({@code rank}: 0 none, 1 D ... 5 S, 5.25 SS, 5.5 SSS; below C it is grey). Roll 0: horizontal, left to right;
     * 180: right to left.
     */
    public static void slash(String namespace, Location at, float yaw, float roll, int color, float rank,
        float baseSize) {
        slash(namespace, at, yaw, 0, roll, 0, color, rank, baseSize, 10);
    }

    /** {@link #slash(String, Location, float, float, int, float, float)} with every parameter. */
    public static void slash(String namespace, Location at, float yaw, float pitch, float roll, float rotationOffset,
        int color, float rank, float baseSize, int lifetime) {
        if (namespace == null || !NAMESPACE.matcher(namespace)
            .matches()) {
            throw new IllegalArgumentException("namespace must match " + NAMESPACE + ": " + namespace);
        }
        if (at == null || at.getWorld() == null) {
            throw new IllegalArgumentException("location with a world required");
        }
        Provider p = provider;
        if (p == null) {
            throw new IllegalStateException("KunftigEffects is not available on this server");
        }
        p.slash(namespace, at, yaw, pitch, roll, rotationOffset, color, rank, baseSize, lifetime);
    }

    /**
     * A Judgement Cut (SlashBlade's {@code EntityJudgementCut}) centred on {@code at}: a dark bubble about a block
     * across, tinted toward the colour's complement, with crescents of wind in the colour spinning through it, for 10
     * ticks; crits when it ends.
     */
    public static void judgementCut(String namespace, Location at, int color) {
        judgementCut(namespace, at, 0, 0, color, 10);
    }

    /** {@link #judgementCut(String, Location, int)} with every parameter (SlashBlade never turns it). */
    public static void judgementCut(String namespace, Location at, float yaw, float pitch, int color, int lifetime) {
        check(namespace, at);
        provider().judgementCut(namespace, at, yaw, pitch, color, lifetime);
    }

    /**
     * A drive (SlashBlade's {@code EntityDrive}): a crescent 3 blocks wide flying with {@code velocity} (blocks per
     * tick), turned by {@code roll} (0 horizontal, 90 vertical), fading over {@code lifetime} ticks.
     */
    public static Projectile drive(String namespace, Location at, Vector velocity, float roll, int color,
        float lifetime) {
        return projectile(namespace, DRIVE, KIND_DRIVE, at, velocity, roll, color, lifetime);
    }

    /** A summoned sword (SlashBlade's {@code EntityAbstractSummonedSword}) flying with {@code velocity}. */
    public static Projectile summonedSword(String namespace, Location at, Vector velocity, float roll, int color) {
        return projectile(namespace, SWORD, KIND_SWORD, at, velocity, roll, color, 0);
    }

    /** A projectile with the model {@code effect} of {@code efeitos}, yaw and pitch toward the velocity. */
    public static Projectile projectile(String namespace, String effect, int kind, Location at, Vector velocity,
        float roll, int color, float lifetime) {
        float[] rot = rotation(velocity);
        return projectile(namespace, effect, kind, at, velocity, rot[0], rot[1], roll, color, lifetime);
    }

    /** {@link #projectile(String, String, int, Location, Vector, float, int, float)} with an explicit rotation. */
    public static Projectile projectile(String namespace, String effect, int kind, Location at, Vector velocity,
        float yaw, float pitch, float roll, int color, float lifetime) {
        check(namespace, at);
        if (effect == null || !NAMESPACE.matcher(effect)
            .matches()) {
            throw new IllegalArgumentException("effect must match " + NAMESPACE + ": " + effect);
        }
        if (kind != KIND_DRIVE && kind != KIND_SWORD) {
            throw new IllegalArgumentException("kind must be KIND_DRIVE or KIND_SWORD");
        }
        if (kind == KIND_DRIVE && !(lifetime > 0)) {
            throw new IllegalArgumentException("a drive needs a lifetime");
        }
        if (velocity == null) {
            throw new IllegalArgumentException("velocity required");
        }
        return provider().projectile(namespace, effect, kind, at, velocity, yaw, pitch, roll, color, lifetime);
    }

    /**
     * Shows {@code owner} the lock-on on {@code target} (null: none), as SlashBlade does while Sneak is held with a
     * blade: the reticle in {@code color} with the target's health, and the view pulled toward it. The client only
     * shows it while the player holds a blade and Sneak.
     */
    public static void lockOn(Player owner, String namespace, Entity target, int color) {
        if (owner == null) {
            throw new IllegalArgumentException("owner required");
        }
        if (namespace == null || !NAMESPACE.matcher(namespace)
            .matches()) {
            throw new IllegalArgumentException("namespace must match " + NAMESPACE + ": " + namespace);
        }
        provider().lockOn(owner, namespace, target, color);
    }

    /**
     * {@code owner}'s concentration rank points (0..1799, 300 per level; S from 1500, SS 1575, SSS 1650), sent at each
     * change as SlashBlade's {@code RankSyncMessage}: the client lets them decay as SlashBlade's does and shows the HUD
     * for 6 seconds.
     */
    public static void rank(Player owner, String namespace, int points) {
        if (owner == null) {
            throw new IllegalArgumentException("owner required");
        }
        if (namespace == null || !NAMESPACE.matcher(namespace)
            .matches()) {
            throw new IllegalArgumentException("namespace must match " + NAMESPACE + ": " + namespace);
        }
        provider().rank(owner, namespace, Math.max(0, Math.min(MAX_RANK_POINTS, points)));
    }

    /**
     * {@code owner}'s combo hit counter (DmC style, the {@code contador} texture): "27 HITS" under the rank HUD,
     * popping at each hit and gone 3 seconds after the last; 0 hides it at once.
     */
    public static void hits(Player owner, String namespace, int hits) {
        if (owner == null) {
            throw new IllegalArgumentException("owner required");
        }
        if (namespace == null || !NAMESPACE.matcher(namespace)
            .matches()) {
            throw new IllegalArgumentException("namespace must match " + NAMESPACE + ": " + namespace);
        }
        provider().hits(owner, namespace, Math.max(0, Math.min(9999, hits)));
    }

    /** SlashBlade's projectile rotation toward {@code v}: {yaw atan2(x, z), pitch atan2(y, horizontal)}, degrees. */
    public static float[] rotation(Vector v) {
        double h = Math.sqrt(v.getX() * v.getX() + v.getZ() * v.getZ());
        return new float[] { (float) Math.toDegrees(Math.atan2(v.getX(), v.getZ())),
            (float) Math.toDegrees(Math.atan2(v.getY(), h)) };
    }

    private static void check(String namespace, Location at) {
        if (namespace == null || !NAMESPACE.matcher(namespace)
            .matches()) {
            throw new IllegalArgumentException("namespace must match " + NAMESPACE + ": " + namespace);
        }
        if (at == null || at.getWorld() == null) {
            throw new IllegalArgumentException("location with a world required");
        }
    }

    private static Provider provider() {
        Provider p = provider;
        if (p == null) {
            throw new IllegalStateException("KunftigEffects is not available on this server");
        }
        return p;
    }

    /** Internal: called by the mod. */
    public static void install(Provider implementation) {
        provider = implementation;
    }
}
