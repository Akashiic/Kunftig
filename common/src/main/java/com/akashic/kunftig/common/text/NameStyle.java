package com.akashic.kunftig.common.text;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Style of the player name between a team's prefix and suffix: optional decorations and either no color,
 * one solid color or a gradient of up to {@link #MAX_STOPS} stops spread over the name's code points.
 * <p>
 * Modern Minecraft only allows a 16-color team color here; per-character gradients are a Kunftig extension,
 * the reason it exists at all being long colored names.
 */
public final class NameStyle {

    public static final int MAX_STOPS = 16;
    public static final NameStyle PLAIN = new NameStyle(0, Collections.<TextColor>emptyList());

    private final int decorations;
    private final List<TextColor> colors;

    private NameStyle(int decorations, List<TextColor> colors) {
        this.decorations = decorations & TextDecoration.ALL_MASK;
        this.colors = colors;
    }

    public static NameStyle of(int decorationMask, List<TextColor> colors) {
        if (colors.size() > MAX_STOPS) {
            throw new IllegalArgumentException("At most " + MAX_STOPS + " colors, got " + colors.size());
        }
        for (TextColor color : colors) {
            if (color == null) {
                throw new IllegalArgumentException("null color");
            }
        }
        return new NameStyle(decorationMask, Collections.unmodifiableList(new ArrayList<TextColor>(colors)));
    }

    public static NameStyle solid(TextColor color, TextDecoration... decorations) {
        int mask = 0;
        for (TextDecoration decoration : decorations) {
            mask |= decoration.bit();
        }
        return of(mask, color == null ? Collections.<TextColor>emptyList() : Collections.singletonList(color));
    }

    public static NameStyle gradient(int decorationMask, TextColor... stops) {
        return of(decorationMask, Arrays.asList(stops));
    }

    public int decorationMask() {
        return decorations;
    }

    public List<TextColor> colors() {
        return colors;
    }

    public boolean isGradient() {
        return colors.size() >= 2;
    }

    /** The single color a one-color consumer should use: the first stop, or {@code null}. */
    public TextColor primaryColor() {
        return colors.isEmpty() ? null : colors.get(0);
    }

    /** Style for the whole name when it is not a gradient (or the decorations of a gradient). */
    public Style baseStyle() {
        Style style = Style.EMPTY.withColor(isGradient() ? null : primaryColor());
        for (TextDecoration decoration : TextDecoration.all()) {
            if ((decorations & decoration.bit()) != 0) {
                style = style.withDecoration(decoration, Boolean.TRUE);
            }
        }
        return style;
    }

    /** Applies this style to {@code name}, interpolating gradient stops linearly in sRGB. */
    public Component apply(String name) {
        if (!isGradient() || name.isEmpty()) {
            return Component.text(name, baseStyle());
        }
        int codePoints = name.codePointCount(0, name.length());
        List<Component> runs = new ArrayList<Component>();
        StringBuilder run = new StringBuilder();
        TextColor runColor = null;
        int offset = 0;
        for (int i = 0; i < codePoints; i++) {
            int cp = name.codePointAt(offset);
            offset += Character.charCount(cp);
            TextColor color = colorAt(i, codePoints);
            if (runColor != null && !runColor.equals(color)) {
                runs.add(Component.text(run.toString(), Style.EMPTY.withColor(runColor)));
                run.setLength(0);
            }
            runColor = color;
            run.appendCodePoint(cp);
        }
        runs.add(Component.text(run.toString(), Style.EMPTY.withColor(runColor)));
        return Component.of("", baseStyle(), runs);
    }

    /** Color of code point {@code index} out of {@code count}: first and last stops are exact. */
    TextColor colorAt(int index, int count) {
        if (count <= 1) {
            return colors.get(0);
        }
        float position = (float) index / (count - 1) * (colors.size() - 1);
        int segment = Math.min((int) position, colors.size() - 2);
        return TextColor.lerp(colors.get(segment), colors.get(segment + 1), position - segment);
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof NameStyle)) {
            return false;
        }
        NameStyle that = (NameStyle) other;
        return decorations == that.decorations && colors.equals(that.colors);
    }

    @Override
    public int hashCode() {
        return 31 * decorations + colors.hashCode();
    }

    @Override
    public String toString() {
        return "NameStyle{decorations=" + decorations + ", colors=" + colors + '}';
    }
}
