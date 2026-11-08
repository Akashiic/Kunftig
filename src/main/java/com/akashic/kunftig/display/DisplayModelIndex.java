package com.akashic.kunftig.display;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.akashic.kunftig.common.text.Json;

/**
 * The displays a resource pack declares in {@code assets/<namespace>/kunftig/displays.json}:
 *
 * <pre>
 * {
 *   "_formato": 1,
 *   "exibicoes": {
 *     "caixa_da_sorte": {
 *       "geo": "akashicfishing:geo/caixa/caixa_da_sorte.geo.json",
 *       "animacoes": "akashicfishing:animations/caixa/caixa_da_sorte.animation.json",
 *       "textura": "akashicfishing:textures/caixa/caixa_da_sorte_comum.png",
 *       "variantes": { "epico": "akashicfishing:textures/caixa/caixa_da_sorte_epico.png" },
 *       "estados": { "abrir": { "animacao": "animation.caixa_da_sorte.abrir", "laco": false } },
 *       "itens": ["item_0", "item_1"],      // bones where the display's item stacks are drawn, in order
 *       "escala_item": 1.0,                 // optional
 *       "luz": [...], "translucido": [...], // bones drawn full bright / without writing depth
 *       "distancia": 48,                    // optional: blocks from the camera beyond which it is not drawn
 *       "escala": 4,                        // optional (API 9): the model is drawn this many times its size
 *       "ocultos": ["parte_quebrada"],      // optional (API 9): bones (and what is under them) that start hidden
 *       "transicao": 6                      // optional (API 9): ticks of blend into each new state
 *     }
 *   }
 * }
 * </pre>
 *
 * The display's id is {@code <namespace>:<key>}. A display with a problem is left out and the problem listed in
 * {@link #errors()}; the rest of the file still applies. No Minecraft classes.
 */
public final class DisplayModelIndex {

    public static final int FORMAT = 1;
    public static final String PATH = "kunftig/displays.json";
    private static final int MAX_DEPTH = 32;
    private static final Pattern KEY = Pattern.compile("[a-z0-9_./-]{1,128}");
    private static final Pattern LOCATION = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    private static final Pattern NAME = Pattern.compile("[a-z0-9_]{1,32}");

    /** A state: the animation it plays and whether it loops. */
    public static final class State {

        public final String animation;
        public final boolean loop;

        State(String animation, boolean loop) {
            this.animation = animation;
            this.loop = loop;
        }
    }

    /** One display model. */
    public static final class Model {

        public final String id;
        public final String geo;
        public final String animations;
        public final String texture;
        public final Map<String, String> variants;
        public final Map<String, State> states;
        public final List<String> itemBones;
        public final float itemScale;
        public final List<String> lightBones;
        public final List<String> translucentBones;
        public final double maxDistance;
        /** API 9: the model's own scale (its units times this). */
        public final float scale;
        /** API 9: bones hidden until the server shows them. */
        public final List<String> hiddenBones;
        /** API 9: ticks of blend into a new state (0: it jumps). */
        public final int transitionTicks;

        Model(String id, String geo, String animations, String texture, Map<String, String> variants,
            Map<String, State> states, List<String> itemBones, float itemScale, List<String> lightBones,
            List<String> translucentBones, double maxDistance, float scale, List<String> hiddenBones,
            int transitionTicks) {
            this.id = id;
            this.geo = geo;
            this.animations = animations;
            this.texture = texture;
            this.variants = variants;
            this.states = states;
            this.itemBones = itemBones;
            this.itemScale = itemScale;
            this.lightBones = lightBones;
            this.translucentBones = translucentBones;
            this.maxDistance = maxDistance;
            this.scale = scale;
            this.hiddenBones = hiddenBones;
            this.transitionTicks = transitionTicks;
        }

        /** The texture of a variant, or the default one when the variant is empty or unknown. */
        public String texture(String variant) {
            String t = variant == null ? null : variants.get(variant);
            return t != null ? t : texture;
        }
    }

    private static final class Invalid extends Exception {

        Invalid(String message) {
            super(message);
        }
    }

    private final Map<String, Model> models = new LinkedHashMap<>();
    private final List<String> errors = new ArrayList<>();

    /** Displays by id ({@code namespace:key}); later packs override earlier ones. */
    public Map<String, Model> models() {
        return Collections.unmodifiableMap(models);
    }

    /** Problems found, one line each. */
    public List<String> errors() {
        return Collections.unmodifiableList(errors);
    }

    /** Reads one manifest of {@code namespace} ({@code source}: where it came from, for the messages). */
    public void add(String namespace, String json, String source) {
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
            errors.add(source + ": \"_formato\" must be " + FORMAT);
            return;
        }
        Object list = root.get("exibicoes");
        if (!(list instanceof Map)) {
            errors.add(source + ": \"exibicoes\" must be an object of id -> display");
            return;
        }
        for (Map.Entry<?, ?> e : ((Map<?, ?>) list).entrySet()) {
            String key = String.valueOf(e.getKey());
            if (key.startsWith("_")) {
                continue;
            }
            String id = namespace + ":" + key;
            String where = source + ": display '" + key + "'";
            if (!KEY.matcher(key)
                .matches()) {
                errors.add(where + ": invalid key (a-z 0-9 / . _ -)");
                continue;
            }
            if (!(e.getValue() instanceof Map)) {
                errors.add(where + ": expected an object");
                continue;
            }
            try {
                models.put(id, parse(id, (Map<?, ?>) e.getValue()));
            } catch (Invalid problem) {
                errors.add(where + ": " + problem.getMessage());
            }
        }
    }

    private static Model parse(String id, Map<?, ?> o) throws Invalid {
        String geo = location(o, "geo");
        String animations = location(o, "animacoes");
        String texture = location(o, "textura");
        Map<String, String> variants = new LinkedHashMap<>();
        Object v = o.get("variantes");
        if (v != null) {
            if (!(v instanceof Map)) {
                throw new Invalid("variantes must be an object of name -> texture");
            }
            for (Map.Entry<?, ?> e : ((Map<?, ?>) v).entrySet()) {
                String name = String.valueOf(e.getKey());
                if (!NAME.matcher(name)
                    .matches() || !(e.getValue() instanceof String)
                    || !LOCATION.matcher((String) e.getValue())
                        .matches()) {
                    throw new Invalid("variantes." + name + ": a name (a-z 0-9 _) and a texture namespace:path");
                }
                variants.put(name, (String) e.getValue());
            }
        }
        Object s = o.get("estados");
        if (!(s instanceof Map) || ((Map<?, ?>) s).isEmpty()) {
            throw new Invalid("needs estados (state -> {animacao, laco})");
        }
        Map<String, State> states = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : ((Map<?, ?>) s).entrySet()) {
            String name = String.valueOf(e.getKey());
            if (!NAME.matcher(name)
                .matches() || !(e.getValue() instanceof Map)) {
                throw new Invalid("estados." + name + ": a name (a-z 0-9 _) and an object");
            }
            Map<?, ?> st = (Map<?, ?>) e.getValue();
            Object anim = st.get("animacao");
            if (!(anim instanceof String) || ((String) anim).isEmpty()) {
                throw new Invalid("estados." + name + ".animacao: the animation's name");
            }
            states.put(name, new State((String) anim, Boolean.TRUE.equals(st.get("laco"))));
        }
        List<String> items = names(o, "itens");
        if (items.size() > DisplayWire.MAX_ITEMS) {
            throw new Invalid("itens: at most " + DisplayWire.MAX_ITEMS + " bones");
        }
        float scale = (float) number(o, "escala_item", 1, 0.05, 16);
        double distance = number(o, "distancia", 64, 4, 256);
        float modelScale = (float) number(o, "escala", 1, 0.05, 64);
        int transition = (int) Math.round(number(o, "transicao", 0, 0, 40));
        return new Model(
            id,
            geo,
            animations,
            texture,
            Collections.unmodifiableMap(variants),
            Collections.unmodifiableMap(states),
            items,
            scale,
            names(o, "luz"),
            names(o, "translucido"),
            distance,
            modelScale,
            names(o, "ocultos"),
            transition);
    }

    private static String location(Map<?, ?> o, String field) throws Invalid {
        Object v = o.get(field);
        if (!(v instanceof String) || !LOCATION.matcher((String) v)
            .matches()) {
            throw new Invalid(field + " must be a resource namespace:path");
        }
        return (String) v;
    }

    private static List<String> names(Map<?, ?> o, String field) throws Invalid {
        Object v = o.get(field);
        if (v == null) {
            return Collections.emptyList();
        }
        if (!(v instanceof List)) {
            throw new Invalid(field + " must be a list of bone names");
        }
        List<String> out = new ArrayList<>();
        for (Object x : (List<?>) v) {
            if (!(x instanceof String) || ((String) x).isEmpty()) {
                throw new Invalid(field + " must be a list of bone names");
            }
            out.add((String) x);
        }
        return Collections.unmodifiableList(out);
    }

    private static double number(Map<?, ?> o, String field, double fallback, double min, double max) throws Invalid {
        Object v = o.get(field);
        if (v == null) {
            return fallback;
        }
        if (!(v instanceof Double) || (Double) v < min || (Double) v > max) {
            throw new Invalid(field + " must be a number from " + min + " to " + max);
        }
        return (Double) v;
    }
}
