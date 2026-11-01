package com.akashic.kunftig.bukkit.nametag;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.akashic.kunftig.bukkit.text.TextParseException;
import com.akashic.kunftig.common.text.NameStyle;
import com.akashic.kunftig.common.text.TextColor;
import com.akashic.kunftig.common.text.TextDecoration;

/**
 * Parses a name style from space- or comma-separated tokens: colors ({@code #ff8800}, {@code gold}, {@code &6}) and
 * decorations ({@code bold}, {@code italic}, {@code underlined}, {@code strikethrough}, {@code obfuscated}).
 * No color = default color, one color = solid, two or more = gradient. {@code plain} (alone) means "no color, but
 * do not let the prefix color carry into the name".
 * <p>
 * Example: {@code #ff5555 #ffaa00 bold} - a bold red-to-gold gradient.
 */
public final class NameStyleParser {

    private NameStyleParser() {}

    public static NameStyle parse(String tokens) throws TextParseException {
        String trimmed = tokens.trim();
        if (trimmed.isEmpty() || trimmed.equalsIgnoreCase("plain")) {
            return NameStyle.PLAIN;
        }
        List<TextColor> colors = new ArrayList<TextColor>();
        int decorations = 0;
        for (String raw : trimmed.split("[\\s,]+")) {
            String token = raw.toLowerCase(Locale.ROOT);
            TextDecoration decoration = decoration(token);
            if (decoration != null) {
                decorations |= decoration.bit();
                continue;
            }
            TextColor color = color(token);
            if (color == null) {
                throw new TextParseException("Unknown color or decoration '" + raw + "' in name style");
            }
            colors.add(color);
        }
        if (colors.size() > NameStyle.MAX_STOPS) {
            throw new TextParseException("A name gradient takes at most " + NameStyle.MAX_STOPS + " colors");
        }
        return NameStyle.of(decorations, colors);
    }

    private static TextColor color(String token) {
        if ((token.startsWith("&") || token.startsWith("\u00a7")) && token.length() == 2) {
            return TextColor.legacy(token.charAt(1));
        }
        if (token.startsWith("&#") && token.length() == 8) {
            return TextColor.parse(token.substring(1));
        }
        return TextColor.parse(token);
    }

    private static TextDecoration decoration(String token) {
        switch (token) {
            case "bold":
            case "b":
            case "&l":
                return TextDecoration.BOLD;
            case "italic":
            case "i":
            case "em":
            case "&o":
                return TextDecoration.ITALIC;
            case "underlined":
            case "underline":
            case "u":
            case "&n":
                return TextDecoration.UNDERLINED;
            case "strikethrough":
            case "st":
            case "&m":
                return TextDecoration.STRIKETHROUGH;
            case "obfuscated":
            case "obf":
            case "&k":
                return TextDecoration.OBFUSCATED;
            default:
                return null;
        }
    }

    /** Inverse of {@link #parse(String)}, for display and persistence. */
    public static String format(NameStyle style) {
        StringBuilder out = new StringBuilder();
        for (TextColor color : style.colors()) {
            out.append(color.asHex()).append(' ');
        }
        for (TextDecoration decoration : TextDecoration.all()) {
            if ((style.decorationMask() & decoration.bit()) != 0) {
                out.append(decoration.jsonKey()).append(' ');
            }
        }
        String result = out.toString().trim();
        return result.isEmpty() ? "plain" : result;
    }
}
