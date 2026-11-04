package com.akashic.kunftig.rodmodel;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;

/**
 * One 3D rod of a {@code kunftig/item_models_3d.json} (issue #13), as read by {@link RodModelIndex}. Immutable; no
 * Minecraft classes. Resource locations are kept as {@code namespace:path} strings.
 */
public final class RodModelDef {

    /** Contexts of {@code display} and {@code contextos}. */
    public static final String FIRST_PERSON = "primeira_pessoa";
    public static final String THIRD_PERSON = "terceira_pessoa";
    public static final String GROUND = "chao";
    public static final String FRAME = "moldura";

    /** States the client plays on its own; every other state comes from the server ({@code KunftigRods}). */
    public static final String IDLE = "parada";
    public static final String DRAW = "sacar";
    public static final String CAST = "lancar";

    /** The animations of one state: rod (controller "vara"), aura (controller "aura"), arm, and whether it loops. */
    public static final class Clips {

        public final String rod;
        public final String aura;
        /** May be null: the arm keeps vanilla's pose. */
        public final String arm;
        public final boolean loop;

        Clips(String rod, String aura, String arm, boolean loop) {
            this.rod = rod;
            this.aura = aura;
            this.arm = arm;
            this.loop = loop;
        }
    }

    /**
     * A display transform, in the manifest's convention (that of the previews and Blockbench): after the grip is
     * brought to the origin, scale, rotate (degrees, Bedrock signs, Z then Y then X) and translate (1/16 block).
     */
    public static final class Display {

        public final float[] rotation;
        public final float[] translation;
        public final float scale;

        public Display(float[] rotation, float[] translation, float scale) {
            this.rotation = rotation.clone();
            this.translation = translation.clone();
            this.scale = scale;
        }

        /** As JSON, to paste back into the manifest generator. */
        @Override
        public String toString() {
            return "{ \"rotation\": [" + num(rotation[0])
                + ", "
                + num(rotation[1])
                + ", "
                + num(rotation[2])
                + "], \"translation\": ["
                + num(translation[0])
                + ", "
                + num(translation[1])
                + ", "
                + num(translation[2])
                + "], \"scale\": "
                + num(scale)
                + " }";
        }

        private static String num(float f) {
            return f == (long) f ? Long.toString((long) f) : Float.toString(f);
        }
    }

    public final String id;
    public final String geo;
    public final String animations;
    /** May be null (no arm pose). */
    public final String arm;
    public final String texture;
    /** Mutation stage to its texture frames (one for a still texture); stages without an entry use a lower one. */
    public final NavigableMap<Integer, List<String>> stageTextures;
    public final int ticksPerFrame;
    public final Map<String, Clips> states;
    public final Set<String> lightBones;
    public final Set<String> translucentBones;
    /** Bone to the mutation stage from which it shows (bones absent here always show). */
    public final Map<String, Integer> boneStages;
    /** May be null: the line keeps vanilla's origin. */
    public final String tipBone;
    /** The rest position of the line's locator, in model pixels (Bedrock axes). */
    public final float[] tipRest;
    public final Set<String> hiddenWhileCast;
    /** Mutation stage to the line color (0xRRGGBB), for that stage and the ones above. */
    public final NavigableMap<Integer, Integer> lineColors;
    /** The point of the model held in the hand, in model pixels (Bedrock axes). */
    public final float[] grip;
    public final Map<String, Display> displays;
    public final Set<String> contexts;

    RodModelDef(String id, String geo, String animations, String arm, String texture,
        NavigableMap<Integer, List<String>> stageTextures, int ticksPerFrame, Map<String, Clips> states,
        Set<String> lightBones, Set<String> translucentBones, Map<String, Integer> boneStages, String tipBone,
        float[] tipRest, Set<String> hiddenWhileCast, NavigableMap<Integer, Integer> lineColors, float[] grip,
        Map<String, Display> displays, Set<String> contexts) {
        this.id = id;
        this.geo = geo;
        this.animations = animations;
        this.arm = arm;
        this.texture = texture;
        this.stageTextures = Collections.unmodifiableNavigableMap(stageTextures);
        this.ticksPerFrame = ticksPerFrame;
        this.states = Collections.unmodifiableMap(states);
        this.lightBones = Collections.unmodifiableSet(lightBones);
        this.translucentBones = Collections.unmodifiableSet(translucentBones);
        this.boneStages = Collections.unmodifiableMap(boneStages);
        this.tipBone = tipBone;
        this.tipRest = tipRest;
        this.hiddenWhileCast = Collections.unmodifiableSet(hiddenWhileCast);
        this.lineColors = Collections.unmodifiableNavigableMap(lineColors);
        this.grip = grip;
        this.displays = Collections.unmodifiableMap(displays);
        this.contexts = Collections.unmodifiableSet(contexts);
    }

    /** The texture of a mutation stage at a client tick (stage 6 cycles through its frames). */
    public String textureFor(int stage, long tick) {
        Map.Entry<Integer, List<String>> e = stage > 0 ? stageTextures.floorEntry(stage) : null;
        if (e == null) {
            return texture;
        }
        List<String> frames = e.getValue();
        if (frames.size() == 1) {
            return frames.get(0);
        }
        int frame = (int) Math.floorMod(tick / ticksPerFrame, (long) frames.size());
        return frames.get(frame);
    }

    /** The line color for a mutation stage, or -1 to keep vanilla's. */
    public int lineColor(int stage) {
        Map.Entry<Integer, Integer> e = lineColors.floorEntry(stage);
        return e == null ? -1 : e.getValue();
    }

    /** Whether a bone shows at this mutation stage. */
    public boolean showsAt(String bone, int stage) {
        Integer from = boneStages.get(bone);
        return from == null || stage >= from;
    }

    /** The clips of a state, or those of {@link #IDLE} for a state this rod does not declare. */
    public Clips clips(String state) {
        Clips c = state == null ? null : states.get(state);
        return c != null ? c : states.get(IDLE);
    }

    public boolean drawsIn(String context) {
        return contexts.contains(context) && displays.containsKey(context);
    }
}
