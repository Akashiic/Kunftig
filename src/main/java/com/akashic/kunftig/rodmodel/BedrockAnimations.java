package com.akashic.kunftig.rodmodel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.akashic.kunftig.common.text.Json;

/**
 * A Bedrock animation file ({@code format_version} 1.8.0) read without GeckoLib: each animation's length and loop
 * flag, and the rotation keyframes of every bone, sampled linearly (the rod pack bakes its curves into linear
 * keyframes, issue #13). Used for the state timings and for the arm, a bone of the player's model that GeckoLib does
 * not animate. No Minecraft classes.
 */
public final class BedrockAnimations {

    private static final int MAX_DEPTH = 12;

    /** One keyframe: the value reached at {@code time} ({@code pre}) and the one leaving it ({@code post}). */
    static final class Key {

        final double time;
        final float[] pre;
        final float[] post;

        Key(double time, float[] pre, float[] post) {
            this.time = time;
            this.pre = pre;
            this.post = post;
        }
    }

    /** One animation. */
    public static final class Clip {

        /** Seconds. */
        public final double length;
        public final boolean loop;
        private final Map<String, List<Key>> rotations;

        Clip(double length, boolean loop, Map<String, List<Key>> rotations) {
            this.length = length;
            this.loop = loop;
            this.rotations = rotations;
        }

        /** Length in client ticks (20 per second), at least 1. */
        public int lengthTicks() {
            return Math.max(1, (int) Math.round(length * 20));
        }

        public boolean animates(String bone) {
            return rotations.containsKey(bone);
        }

        /** The bones this clip rotates. */
        public java.util.Set<String> rotatedBones() {
            return Collections.unmodifiableSet(rotations.keySet());
        }

        /**
         * The rotation of a bone (degrees, Bedrock signs) at {@code seconds} into the clip: looping clips wrap,
         * the others hold their last value. {0, 0, 0} for a bone the clip does not rotate.
         */
        public float[] rotation(String bone, double seconds) {
            List<Key> keys = rotations.get(bone);
            if (keys == null || keys.isEmpty()) {
                return new float[3];
            }
            double t = seconds;
            if (loop && length > 0) {
                t = t % length;
                if (t < 0) {
                    t += length;
                }
            }
            Key first = keys.get(0);
            if (t <= first.time) {
                return first.pre.clone();
            }
            for (int i = 1; i < keys.size(); i++) {
                Key b = keys.get(i);
                if (t <= b.time) {
                    Key a = keys.get(i - 1);
                    double span = b.time - a.time;
                    float f = span <= 0 ? 1 : (float) ((t - a.time) / span);
                    float[] out = new float[3];
                    for (int k = 0; k < 3; k++) {
                        out[k] = a.post[k] + (b.pre[k] - a.post[k]) * f;
                    }
                    return out;
                }
            }
            return keys.get(keys.size() - 1).post.clone();
        }
    }

    private final Map<String, Clip> clips;

    private BedrockAnimations(Map<String, Clip> clips) {
        this.clips = clips;
    }

    /** The animation of that name, or null. */
    public Clip clip(String name) {
        return name == null ? null : clips.get(name);
    }

    public Map<String, Clip> clips() {
        return clips;
    }

    /**
     * Reads one file.
     *
     * @throws IllegalArgumentException with a readable message when it is not a valid animation file
     */
    public static BedrockAnimations read(String json) {
        Object parsed;
        try {
            parsed = Json.parse(json, MAX_DEPTH);
        } catch (Json.JsonException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
        Object animations = parsed instanceof Map ? ((Map<?, ?>) parsed).get("animations") : null;
        if (!(animations instanceof Map)) {
            throw new IllegalArgumentException("not a Bedrock animation file (no \"animations\" object)");
        }
        Map<String, Clip> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : ((Map<?, ?>) animations).entrySet()) {
            if (e.getValue() instanceof Map) {
                out.put(String.valueOf(e.getKey()), clip((Map<?, ?>) e.getValue()));
            }
        }
        return new BedrockAnimations(Collections.unmodifiableMap(out));
    }

    private static Clip clip(Map<?, ?> a) {
        Map<String, List<Key>> rotations = new LinkedHashMap<>();
        double last = 0;
        Object bones = a.get("bones");
        if (bones instanceof Map) {
            for (Map.Entry<?, ?> b : ((Map<?, ?>) bones).entrySet()) {
                if (!(b.getValue() instanceof Map)) {
                    continue;
                }
                List<Key> keys = keys(((Map<?, ?>) b.getValue()).get("rotation"));
                if (!keys.isEmpty()) {
                    rotations.put(String.valueOf(b.getKey()), keys);
                    last = Math.max(last, keys.get(keys.size() - 1).time);
                }
            }
        }
        Object length = a.get("animation_length");
        double seconds = length instanceof Double && (Double) length > 0 ? (Double) length : last;
        Object loop = a.get("loop");
        return new Clip(seconds, Boolean.TRUE.equals(loop) || "true".equals(loop), rotations);
    }

    /** Keyframes in time order: a constant ({@code [x,y,z]} or a number) is one key at 0. */
    private static List<Key> keys(Object channel) {
        List<Key> keys = new ArrayList<>();
        if (channel == null) {
            return keys;
        }
        if (!(channel instanceof Map) || isVectorObject((Map<?, ?>) channel)) {
            float[] v = value(channel, false);
            if (v != null) {
                keys.add(new Key(0, v, v));
            }
            return keys;
        }
        for (Map.Entry<?, ?> e : ((Map<?, ?>) channel).entrySet()) {
            double time;
            try {
                time = Double.parseDouble(String.valueOf(e.getKey()));
            } catch (NumberFormatException ex) {
                continue;
            }
            float[] pre = value(e.getValue(), true);
            float[] post = value(e.getValue(), false);
            if (pre != null && post != null && Double.isFinite(time)) {
                keys.add(new Key(time, pre, post));
            }
        }
        keys.sort((x, y) -> Double.compare(x.time, y.time));
        return keys;
    }

    private static boolean isVectorObject(Map<?, ?> m) {
        return m.containsKey("pre") || m.containsKey("post") || m.containsKey("vector");
    }

    /** {@code [x,y,z]}, a number, or {@code {"pre": .., "post": ..}} / {@code {"vector": ..}}. */
    private static float[] value(Object v, boolean pre) {
        if (v instanceof Map) {
            Map<?, ?> m = (Map<?, ?>) v;
            Object chosen = pre ? (m.containsKey("pre") ? m.get("pre") : m.get("post"))
                : (m.containsKey("post") ? m.get("post") : m.get("pre"));
            if (chosen == null) {
                chosen = m.get("vector");
            }
            return chosen instanceof Map ? null : value(chosen, pre);
        }
        if (v instanceof List && ((List<?>) v).size() == 3) {
            float[] out = new float[3];
            for (int i = 0; i < 3; i++) {
                out[i] = number(((List<?>) v).get(i));
            }
            return out;
        }
        if (v instanceof Double || v instanceof String) {
            float n = number(v);
            return new float[] { n, n, n };
        }
        return null;
    }

    /** A number, or a Molang constant written as a string; other Molang is 0 here. */
    private static float number(Object o) {
        if (o instanceof Double) {
            return ((Double) o).floatValue();
        }
        if (o instanceof String) {
            try {
                return Float.parseFloat(((String) o).trim());
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }
}
