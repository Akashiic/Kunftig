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

    /** The quality of an item model without a quality suffix. */
    public static final String DEFAULT_QUALITY = "normal";
    /** The animation controllers. */
    public static final String ROD_CONTROLLER = "vara";
    public static final String AURA_CONTROLLER = "aura";

    /** The animations of one state: rod (controller "vara"), aura (controller "aura"), arm, and whether it loops. */
    public static final class Clips {

        public final String rod;
        public final String aura;
        /** May be null: the arm keeps vanilla's pose. */
        public final String arm;
        public final boolean loop;
        /**
         * The arm animation while the line is out ({@code braco_com_a_linha}), or null: {@link #arm} either way. A rod
         * held idle with both hands once it is cast, with one before.
         */
        public final String armWithLine;
        /**
         * Both hands on the rod ({@code duas_maos: true}): vanilla's walking swing leaves the arms, or the hands would
         * let go of the rod while walking.
         */
        public final boolean twoHands;
        /** Both hands only while the line is out ({@code duas_maos: "com_a_linha"}). */
        public final boolean twoHandsWithLine;

        Clips(String rod, String aura, String arm, boolean loop) {
            this(rod, aura, arm, null, loop, false, false);
        }

        Clips(String rod, String aura, String arm, boolean loop, boolean twoHands) {
            this(rod, aura, arm, null, loop, twoHands, false);
        }

        Clips(String rod, String aura, String arm, String armWithLine, boolean loop, boolean twoHands,
            boolean twoHandsWithLine) {
            this.rod = rod;
            this.aura = aura;
            this.arm = arm;
            this.armWithLine = armWithLine;
            this.loop = loop;
            this.twoHands = twoHands;
            this.twoHandsWithLine = twoHandsWithLine;
        }

        /** The arm animation, with the line out or not. */
        public String arm(boolean line) {
            return line && armWithLine != null ? armWithLine : arm;
        }

        /** Whether both hands hold the rod, with the line out or not. */
        public boolean twoHands(boolean line) {
            return twoHands || line && twoHandsWithLine;
        }
    }

    /**
     * The rod's inertia in first person ({@code balanco}): the bone that swings about the grip (the root, whose pivot
     * is the grip) and the bones that share the bend, from the grip to the tip.
     */
    public static final class Sway {

        public final String pivot;
        public final List<String> bend;
        public final float strength;

        public Sway(String pivot, List<String> bend, float strength) {
            this.pivot = pivot;
            this.bend = Collections.unmodifiableList(new java.util.ArrayList<>(bend));
            this.strength = strength;
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
    /** Quality to the bones shown only for it (the quality stone); empty when the rod has none. */
    public final Map<String, List<String>> qualityBones;
    /** Controller ({@link #ROD_CONTROLLER}, {@link #AURA_CONTROLLER}) to its blend between animations, in ticks. */
    public final Map<String, Integer> transitions;
    /** May be null: no inertia in first person. */
    public final Sway sway;

    RodModelDef(String id, String geo, String animations, String arm, String texture,
        NavigableMap<Integer, List<String>> stageTextures, int ticksPerFrame, Map<String, Clips> states,
        Set<String> lightBones, Set<String> translucentBones, Map<String, Integer> boneStages, String tipBone,
        float[] tipRest, Set<String> hiddenWhileCast, NavigableMap<Integer, Integer> lineColors, float[] grip,
        Map<String, Display> displays, Set<String> contexts, Map<String, List<String>> qualityBones,
        Map<String, Integer> transitions, Sway sway) {
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
        this.qualityBones = Collections.unmodifiableMap(qualityBones);
        this.transitions = Collections.unmodifiableMap(transitions);
        this.sway = sway;
    }

    /** The blend of a controller between animations, in ticks (0: none). */
    public int transition(String controller) {
        Integer t = transitions.get(controller);
        return t == null ? 0 : t;
    }

    /**
     * The quality whose stone shows for an item of {@code quality} (null: no suffix): itself when the rod declares it,
     * else {@link #DEFAULT_QUALITY}.
     */
    public String shownQuality(String quality) {
        return quality != null && qualityBones.containsKey(quality) ? quality : DEFAULT_QUALITY;
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
