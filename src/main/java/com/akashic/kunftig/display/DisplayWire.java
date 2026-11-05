package com.akashic.kunftig.display;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * How displays travel: a plugin message on {@link #CHANNEL} (a raw {@code S3FPacketCustomPayload}), one operation per
 * message. Items are opaque bytes (the stack's NBT, written by the server bridge, read by the client). No Minecraft
 * classes here.
 *
 * <pre>
 * SHOW  (0): int id, double x, y, z, float yaw, UTF model, UTF variant, UTF state, int elapsed, items
 * STATE (1): int id, UTF state, int elapsed
 * ITEMS (2): int id, items
 * MOVE  (3): int id, double x, y, z, float yaw
 * HIDE  (4): int id
 * items:     byte count, then per item: unsigned short length (0: empty bone), bytes
 * </pre>
 *
 * {@code elapsed}: server ticks the state has been playing (a player who arrives late sees it from there). Kunftig
 * clients register the channel on join; a client that does not know an operation ignores the message.
 */
public final class DisplayWire {

    /** Registered by Kunftig clients that draw displays (Bukkit 1.7 allows at most 16 characters). */
    public static final String CHANNEL = "kunftig:display";

    public static final byte SHOW = 0;
    public static final byte STATE = 1;
    public static final byte ITEMS = 2;
    public static final byte MOVE = 3;
    public static final byte HIDE = 4;

    public static final int MAX_ITEMS = 8;
    public static final int MAX_ITEM_BYTES = 0xFFFF;
    public static final int MAX_NAME = 160;

    private DisplayWire() {}

    /** A decoded message. Fields not carried by the operation are 0 / empty / null. */
    public static final class Message {

        public final byte op;
        public final int id;
        public final double x;
        public final double y;
        public final double z;
        public final float yaw;
        public final String model;
        public final String variant;
        public final String state;
        public final int elapsed;
        public final List<byte[]> items;

        Message(byte op, int id, double x, double y, double z, float yaw, String model, String variant, String state,
            int elapsed, List<byte[]> items) {
            this.op = op;
            this.id = id;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.model = model;
            this.variant = variant;
            this.state = state;
            this.elapsed = elapsed;
            this.items = items;
        }
    }

    public static byte[] show(int id, double x, double y, double z, float yaw, String model, String variant,
        String state, int elapsed, List<byte[]> items) {
        return write(SHOW, id, x, y, z, yaw, model, variant, state, elapsed, items);
    }

    public static byte[] state(int id, String state, int elapsed) {
        return write(STATE, id, 0, 0, 0, 0, null, null, state, elapsed, null);
    }

    public static byte[] items(int id, List<byte[]> items) {
        return write(ITEMS, id, 0, 0, 0, 0, null, null, null, 0, items);
    }

    public static byte[] move(int id, double x, double y, double z, float yaw) {
        return write(MOVE, id, x, y, z, yaw, null, null, null, 0, null);
    }

    public static byte[] hide(int id) {
        return write(HIDE, id, 0, 0, 0, 0, null, null, null, 0, null);
    }

    /** At most {@link #MAX_ITEMS}; null and oversized entries become empty (nothing drawn in that bone). */
    public static List<byte[]> clean(List<byte[]> items) {
        List<byte[]> out = new ArrayList<>();
        if (items != null) {
            for (byte[] item : items) {
                if (out.size() == MAX_ITEMS) {
                    break;
                }
                out.add(item == null || item.length > MAX_ITEM_BYTES ? new byte[0] : item);
            }
        }
        return Collections.unmodifiableList(out);
    }

    private static String cut(String s) {
        String v = s == null ? "" : s;
        return v.length() > MAX_NAME ? v.substring(0, MAX_NAME) : v;
    }

    private static byte[] write(byte op, int id, double x, double y, double z, float yaw, String model, String variant,
        String state, int elapsed, List<byte[]> items) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeByte(op);
            out.writeInt(id);
            if (op == SHOW || op == MOVE) {
                out.writeDouble(x);
                out.writeDouble(y);
                out.writeDouble(z);
                out.writeFloat(yaw);
            }
            if (op == SHOW) {
                out.writeUTF(cut(model));
                out.writeUTF(cut(variant));
            }
            if (op == SHOW || op == STATE) {
                out.writeUTF(cut(state));
                out.writeInt(Math.max(0, elapsed));
            }
            if (op == SHOW || op == ITEMS) {
                List<byte[]> clean = clean(items);
                out.writeByte(clean.size());
                for (byte[] item : clean) {
                    out.writeShort(item.length);
                    out.write(item);
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
            float yaw = 0;
            String model = null;
            String variant = null;
            String state = null;
            int elapsed = 0;
            List<byte[]> items = Collections.emptyList();
            if (op == SHOW || op == MOVE) {
                x = in.readDouble();
                y = in.readDouble();
                z = in.readDouble();
                yaw = in.readFloat();
                if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z) || !Float.isFinite(yaw)) {
                    return null;
                }
            }
            if (op == SHOW) {
                model = cut(in.readUTF());
                variant = cut(in.readUTF());
            }
            if (op == SHOW || op == STATE) {
                state = cut(in.readUTF());
                elapsed = Math.max(0, in.readInt());
            }
            if (op == SHOW || op == ITEMS) {
                int n = in.readUnsignedByte();
                if (n > MAX_ITEMS) {
                    return null;
                }
                List<byte[]> read = new ArrayList<>(n);
                for (int i = 0; i < n; i++) {
                    byte[] item = new byte[in.readUnsignedShort()];
                    in.readFully(item);
                    read.add(item);
                }
                items = Collections.unmodifiableList(read);
            }
            return new Message(op, id, x, y, z, yaw, model, variant, state, elapsed, items);
        } catch (IOException e) {
            return null;
        }
    }
}
