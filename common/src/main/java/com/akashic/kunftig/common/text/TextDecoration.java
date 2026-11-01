package com.akashic.kunftig.common.text;

/** Text decorations, with their JSON key and legacy code. Bit = {@code 1 << ordinal()}. */
public enum TextDecoration {

    OBFUSCATED("obfuscated", 'k'),
    BOLD("bold", 'l'),
    STRIKETHROUGH("strikethrough", 'm'),
    UNDERLINED("underlined", 'n'),
    ITALIC("italic", 'o');

    private static final TextDecoration[] VALUES = values();

    private final String jsonKey;
    private final char legacyCode;

    TextDecoration(String jsonKey, char legacyCode) {
        this.jsonKey = jsonKey;
        this.legacyCode = legacyCode;
    }

    public String jsonKey() {
        return jsonKey;
    }

    public char legacyCode() {
        return legacyCode;
    }

    public int bit() {
        return 1 << ordinal();
    }

    /** Mask containing every decoration. */
    public static final int ALL_MASK = (1 << 5) - 1;

    public static TextDecoration[] all() {
        return VALUES.clone();
    }

    /** The decoration for a legacy code char ({@code k-o}, case-insensitive), or {@code null}. */
    public static TextDecoration byLegacyCode(char code) {
        char lower = Character.toLowerCase(code);
        for (TextDecoration decoration : VALUES) {
            if (decoration.legacyCode == lower) {
                return decoration;
            }
        }
        return null;
    }
}
