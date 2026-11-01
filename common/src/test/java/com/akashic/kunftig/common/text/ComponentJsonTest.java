package com.akashic.kunftig.common.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

class ComponentJsonTest {

    private static String legacy(String json) throws Json.JsonException {
        return LegacyRenderer.render(ComponentJson.parse(json), ColorMode.RGB);
    }

    @Test
    void plainStringIsLiteralText() throws Json.JsonException {
        assertEquals(Component.text("hi"), ComponentJson.parse("\"hi\""));
    }

    @Test
    void arrayElementsInheritFromTheFirst() throws Json.JsonException {
        // Vanilla semantics: [a, b] == a with b appended, so b inherits a's red color.
        assertEquals("\u00a7cAB", legacy("[{\"text\":\"A\",\"color\":\"red\"},\"B\"]"));
    }

    @Test
    void parsesStyleAndExtra() throws Json.JsonException {
        Component c = ComponentJson.parse(
            "{\"text\":\"[\",\"color\":\"#123456\",\"bold\":true,\"extra\":[{\"text\":\"x\",\"bold\":false}],"
                + "\"hoverEvent\":{\"action\":\"show_text\",\"value\":\"ignored\"}}");
        assertEquals("[", c.text());
        assertEquals(TextColor.rgb(0x123456), c.style().color());
        assertEquals(Boolean.TRUE, c.style().decoration(TextDecoration.BOLD));
        assertEquals(Boolean.FALSE, c.children().get(0).style().decoration(TextDecoration.BOLD));
    }

    @Test
    void invalidColorsAreIgnoredLikeVanilla() throws Json.JsonException {
        assertNull(ComponentJson.parse("{\"text\":\"a\",\"color\":\"#12345g\"}").style().color());
        assertNull(ComponentJson.parse("{\"text\":\"a\",\"color\":\"not_a_color\"}").style().color());
    }

    @Test
    void nonLiteralContentFallsBack() throws Json.JsonException {
        assertEquals("Fallback", ComponentJson.parse("{\"translate\":\"x.y\",\"fallback\":\"Fallback\"}").plainText());
        assertEquals("x.y", ComponentJson.parse("{\"translate\":\"x.y\"}").plainText());
        assertEquals("key.jump", ComponentJson.parse("{\"keybind\":\"key.jump\"}").plainText());
        assertEquals("", ComponentJson.parse("{\"score\":{\"name\":\"a\",\"objective\":\"b\"}}").plainText());
        assertEquals("12", ComponentJson.parse("12").plainText());
    }

    @Test
    void rejectsStructuralErrorsAndOversizedTrees() {
        assertThrows(Json.JsonException.class, () -> ComponentJson.parse("[]"));
        assertThrows(Json.JsonException.class, () -> ComponentJson.parse("{\"text\":\"a\",\"extra\":\"b\"}"));
        StringBuilder many = new StringBuilder("[\"\"");
        for (int i = 0; i < ComponentJson.MAX_NODES + 1; i++) {
            many.append(",\"x\"");
        }
        assertThrows(Json.JsonException.class, () -> ComponentJson.parse(many.append(']').toString()));
    }

    @Test
    void writeThenParseIsIdentity() throws Json.JsonException {
        Component original = Component.of("[", Style.of(TextColor.rgb(0xff8800), TextDecoration.BOLD), Arrays.asList(
            Component.text("Ad\"min", Style.EMPTY.withDecoration(TextDecoration.ITALIC, Boolean.FALSE)),
            Component.text("]", Style.of(TextColor.legacy('7')))));
        assertEquals(original, ComponentJson.parse(ComponentJson.write(original)));
    }
}
