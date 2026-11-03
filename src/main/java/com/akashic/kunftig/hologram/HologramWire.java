package com.akashic.kunftig.hologram;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * How holograms travel: a plugin message on {@link #CHANNEL} (a raw {@code S3FPacketCustomPayload}), one operation per
 * message. No Minecraft classes here.
 *
 * <pre>
 * SHOW   (0): int id, double x, y, z, byte lines, UTF line...   (also replaces one already shown)
 * LINES  (1): int id, byte lines, UTF line...
 * MOVE   (2): int id, double x, y, z
 * HIDE   (3): int id
 * </pre>
 *
 * Kunftig clients register the channel on join (the server only sends to them); a client of a newer server that does
 * not know an operation ignores the message.
 */
public final class HologramWire {

    /** Registered by Kunftig clients that draw holograms (Bukkit 1.7 allows at most 16 characters). */
    public static final String CHANNEL = "kunftig:holo";

    public static final byte SHOW = 0;
    public static final byte LINES = 1;
    public static final byte MOVE = 2;
    public static final byte HIDE = 3;

    public static final int MAX_LINES = 16;
    public static final int MAX_LINE_LENGTH = 256;

    private HologramWire() {}

    /** A decoded message. Fields not carried by the operation are 0 / empty. */
    public static final class Message {

        public final byte op;
        public final int id;
        public final double x;
        public final double y;
        public final double z;
        public final List<String> lines;

        Message(byte op, int id, double x, double y, double z, List<String> lines) {
            this.op = op;
            this.id = id;
            this.x = x;
            this.y = y;
            this.z = z;
            this.lines = lines;
        }
    }

    public static byte[] show(int id, double x, double y, double z, List<String> lines) {
        return write(SHOW, id, x, y, z, lines);
    }

    public static byte[] lines(int id, List<String> lines) {
        return write(LINES, id, 0, 0, 0, lines);
    }

    public static byte[] move(int id, double x, double y, double z) {
        return write(MOVE, id, x, y, z, null);
    }

    public static byte[] hide(int id) {
        return write(HIDE, id, 0, 0, 0, null);
    }

    /** At most {@link #MAX_LINES} lines, each cut at {@link #MAX_LINE_LENGTH} characters; null lines become empty. */
    public static List<String> clean(List<String> lines) {
        List<String> out = new ArrayList<>();
        if (lines != null) {
            for (String line : lines) {
                if (out.size() == MAX_LINES) {
                    break;
                }
                String l = line == null ? "" : line;
                out.add(l.length() > MAX_LINE_LENGTH ? l.substring(0, MAX_LINE_LENGTH) : l);
            }
        }
        return Collections.unmodifiableList(out);
    }

    private static byte[] write(byte op, int id, double x, double y, double z, List<String> lines) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeByte(op);
            out.writeInt(id);
            if (op == SHOW || op == MOVE) {
                out.writeDouble(x);
                out.writeDouble(y);
                out.writeDouble(z);
            }
            if (op == SHOW || op == LINES) {
                List<String> clean = clean(lines);
                out.writeByte(clean.size());
                for (String line : clean) {
                    out.writeUTF(line);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException(e); // a byte array never fails
        }
        return bytes.toByteArray();
    }

    /** The message in {@code data}, or null when malformed or of an unknown operation (then do nothing). */
    public static Message read(byte[] data) {
        if (data == null || data.length < 5) {
            return null;
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
            byte op = in.readByte();
            if (op < SHOW || op > HIDE) {
                return null;
            }
            int id = in.readInt();
            double x = 0;
            double y = 0;
            double z = 0;
            if (op == SHOW || op == MOVE) {
                x = in.readDouble();
                y = in.readDouble();
                z = in.readDouble();
                if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
                    return null;
                }
            }
            List<String> lines = Collections.emptyList();
            if (op == SHOW || op == LINES) {
                int n = in.readUnsignedByte();
                if (n > MAX_LINES) {
                    return null;
                }
                List<String> read = new ArrayList<>(n);
                for (int i = 0; i < n; i++) {
                    String line = in.readUTF();
                    read.add(line.length() > MAX_LINE_LENGTH ? line.substring(0, MAX_LINE_LENGTH) : line);
                }
                lines = Collections.unmodifiableList(read);
            }
            return new Message(op, id, x, y, z, lines);
        } catch (IOException e) {
            return null;
        }
    }
}
