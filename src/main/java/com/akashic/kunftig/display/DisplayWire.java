package com.akashic.kunftig.display;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * How displays travel: a plugin message on {@link #CHANNEL} (a raw {@code S3FPacketCustomPayload}), one operation per
 * message. Items are opaque bytes (the stack's NBT, written by the server bridge, read by the client). No Minecraft
 * classes here.
 *
 * <pre>
 * SHOW  (0): int id, double x, y, z, float yaw, UTF model, UTF variant, UTF state, int elapsed, items
 *            [API 9: float sx, sy, sz, bones]
 * STATE (1): int id, UTF state, int elapsed
 * ITEMS (2): int id, items
 * MOVE  (3): int id, double x, y, z, float yaw [API 9: unsigned short ticks]
 * HIDE  (4): int id
 * SCALE (5): int id, float sx, sy, sz                                   (API 9)
 * BONES (6): int id, bones                                              (API 9)
 * items:     byte count, then per item: unsigned short length (0: empty bone), bytes
 * bones:     unsigned short count, then per bone: UTF name, boolean visible (the bones whose visibility the server
 *            set; every other bone keeps the manifest's default)
 * </pre>
 *
 * {@code elapsed}: server ticks the state has been playing (a player who arrives late sees it from there).
 * {@code ticks}
 * of a move: over how many client ticks the client glides there (0: at once). The API 9 parts are added at the end of
 * existing operations or as new operations, so an API 8 client still reads everything it knows (it stops reading
 * before the extra bytes and ignores unknown operations). Kunftig clients register the channel on join; those that
 * also draw the API 9 parts register {@link #CHANNEL_V2} as well.
 */
public final class DisplayWire {

    /** Registered by Kunftig clients that draw displays (Bukkit 1.7 allows at most 16 characters). */
    public static final String CHANNEL = "kunftig:display";
    /**
     * Registered, besides {@link #CHANNEL}, by clients that draw scale, hidden bones, transitions and glides (API 9).
     * Nothing is sent on it: it only tells the server what the client can draw.
     */
    public static final String CHANNEL_V2 = "kunftig:display2";

    public static final byte SHOW = 0;
    public static final byte STATE = 1;
    public static final byte ITEMS = 2;
    public static final byte MOVE = 3;
    public static final byte HIDE = 4;
    public static final byte SCALE = 5;
    public static final byte BONES = 6;

    public static final int MAX_ITEMS = 8;
    public static final int MAX_ITEM_BYTES = 0xFFFF;
    public static final int MAX_NAME = 160;
    public static final int MAX_BONES = 512;
    public static final int MAX_MOVE_TICKS = 200;
    public static final float MIN_SCALE = 0.01f;
    public static final float MAX_SCALE = 64f;

    private static final float[] NO_SCALE = { 1, 1, 1 };

    private DisplayWire() {}

    /** A decoded message. Fields not carried by the operation are 0 / empty / null (scale: 1, 1, 1). */
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
        /** SHOW and SCALE: the display's scale on its X, Y and Z (on top of the manifest's). */
        public final float[] scale;
        /** SHOW and BONES: the bones whose visibility the server set. */
        public final Map<String, Boolean> bones;
        /** MOVE: client ticks to glide over. */
        public final int ticks;

        Message(byte op, int id, double x, double y, double z, float yaw, String model, String variant, String state,
            int elapsed, List<byte[]> items, float[] scale, Map<String, Boolean> bones, int ticks) {
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
            this.scale = scale;
            this.bones = bones;
            this.ticks = ticks;
        }
    }

    /** API 8 SHOW (scale 1, no bone set). */
    public static byte[] show(int id, double x, double y, double z, float yaw, String model, String variant,
        String state, int elapsed, List<byte[]> items) {
        return show(id, x, y, z, yaw, model, variant, state, elapsed, items, NO_SCALE, Collections.emptyMap());
    }

    public static byte[] show(int id, double x, double y, double z, float yaw, String model, String variant,
        String state, int elapsed, List<byte[]> items, float[] scale, Map<String, Boolean> bones) {
        return write(SHOW, id, x, y, z, yaw, model, variant, state, elapsed, items, scale, bones, 0);
    }

    public static byte[] state(int id, String state, int elapsed) {
        return write(STATE, id, 0, 0, 0, 0, null, null, state, elapsed, null, null, null, 0);
    }

    public static byte[] items(int id, List<byte[]> items) {
        return write(ITEMS, id, 0, 0, 0, 0, null, null, null, 0, items, null, null, 0);
    }

    public static byte[] move(int id, double x, double y, double z, float yaw) {
        return move(id, x, y, z, yaw, 0);
    }

    public static byte[] move(int id, double x, double y, double z, float yaw, int ticks) {
        return write(MOVE, id, x, y, z, yaw, null, null, null, 0, null, null, null, ticks);
    }

    public static byte[] hide(int id) {
        return write(HIDE, id, 0, 0, 0, 0, null, null, null, 0, null, null, null, 0);
    }

    public static byte[] scale(int id, float[] scale) {
        return write(SCALE, id, 0, 0, 0, 0, null, null, null, 0, null, scale, null, 0);
    }

    public static byte[] bones(int id, Map<String, Boolean> bones) {
        return write(BONES, id, 0, 0, 0, 0, null, null, null, 0, null, null, bones, 0);
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

    /** A scale kept within {@link #MIN_SCALE} .. {@link #MAX_SCALE} on each axis (NaN: 1). */
    public static float clampScale(double v) {
        if (Double.isNaN(v)) {
            return 1;
        }
        return (float) Math.max(MIN_SCALE, Math.min(MAX_SCALE, v));
    }

    private static String cut(String s) {
        String v = s == null ? "" : s;
        return v.length() > MAX_NAME ? v.substring(0, MAX_NAME) : v;
    }

    private static void writeBones(DataOutputStream out, Map<String, Boolean> bones) throws IOException {
        int n = bones == null ? 0 : Math.min(MAX_BONES, bones.size());
        out.writeShort(n);
        if (n == 0) {
            return;
        }
        int i = 0;
        for (Map.Entry<String, Boolean> e : bones.entrySet()) {
            if (i++ == n) {
                break;
            }
            out.writeUTF(cut(e.getKey()));
            out.writeBoolean(Boolean.TRUE.equals(e.getValue()));
        }
    }

    private static Map<String, Boolean> readBones(DataInputStream in) throws IOException {
        int n = in.readUnsignedShort();
        if (n > MAX_BONES) {
            throw new IOException("too many bones");
        }
        Map<String, Boolean> out = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            out.put(cut(in.readUTF()), in.readBoolean());
        }
        return Collections.unmodifiableMap(out);
    }

    private static void writeScale(DataOutputStream out, float[] scale) throws IOException {
        float[] s = scale == null || scale.length != 3 ? NO_SCALE : scale;
        for (float v : s) {
            out.writeFloat(clampScale(v));
        }
    }

    private static float[] readScale(DataInputStream in) throws IOException {
        float[] s = new float[3];
        for (int i = 0; i < 3; i++) {
            float v = in.readFloat();
            if (!Float.isFinite(v)) {
                throw new IOException("bad scale");
            }
            s[i] = clampScale(v);
        }
        return s;
    }

    private static byte[] write(byte op, int id, double x, double y, double z, float yaw, String model, String variant,
        String state, int elapsed, List<byte[]> items, float[] scale, Map<String, Boolean> bones, int ticks) {
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
            if (op == SHOW || op == SCALE) {
                writeScale(out, scale);
            }
            if (op == SHOW || op == BONES) {
                writeBones(out, bones);
            }
            if (op == MOVE) {
                out.writeShort(Math.max(0, Math.min(MAX_MOVE_TICKS, ticks)));
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
            if (op < SHOW || op > BONES) {
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
            float[] scale = NO_SCALE.clone();
            Map<String, Boolean> bones = Collections.emptyMap();
            int ticks = 0;
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
            // API 9: SHOW may end here (an API 8 server); the rest is optional on SHOW and MOVE
            if (op == SCALE || (op == SHOW && in.available() > 0)) {
                scale = readScale(in);
            }
            if (op == BONES || (op == SHOW && in.available() > 0)) {
                bones = readBones(in);
            }
            if (op == MOVE && in.available() >= 2) {
                ticks = Math.min(MAX_MOVE_TICKS, in.readUnsignedShort());
            }
            return new Message(op, id, x, y, z, yaw, model, variant, state, elapsed, items, scale, bones, ticks);
        } catch (IOException e) {
            return null;
        }
    }
}
