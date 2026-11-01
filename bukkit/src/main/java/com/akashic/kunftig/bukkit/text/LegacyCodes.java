package com.akashic.kunftig.bukkit.text;

/**
 * Converts legacy formatting codes ({@code &} or {@code §}) into MiniMessage tags, so admins can mix both styles:
 * {@code &c&lAdmin <gradient:red:gold>Name</gradient>}.
 * <ul>
 * <li>{@code &a}..{@code &f}, {@code &0}..{@code &9} become {@code <reset><color>}: in legacy text a color code ends
 * every decoration, and {@code <reset>} reproduces that;</li>
 * <li>{@code &k}..{@code &o} become decoration tags, {@code &r} becomes {@code <reset>};</li>
 * <li>{@code &#RRGGBB} and Spigot's {@code &x&R&R&G&G&B&B} become {@code <reset><#RRGGBB>};</li>
 * <li>{@code \&} produces a literal {@code &}; anything else is left untouched.</li>
 * </ul>
 */
public final class LegacyCodes {

    private static final String[] COLOR_NAMES = { "black", "dark_blue", "dark_green", "dark_aqua", "dark_red",
        "dark_purple", "gold", "gray", "dark_gray", "blue", "green", "aqua", "red", "light_purple", "yellow", "white" };

    private LegacyCodes() {}

    private static boolean isCodeChar(char c) {
        return c == '&' || c == '\u00a7';
    }

    public static String toMiniMessage(String input) {
        StringBuilder out = new StringBuilder(input.length() + 16);
        int i = 0;
        while (i < input.length()) {
            char c = input.charAt(i);
            if (c == '\\' && i + 1 < input.length() && input.charAt(i + 1) == '&') {
                out.append('&');
                i += 2;
                continue;
            }
            if (!isCodeChar(c) || i + 1 >= input.length()) {
                out.append(c);
                i++;
                continue;
            }
            char code = Character.toLowerCase(input.charAt(i + 1));
            String hex;
            if (code == '#' && (hex = hexAt(input, i + 2)) != null) {
                out.append("<reset><#").append(hex).append('>');
                i += 8;
            } else if (code == 'x' && (hex = spigotHexAt(input, i + 2)) != null) {
                out.append("<reset><#").append(hex).append('>');
                i += 14;
            } else if (code >= '0' && code <= '9' || code >= 'a' && code <= 'f') {
                out.append("<reset><").append(COLOR_NAMES[Character.digit(code, 16)]).append('>');
                i += 2;
            } else if (code >= 'k' && code <= 'o' || code == 'r') {
                out.append(decorationTag(code));
                i += 2;
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    private static String decorationTag(char code) {
        switch (code) {
            case 'k':
                return "<obfuscated>";
            case 'l':
                return "<bold>";
            case 'm':
                return "<strikethrough>";
            case 'n':
                return "<underlined>";
            case 'o':
                return "<italic>";
            default:
                return "<reset>";
        }
    }

    /** Six hex digits starting at {@code start}, or null. */
    private static String hexAt(String s, int start) {
        if (start + 6 > s.length()) {
            return null;
        }
        for (int k = start; k < start + 6; k++) {
            if (Character.digit(s.charAt(k), 16) < 0) {
                return null;
            }
        }
        return s.substring(start, start + 6).toLowerCase(java.util.Locale.ROOT);
    }

    /** Six code-prefixed hex digits ({@code &R&R&G&G&B&B}) starting at {@code start}, or null. */
    private static String spigotHexAt(String s, int start) {
        if (start + 12 > s.length()) {
            return null;
        }
        StringBuilder hex = new StringBuilder(6);
        for (int k = start; k < start + 12; k += 2) {
            if (!isCodeChar(s.charAt(k)) || Character.digit(s.charAt(k + 1), 16) < 0) {
                return null;
            }
            hex.append(Character.toLowerCase(s.charAt(k + 1)));
        }
        return hex.toString();
    }
}
