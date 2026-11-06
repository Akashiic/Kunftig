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
 * SLASH (0): UTF namespace, double x, y, z, float yaw, float pitch, float roll, float rotationOffset, int color (RGB),
 *            float rank (concentration rank level, 0 = none, 1 = D ... 5 = S, 5.25 = SS, 5.5 = SSS), float baseSize,
 *            ubyte lifetime (ticks; SlashBlade's clients always draw 10)
 * </pre>
 */
public final class EffectWire {

    public static final String CHANNEL = "kunftig:effect";
    public static final byte SLASH = 0;
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

    public static byte[] slash(Slash s) {
        if (!NAMESPACE.matcher(s.namespace)
            .matches()) {
            throw new IllegalArgumentException("invalid namespace " + s.namespace);
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeByte(SLASH);
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
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return bytes.toByteArray();
    }

    /** The slash in {@code data}, or null when malformed or another operation. */
    public static Slash readSlash(byte[] data) {
        if (data == null || data.length < 2 || data[0] != SLASH) {
            return null;
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
            in.readByte();
            String ns = in.readUTF();
            if (!NAMESPACE.matcher(ns)
                .matches()) {
                return null;
            }
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
            double[] finite = { x, y, z, yaw, pitch, roll, offset, rank, size };
            for (double f : finite) {
                if (!Double.isFinite(f)) {
                    return null;
                }
            }
            return new Slash(ns, x, y, z, yaw, pitch, roll, offset, color, rank, size, life);
        } catch (IOException e) {
            return null;
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
