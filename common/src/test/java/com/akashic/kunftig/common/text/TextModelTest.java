package com.akashic.kunftig.common.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

class TextModelTest {

    @Test
    void colorsParseAndSerialize() {
        assertEquals(0x00AAAA, TextColor.parse("dark_aqua").value());
        assertEquals(0xABCDEF, TextColor.parse("#AbCdEf").value());
        assertNull(TextColor.parse("#abc"));
        assertNull(TextColor.parse(null));
        assertSame(TextColor.legacy('c'), TextColor.rgb(0xFF5555));
        assertEquals("red", TextColor.rgb(0xFF5555).serialize());
        assertEquals("#123456", TextColor.rgb(0x123456).serialize());
    }

    @Test
    void nearestLegacyIsStable() {
        for (char code : "0123456789abcdef".toCharArray()) {
            TextColor legacy = TextColor.legacy(code);
            assertSame(legacy, legacy.nearestLegacy());
        }
        assertEquals('f', TextColor.rgb(0xFEFEFE).nearestLegacy().legacyCode());
        assertEquals('0', TextColor.rgb(0x101010).nearestLegacy().legacyCode());
        assertEquals('6', TextColor.rgb(0xFFA500).nearestLegacy().legacyCode());
    }

    @Test
    void lerpHitsEndpoints() {
        TextColor a = TextColor.rgb(0x102030), b = TextColor.rgb(0xF0E0D0);
        assertEquals(a, TextColor.lerp(a, b, 0f));
        assertEquals(b, TextColor.lerp(a, b, 1f));
        assertEquals(b, TextColor.lerp(a, b, 7f));
        int mid = TextColor.lerp(TextColor.rgb(0), TextColor.rgb(0xFFFFFF), 0.5f).value();
        for (int shift = 0; shift <= 16; shift += 8) {
            int channel = (mid >> shift) & 0xFF;
            assertTrue(channel == 127 || channel == 128, Integer.toHexString(mid));
        }
    }

    @Test
    void styleInheritance() {
        Style parent = Style.of(TextColor.legacy('c'), TextDecoration.BOLD);
        Style child = Style.EMPTY.withDecoration(TextDecoration.BOLD, Boolean.FALSE)
            .withDecoration(TextDecoration.ITALIC, Boolean.TRUE);
        Style resolved = child.inheritFrom(parent);
        assertEquals(TextColor.legacy('c'), resolved.color());
        assertEquals(Boolean.FALSE, resolved.decoration(TextDecoration.BOLD));
        assertEquals(TextDecoration.ITALIC.bit(), resolved.enabledMask());
        assertTrue(Style.EMPTY.isEmpty());
        assertFalse(parent.isEmpty());
    }

    @Test
    void solidNameStyle() {
        NameStyle style = NameStyle.solid(TextColor.legacy('b'), TextDecoration.BOLD);
        assertEquals("\u00a7b\u00a7lNick", LegacyRenderer.render(style.apply("Nick"), ColorMode.RGB));
        assertFalse(style.isGradient());
        assertEquals("Nick", LegacyRenderer.render(NameStyle.PLAIN.apply("Nick"), ColorMode.RGB));
    }

    @Test
    void gradientEndpointsAreExactAndRunsMerge() {
        TextColor red = TextColor.rgb(0xFF0000), blue = TextColor.rgb(0x0000FF);
        NameStyle gradient = NameStyle.gradient(0, red, blue);
        Component name = gradient.apply("Akashic");
        assertEquals("Akashic", name.plainText());
        List<TextColor> colors = new ArrayList<TextColor>();
        name.forEachRun((text, style) -> colors.add(style.color()));
        assertEquals(red, colors.get(0));
        assertEquals(blue, colors.get(colors.size() - 1));

        NameStyle flat = NameStyle.gradient(0, red, red);
        assertEquals(1, flat.apply("Akashic").children().size(), "equal colors merge into one run");
    }

    @Test
    void gradientHandlesEdgeCases() {
        NameStyle gradient = NameStyle.gradient(TextDecoration.ITALIC.bit(), TextColor.rgb(0xFF0000),
            TextColor.rgb(0x00FF00), TextColor.rgb(0x0000FF));
        assertEquals("", gradient.apply("").plainText());
        assertEquals(TextColor.rgb(0xFF0000), gradient.colorAt(0, 1));
        assertEquals(TextColor.rgb(0x00FF00), gradient.colorAt(2, 5), "middle stop is exact");
        String emoji = "a\uD83D\uDE00b";
        assertEquals(emoji, gradient.apply(emoji).plainText(), "surrogate pairs stay intact");
        assertThrows(IllegalArgumentException.class,
            () -> NameStyle.of(0, Collections.nCopies(NameStyle.MAX_STOPS + 1, TextColor.rgb(0))));
    }
}
