package com.akashic.kunftig.common.text;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

class LegacyRendererTest {

    private static final String S = "\u00a7";

    private static Component runs(Component... children) {
        return Component.of("", Style.EMPTY, Arrays.asList(children));
    }

    @Test
    void plainTextHasNoCodes() {
        assertEquals("Admin", LegacyRenderer.render(Component.text("Admin"), ColorMode.RGB));
    }

    @Test
    void legacyColorsUseShortCodeEvenInRgbMode() {
        assertEquals(S + "cA", LegacyRenderer.render(Component.text("A", Style.of(TextColor.rgb(0xFF5555))), ColorMode.RGB));
    }

    @Test
    void hexColorsUseSectionXInRgbModeAndNearestLegacyOtherwise() {
        Component c = Component.text("A", Style.of(TextColor.rgb(0xFFA500)));
        assertEquals(S + "x" + S + "f" + S + "f" + S + "a" + S + "5" + S + "0" + S + "0A", LegacyRenderer.render(c, ColorMode.RGB));
        assertEquals(S + "6A", LegacyRenderer.render(c, ColorMode.LEGACY));
    }

    @Test
    void decorationsAreReEmittedAfterEveryColorChange() {
        // Correct whether or not the renderer resets decorations on a color code.
        Component c = runs(Component.text("a", Style.of(TextColor.legacy('c'), TextDecoration.BOLD)),
            Component.text("b", Style.of(TextColor.legacy('9'), TextDecoration.BOLD)));
        assertEquals(S + "c" + S + "la" + S + "9" + S + "lb", LegacyRenderer.render(c, ColorMode.RGB));
    }

    @Test
    void removingADecorationWithALegacyColorReusesTheColorCode() {
        Component c = runs(Component.text("a", Style.of(TextColor.legacy('c'), TextDecoration.BOLD)),
            Component.text("b", Style.of(TextColor.legacy('c'))));
        assertEquals(S + "c" + S + "la" + S + "cb", LegacyRenderer.render(c, ColorMode.RGB));
    }

    @Test
    void removingADecorationWithAHexColorGoesThroughReset() {
        TextColor hex = TextColor.rgb(0x123456);
        Component c = runs(Component.text("a", Style.of(hex, TextDecoration.BOLD)), Component.text("b", Style.of(hex)));
        String x = S + "x" + S + "1" + S + "2" + S + "3" + S + "4" + S + "5" + S + "6";
        assertEquals(x + S + "la" + S + "r" + x + "b", LegacyRenderer.render(c, ColorMode.RGB));
    }

    @Test
    void removingADecorationWithoutColorGoesThroughReset() {
        Component c = runs(Component.text("a", Style.of(null, TextDecoration.BOLD)), Component.text("b"));
        assertEquals(S + "la" + S + "rb", LegacyRenderer.render(c, ColorMode.RGB));
    }

    @Test
    void addingADecorationKeepsTheColor() {
        Component c = runs(Component.text("a", Style.of(TextColor.legacy('c'))),
            Component.text("b", Style.of(TextColor.legacy('c'), TextDecoration.ITALIC)));
        assertEquals(S + "ca" + S + "ob", LegacyRenderer.render(c, ColorMode.RGB));
    }

    @Test
    void returningToDefaultColorResets() {
        Component c = runs(Component.text("a", Style.of(TextColor.legacy('c'))), Component.text("b"));
        assertEquals(S + "ca" + S + "rb", LegacyRenderer.render(c, ColorMode.RGB));
    }

    @Test
    void inheritanceIsResolved() {
        Component c = Component.of("[", Style.of(TextColor.legacy('7')),
            Arrays.asList(Component.text("VIP", Style.of(TextColor.legacy('6'), TextDecoration.BOLD)), Component.text("]")));
        assertEquals(S + "7[" + S + "6" + S + "lVIP" + S + "7]", LegacyRenderer.render(c, ColorMode.RGB));
    }

    @Test
    void identicalReducedColorsAreNotRepeated() {
        Component c = runs(Component.text("a", Style.of(TextColor.rgb(0xFF0001))),
            Component.text("b", Style.of(TextColor.rgb(0xFE0000))));
        String legacy = LegacyRenderer.render(c, ColorMode.LEGACY);
        assertEquals(1, legacy.length() - legacy.replace(S, "").length(), legacy);
        assertEquals("ab", LegacyText.strip(legacy));
    }

    @Test
    void literalSectionSignsMakeTheNextRunResetFirst() {
        Component c = runs(Component.text(S + "lraw", Style.of(TextColor.legacy('c'))),
            Component.text("x", Style.of(TextColor.legacy('c'))));
        assertEquals(S + "c" + S + "lraw" + S + "r" + S + "cx", LegacyRenderer.render(c, ColorMode.RGB));
    }

    @Test
    void resetIsOnlyEmittedWhenNeeded() {
        assertEquals("", new LegacyRenderer(ColorMode.RGB).reset().toString());
        assertEquals(S + "r", new LegacyRenderer(ColorMode.RGB).assumeUnknownStyle().reset().toString());
    }

    @Test
    void copyIsIndependentAndKeepsState() {
        LegacyRenderer original = new LegacyRenderer(ColorMode.RGB).appendRun("a", Style.of(TextColor.legacy('c')));
        LegacyRenderer copy = original.copy();
        copy.appendRun("b", Style.of(TextColor.legacy('c')));
        assertEquals(S + "ca", original.toString());
        assertEquals(S + "cab", copy.toString(), "copy knows the color is already active");
    }
}
