package com.akashic.kunftig.blade;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * How the state of a player's SlashBlade-style blade travels to the clients that draw it, on {@link #CHANNEL} (raw
 * plugin messages). No Minecraft classes.
 *
 * <pre>
 * STATE  (0): int entityId, int elapsedTicks, ubyte n, n x segment, segment idle,
 *             boolean body [short start, short end, boolean blendLegs, int bodyElapsedTicks], boolean charged
 * CHARGE (1): int entityId, boolean charged
 * segment:    short startFrame, short endFrame, boolean loop, int durationMs
 * </pre>
 *
 * {@code elapsedTicks} is how long ago the chain started (a player who comes closer later joins it mid-way). The
 * body clip plays {@code (end - start) * 2/3} ticks of the player motion, then the body is vanilla again.
 */
public final class BladeWire {

    public static final String CHANNEL = "kunftig:blade";
    public static final byte STATE = 0;
    public static final byte CHARGE = 1;
    public static final int MAX_SEGMENTS = 16;

    private BladeWire() {}

    /** A body clip of the player motion. */
    public static final class Body {

        public final int start;
        public final int end;
        public final boolean blendLegs;

        public Body(int start, int end, boolean blendLegs) {
            this.start = start;
            this.end = end;
            this.blendLegs = blendLegs;
        }

        /** Ticks the clip plays: {@code (end - start) * 20/30}, as SlashBlade's player animation. */
        public int spanTicks() {
            return (int) ((end - start) * 20.0 / 30.0);
        }
    }

    /** A decoded message. */
    public static final class Message {

        public final byte op;
        public final int entityId;
        public final int elapsedTicks;
        public final List<BladeTimeline.Segment> chain;
        public final BladeTimeline.Segment idle;
        public final Body body;
        public final int bodyElapsedTicks;
        public final boolean charged;

        Message(byte op, int entityId, int elapsedTicks, List<BladeTimeline.Segment> chain, BladeTimeline.Segment idle,
            Body body, int bodyElapsedTicks, boolean charged) {
            this.op = op;
            this.entityId = entityId;
            this.elapsedTicks = elapsedTicks;
            this.chain = chain;
            this.idle = idle;
            this.body = body;
            this.bodyElapsedTicks = bodyElapsedTicks;
            this.charged = charged;
        }
    }

    public static byte[] state(int entityId, int elapsedTicks, List<BladeTimeline.Segment> chain,
        BladeTimeline.Segment idle, Body body, int bodyElapsedTicks, boolean charged) {
        if (chain.size() > MAX_SEGMENTS) {
            throw new IllegalArgumentException("at most " + MAX_SEGMENTS + " segments");
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeByte(STATE);
            out.writeInt(entityId);
            out.writeInt(Math.max(0, elapsedTicks));
            out.writeByte(chain.size());
            for (BladeTimeline.Segment s : chain) {
                segment(out, s);
            }
            segment(out, idle);
            out.writeBoolean(body != null);
            if (body != null) {
                out.writeShort(body.start);
                out.writeShort(body.end);
                out.writeBoolean(body.blendLegs);
                out.writeInt(Math.max(0, bodyElapsedTicks));
            }
            out.writeBoolean(charged);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return bytes.toByteArray();
    }

    public static byte[] charge(int entityId, boolean charged) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeByte(CHARGE);
            out.writeInt(entityId);
            out.writeBoolean(charged);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return bytes.toByteArray();
    }

    private static void segment(DataOutputStream out, BladeTimeline.Segment s) throws IOException {
        out.writeShort(s.start);
        out.writeShort(s.end);
        out.writeBoolean(s.loop);
        out.writeInt(s.durationMs);
    }

    private static BladeTimeline.Segment segment(DataInputStream in) throws IOException {
        int start = in.readUnsignedShort();
        int end = in.readUnsignedShort();
        boolean loop = in.readBoolean();
        int duration = in.readInt();
        return new BladeTimeline.Segment(start, end, loop, duration);
    }

    /** The message in {@code data}, or null when malformed or unknown. */
    public static Message read(byte[] data) {
        if (data == null || data.length < 1) {
            return null;
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
            byte op = in.readByte();
            if (op == CHARGE) {
                int id = in.readInt();
                return new Message(op, id, 0, null, null, null, 0, in.readBoolean());
            }
            if (op != STATE) {
                return null;
            }
            int id = in.readInt();
            int elapsed = in.readInt();
            int n = in.readUnsignedByte();
            if (n > MAX_SEGMENTS) {
                return null;
            }
            List<BladeTimeline.Segment> chain = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                chain.add(segment(in));
            }
            BladeTimeline.Segment idle = segment(in);
            Body body = null;
            int bodyElapsed = 0;
            if (in.readBoolean()) {
                body = new Body(in.readUnsignedShort(), in.readUnsignedShort(), in.readBoolean());
                bodyElapsed = in.readInt();
            }
            boolean charged = in.readBoolean();
            return new Message(op, id, Math.max(0, elapsed), chain, idle, body, Math.max(0, bodyElapsed), charged);
        } catch (IOException e) {
            return null;
        }
    }
}
