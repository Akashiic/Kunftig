package com.akashic.kunftig.bukkit.text;

import java.util.ArrayList;
import java.util.List;

import com.akashic.kunftig.common.text.Component;
import com.akashic.kunftig.common.text.ComponentJson;
import com.akashic.kunftig.common.text.Json;
import com.akashic.kunftig.common.text.Style;
import com.akashic.kunftig.common.text.TextColor;
import com.akashic.kunftig.common.text.TextDecoration;

import net.kyori.adventure.text.KeybindComponent;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.ParsingException;

/**
 * Parses text written by admins or other plugins into a {@link Component}.
 * <p>
 * Accepted forms: a JSON text component, otherwise MiniMessage with legacy {@code &}/{@code §} codes allowed
 * anywhere (see {@link LegacyCodes}). Input is JSON only when it starts with <code>{</code>, or with {@code [}
 * followed by <code>{</code> or {@code "}: prefixes such as {@code [VIP] } are ordinary text.
 */
public final class TextParser {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.builder().strict(false).build();

    private TextParser() {}

    public static Component parse(String input) throws TextParseException {
        if (input == null || input.isEmpty()) {
            return Component.EMPTY;
        }
        String trimmed = input.trim();
        if (looksLikeJson(trimmed)) {
            try {
                return ComponentJson.parse(trimmed);
            } catch (Json.JsonException e) {
                throw new TextParseException("Invalid JSON text component: " + e.getMessage());
            }
        }
        net.kyori.adventure.text.Component parsed;
        try {
            parsed = MINI_MESSAGE.deserialize(LegacyCodes.toMiniMessage(input));
        } catch (ParsingException e) {
            throw new TextParseException("Invalid MiniMessage: " + e.getMessage());
        }
        return convert(parsed);
    }

    static boolean looksLikeJson(String trimmed) {
        if (trimmed.startsWith("{")) {
            return true;
        }
        if (!trimmed.startsWith("[")) {
            return false;
        }
        String rest = trimmed.substring(1).trim();
        return rest.startsWith("{") || rest.startsWith("\"");
    }

    /** Converts an Adventure component tree into the shared model (text, color, decorations, children). */
    static Component convert(net.kyori.adventure.text.Component source) {
        String text;
        if (source instanceof TextComponent) {
            text = ((TextComponent) source).content();
        } else if (source instanceof TranslatableComponent) {
            TranslatableComponent translatable = (TranslatableComponent) source;
            text = translatable.fallback() != null ? translatable.fallback() : translatable.key();
        } else if (source instanceof KeybindComponent) {
            text = ((KeybindComponent) source).keybind();
        } else {
            text = "";
        }

        Style style = Style.EMPTY;
        if (source.color() != null) {
            style = style.withColor(TextColor.rgb(source.color().value()));
        }
        for (TextDecoration decoration : TextDecoration.all()) {
            net.kyori.adventure.text.format.TextDecoration.State state = source.decoration(adventure(decoration));
            if (state != net.kyori.adventure.text.format.TextDecoration.State.NOT_SET) {
                style = style.withDecoration(decoration,
                    state == net.kyori.adventure.text.format.TextDecoration.State.TRUE);
            }
        }

        List<Component> children = new ArrayList<Component>(source.children().size());
        for (net.kyori.adventure.text.Component child : source.children()) {
            children.add(convert(child));
        }
        return Component.of(text, style, children);
    }

    private static net.kyori.adventure.text.format.TextDecoration adventure(TextDecoration decoration) {
        switch (decoration) {
            case OBFUSCATED:
                return net.kyori.adventure.text.format.TextDecoration.OBFUSCATED;
            case BOLD:
                return net.kyori.adventure.text.format.TextDecoration.BOLD;
            case STRIKETHROUGH:
                return net.kyori.adventure.text.format.TextDecoration.STRIKETHROUGH;
            case UNDERLINED:
                return net.kyori.adventure.text.format.TextDecoration.UNDERLINED;
            case ITALIC:
            default:
                return net.kyori.adventure.text.format.TextDecoration.ITALIC;
        }
    }
}
