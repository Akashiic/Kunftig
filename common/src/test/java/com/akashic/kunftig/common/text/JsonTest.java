package com.akashic.kunftig.common.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class JsonTest {

    @Test
    void parsesAllValueTypes() throws Json.JsonException {
        Object value = Json.parse(" {\"a\": [1, -2.5e3, true, false, null, \"s\"], \"b\": {}} ", 8);
        Map<String, Object> expected = new LinkedHashMap<String, Object>();
        expected.put("a", Arrays.asList(1.0, -2500.0, true, false, Json.NULL, "s"));
        expected.put("b", new LinkedHashMap<String, Object>());
        assertEquals(expected, value);
        assertSame(Json.NULL, Json.parse("null", 1));
    }

    @Test
    void decodesEscapes() throws Json.JsonException {
        assertEquals("\"\\/\b\f\n\r\t\u00e9\uD83D\uDE00", Json.parse("\"\\\"\\\\\\/\\b\\f\\n\\r\\t\\u00e9\\ud83d\\ude00\"", 1));
    }

    @Test
    void rejectsMalformedInput() {
        String[] bad = { "", "{", "[1,]", "{\"a\":1,}", "{a:1}", "\"unterminated", "\"\\x\"", "\"\\u12\"", "01", "1.",
            "-", "1e", "tru", "[1] 2", "\"tab\there\"", "{\"a\" 1}" };
        for (String input : bad) {
            assertThrows(Json.JsonException.class, () -> Json.parse(input, 8), input);
        }
    }

    @Test
    void enforcesDepthLimit() throws Json.JsonException {
        Json.parse("[[[[1]]]]", 4);
        assertThrows(Json.JsonException.class, () -> Json.parse("[[[[[1]]]]]", 4));
        StringBuilder deep = new StringBuilder();
        for (int i = 0; i < 100000; i++) {
            deep.append('[');
        }
        assertThrows(Json.JsonException.class, () -> Json.parse(deep.toString(), 64), "no stack overflow");
    }

    @Test
    void writesEscapedStringsThatParseBack() throws Json.JsonException {
        String original = "quote\" backslash\\ newline\n control\u0001 sep\u2028 \u00a7c";
        StringBuilder out = new StringBuilder();
        Json.writeString(out, original);
        assertEquals(original, Json.parse(out.toString(), 1));
    }
}
