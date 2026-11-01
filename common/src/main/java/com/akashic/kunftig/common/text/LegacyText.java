package com.akashic.kunftig.common.text;

/**
 * Utilities for legacy {@code §}-formatted strings that never split a formatting code: a {@code §c} pair and a
 * complete {@code §x§r§r§g§g§b§b} sequence are treated as indivisible atoms, as is a UTF-16 surrogate pair.
 */
public final class LegacyText {

    public static final char SECTION = '\u00a7';
    /** Length of a {@code §x§r§r§g§g§b§b} sequence. */
    public static final int HEX_SEQUENCE_LENGTH = 14;

    private LegacyText() {}

    /** Length of the atom starting at {@code index} (1, 2 or 14). */
    static int atomLength(CharSequence s, int index) {
        char c = s.charAt(index);
        if (c == SECTION) {
            if (index + 1 >= s.length()) {
                return 1;
            }
            if (isHexSequence(s, index)) {
                return HEX_SEQUENCE_LENGTH;
            }
            return 2;
        }
        if (Character.isHighSurrogate(c) && index + 1 < s.length() && Character.isLowSurrogate(s.charAt(index + 1))) {
            return 2;
        }
        return 1;
    }

    static boolean isFormattingAtom(CharSequence s, int index) {
        return s.charAt(index) == SECTION;
    }

    /** Whether a complete {@code §x§h§h§h§h§h§h} sequence starts at {@code index}. */
    public static boolean isHexSequence(CharSequence s, int index) {
        if (index + HEX_SEQUENCE_LENGTH > s.length() || s.charAt(index) != SECTION) {
            return false;
        }
        char x = s.charAt(index + 1);
        if (x != 'x' && x != 'X') {
            return false;
        }
        for (int i = 0; i < 6; i++) {
            int p = index + 2 + i * 2;
            if (s.charAt(p) != SECTION || Character.digit(s.charAt(p + 1), 16) < 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * Cuts {@code s} to at most {@code maxChars} UTF-16 chars at an atom boundary, then drops formatting codes
     * left dangling at the end (they would color nothing). A lone trailing {@code §} is dropped.
     */
    public static String truncate(String s, int maxChars) {
        if (maxChars <= 0) {
            return "";
        }
        int end = 0;
        int lastVisibleEnd = 0;
        while (end < s.length()) {
            int length = atomLength(s, end);
            if (end + length > maxChars) {
                break;
            }
            boolean formatting = isFormattingAtom(s, end);
            if (formatting && length == 1) {
                break; // lone trailing '\u00a7'
            }
            end += length;
            if (!formatting) {
                lastVisibleEnd = end;
            }
        }
        return s.substring(0, lastVisibleEnd);
    }

    /** Removes every formatting code. */
    public static String strip(String s) {
        StringBuilder out = new StringBuilder(s.length());
        int i = 0;
        while (i < s.length()) {
            int length = atomLength(s, i);
            if (!isFormattingAtom(s, i)) {
                out.append(s, i, i + length);
            }
            i += length;
        }
        return out.toString();
    }
}
