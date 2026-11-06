package com.akashic.kunftig.blade;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.akashic.kunftig.common.text.Json;

/**
 * The blades a resource pack declares in {@code assets/<namespace>/kunftig/blades.json}: for each item model id (the
 * {@code kunftig:model} of an item, as for the icons and the 3D held models), SlashBlade's OBJ and texture and how the
 * blade is carried when not in use, plus the two motions every blade of the namespace plays (the blade rig's and the
 * player body's). Later packs override earlier ones per blade. No Minecraft classes.
 *
 * <pre>
 * {
 *   "_formato": 1,
 *   "movimento_lamina": "overkill:combostate/motion.vmd",
 *   "movimento_jogador": "overkill:model/pa/player_motion.vmd",
 *   "laminas": {
 *     "katana/basica": {
 *       "obj": "overkill:model/blade.obj", "textura": "overkill:model/blade.png",
 *       "porte": "DEFAULT", "itens": ["minecraft:golden_sword"], "quebrada": "overkill:broken"
 *     }
 *   }
 * }
 * </pre>
 */
public final class BladeIndex {

    public static final String PATH = "kunftig/blades.json";
    private static final Pattern RESOURCE = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    private static final Pattern KEY = Pattern.compile("[a-z0-9_./-]{1,128}");

    /** How a blade not in the main hand is carried (SlashBlade's {@code CarryType}). */
    public enum Carry {
        DEFAULT,
        KATANA,
        PSO2,
        NINJA,
        RNINJA,
        NONE
    }

    /** One blade. */
    public static final class Blade {

        public final String id;
        public final String obj;
        public final String texture;
        public final Carry carry;
        public final String bladeMotion;
        public final String playerMotion;
        public final List<String> items;
        /** The persistent data key whose positive number marks the item broken (null: never broken). */
        public final String brokenKey;

        Blade(String id, String obj, String texture, Carry carry, String bladeMotion, String playerMotion,
            List<String> items, String brokenKey) {
            this.id = id;
            this.obj = obj;
            this.texture = texture;
            this.carry = carry;
            this.bladeMotion = bladeMotion;
            this.playerMotion = playerMotion;
            this.items = Collections.unmodifiableList(items);
            this.brokenKey = brokenKey;
        }
    }

    private final Map<String, Blade> blades = new LinkedHashMap<>();
    private final List<String> errors = new ArrayList<>();

    public Map<String, Blade> blades() {
        return Collections.unmodifiableMap(blades);
    }

    public List<String> errors() {
        return Collections.unmodifiableList(errors);
    }

    public boolean isEmpty() {
        return blades.isEmpty();
    }

    /** The blade of an item model id ({@code namespace:path}), or null. */
    public Blade get(String modelId) {
        return modelId == null ? null : blades.get(modelId);
    }

    /** Every item some blade is drawn on. */
    public Set<String> items() {
        Set<String> out = new LinkedHashSet<>();
        for (Blade b : blades.values()) {
            out.addAll(b.items);
        }
        return out;
    }

    /** Reads one namespace's file (a later call overrides earlier blades of the same id). */
    public void add(String namespace, String json, String source) {
        Map<?, ?> root;
        try {
            Object o = Json.parse(json, 32);
            if (!(o instanceof Map)) {
                errors.add(source + ": not a JSON object");
                return;
            }
            root = (Map<?, ?>) o;
        } catch (Json.JsonException e) {
            errors.add(source + ": " + e.getMessage());
            return;
        }
        Object format = root.get("_formato");
        if (!(format instanceof Double) || (Double) format != 1) {
            errors.add(source + ": _formato must be 1");
            return;
        }
        String bladeMotion = resource(root, "movimento_lamina", namespace, source, true);
        String playerMotion = resource(root, "movimento_jogador", namespace, source, false);
        Object list = root.get("laminas");
        if (!(list instanceof Map)) {
            errors.add(source + ": \"laminas\" must be an object of model -> blade");
            return;
        }
        if (bladeMotion == null) {
            return;
        }
        for (Map.Entry<?, ?> e : ((Map<?, ?>) list).entrySet()) {
            String key = String.valueOf(e.getKey());
            String where = source + ": " + key;
            if (!KEY.matcher(key)
                .matches() || !(e.getValue() instanceof Map)) {
                errors.add(where + ": invalid blade (key a-z 0-9 _ . / -, with an object)");
                continue;
            }
            Map<?, ?> b = (Map<?, ?>) e.getValue();
            String obj = resource(b, "obj", namespace, where, true);
            String texture = resource(b, "textura", namespace, where, true);
            if (obj == null || texture == null) {
                continue;
            }
            Carry carry = Carry.DEFAULT;
            Object c = b.get("porte");
            if (c != null) {
                try {
                    carry = Carry.valueOf(String.valueOf(c));
                } catch (IllegalArgumentException ex) {
                    errors.add(where + ": porte must be one of " + java.util.Arrays.toString(Carry.values()));
                    continue;
                }
            }
            List<String> items = new ArrayList<>();
            Object it = b.get("itens");
            if (it instanceof List) {
                for (Object x : (List<?>) it) {
                    items.add(String.valueOf(x));
                }
            } else if (it != null) {
                errors.add(where + ": itens must be a list of item names");
                continue;
            }
            Object broken = b.get("quebrada");
            String brokenKey = broken == null ? null : String.valueOf(broken);
            String id = namespace + ":" + key;
            blades.put(
                id,
                new Blade(
                    id,
                    obj,
                    texture,
                    carry,
                    resource(b, "movimento_lamina", namespace, where, false) != null
                        ? resource(b, "movimento_lamina", namespace, where, false)
                        : bladeMotion,
                    resource(b, "movimento_jogador", namespace, where, false) != null
                        ? resource(b, "movimento_jogador", namespace, where, false)
                        : playerMotion,
                    items,
                    brokenKey));
        }
    }

    private String resource(Map<?, ?> o, String field, String namespace, String where, boolean required) {
        Object v = o.get(field);
        if (v == null) {
            if (required) {
                errors.add(where + ": missing " + field);
            }
            return null;
        }
        String s = String.valueOf(v);
        if (!s.contains(":")) {
            s = namespace + ":" + s;
        }
        if (!RESOURCE.matcher(s)
            .matches()) {
            errors.add(where + ": " + field + " is not a resource (namespace:path): " + s);
            return null;
        }
        return s;
    }
}
