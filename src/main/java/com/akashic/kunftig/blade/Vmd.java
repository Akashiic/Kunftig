package com.akashic.kunftig.blade;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A VMD (MikuMikuDance motion) file, as SlashBlade plays it: bone keys only, 30 frames per second, sampled with a
 * linear lerp of positions and a normalized lerp of rotations ({@link Mmd#nlerp}); the files' Bézier curves are
 * ignored, as NyMMD does. Bones without keys keep the rest pose (no offset, identity). No Minecraft classes.
 */
public final class Vmd {

    /** Frames per second of MMD motions. */
    public static final double FPS = 30;
    /** Milliseconds per frame. */
    public static final double MS_PER_FRAME = 1000.0 / FPS;

    private static final Charset SJIS = Charset.forName("Shift_JIS");
    private static final String MAGIC = "Vocaloid Motion Data 0002";

    private static final class Key {

        final int frame;
        final double[] pos;
        final double[] rot;

        Key(int frame, double[] pos, double[] rot) {
            this.frame = frame;
            this.pos = pos;
            this.rot = rot;
        }
    }

    private final String model;
    private final Map<String, Key[]> bones;
    private final int maxFrame;

    private Vmd(String model, Map<String, Key[]> bones, int maxFrame) {
        this.model = model;
        this.bones = bones;
        this.maxFrame = maxFrame;
    }

    /** The model name in the file (e.g. "BladeHolder", "Alex"). */
    public String model() {
        return model;
    }

    /** The largest keyed frame in the file. */
    public int maxFrame() {
        return maxFrame;
    }

    public boolean has(String bone) {
        return bones.containsKey(bone);
    }

    public java.util.Set<String> bones() {
        return Collections.unmodifiableSet(bones.keySet());
    }

    /** A motion that failed to read. */
    public static final class FormatException extends Exception {

        private static final long serialVersionUID = 1L;

        FormatException(String message) {
            super(message);
        }
    }

    public static Vmd parse(byte[] data, String source) throws FormatException {
        try {
            ByteBuffer b = ByteBuffer.wrap(data)
                .order(ByteOrder.LITTLE_ENDIAN);
            String magic = text(b, 30);
            if (!magic.startsWith(MAGIC)) {
                throw new FormatException(source + ": not a VMD file (" + magic + ")");
            }
            String model = text(b, 20);
            int n = b.getInt();
            if (n < 0 || n > (data.length - 54) / 111) {
                throw new FormatException(source + ": bad bone key count " + n);
            }
            Map<String, List<Key>> keys = new HashMap<>();
            int max = 0;
            for (int i = 0; i < n; i++) {
                String name = text(b, 15);
                int frame = b.getInt();
                double[] pos = { b.getFloat(), b.getFloat(), b.getFloat() };
                double[] rot = Mmd.normalize(new double[] { b.getFloat(), b.getFloat(), b.getFloat(), b.getFloat() });
                b.position(b.position() + 64); // interpolation curves: ignored, as NyMMD does
                keys.computeIfAbsent(name, k -> new ArrayList<>())
                    .add(new Key(frame, pos, rot));
                max = Math.max(max, frame);
            }
            Map<String, Key[]> bones = new HashMap<>();
            for (Map.Entry<String, List<Key>> e : keys.entrySet()) {
                List<Key> list = e.getValue();
                list.sort(Comparator.comparingInt(k -> k.frame));
                bones.put(e.getKey(), list.toArray(new Key[0]));
            }
            return new Vmd(model, bones, max);
        } catch (java.nio.BufferUnderflowException e) {
            throw new FormatException(source + ": truncated VMD file");
        }
    }

    private static String text(ByteBuffer b, int len) {
        byte[] raw = new byte[len];
        b.get(raw);
        int end = 0;
        while (end < len && raw[end] != 0) {
            end++;
        }
        return new String(raw, 0, end, SJIS);
    }

    /**
     * A bone at {@code frame} (fractional): {pos x, y, z, rot x, y, z, w}. Past the last key: the last key. NyMMD's
     * sampling ({@code MotionData.getMotionPosRot}).
     */
    public double[] sample(String bone, double frame) {
        Key[] k = bones.get(bone);
        if (k == null || k.length == 0) {
            return new double[] { 0, 0, 0, 0, 0, 0, 1 };
        }
        double f = Math.min(frame, k[k.length - 1].frame);
        int i = 0;
        while (i < k.length && !(f < k[i].frame)) {
            i++;
        }
        if (i == k.length) {
            i = k.length - 1;
        }
        int k0 = i - 1;
        int k1 = k0 + 1;
        if (k1 == k.length) {
            k1 = k.length - 1;
        }
        if (k0 < 0) {
            k0 = 0;
        }
        double[] pos;
        double[] rot;
        if (k0 != k1) {
            double t = (f - k[k0].frame) / (double) (k[k1].frame - k[k0].frame);
            pos = new double[] { k[k0].pos[0] + (k[k1].pos[0] - k[k0].pos[0]) * t,
                k[k0].pos[1] + (k[k1].pos[1] - k[k0].pos[1]) * t, k[k0].pos[2] + (k[k1].pos[2] - k[k0].pos[2]) * t };
            rot = Mmd.nlerp(k[k0].rot, k[k1].rot, t);
        } else {
            pos = k[k0].pos;
            rot = k[k0].rot;
        }
        return new double[] { pos[0], pos[1], pos[2], rot[0], rot[1], rot[2], rot[3] };
    }

    /** A bone's local matrix at {@code frame} (rotation, then its VMD position: offsets are 0 in these rigs). */
    public double[] local(String bone, double frame) {
        double[] s = sample(bone, frame);
        return Mmd.matrix(new double[] { s[3], s[4], s[5], s[6] }, new double[] { s[0], s[1], s[2] });
    }
}
