package com.akashic.kunftig.rodmodel;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.regex.Pattern;

/**
 * How the state of the 3D rods travels from the server to the clients (issue #13): a plugin message on
 * {@link #CHANNEL} (a raw {@code S3FPacketCustomPayload}), one per message. No Minecraft classes.
 *
 * <pre>
 * STATE   (0): int entityId, UTF state, ubyte elapsedTicks, ubyte tension (0..100)
 * TENSION (1): int entityId, ubyte tension (0..100)
 * </pre>
 *
 * {@code entityId} is the angler's entity id; {@code elapsedTicks} (capped at 255) is how long the state has been
 * running, so a player who comes close mid-animation sees it at the right point. Kunftig clients that draw 3D rods
 * register the channel on join (the server only sends to them); unknown operations are ignored.
 */
public final class RodWire {

    /** Registered by Kunftig clients that draw 3D rods (Bukkit 1.7 allows at most 16 characters). */
    public static final String CHANNEL = "kunftig:rod";

    public static final byte STATE = 0;
    public static final byte TENSION = 1;

    /** State names: those of the manifest's {@code estados}. */
    public static final Pattern STATE_NAME = Pattern.compile("[a-z0-9_]{1,32}");
    public static final int MAX_ELAPSED = 255;
    /** Tension travels in hundredths. */
    public static final int TENSION_STEPS = 100;

    private RodWire() {}

    /** A decoded message. */
    public static final class Message {

        public final byte op;
        public final int entityId;
        /** Null for {@link #TENSION}. */
        public final String state;
        public final int elapsedTicks;
        public final float tension;

        Message(byte op, int entityId, String state, int elapsedTicks, float tension) {
            this.op = op;
            this.entityId = entityId;
            this.state = state;
            this.elapsedTicks = elapsedTicks;
            this.tension = tension;
        }
    }

    /** Tension 0..1 in hundredths, the unit it travels in. */
    public static int quantize(float tension) {
        if (!(tension > 0)) {
            return 0;
        }
        return Math.min(TENSION_STEPS, Math.round(tension * TENSION_STEPS));
    }

    public static byte[] state(int entityId, String state, long elapsedTicks, float tension) {
        if (state == null || !STATE_NAME.matcher(state)
            .matches()) {
            throw new IllegalArgumentException("invalid rod state: " + state);
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeByte(STATE);
            out.writeInt(entityId);
            out.writeUTF(state);
            out.writeByte((int) Math.max(0, Math.min(MAX_ELAPSED, elapsedTicks)));
            out.writeByte(quantize(tension));
        } catch (IOException e) {
            throw new IllegalStateException(e); // a byte array never fails
        }
        return bytes.toByteArray();
    }

    public static byte[] tension(int entityId, float tension) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeByte(TENSION);
            out.writeInt(entityId);
            out.writeByte(quantize(tension));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return bytes.toByteArray();
    }

    /** The message in {@code data}, or null when malformed or of an unknown operation (then do nothing). */
    public static Message read(byte[] data) {
        if (data == null || data.length < 6) {
            return null;
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
            byte op = in.readByte();
            int entityId = in.readInt();
            if (op == STATE) {
                String state = in.readUTF();
                if (!STATE_NAME.matcher(state)
                    .matches()) {
                    return null;
                }
                int elapsed = in.readUnsignedByte();
                int tension = in.readUnsignedByte();
                return new Message(op, entityId, state, elapsed, tension(tension));
            }
            if (op == TENSION) {
                return new Message(op, entityId, null, 0, tension(in.readUnsignedByte()));
            }
            return null;
        } catch (IOException e) {
            return null;
        }
    }

    private static float tension(int hundredths) {
        return Math.min(TENSION_STEPS, hundredths) / (float) TENSION_STEPS;
    }
}
