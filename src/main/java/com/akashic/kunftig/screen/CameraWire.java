package com.akashic.kunftig.screen;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * The camera lock-on (API 9): the server tells one player's client to keep turning his view toward an entity, as
 * plugin messages on {@link #CHANNEL}. No Minecraft classes.
 *
 * <pre>
 * LOCK   (0): int entityId, float strength (0..1), float maxDistance (blocks), boolean marker
 * UNLOCK (1): (nothing)
 * </pre>
 *
 * {@code strength}: how much of the way to the target the view turns per tick (1: snaps). The client lets go on its own
 * when the entity is gone, dead or farther than {@code maxDistance}. Unknown operations are ignored.
 */
public final class CameraWire {

    public static final String CHANNEL = "kunftig:camera";

    public static final byte LOCK = 0;
    public static final byte UNLOCK = 1;

    public static final float MAX_DISTANCE = 64;

    private CameraWire() {}

    /** A decoded message (for UNLOCK only {@code op}). */
    public static final class Message {

        public final byte op;
        public final int entityId;
        public final float strength;
        public final float maxDistance;
        public final boolean marker;

        Message(byte op, int entityId, float strength, float maxDistance, boolean marker) {
            this.op = op;
            this.entityId = entityId;
            this.strength = strength;
            this.maxDistance = maxDistance;
            this.marker = marker;
        }
    }

    /** Strength within 0.01..1 (not a number: the default 0.35). */
    public static float strength(float s) {
        return Float.isNaN(s) ? 0.35F : Math.max(0.01F, Math.min(1, s));
    }

    public static float distance(float d) {
        return Float.isNaN(d) ? 24 : Math.max(1, Math.min(MAX_DISTANCE, d));
    }

    public static byte[] lock(int entityId, float strength, float maxDistance, boolean marker) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeByte(LOCK);
            out.writeInt(entityId);
            out.writeFloat(strength(strength));
            out.writeFloat(distance(maxDistance));
            out.writeBoolean(marker);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return bytes.toByteArray();
    }

    public static byte[] unlock() {
        return new byte[] { UNLOCK };
    }

    public static Message read(byte[] data) {
        if (data == null || data.length < 1) {
            return null;
        }
        if (data[0] == UNLOCK) {
            return new Message(UNLOCK, 0, 0, 0, false);
        }
        if (data[0] != LOCK) {
            return null;
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
            in.readByte();
            int id = in.readInt();
            float s = in.readFloat();
            float d = in.readFloat();
            boolean marker = in.readBoolean();
            if (!Float.isFinite(s) || !Float.isFinite(d)) {
                return null;
            }
            return new Message(LOCK, id, strength(s), distance(d), marker);
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * How far to turn a view angle this frame: {@code strength} of the way per tick, for a frame lasting
     * {@code ticks} (a fraction at high frame rates), so the speed does not depend on the frame rate.
     */
    public static float step(float delta, float strength, float ticks) {
        double keep = Math.pow(1 - strength, Math.max(0, ticks));
        return (float) (delta * (1 - keep));
    }

    /** An angle difference in -180..180. */
    public static float wrap(float degrees) {
        float d = degrees % 360;
        if (d >= 180) {
            d -= 360;
        } else if (d < -180) {
            d += 360;
        }
        return d;
    }

    /** Yaw and pitch (Minecraft's: yaw 0 looks south, +Z; pitch positive looks down) from one point to another. */
    public static float[] look(double fromX, double fromY, double fromZ, double toX, double toY, double toZ) {
        double dx = toX - fromX;
        double dy = toY - fromY;
        double dz = toZ - fromZ;
        double flat = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90);
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, flat));
        return new float[] { yaw, pitch };
    }
}
