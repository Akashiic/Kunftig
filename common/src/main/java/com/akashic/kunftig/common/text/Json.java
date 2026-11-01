package com.akashic.kunftig.common.text;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Small strict RFC 8259 JSON reader/writer used for text components.
 * <p>
 * Kept dependency-free on purpose: the code runs against Gson 2.2.4 (Minecraft 1.7.10, Thermos) and
 * Gson 2.10+ (Paper), whose APIs differ, and it parses data received from the network, so nesting depth
 * is bounded. Values map to {@link Map} (insertion ordered), {@link List}, {@link String},
 * {@link Boolean}, {@link Double} and {@link #NULL}.
 */
public final class Json {

    /** Marker for JSON {@code null}. */
    public static final Object NULL = new Object() {

        @Override
        public String toString() {
            return "null";
        }
    };

    private Json() {}

    public static Object parse(String input, int maxDepth) throws JsonException {
        Parser parser = new Parser(input, maxDepth);
        parser.skipWhitespace();
        Object value = parser.readValue(0);
        parser.skipWhitespace();
        if (parser.pos != input.length()) {
            throw parser.error("Unexpected trailing content");
        }
        return value;
    }

    /** Thrown for malformed or over-limit JSON. */
    public static final class JsonException extends Exception {

        private static final long serialVersionUID = 1L;

        JsonException(String message) {
            super(message);
        }
    }

    private static final class Parser {

        private final String in;
        private final int maxDepth;
        private int pos;

        Parser(String in, int maxDepth) {
            this.in = in;
            this.maxDepth = maxDepth;
        }

        JsonException error(String message) {
            return new JsonException(message + " at offset " + pos);
        }

        void skipWhitespace() {
            while (pos < in.length()) {
                char c = in.charAt(pos);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                    pos++;
                } else {
                    break;
                }
            }
        }

        Object readValue(int depth) throws JsonException {
            if (pos >= in.length()) {
                throw error("Unexpected end of input");
            }
            char c = in.charAt(pos);
            switch (c) {
                case '{':
                    return readObject(depth + 1);
                case '[':
                    return readArray(depth + 1);
                case '"':
                    return readString();
                case 't':
                    expectLiteral("true");
                    return Boolean.TRUE;
                case 'f':
                    expectLiteral("false");
                    return Boolean.FALSE;
                case 'n':
                    expectLiteral("null");
                    return NULL;
                default:
                    if (c == '-' || (c >= '0' && c <= '9')) {
                        return readNumber();
                    }
                    throw error("Unexpected character '" + c + "'");
            }
        }

        void expectLiteral(String literal) throws JsonException {
            if (!in.startsWith(literal, pos)) {
                throw error("Expected " + literal);
            }
            pos += literal.length();
        }

        Map<String, Object> readObject(int depth) throws JsonException {
            if (depth > maxDepth) {
                throw error("Nesting deeper than " + maxDepth);
            }
            pos++; // '{'
            Map<String, Object> map = new LinkedHashMap<String, Object>();
            skipWhitespace();
            if (pos < in.length() && in.charAt(pos) == '}') {
                pos++;
                return map;
            }
            while (true) {
                skipWhitespace();
                if (pos >= in.length() || in.charAt(pos) != '"') {
                    throw error("Expected object key");
                }
                String key = readString();
                skipWhitespace();
                if (pos >= in.length() || in.charAt(pos) != ':') {
                    throw error("Expected ':'");
                }
                pos++;
                skipWhitespace();
                map.put(key, readValue(depth));
                skipWhitespace();
                if (pos >= in.length()) {
                    throw error("Unterminated object");
                }
                char c = in.charAt(pos++);
                if (c == '}') {
                    return map;
                }
                if (c != ',') {
                    throw error("Expected ',' or '}'");
                }
            }
        }

        List<Object> readArray(int depth) throws JsonException {
            if (depth > maxDepth) {
                throw error("Nesting deeper than " + maxDepth);
            }
            pos++; // '['
            List<Object> list = new ArrayList<Object>();
            skipWhitespace();
            if (pos < in.length() && in.charAt(pos) == ']') {
                pos++;
                return list;
            }
            while (true) {
                skipWhitespace();
                list.add(readValue(depth));
                skipWhitespace();
                if (pos >= in.length()) {
                    throw error("Unterminated array");
                }
                char c = in.charAt(pos++);
                if (c == ']') {
                    return list;
                }
                if (c != ',') {
                    throw error("Expected ',' or ']'");
                }
            }
        }

        String readString() throws JsonException {
            pos++; // opening quote
            StringBuilder out = null;
            int start = pos;
            while (pos < in.length()) {
                char c = in.charAt(pos);
                if (c == '"') {
                    String result = out == null ? in.substring(start, pos) : out.append(in, start, pos).toString();
                    pos++;
                    return result;
                }
                if (c < 0x20) {
                    throw error("Unescaped control character in string");
                }
                if (c == '\\') {
                    if (out == null) {
                        out = new StringBuilder();
                    }
                    out.append(in, start, pos);
                    pos++;
                    if (pos >= in.length()) {
                        break;
                    }
                    char escape = in.charAt(pos++);
                    switch (escape) {
                        case '"':
                        case '\\':
                        case '/':
                            out.append(escape);
                            break;
                        case 'b':
                            out.append('\b');
                            break;
                        case 'f':
                            out.append('\f');
                            break;
                        case 'n':
                            out.append('\n');
                            break;
                        case 'r':
                            out.append('\r');
                            break;
                        case 't':
                            out.append('\t');
                            break;
                        case 'u':
                            if (pos + 4 > in.length()) {
                                throw error("Truncated unicode escape");
                            }
                            int code = 0;
                            for (int i = 0; i < 4; i++) {
                                int digit = Character.digit(in.charAt(pos + i), 16);
                                if (digit < 0) {
                                    throw error("Invalid unicode escape");
                                }
                                code = (code << 4) | digit;
                            }
                            pos += 4;
                            out.append((char) code);
                            break;
                        default:
                            throw error("Invalid escape '\\" + escape + "'");
                    }
                    start = pos;
                    continue;
                }
                pos++;
            }
            throw error("Unterminated string");
        }

        Double readNumber() throws JsonException {
            int start = pos;
            if (in.charAt(pos) == '-') {
                pos++;
            }
            if (pos >= in.length()) {
                throw error("Truncated number");
            }
            if (in.charAt(pos) == '0') {
                pos++;
            } else if (in.charAt(pos) >= '1' && in.charAt(pos) <= '9') {
                skipDigits();
            } else {
                throw error("Invalid number");
            }
            if (pos < in.length() && in.charAt(pos) == '.') {
                pos++;
                requireDigit();
                skipDigits();
            }
            if (pos < in.length() && (in.charAt(pos) == 'e' || in.charAt(pos) == 'E')) {
                pos++;
                if (pos < in.length() && (in.charAt(pos) == '+' || in.charAt(pos) == '-')) {
                    pos++;
                }
                requireDigit();
                skipDigits();
            }
            return Double.valueOf(in.substring(start, pos));
        }

        void requireDigit() throws JsonException {
            if (pos >= in.length() || in.charAt(pos) < '0' || in.charAt(pos) > '9') {
                throw error("Expected digit");
            }
        }

        void skipDigits() {
            while (pos < in.length() && in.charAt(pos) >= '0' && in.charAt(pos) <= '9') {
                pos++;
            }
        }
    }

    /** Appends {@code value} as a quoted, escaped JSON string. */
    public static void writeString(StringBuilder out, String value) {
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"':
                    out.append("\\\"");
                    break;
                case '\\':
                    out.append("\\\\");
                    break;
                case '\n':
                    out.append("\\n");
                    break;
                case '\r':
                    out.append("\\r");
                    break;
                case '\t':
                    out.append("\\t");
                    break;
                default:
                    if (c < 0x20 || c == ' ' || c == ' ') {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
            }
        }
        out.append('"');
    }
}
