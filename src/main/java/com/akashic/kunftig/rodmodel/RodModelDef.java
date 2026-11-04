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
     * The line hanging from the tip ({@code linha_pendurada}): a bone that hangs along its -Y and swings with gravity
     * (a pendulum in world space), and its length in model pixels.
     */
    public static final class HangingLine {

        public final String bone;
        public final float lengthPixels;

        public HangingLine(String bone, float lengthPixels) {
            this.bone = bone;
            this.lengthPixels = lengthPixels;
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

    /**
     * The model's size read from the item ({@code tamanho}): a number of the item's data (Bukkit's persistent data,
     * {@code PublicBukkitValues.<chave>}), in {@code unidades_por_bloco} per block. The model, {@code comprimento}
     * pixels long as modeled, is drawn {@code value / unidades_por_bloco} blocks long, between {@code minimo} and
     * {@code maximo} blocks; an item without the value uses {@code padrao}.
     */
    public static final class Size {

        public final String key;
        public final double unitsPerBlock;
        public final float modelLength;
        public final double minBlocks;
        public final double maxBlocks;
        public final double fallback;

        public Size(String key, double unitsPerBlock, float modelLength, double minBlocks, double maxBlocks,
            double fallback) {
            this.key = key;
            this.unitsPerBlock = unitsPerBlock;
            this.modelLength = modelLength;
            this.minBlocks = minBlocks;
            this.maxBlocks = maxBlocks;
            this.fallback = fallback;
        }

        /** The value to use for an item: its own when it is a finite number, else {@link #fallback}. */
        public double valueOr(Double value) {
            return value != null && !value.isNaN() && !value.isInfinite() ? value : fallback;
        }

        /** The factor the model is scaled by (about the grip) for a value. */
        public float scale(double value) {
            double blocks = Math.max(minBlocks, Math.min(maxBlocks, value / unitsPerBlock));
            return (float) (blocks * 16 / modelLength);
        }
    }

    /**
     * A size band ({@code faixas}): up to {@code ate} (inclusive; the last band has no limit), the states the client
     * and the server play are swapped for the band's ({@code estados}: state -> state of {@code estados}), and the
     * band's {@code display} replaces the model's for its contexts. A big fish is held with both hands, a giant one
     * above the head.
     */
    public static final class Band {

        public final String name;
        public final double upTo;
        public final Map<String, String> states;
        public final Map<String, Display> displays;
        /** The band's own grip ({@code pega}), or null: the model's. The model is scaled about it. */
        public final float[] grip;

        public Band(String name, double upTo, Map<String, String> states, Map<String, Display> displays) {
            this(name, upTo, states, displays, null);
        }

        public Band(String name, double upTo, Map<String, String> states, Map<String, Display> displays, float[] grip) {
            this.name = name;
            this.upTo = upTo;
            this.states = Collections.unmodifiableMap(states);
            this.displays = Collections.unmodifiableMap(displays);
            this.grip = grip == null ? null : grip.clone();
        }

        /** The state of {@code estados} played for {@code state} in this band. */
        public String state(String state) {
            String s = state == null ? null : states.get(state);
            return s != null ? s : state;
        }
    }

    /** Items whose renderer draws a model without {@code itens}: the fishing rod. */
    public static final List<String> DEFAULT_ITEMS = Collections.singletonList("minecraft:fishing_rod");

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
    /** May be null: the hanging line follows the rod as modeled. */
    public final HangingLine hangingLine;
    /**
     * Registry names of the items ({@code itens}) whose renderer draws this model; {@link #DEFAULT_ITEMS} by default.
     */
    public final List<String> items;
    /** May be null: the model keeps its modeled size. */
    public final Size size;
    /** Size bands, smallest first; empty without {@code faixas}. Need {@link #size}. */
    public final List<Band> bands;
    /** Quality to its texture frames ({@code texturas_qualidade}), over the mutation stage's texture. */
    public final Map<String, List<String>> qualityTextures;

    RodModelDef(String id, String geo, String animations, String arm, String texture,
        NavigableMap<Integer, List<String>> stageTextures, int ticksPerFrame, Map<String, Clips> states,
        Set<String> lightBones, Set<String> translucentBones, Map<String, Integer> boneStages, String tipBone,
        float[] tipRest, Set<String> hiddenWhileCast, NavigableMap<Integer, Integer> lineColors, float[] grip,
        Map<String, Display> displays, Set<String> contexts, Map<String, List<String>> qualityBones,
        Map<String, Integer> transitions, Sway sway, HangingLine hangingLine) {
        this(
            id,
            geo,
            animations,
            arm,
            texture,
            stageTextures,
            ticksPerFrame,
            states,
            lightBones,
            translucentBones,
            boneStages,
            tipBone,
            tipRest,
            hiddenWhileCast,
            lineColors,
            grip,
            displays,
            contexts,
            qualityBones,
            transitions,
            sway,
            hangingLine,
            DEFAULT_ITEMS,
            null,
            Collections.<Band>emptyList(),
            Collections.<String, List<String>>emptyMap());
    }

    RodModelDef(String id, String geo, String animations, String arm, String texture,
        NavigableMap<Integer, List<String>> stageTextures, int ticksPerFrame, Map<String, Clips> states,
        Set<String> lightBones, Set<String> translucentBones, Map<String, Integer> boneStages, String tipBone,
        float[] tipRest, Set<String> hiddenWhileCast, NavigableMap<Integer, Integer> lineColors, float[] grip,
        Map<String, Display> displays, Set<String> contexts, Map<String, List<String>> qualityBones,
        Map<String, Integer> transitions, Sway sway, HangingLine hangingLine, List<String> items, Size size,
        List<Band> bands, Map<String, List<String>> qualityTextures) {
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
        this.hangingLine = hangingLine;
        this.items = Collections.unmodifiableList(new java.util.ArrayList<>(items));
        this.size = size;
        this.bands = Collections.unmodifiableList(new java.util.ArrayList<>(bands));
        this.qualityTextures = Collections.unmodifiableMap(qualityTextures);
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
        return textureFor(null, stage, tick);
    }

    /**
     * The texture of an item of {@code quality} (null: no suffix, {@link #DEFAULT_QUALITY}) at a mutation stage and a
     * client tick: the quality's own ({@code texturas_qualidade}) when the model has one, else the stage's. Animated
     * textures cycle through their
     * frames.
     */
    public String textureFor(String quality, int stage, long tick) {
        List<String> frames = qualityTextures.get(quality == null ? DEFAULT_QUALITY : quality);
        if (frames == null) {
            Map.Entry<Integer, List<String>> e = stage > 0 ? stageTextures.floorEntry(stage) : null;
            if (e == null) {
                return texture;
            }
            frames = e.getValue();
        }
        if (frames.size() == 1) {
            return frames.get(0);
        }
        int frame = (int) Math.floorMod(tick / ticksPerFrame, (long) frames.size());
        return frames.get(frame);
    }

    /** The size band of a value ({@link Size#valueOr}), or null when the model has none. */
    public Band band(double value) {
        if (bands.isEmpty()) {
            return null;
        }
        for (Band b : bands) {
            if (value <= b.upTo) {
                return b;
            }
        }
        return bands.get(bands.size() - 1);
    }

    /** {@link #clips(String)} of the state a band plays for {@code state} (no band: the state itself). */
    public Clips clips(String state, Band band) {
        return clips(band == null ? state : band.state(state));
    }

    /** The display of a context in a band: the band's own, else the model's (may be null). */
    public Display display(String context, Band band) {
        Display d = band == null ? null : band.displays.get(context);
        return d != null ? d : displays.get(context);
    }

    /** The grip in a band: the band's own, else the model's. */
    public float[] grip(Band band) {
        return band != null && band.grip != null ? band.grip : grip;
    }

    /** A display scaled by {@code factor} (the model's size). */
    public static Display scaled(Display d, float factor) {
        return d == null || factor == 1F ? d : new Display(d.rotation, d.translation, d.scale * factor);
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
