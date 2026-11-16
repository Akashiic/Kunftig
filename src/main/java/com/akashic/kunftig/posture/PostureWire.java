package com.akashic.kunftig.posture;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Postures on the wire (API 15, channel {@link #CHANNEL}, server to client): a player sits or lies at a point, of the
 * world or of a display's frame (a ship's chair, bunk, hammock), and every Kunftig client holds and draws him there.
 * One message per change: version, the player's entity id, the kind ({@link #STAND} ends it), the display (-1: the
 * world), the point (the feet of one who sits; the middle of the body of one who lies), the yaw (where one who sits
 * faces, where the head of one who lies points; added to the display's) and how high above the point the eyes of
 * one who lies are. No Minecraft classes.
 */
public final class PostureWire {

    public static final String CHANNEL = "kunftig:pose";
    public static final byte VERSION = 1;

    public static final byte STAND = 0;
    public static final byte SIT = 1;
    public static final byte LIE = 2;

    /** No display: the point and the yaw are the world's. */
    public static final int WORLD = -1;
    /** Vanilla's eye height: the eyes of one who lies are never asked higher than this above the point. */
    public static final float STANDING_EYE = 1.62f;

    /** One player's posture. Immutable. */
    public static final class Posture {

        public final int entity;
        public final byte kind;
        public final int display;
        public final double x;
        public final double y;
        public final double z;
        public final float yaw;
        public final float eye;

        public Posture(int entity, byte kind, int display, double x, double y, double z, float yaw, float eye) {
            this.entity = entity;
            this.kind = kind;
            this.display = display;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.eye = eye;
        }

        public Posture forEntity(int id) {
            return new Posture(id, kind, display, x, y, z, yaw, eye);
        }
    }

    private PostureWire() {}

    public static Posture stand(int entity) {
        return new Posture(entity, STAND, WORLD, 0, 0, 0, 0, STANDING_EYE);
    }

    public static byte[] encode(Posture p) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(48);
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeByte(VERSION);
            out.writeInt(p.entity);
            out.writeByte(p.kind);
            out.writeInt(p.display);
            out.writeDouble(p.x);
            out.writeDouble(p.y);
            out.writeDouble(p.z);
            out.writeFloat(p.yaw);
            out.writeFloat(p.eye);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return bytes.toByteArray();
    }

    /**
     * The message, or null when it is not one this version reads (another version, cut short, numbers that are not).
     */
    public static Posture decode(byte[] data) {
        if (data == null || data.length < 1 || data[0] != VERSION) {
            return null;
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data, 1, data.length - 1))) {
            int entity = in.readInt();
            byte kind = in.readByte();
            int display = in.readInt();
            double x = in.readDouble();
            double y = in.readDouble();
            double z = in.readDouble();
            float yaw = in.readFloat();
            float eye = in.readFloat();
            if (kind < STAND || kind > LIE || !finite(x) || !finite(y) || !finite(z) || !finite(yaw) || !finite(eye)) {
                return null;
            }
            return new Posture(entity, kind, display < 0 ? WORLD : display, x, y, z, yaw, clampEye(eye));
        } catch (IOException e) {
            return null;
        }
    }

    public static float clampEye(float eye) {
        return eye < 0 ? 0 : Math.min(eye, STANDING_EYE);
    }

    /** How far the local player who lies is lowered so that his eyes are {@code eye} above the point. */
    public static float drop(byte kind, float eye) {
        return kind == LIE ? STANDING_EYE - clampEye(eye) : 0;
    }

    /** Half the body of one who lies: the model is laid from here back, so that its middle is on the point. */
    public static final float HALF_BODY = 0.9f;

    private static boolean finite(double v) {
        return !Double.isNaN(v) && !Double.isInfinite(v);
    }

    /** The channel name as the client registers it. */
    public static byte[] registration() {
        return CHANNEL.getBytes(StandardCharsets.UTF_8);
    }
}
