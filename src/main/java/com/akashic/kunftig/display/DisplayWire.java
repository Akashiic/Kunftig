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
 * SHOW   (0): int id, double x, y, z, float yaw, UTF model, UTF variant, UTF state, int elapsed, items
 *             [API 9: float pitch, float roll, double vx, vy, vz]
 * STATE  (1): int id, UTF state, int elapsed
 * ITEMS  (2): int id, items
 * MOVE   (3): int id, double x, y, z, float yaw [API 9: ubyte glideTicks, float pitch, float roll]
 * HIDE   (4): int id
 * MOTION (5): int id, double x, y, z, double vx, vy, vz                                            (API 9)
 * items:      byte count, then per item: unsigned short length (0: empty bone), bytes
 * </pre>
 *
 * {@code elapsed}: server ticks the state has been playing (a player who arrives late sees it from there).
 * {@code glideTicks}: the client moves there over that many ticks instead of jumping (0: jumps). {@code vx, vy, vz}:
 * blocks per tick the display flies on its own, on the client and on the server alike (a projectile), until the next
 * MOVE or MOTION. The API 9 fields go at the end, so an older client reads what it knows and skips them (it sees the
 * display jump, stand upright, and not fly). Kunftig clients register the channel on join; a client that does not
 * know an operation ignores the message.
 */
public final class DisplayWire {

    /** Registered by Kunftig clients that draw displays (Bukkit 1.7 allows at most 16 characters). */
    public static final String CHANNEL = "kunftig:display";

    public static final byte SHOW = 0;
    public static final byte STATE = 1;
    public static final byte ITEMS = 2;
    public static final byte MOVE = 3;
    public static final byte HIDE = 4;
    public static final byte MOTION = 5;

    public static final int MAX_ITEMS = 8;
    public static final int MAX_ITEM_BYTES = 0xFFFF;
    public static final int MAX_NAME = 160;
    /** Fastest flight, blocks per tick on each axis (a vanilla arrow at full draw does about 3). */
    public static final double MAX_SPEED = 10;
    /** Longest glide in ticks. */
    public static final int MAX_GLIDE = 255;

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
        public float pitch;
        public float roll;
        public double vx;
        public double vy;
        public double vz;
        public int glideTicks;

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
        return show(id, x, y, z, yaw, 0, 0, 0, 0, 0, model, variant, state, elapsed, items);
    }

    /** SHOW with the API 9 fields: the full rotation and the flight (blocks per tick). */
    public static byte[] show(int id, double x, double y, double z, float yaw, float pitch, float roll, double vx,
        double vy, double vz, String model, String variant, String state, int elapsed, List<byte[]> items) {
        byte[] base = write(SHOW, id, x, y, z, yaw, model, variant, state, elapsed, items);
        return append(base, out -> {
            out.writeFloat(pitch);
            out.writeFloat(roll);
            out.writeDouble(speed(vx));
            out.writeDouble(speed(vy));
            out.writeDouble(speed(vz));
        });
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

    /** MOVE with the API 9 fields: glide there over {@code glideTicks} (0: jump), with the full rotation. */
    public static byte[] move(int id, double x, double y, double z, float yaw, int glideTicks, float pitch,
        float roll) {
        byte[] base = write(MOVE, id, x, y, z, yaw, null, null, null, 0, null);
        return append(base, out -> {
            out.writeByte(Math.max(0, Math.min(MAX_GLIDE, glideTicks)));
            out.writeFloat(pitch);
            out.writeFloat(roll);
        });
    }

    /** MOTION: from {@code x, y, z} the display flies {@code vx, vy, vz} blocks per tick. */
    public static byte[] motion(int id, double x, double y, double z, double vx, double vy, double vz) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeByte(MOTION);
            out.writeInt(id);
            out.writeDouble(x);
            out.writeDouble(y);
            out.writeDouble(z);
            out.writeDouble(speed(vx));
            out.writeDouble(speed(vy));
            out.writeDouble(speed(vz));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return bytes.toByteArray();
    }

    /** A speed within {@link #MAX_SPEED} (not a number: 0). */
    public static double speed(double v) {
        return Double.isNaN(v) ? 0 : Math.max(-MAX_SPEED, Math.min(MAX_SPEED, v));
    }

    private interface Extra {

        void write(DataOutputStream out) throws IOException;
    }

    private static byte[] append(byte[] base, Extra extra) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.write(base);
            extra.write(out);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return bytes.toByteArray();
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
            if (op < SHOW || op > MOTION) {
                return null;
            }
            int id = in.readInt();
            if (op == MOTION) {
                double[] v = new double[6];
                for (int i = 0; i < 6; i++) {
                    v[i] = in.readDouble();
                    if (!Double.isFinite(v[i])) {
                        return null;
                    }
                }
                Message m = new Message(op, id, v[0], v[1], v[2], 0, null, null, null, 0, Collections.emptyList());
                m.vx = speed(v[3]);
                m.vy = speed(v[4]);
                m.vz = speed(v[5]);
                return m;
            }
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
            Message m = new Message(op, id, x, y, z, yaw, model, variant, state, elapsed, items);
            // API 9 fields at the end, absent from an older server's messages
            if (op == SHOW && in.available() >= 32) {
                m.pitch = in.readFloat();
                m.roll = in.readFloat();
                m.vx = speed(in.readDouble());
                m.vy = speed(in.readDouble());
                m.vz = speed(in.readDouble());
            } else if (op == MOVE && in.available() >= 9) {
                m.glideTicks = in.readUnsignedByte();
                m.pitch = in.readFloat();
                m.roll = in.readFloat();
            }
            if (!Float.isFinite(m.pitch) || !Float.isFinite(m.roll)
                || !Double.isFinite(m.vx)
                || !Double.isFinite(m.vy)
                || !Double.isFinite(m.vz)) {
                return null;
            }
            return m;
        } catch (IOException e) {
            return null;
        }
    }
}
