package com.akashic.kunftig.itemmodel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.akashic.kunftig.common.text.Json;

/**
 * The item models a resource pack declares in {@code assets/<namespace>/kunftig/item_models.json}: model key to
 * 1.7.10 icon name.
 *
 * <pre>
 * {
 *   "tucunare": "akashicfishing:fish/tucunare",
 *   "bota_velha": "junk/boot"
 * }
 * </pre>
 *
 * In {@code assets/akashicfishing/...} this declares models {@code akashicfishing:tucunare} and
 * {@code akashicfishing:bota_velha}. A model can also be an object, {@code {"icone": "...", "celula": "..."}}: the
 * {@code celula} is drawn in container slots over the whole 18x18 slot cell (the icon's 16x16 face and the 1 pixel
 * around
 * it), so the items of neighboring slots can join into one picture (a button over several slots). Its texture is 32x32
 * with the cell in the top left 18x18 (a power of two: the items atlas keeps its mipmaps). The same can be said with a
 * sibling entry, {@code "<model>.celula": "<icon>"}, which a Kunftig without slot cells reads as one more model no item
 * uses (the pack keeps working there). Icon names are those of
 * {@code Item#setTextureName}:
 * {@code akashicfishing:fish/tucunare} is {@code assets/akashicfishing/textures/items/fish/tucunare.png} (animated
 * with a {@code .mcmeta} as usual); without a namespace, the file's own is used. No Minecraft classes here.
 */
public final class ItemModelIndex {

    /** Path of the index inside each resource domain. */
    public static final String PATH = "kunftig/item_models.json";
    /** Where the server writes the model id ({@code KunftigItems.MODEL_KEY} under {@code PublicBukkitValues}). */
    public static final String NBT_ROOT = "PublicBukkitValues";
    public static final String NBT_KEY = "kunftig:model";

    private static final Pattern NAMESPACE = Pattern.compile("[a-z0-9._-]+");
    private static final Pattern PATH_PART = Pattern.compile("[a-z0-9/._-]+");
    private static final int MAX_DEPTH = 4;

    private final Map<String, String> icons = new LinkedHashMap<>();
    private final Map<String, String> cells = new LinkedHashMap<>();
    private final List<String> errors = new ArrayList<>();

    /** Model id ({@code namespace:key}) to icon name, in the order declared; later packs override earlier ones. */
    public Map<String, String> icons() {
        return Collections.unmodifiableMap(icons);
    }

    /** Suffix of a sibling entry that gives a model's slot cell. */
    public static final String CELL_SUFFIX = ".celula";

    /**
     * Model id to the icon name of its slot cell ({@code celula}, or a sibling {@code <model>.celula}), for the models
     * that have one.
     */
    public Map<String, String> cells() {
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : icons.entrySet()) {
            String key = e.getKey();
            if (key.endsWith(CELL_SUFFIX)) {
                String model = key.substring(0, key.length() - CELL_SUFFIX.length());
                if (icons.containsKey(model)) {
                    out.put(model, e.getValue());
                }
            }
        }
        out.putAll(cells);
        return Collections.unmodifiableMap(out);
    }

    /** Problems found, one line each (the rest of the file still applies). */
    public List<String> errors() {
        return Collections.unmodifiableList(errors);
    }

    /** Adds the declarations of one {@code item_models.json} of {@code namespace}. */
    public void add(String namespace, String json, String source) {
        Object parsed;
        try {
            parsed = Json.parse(json, MAX_DEPTH);
        } catch (Json.JsonException e) {
            errors.add(source + ": " + e.getMessage());
            return;
        }
        if (!(parsed instanceof Map)) {
            errors.add(source + ": expected a JSON object of model -> icon");
            return;
        }
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) parsed).entrySet()) {
            String key = String.valueOf(entry.getKey());
            Object value = entry.getValue();
            if (!PATH_PART.matcher(key)
                .matches()) {
                errors.add(source + ": invalid model key '" + key + "' (use a-z 0-9 / . _ -)");
                continue;
            }
            String cell = null;
            if (value instanceof Map) {
                Map<?, ?> o = (Map<?, ?>) value;
                Object c = o.get("celula");
                if (c != null) {
                    cell = c instanceof String ? iconName(namespace, (String) c) : null;
                    if (cell == null) {
                        errors.add(source + ": model '" + key + "': \"celula\" needs an icon name");
                        continue;
                    }
                }
                value = o.get("icone");
            }
            String icon = value instanceof String ? iconName(namespace, (String) value) : null;
            if (icon == null) {
                errors.add(source + ": model '" + key + "' needs an icon name like \"" + namespace + ":path\"");
                continue;
            }
            icons.put(namespace + ":" + key, icon);
            if (cell != null) {
                cells.put(namespace + ":" + key, cell);
            } else {
                cells.remove(namespace + ":" + key); // a later pack without a cell for this model
            }
        }
    }

    /** {@code ns:path} as is, {@code path} as {@code namespace:path}; null if invalid. */
    static String iconName(String namespace, String value) {
        int colon = value.indexOf(':');
        String ns = colon < 0 ? namespace : value.substring(0, colon);
        String path = colon < 0 ? value : value.substring(colon + 1);
        if (!NAMESPACE.matcher(ns)
            .matches()
            || !PATH_PART.matcher(path)
                .matches()
            || path.startsWith("/")
            || path.endsWith("/")) {
            return null;
        }
        return ns + ":" + path;
    }
}
