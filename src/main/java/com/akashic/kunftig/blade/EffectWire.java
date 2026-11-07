package com.akashic.kunftig.blade;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.regex.Pattern;

/**
 * SlashBlade-style effects sent to the clients around, on {@link #CHANNEL} (raw plugin messages). Each effect plays on
 * the client from its parameters, with the models and textures of the pack namespace it names
 * ({@code efeitos} of {@code kunftig/blades.json}). No Minecraft classes.
 *
 * <pre>
 * SLASH (0):         UTF namespace, double x, y, z, float yaw, float pitch, float roll, float rotationOffset,
 *                    int color (RGB), float rank (concentration rank level, 0 = none, 1 = D ... 5 = S, 5.25 = SS,
 *                    5.5 = SSS), float baseSize, ubyte lifetime (ticks; SlashBlade's clients always draw 10)
 * JUDGEMENT_CUT (1): UTF namespace, double x, y, z, float yaw, float pitch, int color (RGB), ubyte lifetime
 * PROJECTILE (2):    UTF namespace, UTF effect (key of efeitos), int id, ubyte kind (0 drive, 1 summoned sword),
 *                    double x, y, z, double vx, vy, vz (blocks per tick), float yaw, float pitch, float roll,
 *                    int color (RGB), float lifetime (ticks; the drive's fade)
 * MOVE (3):          int id, double x, y, z, double vx, vy, vz, float yaw, float pitch
 * STICK (4):         int id, int entityId, float offsetYaw
 * REMOVE (5):        int id, boolean burst
 * LOCK (6):          UTF namespace, int entityId (-1: none), int color (RGB)       (only to the blade's holder)
 * RANK (7):          UTF namespace, int points (0 .. 1799)                         (only to the rank's owner)
 * HITS (8):          UTF namespace, int hits (0: the combo ended)                  (only to the attacker)
 * </pre>
 *
 * Projectiles move on the client as SlashBlade's summoned swords and drives do (see {@link EffectProjectile}); the
 * server sends MOVE only when it changes the motion, STICK when a sword sticks in an entity, REMOVE at the end.
 */
public final class EffectWire {

    public static final String CHANNEL = "kunftig:effect";
    public static final byte SLASH = 0;
    public static final byte JUDGEMENT_CUT = 1;
    public static final byte PROJECTILE = 2;
    public static final byte MOVE = 3;
    public static final byte STICK = 4;
    public static final byte REMOVE = 5;
    public static final byte LOCK = 6;
    public static final byte RANK = 7;
    public static final byte HITS = 8;
    public static final int KIND_DRIVE = 0;
    public static final int KIND_SWORD = 1;
    public static final Pattern NAMESPACE = Pattern.compile("[a-z0-9_.-]{1,64}");

    private EffectWire() {}

    /** A slash arc (SlashBlade's {@code EntitySlashEffect}). */
    public static final class Slash {

        public final String namespace;
        public final double x;
        public final double y;
        public final double z;
        public final float yaw;
        public final float pitch;
        public final float roll;
        public final float rotationOffset;
        public final int color;
        public final float rank;
        public final float baseSize;
        public final int lifetime;

        public Slash(String namespace, double x, double y, double z, float yaw, float pitch, float roll,
            float rotationOffset, int color, float rank, float baseSize, int lifetime) {
            this.namespace = namespace;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
            this.roll = roll;
            this.rotationOffset = rotationOffset;
            this.color = color & 0xFFFFFF;
            this.rank = rank;
            this.baseSize = baseSize;
            this.lifetime = Math.max(1, Math.min(255, lifetime));
        }
    }

    /** A Judgement Cut sphere (SlashBlade's {@code EntityJudgementCut}). */
    public static final class JudgementCut {

        public final String namespace;
        public final double x;
        public final double y;
        public final double z;
        public final float yaw;
        public final float pitch;
        public final int color;
        public final int lifetime;

        public JudgementCut(String namespace, double x, double y, double z, float yaw, float pitch, int color,
            int lifetime) {
            this.namespace = namespace;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
            this.color = color & 0xFFFFFF;
            this.lifetime = Math.max(1, Math.min(255, lifetime));
        }
    }

    /**
     * A drive or a summoned sword appearing (SlashBlade's {@code EntityDrive}, {@code EntityAbstractSummonedSword}).
     */
    public static final class Projectile {

        public final String namespace;
        public final String effect;
        public final int id;
        public final int kind;
        public final double x;
        public final double y;
        public final double z;
        public final double vx;
        public final double vy;
        public final double vz;
        public final float yaw;
        public final float pitch;
        public final float roll;
        public final int color;
        public final float lifetime;

        public Projectile(String namespace, String effect, int id, int kind, double x, double y, double z, double vx,
            double vy, double vz, float yaw, float pitch, float roll, int color, float lifetime) {
            this.namespace = namespace;
            this.effect = effect;
            this.id = id;
            this.kind = kind;
            this.x = x;
            this.y = y;
            this.z = z;
            this.vx = vx;
            this.vy = vy;
            this.vz = vz;
            this.yaw = yaw;
            this.pitch = pitch;
            this.roll = roll;
            this.color = color & 0xFFFFFF;
            this.lifetime = lifetime;
        }
    }

    /** A projectile's new position, motion and rotation. */
    public static final class Move {

        public final int id;
        public final double x;
        public final double y;
        public final double z;
        public final double vx;
        public final double vy;
        public final double vz;
        public final float yaw;
        public final float pitch;

        public Move(int id, double x, double y, double z, double vx, double vy, double vz, float yaw, float pitch) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.z = z;
            this.vx = vx;
            this.vy = vy;
            this.vz = vz;
            this.yaw = yaw;
            this.pitch = pitch;
        }
    }

    /** A summoned sword stuck in an entity. */
    public static final class Stick {

        public final int id;
        public final int entityId;
        public final float offsetYaw;

        public Stick(int id, int entityId, float offsetYaw) {
            this.id = id;
            this.entityId = entityId;
            this.offsetYaw = offsetYaw;
        }
    }

    /** A projectile ending, shattering ({@code burst}) or not. */
    public static final class Remove {

        public final int id;
        public final boolean burst;

        public Remove(int id, boolean burst) {
            this.id = id;
            this.burst = burst;
        }
    }

    /** The blade's lock-on target of the receiving player, or none ({@code entityId} -1). */
    public static final class Lock {

        public final String namespace;
        public final int entityId;
        public final int color;

        public Lock(String namespace, int entityId, int color) {
            this.namespace = namespace;
            this.entityId = entityId;
            this.color = color & 0xFFFFFF;
        }
    }

    /** The receiving player's concentration rank points (SlashBlade's {@code RankSyncMessage}). */
    public static final class Rank {

        public final String namespace;
        public final int points;

        public Rank(String namespace, int points) {
            this.namespace = namespace;
            this.points = Math.max(0, Math.min(ConcentrationRank.MAX_POINTS, points));
        }
    }

    /** The receiving player's combo hit count (a DmC-style counter); 0 ends the combo. */
    public static final class Hits {

        public final String namespace;
        public final int hits;

        public Hits(String namespace, int hits) {
            this.namespace = namespace;
            this.hits = Math.max(0, Math.min(9999, hits));
        }
    }

    private interface Writer {

        void write(DataOutputStream out) throws IOException;
    }

    private static byte[] encode(int op, Writer w) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeByte(op);
            w.write(out);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return bytes.toByteArray();
    }

    private static void checkName(String what, String name) {
        if (name == null || !NAMESPACE.matcher(name)
            .matches()) {
            throw new IllegalArgumentException("invalid " + what + " " + name);
        }
    }

    public static byte[] slash(Slash s) {
        checkName("namespace", s.namespace);
        return encode(SLASH, out -> {
            out.writeUTF(s.namespace);
            out.writeDouble(s.x);
            out.writeDouble(s.y);
            out.writeDouble(s.z);
            out.writeFloat(s.yaw);
            out.writeFloat(s.pitch);
            out.writeFloat(s.roll);
            out.writeFloat(s.rotationOffset);
            out.writeInt(s.color);
            out.writeFloat(s.rank);
            out.writeFloat(s.baseSize);
            out.writeByte(s.lifetime);
        });
    }

    public static byte[] judgementCut(JudgementCut j) {
        checkName("namespace", j.namespace);
        return encode(JUDGEMENT_CUT, out -> {
            out.writeUTF(j.namespace);
            out.writeDouble(j.x);
            out.writeDouble(j.y);
            out.writeDouble(j.z);
            out.writeFloat(j.yaw);
            out.writeFloat(j.pitch);
            out.writeInt(j.color);
            out.writeByte(j.lifetime);
        });
    }

    public static byte[] projectile(Projectile p) {
        checkName("namespace", p.namespace);
        checkName("effect", p.effect);
        if (p.kind != KIND_DRIVE && p.kind != KIND_SWORD) {
            throw new IllegalArgumentException("invalid kind " + p.kind);
        }
        return encode(PROJECTILE, out -> {
            out.writeUTF(p.namespace);
            out.writeUTF(p.effect);
            out.writeInt(p.id);
            out.writeByte(p.kind);
            out.writeDouble(p.x);
            out.writeDouble(p.y);
            out.writeDouble(p.z);
            out.writeDouble(p.vx);
            out.writeDouble(p.vy);
            out.writeDouble(p.vz);
            out.writeFloat(p.yaw);
            out.writeFloat(p.pitch);
            out.writeFloat(p.roll);
            out.writeInt(p.color);
            out.writeFloat(p.lifetime);
        });
    }

    public static byte[] move(Move m) {
        return encode(MOVE, out -> {
            out.writeInt(m.id);
            out.writeDouble(m.x);
            out.writeDouble(m.y);
            out.writeDouble(m.z);
            out.writeDouble(m.vx);
            out.writeDouble(m.vy);
            out.writeDouble(m.vz);
            out.writeFloat(m.yaw);
            out.writeFloat(m.pitch);
        });
    }

    public static byte[] stick(Stick s) {
        return encode(STICK, out -> {
            out.writeInt(s.id);
            out.writeInt(s.entityId);
            out.writeFloat(s.offsetYaw);
        });
    }

    public static byte[] remove(Remove r) {
        return encode(REMOVE, out -> {
            out.writeInt(r.id);
            out.writeBoolean(r.burst);
        });
    }

    public static byte[] lock(Lock l) {
        checkName("namespace", l.namespace);
        return encode(LOCK, out -> {
            out.writeUTF(l.namespace);
            out.writeInt(l.entityId);
            out.writeInt(l.color);
        });
    }

    public static byte[] rank(Rank r) {
        checkName("namespace", r.namespace);
        return encode(RANK, out -> {
            out.writeUTF(r.namespace);
            out.writeInt(r.points);
        });
    }

    public static byte[] hits(Hits h) {
        checkName("namespace", h.namespace);
        return encode(HITS, out -> {
            out.writeUTF(h.namespace);
            out.writeInt(h.hits);
        });
    }

    /** The slash in {@code data}, or null when malformed or another operation. */
    public static Slash readSlash(byte[] data) {
        Object o = read(data);
        return o instanceof Slash ? (Slash) o : null;
    }

    /**
     * The message in {@code data} ({@link Slash}, {@link JudgementCut}, {@link Projectile}, {@link Move},
     * {@link Stick}, {@link Remove}, {@link Lock}, {@link Rank} or {@link Hits}), or null when malformed or unknown.
     */
    public static Object read(byte[] data) {
        if (data == null || data.length < 2) {
            return null;
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
            Object m = readBody(in.readByte(), in);
            return in.available() == 0 ? m : null;
        } catch (IOException | IllegalArgumentException e) {
            return null;
        }
    }

    private static Object readBody(byte op, DataInputStream in) throws IOException {
        switch (op) {
            case SLASH: {
                String ns = name(in);
                double x = in.readDouble();
                double y = in.readDouble();
                double z = in.readDouble();
                float yaw = in.readFloat();
                float pitch = in.readFloat();
                float roll = in.readFloat();
                float offset = in.readFloat();
                int color = in.readInt();
                float rank = in.readFloat();
                float size = in.readFloat();
                int life = in.readUnsignedByte();
                finite(x, y, z, yaw, pitch, roll, offset, rank, size);
                return new Slash(ns, x, y, z, yaw, pitch, roll, offset, color, rank, size, life);
            }
            case JUDGEMENT_CUT: {
                String ns = name(in);
                double x = in.readDouble();
                double y = in.readDouble();
                double z = in.readDouble();
                float yaw = in.readFloat();
                float pitch = in.readFloat();
                int color = in.readInt();
                int life = in.readUnsignedByte();
                finite(x, y, z, yaw, pitch);
                return new JudgementCut(ns, x, y, z, yaw, pitch, color, life);
            }
            case PROJECTILE: {
                String ns = name(in);
                String effect = name(in);
                int id = in.readInt();
                int kind = in.readUnsignedByte();
                if (kind != KIND_DRIVE && kind != KIND_SWORD) {
                    return null;
                }
                double x = in.readDouble();
                double y = in.readDouble();
                double z = in.readDouble();
                double vx = in.readDouble();
                double vy = in.readDouble();
                double vz = in.readDouble();
                float yaw = in.readFloat();
                float pitch = in.readFloat();
                float roll = in.readFloat();
                int color = in.readInt();
                float life = in.readFloat();
                finite(x, y, z, vx, vy, vz, yaw, pitch, roll, life);
                return new Projectile(ns, effect, id, kind, x, y, z, vx, vy, vz, yaw, pitch, roll, color, life);
            }
            case MOVE: {
                int id = in.readInt();
                double x = in.readDouble();
                double y = in.readDouble();
                double z = in.readDouble();
                double vx = in.readDouble();
                double vy = in.readDouble();
                double vz = in.readDouble();
                float yaw = in.readFloat();
                float pitch = in.readFloat();
                finite(x, y, z, vx, vy, vz, yaw, pitch);
                return new Move(id, x, y, z, vx, vy, vz, yaw, pitch);
            }
            case STICK: {
                int id = in.readInt();
                int entity = in.readInt();
                float offset = in.readFloat();
                finite(offset);
                return new Stick(id, entity, offset);
            }
            case REMOVE:
                return new Remove(in.readInt(), in.readBoolean());
            case LOCK:
                return new Lock(name(in), in.readInt(), in.readInt());
            case RANK:
                return new Rank(name(in), in.readInt());
            case HITS:
                return new Hits(name(in), in.readInt());
            default:
                return null;
        }
    }

    private static String name(DataInputStream in) throws IOException {
        String s = in.readUTF();
        if (!NAMESPACE.matcher(s)
            .matches()) {
            throw new IllegalArgumentException(s);
        }
        return s;
    }

    private static void finite(double... values) {
        for (double v : values) {
            if (!Double.isFinite(v)) {
                throw new IllegalArgumentException("not finite");
            }
        }
    }

    /** SlashBlade's concentration rank code (0 none, 1 D, 2 C, 3 B, 4 A, 5 S, 6 SS, 7 SSS) of a rank level. */
    public static int rankCode(float level) {
        if (level < 1) {
            return 0;
        }
        if (level < 5) {
            return (int) level;
        }
        if (level < 5.25F) {
            return 5;
        }
        return level < 5.5F ? 6 : 7;
    }
}
