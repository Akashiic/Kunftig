package com.akashic.kunftig.screen;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.regex.Pattern;

/**
 * The screen HUD (API 11): elements a plugin places on one player's screen (text, a bar, a picture of the resource
 * pack), as plugin messages on {@link #CHANNEL}, one operation per message. No Minecraft classes.
 *
 * <pre>
 * SET    (0): UTF id, element
 * REMOVE (1): UTF id
 * CLEAR  (2): (nothing)
 * element:    ubyte anchor, short x, short y, float scale, UTF text, float progress (-1: no bar), short barWidth,
 *             int barColor, int barBackground, UTF texture ("" none), short textureWidth, short textureHeight,
 *             int durationTicks (0: until removed), ubyte fadeTicks
 * </pre>
 *
 * Positions are in GUI pixels (the scaled resolution) from the anchor, colors are ARGB. Kunftig clients register
 * the channel on join; unknown operations are ignored.
 */
public final class HudWire {

    public static final String CHANNEL = "kunftig:hud";

    public static final byte SET = 0;
    public static final byte REMOVE = 1;
    public static final byte CLEAR = 2;

    /** Anchors, in this order on the wire. */
    public static final int TOP_LEFT = 0;
    public static final int TOP = 1;
    public static final int TOP_RIGHT = 2;
    public static final int LEFT = 3;
    public static final int CENTER = 4;
    public static final int RIGHT = 5;
    public static final int BOTTOM_LEFT = 6;
    public static final int BOTTOM = 7;
    public static final int BOTTOM_RIGHT = 8;
    public static final int ANCHORS = 9;

    public static final Pattern ID = Pattern.compile("[a-z0-9_.-]{1,64}:[a-z0-9_./-]{1,64}");
    public static final Pattern TEXTURE = Pattern.compile("([a-z0-9_.-]{1,64}:[a-z0-9_./-]{1,128}\\.png)?");
    public static final int MAX_TEXT = 512;
    /** Elements a client keeps; more are dropped (oldest set first). */
    public static final int MAX_ELEMENTS = 32;

    private HudWire() {}

    /** One element. Immutable. */
    public static final class Element {

        public final int anchor;
        public final int x;
        public final int y;
        public final float scale;
        public final String text;
        /** 0..1, or negative for no bar. */
        public final float progress;
        public final int barWidth;
        public final int barColor;
        public final int barBackground;
        public final String texture;
        public final int textureWidth;
        public final int textureHeight;
        public final int durationTicks;
        public final int fadeTicks;

        public Element(int anchor, int x, int y, float scale, String text, float progress, int barWidth, int barColor,
            int barBackground, String texture, int textureWidth, int textureHeight, int durationTicks, int fadeTicks) {
            this.anchor = Math.max(0, Math.min(ANCHORS - 1, anchor));
            this.x = clampShort(x);
            this.y = clampShort(y);
            this.scale = Float.isFinite(scale) ? Math.max(0.25F, Math.min(8, scale)) : 1;
            String t = text == null ? "" : text;
            this.text = t.length() > MAX_TEXT ? t.substring(0, MAX_TEXT) : t;
            this.progress = !Float.isFinite(progress) || progress < 0 ? -1 : Math.min(1, progress);
            this.barWidth = Math.max(0, Math.min(1000, barWidth));
            this.barColor = barColor;
            this.barBackground = barBackground;
            this.texture = texture == null || !TEXTURE.matcher(texture)
                .matches() ? "" : texture;
            this.textureWidth = Math.max(0, Math.min(1000, textureWidth));
            this.textureHeight = Math.max(0, Math.min(1000, textureHeight));
            this.durationTicks = Math.max(0, durationTicks);
            this.fadeTicks = Math.max(0, Math.min(255, fadeTicks));
        }

        private static int clampShort(int v) {
            return Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, v));
        }
    }

    /** A decoded message; {@code id} and {@code element} are null when the operation has none. */
    public static final class Message {

        public final byte op;
        public final String id;
        public final Element element;

        Message(byte op, String id, Element element) {
            this.op = op;
            this.id = id;
            this.element = element;
        }
    }

    public static byte[] set(String id, Element e) {
        if (id == null || !ID.matcher(id)
            .matches() || e == null) {
            throw new IllegalArgumentException("invalid hud element " + id);
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeByte(SET);
            out.writeUTF(id);
            out.writeByte(e.anchor);
            out.writeShort(e.x);
            out.writeShort(e.y);
            out.writeFloat(e.scale);
            out.writeUTF(e.text);
            out.writeFloat(e.progress);
            out.writeShort(e.barWidth);
            out.writeInt(e.barColor);
            out.writeInt(e.barBackground);
            out.writeUTF(e.texture);
            out.writeShort(e.textureWidth);
            out.writeShort(e.textureHeight);
            out.writeInt(e.durationTicks);
            out.writeByte(e.fadeTicks);
        } catch (IOException ex) {
            throw new IllegalStateException(ex); // a byte array never fails
        }
        return bytes.toByteArray();
    }

    public static byte[] remove(String id) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeByte(REMOVE);
            out.writeUTF(id);
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
        return bytes.toByteArray();
    }

    public static byte[] clear() {
        return new byte[] { CLEAR };
    }

    /** The message in {@code data}, or null when malformed or unknown (then do nothing). */
    public static Message read(byte[] data) {
        if (data == null || data.length < 1) {
            return null;
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
            byte op = in.readByte();
            if (op == CLEAR) {
                return new Message(op, null, null);
            }
            if (op != SET && op != REMOVE) {
                return null;
            }
            String id = in.readUTF();
            if (!ID.matcher(id)
                .matches()) {
                return null;
            }
            if (op == REMOVE) {
                return new Message(op, id, null);
            }
            Element e = new Element(
                in.readUnsignedByte(),
                in.readShort(),
                in.readShort(),
                in.readFloat(),
                in.readUTF(),
                in.readFloat(),
                in.readShort(),
                in.readInt(),
                in.readInt(),
                in.readUTF(),
                in.readShort(),
                in.readShort(),
                in.readInt(),
                in.readUnsignedByte());
            return new Message(op, id, e);
        } catch (IOException ex) {
            return null;
        }
    }
}
