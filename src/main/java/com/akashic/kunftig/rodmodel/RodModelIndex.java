package com.akashic.kunftig.rodmodel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

import com.akashic.kunftig.common.text.Json;

/**
 * The 3D rods a resource pack declares in {@code assets/<namespace>/kunftig/item_models_3d.json} (format 1, issue
 * #13), keyed like item models ({@code kunftig:model}): {@code akashicfishing:vara/vara-solar} is the key
 * {@code vara/vara-solar} of {@code assets/akashicfishing/...}. A stack's model may carry suffixes that select the
 * look of the same rod: {@code vara/vara-solar.ouro.m3.cast} is the rod {@code vara/vara-solar}, quality {@code ouro},
 * mutation stage 3, line out ({@link #resolve}).
 * <p>
 * A model with a problem is left out (the item keeps its 2D icon) and the problem is listed in {@link #errors()}; the
 * rest of the file still applies. No Minecraft classes.
 */
public final class RodModelIndex {

    /** Path of the manifest inside each resource domain. */
    public static final String PATH = "kunftig/item_models_3d.json";
    public static final int FORMAT = 1;

    static final String CAST_SUFFIX = ".cast";
    private static final Pattern NAMESPACE = Pattern.compile("[a-z0-9._-]+");
    private static final Pattern PATH_PART = Pattern.compile("[a-z0-9/._-]+");
    private static final Pattern STAGE = Pattern.compile("\\.m([0-9]{1,2})$");
    private static final Pattern BONE = Pattern.compile("[^\\s]{1,64}");
    private static final Pattern STATE = Pattern.compile("[a-z0-9_]{1,32}");
    private static final Pattern COLOR = Pattern.compile("#?[0-9a-fA-F]{6}");
    private static final int MAX_DEPTH = 10;
    private static final int MAX_STAGE = 99;
    private static final int RESOLVE_CACHE = 512;

    /** A stack's model resolved to a rod and the suffixes that select its look. */
    public static final class Resolved {

        public final RodModelDef model;
        /** The quality suffix, or null. */
        public final String quality;
        public final int stage;
        public final boolean cast;

        Resolved(RodModelDef model, String quality, int stage, boolean cast) {
            this.model = model;
            this.quality = quality;
            this.stage = stage;
            this.cast = cast;
        }
    }

    private static final Resolved NONE = new Resolved(null, null, 0, false);

    /**
     * Fields of a rod Kunftig knows: those it reads, and those the pack keeps for people and tools ({@code nome},
     * {@code controladores}, {@code molang}). Anything else is listed by {@link #ignored()}.
     */
    public static final Set<String> KNOWN_FIELDS = Collections.unmodifiableSet(
        new LinkedHashSet<>(
            java.util.Arrays.asList(
                "geo",
                "animacoes",
                "braco",
                "textura",
                "texturas_mutacao",
                "ticks_por_quadro",
                "transicao_ticks",
                "estados",
                "luz",
                "translucido",
                "mutacao",
                "qualidade",
                "ponta_da_linha",
                "esconder_com_a_linha_na_agua",
                "cor_da_linha",
                "pega",
                "display",
                "contextos",
                "nome",
                "controladores",
                "molang")));

    private final Map<String, RodModelDef> models = new LinkedHashMap<>();
    private final List<String> errors = new ArrayList<>();
    private final List<String> ignored = new ArrayList<>();
    private final Map<String, Resolved> resolved = new HashMap<>();

    /** Rods by id ({@code namespace:key}), in the order declared; later packs override earlier ones. */
    public Map<String, RodModelDef> models() {
        return Collections.unmodifiableMap(models);
    }

    /** Problems found, one line each. */
    public List<String> errors() {
        return Collections.unmodifiableList(errors);
    }

    /** Fields of the rods this Kunftig does not know and leaves aside (a newer pack, or a typo), one line each. */
    public List<String> ignored() {
        return Collections.unmodifiableList(ignored);
    }

    public boolean isEmpty() {
        return models.isEmpty();
    }

    /**
     * The rod of a stack's model id ({@code namespace:key[.quality][.m<stage>][.cast]}), or null when it has no 3D
     * model. Cached: called for every rendered rod.
     */
    public Resolved resolve(String modelId) {
        if (modelId == null || models.isEmpty()) {
            return null;
        }
        Resolved r = resolved.get(modelId);
        if (r == null) {
            r = lookup(modelId);
            if (resolved.size() >= RESOLVE_CACHE) {
                resolved.clear();
            }
            resolved.put(modelId, r);
        }
        return r == NONE ? null : r;
    }

    private Resolved lookup(String id) {
        String key = id;
        boolean cast = false;
        if (key.endsWith(CAST_SUFFIX)) {
            key = key.substring(0, key.length() - CAST_SUFFIX.length());
            cast = true;
        }
        int stage = 0;
        java.util.regex.Matcher m = STAGE.matcher(key);
        if (m.find()) {
            stage = Integer.parseInt(m.group(1));
            key = key.substring(0, m.start());
        }
        RodModelDef def = models.get(key);
        if (def != null) {
            return new Resolved(def, null, stage, cast);
        }
        int dot = key.lastIndexOf('.');
        if (dot > Math.max(key.lastIndexOf('/'), key.indexOf(':'))) {
            def = models.get(key.substring(0, dot));
            if (def != null) {
                return new Resolved(def, key.substring(dot + 1), stage, cast);
            }
        }
        return NONE;
    }

    /** Adds the rods of one {@code item_models_3d.json} of {@code namespace}. */
    public void add(String namespace, String json, String source) {
        resolved.clear();
        Object parsed;
        try {
            parsed = Json.parse(json, MAX_DEPTH);
        } catch (Json.JsonException e) {
            errors.add(source + ": " + e.getMessage());
            return;
        }
        if (!(parsed instanceof Map)) {
            errors.add(source + ": expected a JSON object");
            return;
        }
        Map<?, ?> root = (Map<?, ?>) parsed;
        Object format = root.get("_formato");
        if (!(format instanceof Double) || ((Double) format).intValue() != FORMAT) {
            errors.add(source + ": \"_formato\" must be " + FORMAT + " (this Kunftig reads format " + FORMAT + ")");
            return;
        }
        Object list = root.get("modelos");
        if (!(list instanceof Map)) {
            errors.add(source + ": \"modelos\" must be an object of model -> rod");
            return;
        }
        for (Map.Entry<?, ?> e : ((Map<?, ?>) list).entrySet()) {
            String key = String.valueOf(e.getKey());
            if (key.startsWith("_")) {
                continue; // comments
            }
            String where = source + ": model '" + key + "'";
            if (!PATH_PART.matcher(key)
                .matches() || key.contains(CAST_SUFFIX + ".")
                || key.endsWith(CAST_SUFFIX)
                || STAGE.matcher(key)
                    .find()) {
                errors.add(where + ": invalid key (a-z 0-9 / . _ -, without the .<stage>/.cast suffixes)");
                continue;
            }
            if (!(e.getValue() instanceof Map)) {
                errors.add(where + ": expected an object");
                continue;
            }
            for (Object field : ((Map<?, ?>) e.getValue()).keySet()) {
                if (!KNOWN_FIELDS.contains(String.valueOf(field)) && !String.valueOf(field)
                    .startsWith("_")) {
                    ignored.add(where + ": field '" + field + "' is not read by this Kunftig");
                }
            }
            try {
                RodModelDef def = model(namespace, namespace + ":" + key, (Map<?, ?>) e.getValue());
                models.put(def.id, def);
            } catch (Invalid problem) {
                errors.add(where + ": " + problem.getMessage());
            }
        }
    }

    /** A field that makes the whole model unusable. */
    private static final class Invalid extends Exception {

        private static final long serialVersionUID = 1L;

        Invalid(String message) {
            super(message, null, false, false);
        }
    }

    private static RodModelDef model(String ns, String id, Map<?, ?> o) throws Invalid {
        String geo = location(ns, o, "geo", true);
        String animations = location(ns, o, "animacoes", true);
        String arm = location(ns, o, "braco", false);
        String texture = location(ns, o, "textura", true);

        TreeMap<Integer, List<String>> stageTextures = new TreeMap<>();
        for (Map.Entry<?, ?> e : object(o, "texturas_mutacao").entrySet()) {
            int stage = stage(e.getKey(), "texturas_mutacao");
            List<String> frames = new ArrayList<>();
            if (e.getValue() instanceof List) {
                for (Object f : (List<?>) e.getValue()) {
                    frames.add(location(ns, f, "texturas_mutacao." + e.getKey()));
                }
            } else {
                frames.add(location(ns, e.getValue(), "texturas_mutacao." + e.getKey()));
            }
            if (frames.isEmpty()) {
                throw new Invalid("texturas_mutacao." + e.getKey() + " has no texture");
            }
            stageTextures.put(stage, Collections.unmodifiableList(frames));
        }
        int ticksPerFrame = (int) number(o, "ticks_por_quadro", 3);
        if (ticksPerFrame < 1) {
            throw new Invalid("ticks_por_quadro must be 1 or more");
        }

        Map<String, RodModelDef.Clips> states = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : object(o, "estados").entrySet()) {
            String name = String.valueOf(e.getKey());
            if (!STATE.matcher(name)
                .matches() || !(e.getValue() instanceof Map)) {
                throw new Invalid("invalid state '" + name + "' (a-z 0-9 _, with an object)");
            }
            Map<?, ?> s = (Map<?, ?>) e.getValue();
            String rod = text(s, "vara", true, "estados." + name);
            String aura = text(s, "aura", false, "estados." + name);
            String armClip = text(s, "braco", false, "estados." + name);
            Object loop = s.get("laco");
            Object twoHands = s.get("duas_maos");
            states.put(
                name,
                new RodModelDef.Clips(rod, aura, armClip, Boolean.TRUE.equals(loop), Boolean.TRUE.equals(twoHands)));
        }
        if (!states.containsKey(RodModelDef.IDLE)) {
            throw new Invalid("estados needs \"" + RodModelDef.IDLE + "\"");
        }

        Set<String> light = bones(o, "luz");
        Set<String> translucent = bones(o, "translucido");
        Map<String, Integer> boneStages = new HashMap<>();
        for (Map.Entry<?, ?> e : object(o, "mutacao").entrySet()) {
            int stage = stage(e.getKey(), "mutacao");
            for (String bone : bones(e.getValue(), "mutacao." + e.getKey())) {
                boneStages.merge(bone, stage, Math::min);
            }
        }

        String tipBone = null;
        float[] tipRest = null;
        Map<?, ?> tip = object(o, "ponta_da_linha");
        if (!tip.isEmpty()) {
            tipBone = text(tip, "osso", true, "ponta_da_linha");
            tipRest = vector(tip.get("posicao"), "ponta_da_linha.posicao");
        }

        TreeMap<Integer, Integer> lineColors = new TreeMap<>();
        for (Map.Entry<?, ?> e : object(o, "cor_da_linha").entrySet()) {
            int stage = stage(e.getKey(), "cor_da_linha");
            Object c = e.getValue();
            if (!(c instanceof String) || !COLOR.matcher((String) c)
                .matches()) {
                throw new Invalid("cor_da_linha." + e.getKey() + " must be a color like \"ffe9a8\"");
            }
            String hex = (String) c;
            lineColors.put(stage, Integer.parseInt(hex.startsWith("#") ? hex.substring(1) : hex, 16));
        }

        float[] grip = o.containsKey("pega") ? vector(o.get("pega"), "pega") : new float[3];

        Map<String, List<String>> qualityBones = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : object(o, "qualidade").entrySet()) {
            String quality = String.valueOf(e.getKey());
            if (!STATE.matcher(quality)
                .matches()) {
                throw new Invalid("qualidade: invalid quality '" + quality + "'");
            }
            qualityBones.put(
                quality,
                Collections.unmodifiableList(new ArrayList<>(bones(e.getValue(), "qualidade." + quality))));
        }

        Map<String, Integer> transitions = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : object(o, "transicao_ticks").entrySet()) {
            Object v = e.getValue();
            if (!(v instanceof Double) || (Double) v < 0 || (Double) v > 100 || (Double) v != Math.floor((Double) v)) {
                throw new Invalid("transicao_ticks." + e.getKey() + " must be a whole number of ticks, 0 to 100");
            }
            transitions.put(String.valueOf(e.getKey()), ((Double) v).intValue());
        }

        Map<String, RodModelDef.Display> displays = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : object(o, "display").entrySet()) {
            String context = String.valueOf(e.getKey());
            if (!(e.getValue() instanceof Map)) {
                throw new Invalid("display." + context + " must be an object");
            }
            Map<?, ?> d = (Map<?, ?>) e.getValue();
            float[] rotation = d.containsKey("rotation") ? vector(d.get("rotation"), "display." + context + ".rotation")
                : new float[3];
            float[] translation = d.containsKey("translation")
                ? vector(d.get("translation"), "display." + context + ".translation")
                : new float[3];
            float scale = (float) number(d, "scale", 1);
            if (!(scale > 0)) {
                throw new Invalid("display." + context + ".scale must be above 0");
            }
            displays.put(context, new RodModelDef.Display(rotation, translation, scale));
        }
        Set<String> contexts = o.containsKey("contextos") ? bones(o, "contextos")
            : new LinkedHashSet<>(displays.keySet());

        return new RodModelDef(
            id,
            geo,
            animations,
            arm,
            texture,
            stageTextures,
            ticksPerFrame,
            states,
            light,
            translucent,
            boneStages,
            tipBone,
            tipRest,
            bones(o, "esconder_com_a_linha_na_agua"),
            lineColors,
            grip,
            displays,
            contexts,
            qualityBones,
            transitions);
    }

    private static Map<?, ?> object(Map<?, ?> o, String field) throws Invalid {
        Object v = o.get(field);
        if (v == null) {
            return Collections.emptyMap();
        }
        if (!(v instanceof Map)) {
            throw new Invalid(field + " must be an object");
        }
        return (Map<?, ?>) v;
    }

    private static String text(Map<?, ?> o, String field, boolean required, String where) throws Invalid {
        Object v = o.get(field);
        if (v == null) {
            if (required) {
                throw new Invalid(where + " needs \"" + field + "\"");
            }
            return null;
        }
        if (!(v instanceof String) || ((String) v).isEmpty()) {
            throw new Invalid(where + "." + field + " must be a non-empty string");
        }
        return (String) v;
    }

    private static String location(String ns, Map<?, ?> o, String field, boolean required) throws Invalid {
        Object v = o.get(field);
        if (v == null) {
            if (required) {
                throw new Invalid("needs \"" + field + "\"");
            }
            return null;
        }
        return location(ns, v, field);
    }

    /** {@code ns:path} as is, {@code path} as {@code namespace:path}. */
    static String location(String ns, Object value, String field) throws Invalid {
        if (!(value instanceof String)) {
            throw new Invalid(field + " must be a resource like \"" + ns + ":path\"");
        }
        String s = (String) value;
        int colon = s.indexOf(':');
        String n = colon < 0 ? ns : s.substring(0, colon);
        String path = colon < 0 ? s : s.substring(colon + 1);
        if (!NAMESPACE.matcher(n)
            .matches()
            || !PATH_PART.matcher(path)
                .matches()
            || path.startsWith("/")
            || path.endsWith("/")) {
            throw new Invalid(field + ": invalid resource '" + s + "'");
        }
        return n + ":" + path;
    }

    private static double number(Map<?, ?> o, String field, double fallback) throws Invalid {
        Object v = o.get(field);
        if (v == null) {
            return fallback;
        }
        if (!(v instanceof Double) || !Double.isFinite((Double) v)) {
            throw new Invalid(field + " must be a number");
        }
        return (Double) v;
    }

    private static float[] vector(Object v, String field) throws Invalid {
        if (!(v instanceof List) || ((List<?>) v).size() != 3) {
            throw new Invalid(field + " must be [x, y, z]");
        }
        float[] out = new float[3];
        for (int i = 0; i < 3; i++) {
            Object n = ((List<?>) v).get(i);
            if (!(n instanceof Double) || !Double.isFinite((Double) n)) {
                throw new Invalid(field + " must be [x, y, z] numbers");
            }
            out[i] = ((Double) n).floatValue();
        }
        return out;
    }

    private static int stage(Object key, String field) throws Invalid {
        String s = String.valueOf(key);
        try {
            int stage = Integer.parseInt(s);
            if (stage >= 0 && stage <= MAX_STAGE) {
                return stage;
            }
        } catch (NumberFormatException e) {
            // reported below
        }
        throw new Invalid(field + ": invalid stage '" + s + "' (0 to " + MAX_STAGE + ")");
    }

    private static Set<String> bones(Map<?, ?> o, String field) throws Invalid {
        return bones(o.get(field), field);
    }

    private static Set<String> bones(Object v, String field) throws Invalid {
        Set<String> out = new LinkedHashSet<>();
        if (v == null) {
            return out;
        }
        if (!(v instanceof List)) {
            throw new Invalid(field + " must be a list of names");
        }
        for (Object b : (List<?>) v) {
            if (!(b instanceof String) || !BONE.matcher((String) b)
                .matches()) {
                throw new Invalid(field + ": invalid name '" + b + "'");
            }
            out.add((String) b);
        }
        return out;
    }
}
