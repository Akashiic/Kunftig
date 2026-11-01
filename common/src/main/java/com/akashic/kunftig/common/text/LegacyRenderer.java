package com.akashic.kunftig.common.text;

/**
 * Renders components into legacy {@code §}-formatted strings for the 1.7.10 font renderer.
 * <p>
 * The output is correct whether or not the target renderer resets decorations on a color code (vanilla does;
 * in Angelica it is configurable for {@code §x}): after any color code every active decoration is re-emitted,
 * and a decoration is turned off either by a short legacy color code (which ends decorations everywhere) or by
 * {@code §r}. Codes are only emitted when the effective style
 * changes, so long gradients stay as short as the format allows.
 * <p>
 * Literal {@code §} characters inside component text are kept (modern clients apply them too); afterwards the
 * renderer no longer knows the active style and re-emits it in full.
 */
public final class LegacyRenderer {

    private final ColorMode mode;
    private final StringBuilder out = new StringBuilder();
    /** Emitted color token, {@code null} for the renderer's default color. */
    private String color;
    private int decorations;
    private boolean unknown;

    /** A renderer that assumes the default style at the start of its output. */
    public LegacyRenderer(ColorMode mode) {
        this.mode = mode;
    }

    /** An independent renderer with the same output and style state, e.g. to try an append without committing. */
    public LegacyRenderer copy() {
        LegacyRenderer copy = new LegacyRenderer(mode);
        copy.out.append(out);
        copy.color = color;
        copy.decorations = decorations;
        copy.unknown = unknown;
        return copy;
    }

    public static String render(Component component, ColorMode mode) {
        return new LegacyRenderer(mode).append(component).toString();
    }

    /** Marks the current style as unknown, e.g. after concatenating text produced elsewhere. */
    public LegacyRenderer assumeUnknownStyle() {
        unknown = true;
        return this;
    }

    public LegacyRenderer append(Component component) {
        component.forEachRun(this::appendRun);
        return this;
    }

    /** Emits {@code §r} unless the style is already known to be the default. */
    public LegacyRenderer reset() {
        if (unknown || color != null || decorations != 0) {
            out.append(LegacyText.SECTION).append('r');
            color = null;
            decorations = 0;
            unknown = false;
        }
        return this;
    }

    /** Emits the codes for {@code style} without text, e.g. to color text appended later by someone else. */
    public LegacyRenderer applyStyle(Style style) {
        transition(style);
        return this;
    }

    public LegacyRenderer appendRun(String text, Style style) {
        if (text.isEmpty()) {
            return this;
        }
        transition(style);
        out.append(text);
        if (text.indexOf(LegacyText.SECTION) >= 0) {
            unknown = true;
        }
        return this;
    }

    private void transition(Style style) {
        String targetColor = token(style.color());
        int targetDecorations = style.enabledMask();
        boolean decorationRemoved = (decorations & ~targetDecorations) != 0;
        // A short legacy color code ends every decoration on every renderer (vanilla, and Angelica's COLOR code);
        // only the default color and §x (configurable in Angelica) need an explicit §r to turn decorations off.
        boolean shortColor = targetColor != null && targetColor.length() == 2;
        if (unknown || (targetColor == null && (color != null || decorationRemoved))
            || (decorationRemoved && !shortColor)) {
            out.append(LegacyText.SECTION).append('r');
            color = null;
            decorations = 0;
            unknown = false;
            decorationRemoved = false;
        }
        if (targetColor != null && (!targetColor.equals(color) || decorationRemoved)) {
            out.append(targetColor);
            color = targetColor;
            appendDecorations(targetDecorations);
        } else {
            appendDecorations(targetDecorations & ~decorations);
        }
        decorations = targetDecorations;
    }

    private void appendDecorations(int mask) {
        for (TextDecoration decoration : TextDecoration.all()) {
            if ((mask & decoration.bit()) != 0) {
                out.append(LegacyText.SECTION).append(decoration.legacyCode());
            }
        }
    }

    private String token(TextColor textColor) {
        if (textColor == null) {
            return null;
        }
        if (textColor.isLegacy()) {
            return new String(new char[] { LegacyText.SECTION, textColor.legacyCode() });
        }
        if (mode == ColorMode.LEGACY) {
            return new String(new char[] { LegacyText.SECTION, textColor.nearestLegacy().legacyCode() });
        }
        String hex = String.format("%06x", textColor.value());
        StringBuilder token = new StringBuilder(LegacyText.HEX_SEQUENCE_LENGTH).append(LegacyText.SECTION).append('x');
        for (int i = 0; i < 6; i++) {
            token.append(LegacyText.SECTION).append(hex.charAt(i));
        }
        return token.toString();
    }

    public int length() {
        return out.length();
    }

    @Override
    public String toString() {
        return out.toString();
    }
}
