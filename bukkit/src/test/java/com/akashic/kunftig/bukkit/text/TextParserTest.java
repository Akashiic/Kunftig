package com.akashic.kunftig.bukkit.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.akashic.kunftig.common.text.ColorMode;
import com.akashic.kunftig.common.text.Component;
import com.akashic.kunftig.common.text.LegacyRenderer;
import com.akashic.kunftig.common.text.TextColor;

class TextParserTest {

    private static final String S = "\u00a7";

    private static String legacy(String input) throws TextParseException {
        return LegacyRenderer.render(TextParser.parse(input), ColorMode.RGB);
    }

    @Test
    void legacyCodesConvertToMiniMessage() {
        assertEquals("<reset><red><bold>Admin", LegacyCodes.toMiniMessage("&c&lAdmin"));
        assertEquals("<reset><#ff8800>x", LegacyCodes.toMiniMessage("&#FF8800x"));
        assertEquals("<reset><#ff8800>x", LegacyCodes.toMiniMessage("&x&f&f&8&8&0&0x"));
        assertEquals("<reset><#ff8800>x", LegacyCodes.toMiniMessage(S + "x" + S + "f" + S + "f" + S + "8" + S + "8"
            + S + "0" + S + "0x"));
        assertEquals("a & b &z &", LegacyCodes.toMiniMessage("a & b &z &"));
        assertEquals("&c literal", LegacyCodes.toMiniMessage("\\&c literal"));
        assertEquals("<reset>x", LegacyCodes.toMiniMessage("&rx"));
    }

    @Test
    void legacyColorEndsDecorationsLikeVanilla() throws TextParseException {
        // In legacy text a color after bold cancels the bold.
        assertEquals(S + "cText", legacy("&l&cText"));
        assertEquals(S + "c" + S + "lText", legacy("&c&lText"));
    }

    @Test
    void miniMessageGradientBecomesPerCharacterColors() throws TextParseException {
        Component c = TextParser.parse("<gradient:#ff0000:#0000ff>Akashiic</gradient>");
        assertEquals("Akashiic", c.plainText());
        List<TextColor> colors = new ArrayList<TextColor>();
        c.forEachRun((text, style) -> colors.add(style.color()));
        assertEquals(8, colors.size());
        assertEquals(TextColor.rgb(0xff0000), colors.get(0));
        assertEquals(TextColor.rgb(0x0000ff), colors.get(7));
    }

    @Test
    void mixedLegacyAndMiniMessage() throws TextParseException {
        assertEquals(S + "8[" + S + "6VIP" + S + "8] " + S + "fName", legacy("&8[<gold>VIP</gold>&8] <white>Name"));
    }

    @Test
    void jsonInputIsAccepted() throws TextParseException {
        assertEquals(S + "a[VIP]", legacy("{\"text\":\"[VIP]\",\"color\":\"green\"}"));
        assertThrows(TextParseException.class, () -> TextParser.parse("{\"text\":"));
    }

    @Test
    void bracketedPrefixesAreTextNotJson() throws TextParseException {
        assertEquals("[VIP] ", TextParser.parse("[VIP] ").plainText());
        assertEquals(" [AFK]", TextParser.parse(" [AFK]").plainText());
        assertEquals("[1]", TextParser.parse("[1]").plainText());
        assertEquals(S + "c[A]", legacy("&c[A]"));
        assertEquals("AB", TextParser.parse("[\"A\", \"B\"]").plainText());
        assertEquals("AB", TextParser.parse(" [ {\"text\":\"A\"}, \"B\"]").plainText());
    }

    @Test
    void emptyInputIsEmpty() throws TextParseException {
        assertTrue(TextParser.parse("").plainText().isEmpty());
        assertTrue(TextParser.parse(null).plainText().isEmpty());
    }
}
