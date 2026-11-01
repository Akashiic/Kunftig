package com.akashic.kunftig.common.text;

import java.util.Locale;

/**
 * An sRGB text color. Instances created for the 16 legacy colors carry their legacy code so the
 * renderer can emit the short {@code §c} form instead of the 14-character {@code §x} form.
 */
public final class TextColor {

    /** Legacy names in code order 0-f, as used by Minecraft's JSON text format. */
    private static final String[] LEGACY_NAMES = { "black", "dark_blue", "dark_green", "dark_aqua", "dark_red",
        "dark_purple", "gold", "gray", "dark_gray", "blue", "green", "aqua", "red", "light_purple", "yellow", "white" };

    /** RGB values of the vanilla 1.7.10 FontRenderer palette, in code order 0-f. */
    private static final int[] LEGACY_RGB = { 0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00,
        0xAAAAAA, 0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF };

    private static final String LEGACY_CODES = "0123456789abcdef";

    private static final TextColor[] LEGACY = new TextColor[16];

    static {
        for (int i = 0; i < 16; i++) {
            LEGACY[i] = new TextColor(LEGACY_RGB[i], LEGACY_CODES.charAt(i));
        }
    }

    private final int rgb;
    /** Legacy code char when this color is exactly one of the 16 legacy colors, else {@code 0}. */
    private final char legacyCode;

    private TextColor(int rgb, char legacyCode) {
        this.rgb = rgb & 0xFFFFFF;
        this.legacyCode = legacyCode;
    }

    /** Returns the color for an RGB value, reusing the legacy instance when the value is a legacy color. */
    public static TextColor rgb(int rgb) {
        int value = rgb & 0xFFFFFF;
        for (TextColor legacy : LEGACY) {
            if (legacy.rgb == value) {
                return legacy;
            }
        }
        return new TextColor(value, (char) 0);
    }

    /** Legacy color for a code char {@code 0-9a-f} (case-insensitive), or {@code null}. */
    public static TextColor legacy(char code) {
        int index = LEGACY_CODES.indexOf(Character.toLowerCase(code));
        return index < 0 ? null : LEGACY[index];
    }

    /** Parses {@code #RRGGBB} or a legacy name such as {@code dark_red}; returns {@code null} if invalid. */
    public static TextColor parse(String value) {
        if (value == null) {
            return null;
        }
        if (value.length() == 7 && value.charAt(0) == '#') {
            int rgb = 0;
            for (int i = 1; i < 7; i++) {
                int digit = Character.digit(value.charAt(i), 16);
                if (digit < 0) {
                    return null;
                }
                rgb = (rgb << 4) | digit;
            }
            return rgb(rgb);
        }
        String name = value.toLowerCase(Locale.ROOT);
        for (int i = 0; i < 16; i++) {
            if (LEGACY_NAMES[i].equals(name)) {
                return LEGACY[i];
            }
        }
        return null;
    }

    public int value() {
        return rgb;
    }

    public boolean isLegacy() {
        return legacyCode != 0;
    }

    /** The legacy code char, only valid when {@link #isLegacy()}. */
    public char legacyCode() {
        return legacyCode;
    }

    /** Nearest of the 16 legacy colors, using the "redmean" weighted RGB distance. */
    public TextColor nearestLegacy() {
        if (isLegacy()) {
            return this;
        }
        TextColor best = LEGACY[0];
        long bestDistance = Long.MAX_VALUE;
        for (TextColor candidate : LEGACY) {
            long distance = distance(rgb, candidate.rgb);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return best;
    }

    /**
     * Squared "redmean" distance (scaled by 256 to stay in integers): a cheap approximation of
     * perceptual distance that weighs red/blue differences by the mean red level.
     */
    static long distance(int a, int b) {
        int r1 = (a >> 16) & 0xFF, g1 = (a >> 8) & 0xFF, b1 = a & 0xFF;
        int r2 = (b >> 16) & 0xFF, g2 = (b >> 8) & 0xFF, b2 = b & 0xFF;
        long rMean = (r1 + r2) / 2;
        long dr = r1 - r2, dg = g1 - g2, db = b1 - b2;
        return ((512 + rMean) * dr * dr) + 1024 * dg * dg + ((767 - rMean) * db * db);
    }

    /** Linear interpolation in sRGB space, {@code t} clamped to [0, 1]. */
    public static TextColor lerp(TextColor from, TextColor to, float t) {
        float clamped = Math.max(0f, Math.min(1f, t));
        int a = from.rgb, b = to.rgb;
        int r = Math.round(((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * clamped);
        int g = Math.round(((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * clamped);
        int bl = Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * clamped);
        return rgb((r << 16) | (g << 8) | bl);
    }

    /** JSON form: the legacy name for legacy colors, otherwise {@code #rrggbb}. */
    public String serialize() {
        if (isLegacy()) {
            return LEGACY_NAMES[LEGACY_CODES.indexOf(legacyCode)];
        }
        return asHex();
    }

    public String asHex() {
        return String.format(Locale.ROOT, "#%06x", rgb);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof TextColor && ((TextColor) other).rgb == rgb;
    }

    @Override
    public int hashCode() {
        return rgb;
    }

    @Override
    public String toString() {
        return serialize();
    }
}
