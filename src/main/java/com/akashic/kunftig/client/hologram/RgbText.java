package com.akashic.kunftig.client.hologram;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Hex colors in hologram text, as Spigot writes them: {@code §x§R§R§G§G§B§B} sets the color of what follows (up to the
 * next color). The 1.7.10 font only knows the 16 legacy colors, so a line is cut into runs, each drawn with its own
 * color; legacy codes ({@code §a}, {@code §l}...) inside a run work as usual. A {@code §x} that is not followed by six
 * hex digits is left as it is.
 */
final class RgbText {

    /** A piece of the line and its color (0xRRGGBB), or -1 to keep the font's default (white). */
    static final class Run {

        final String text;
        final int rgb;

        Run(String text, int rgb) {
            this.text = text;
            this.rgb = rgb;
        }

        @Override
        public String toString() {
            return (rgb < 0 ? "-" : Integer.toHexString(rgb)) + ":" + text;
        }
    }

    private RgbText() {}

    /** Whether the line has any hex color (most do not: they are drawn as before, in one piece). */
    static boolean hasRgb(String line) {
        return line.indexOf('§') >= 0 && hexAt(line, line.indexOf("§x")) >= 0;
    }

    /** The runs of a line. A line without hex colors is one run with -1. */
    static List<Run> runs(String line) {
        if (line.indexOf("§x") < 0) {
            return Collections.singletonList(new Run(line, -1));
        }
        List<Run> out = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        int rgb = -1;
        int i = 0;
        while (i < line.length()) {
            int hex = line.startsWith("§x", i) ? hexAt(line, i) : -1;
            if (hex >= 0) {
                if (text.length() > 0) {
                    out.add(new Run(text.toString(), rgb));
                    text.setLength(0);
                }
                rgb = hex;
                i += 14;
                continue;
            }
            // A legacy color resets the hex one (it would win anyway inside the run).
            if (line.charAt(i) == '§' && i + 1 < line.length() && isLegacyColor(line.charAt(i + 1))) {
                if (text.length() > 0) {
                    out.add(new Run(text.toString(), rgb));
                    text.setLength(0);
                }
                rgb = -1;
            }
            text.append(line.charAt(i));
            i++;
        }
        if (text.length() > 0) {
            out.add(new Run(text.toString(), rgb));
        }
        return out;
    }

    /** The color of a {@code §x§R§R§G§G§B§B} at {@code at}, or -1. */
    static int hexAt(String s, int at) {
        if (at < 0 || at + 14 > s.length() || s.charAt(at) != '§' || Character.toLowerCase(s.charAt(at + 1)) != 'x') {
            return -1;
        }
        int value = 0;
        for (int k = 0; k < 6; k++) {
            int p = at + 2 + k * 2;
            if (s.charAt(p) != '§') {
                return -1;
            }
            int digit = Character.digit(s.charAt(p + 1), 16);
            if (digit < 0) {
                return -1;
            }
            value = value * 16 + digit;
        }
        return value;
    }

    /** The line without hex codes (what the font measures and draws). */
    static String strip(String line) {
        StringBuilder b = new StringBuilder();
        for (Run r : runs(line)) {
            b.append(r.text);
        }
        return b.toString();
    }

    private static boolean isLegacyColor(char c) {
        return Character.digit(c, 16) >= 0 || c == 'r' || c == 'R';
    }
}
