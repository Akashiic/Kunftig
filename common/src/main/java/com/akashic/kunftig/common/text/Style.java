package com.akashic.kunftig.common.text;

import java.util.Objects;

/**
 * Immutable component style with Minecraft's inheritance semantics: a {@code null} color and an
 * unspecified decoration inherit from the parent component.
 */
public final class Style {

    public static final Style EMPTY = new Style(null, 0, 0);

    private final TextColor color;
    /** Decorations whose value is specified on this style. */
    private final int specifiedMask;
    /** Values of the specified decorations (bits outside {@link #specifiedMask} are always 0). */
    private final int valueMask;

    private Style(TextColor color, int specifiedMask, int valueMask) {
        this.color = color;
        this.specifiedMask = specifiedMask & TextDecoration.ALL_MASK;
        this.valueMask = valueMask & this.specifiedMask;
    }

    public static Style of(TextColor color, TextDecoration... enabled) {
        int mask = 0;
        for (TextDecoration decoration : enabled) {
            mask |= decoration.bit();
        }
        return new Style(color, mask, mask);
    }

    public TextColor color() {
        return color;
    }

    public Style withColor(TextColor newColor) {
        return new Style(newColor, specifiedMask, valueMask);
    }

    public Style withDecoration(TextDecoration decoration, Boolean value) {
        int bit = decoration.bit();
        if (value == null) {
            return new Style(color, specifiedMask & ~bit, valueMask & ~bit);
        }
        return new Style(color, specifiedMask | bit, value ? valueMask | bit : valueMask & ~bit);
    }

    /** {@code TRUE}/{@code FALSE} when specified, {@code null} when inherited. */
    public Boolean decoration(TextDecoration decoration) {
        int bit = decoration.bit();
        if ((specifiedMask & bit) == 0) {
            return null;
        }
        return (valueMask & bit) != 0;
    }

    /** Resolves this style on top of a fully resolved parent style. */
    public Style inheritFrom(Style parent) {
        TextColor resolvedColor = color != null ? color : parent.color;
        int resolvedValues = (parent.valueMask & ~specifiedMask) | valueMask;
        int resolvedSpecified = parent.specifiedMask | specifiedMask;
        return new Style(resolvedColor, resolvedSpecified, resolvedValues);
    }

    /** Bit mask of decorations that are effectively on (unspecified counts as off). */
    public int enabledMask() {
        return valueMask;
    }

    public boolean isEmpty() {
        return color == null && specifiedMask == 0;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof Style)) {
            return false;
        }
        Style that = (Style) other;
        return Objects.equals(color, that.color) && specifiedMask == that.specifiedMask && valueMask == that.valueMask;
    }

    @Override
    public int hashCode() {
        return Objects.hash(color, specifiedMask, valueMask);
    }

    @Override
    public String toString() {
        return "Style{color=" + color + ", specified=" + specifiedMask + ", values=" + valueMask + '}';
    }
}
