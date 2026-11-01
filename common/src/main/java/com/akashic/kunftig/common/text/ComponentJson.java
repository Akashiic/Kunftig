package com.akashic.kunftig.common.text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Converts between {@link Component} and Minecraft's JSON text format (1.16+ dialect: colors may be
 * {@code #rrggbb}). Parsing follows vanilla semantics:
 * <ul>
 * <li>a JSON string is a literal text component;</li>
 * <li>a JSON array is its first element with the remaining elements appended as children, so they
 * inherit the first element's style;</li>
 * <li>{@code translate} renders its {@code fallback} (or the key), {@code keybind} its key, and
 * {@code score}/{@code selector}/{@code nbt} render empty: names never need them, and this side
 * cannot resolve them;</li>
 * <li>unknown keys and invalid colors are ignored, like the vanilla client does.</li>
 * </ul>
 */
public final class ComponentJson {

    /** Maximum nesting depth accepted from the network. */
    public static final int MAX_DEPTH = 32;
    /** Maximum number of component nodes accepted from the network. */
    public static final int MAX_NODES = 2048;

    private ComponentJson() {}

    public static Component parse(String json) throws Json.JsonException {
        Object root = Json.parse(json, MAX_DEPTH * 2 + 2);
        int[] budget = { MAX_NODES };
        return toComponent(root, budget);
    }

    private static Component toComponent(Object node, int[] budget) throws Json.JsonException {
        if (--budget[0] < 0) {
            throw new Json.JsonException("Component has more than " + MAX_NODES + " nodes");
        }
        if (node instanceof String) {
            return Component.text((String) node);
        }
        if (node instanceof Boolean || node instanceof Double) {
            return Component.text(primitiveText(node));
        }
        if (node instanceof List) {
            List<?> list = (List<?>) node;
            if (list.isEmpty()) {
                throw new Json.JsonException("Empty component array");
            }
            Component first = toComponent(list.get(0), budget);
            if (list.size() == 1) {
                return first;
            }
            List<Component> children = new ArrayList<Component>(first.children());
            for (int i = 1; i < list.size(); i++) {
                children.add(toComponent(list.get(i), budget));
            }
            return Component.of(first.text(), first.style(), children);
        }
        if (node instanceof Map) {
            return objectToComponent((Map<?, ?>) node, budget);
        }
        throw new Json.JsonException("A component must be a string, array or object");
    }

    private static String primitiveText(Object value) {
        if (value instanceof Double) {
            double d = (Double) value;
            if (d == Math.rint(d) && !Double.isInfinite(d) && Math.abs(d) < 1e15) {
                return Long.toString((long) d);
            }
        }
        return String.valueOf(value);
    }

    private static Component objectToComponent(Map<?, ?> object, int[] budget) throws Json.JsonException {
        String text = textOf(object);

        Style style = Style.EMPTY;
        Object color = object.get("color");
        if (color instanceof String) {
            style = style.withColor(TextColor.parse((String) color));
        }
        for (TextDecoration decoration : TextDecoration.all()) {
            Object value = object.get(decoration.jsonKey());
            if (value instanceof Boolean) {
                style = style.withDecoration(decoration, (Boolean) value);
            }
        }

        List<Component> children = Collections.emptyList();
        Object extra = object.get("extra");
        if (extra instanceof List) {
            List<?> extras = (List<?>) extra;
            children = new ArrayList<Component>(extras.size());
            for (Object child : extras) {
                children.add(toComponent(child, budget));
            }
        } else if (extra != null && extra != Json.NULL) {
            throw new Json.JsonException("'extra' must be an array");
        }
        return Component.of(text, style, children);
    }

    private static String textOf(Map<?, ?> object) {
        Object text = object.get("text");
        if (text instanceof String) {
            return (String) text;
        }
        if (text instanceof Boolean || text instanceof Double) {
            return primitiveText(text);
        }
        Object translate = object.get("translate");
        if (translate instanceof String) {
            Object fallback = object.get("fallback");
            return fallback instanceof String ? (String) fallback : (String) translate;
        }
        Object keybind = object.get("keybind");
        if (keybind instanceof String) {
            return (String) keybind;
        }
        return "";
    }

    public static String write(Component component) {
        StringBuilder out = new StringBuilder();
        write(component, out);
        return out.toString();
    }

    private static void write(Component component, StringBuilder out) {
        out.append("{\"text\":");
        Json.writeString(out, component.text());
        Style style = component.style();
        if (style.color() != null) {
            out.append(",\"color\":");
            Json.writeString(out, style.color().serialize());
        }
        for (TextDecoration decoration : TextDecoration.all()) {
            Boolean value = style.decoration(decoration);
            if (value != null) {
                out.append(",\"").append(decoration.jsonKey()).append("\":").append(value.booleanValue());
            }
        }
        if (!component.children().isEmpty()) {
            out.append(",\"extra\":[");
            boolean first = true;
            for (Component child : component.children()) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                write(child, out);
            }
            out.append(']');
        }
        out.append('}');
    }
}
