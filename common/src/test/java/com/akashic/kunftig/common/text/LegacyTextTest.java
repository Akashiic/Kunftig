package com.akashic.kunftig.common.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LegacyTextTest {

    private static final String S = "\u00a7";
    private static final String HEX = S + "x" + S + "1" + S + "2" + S + "3" + S + "4" + S + "5" + S + "6";

    @Test
    void shortStringsAreKeptButTrailingCodesDropped() {
        assertEquals(S + "cAdmin", LegacyText.truncate(S + "cAdmin", 16));
        assertEquals(S + "cAdmin", LegacyText.truncate(S + "cAdmin" + S + "l", 16));
        assertEquals("abc", LegacyText.truncate("abc" + S, 16));
    }

    @Test
    void neverSplitsAColorPair() {
        assertEquals("abc", LegacyText.truncate("abc" + S + "cd", 4));
        assertEquals("abc", LegacyText.truncate("abc" + S + "cd", 5));
        assertEquals("abc" + S + "cd", LegacyText.truncate("abc" + S + "cd", 6));
    }

    @Test
    void neverSplitsAHexSequence() {
        String s = "a" + HEX + "b";
        for (int max = 1; max < s.length(); max++) {
            String cut = LegacyText.truncate(s, max);
            assertTrue(cut.equals("a") || cut.equals(s), max + " -> " + cut);
        }
        assertEquals(s, LegacyText.truncate(s, s.length()));
    }

    @Test
    void neverSplitsASurrogatePair() {
        String s = "ab\uD83D\uDE00";
        assertEquals("ab", LegacyText.truncate(s, 3));
        assertEquals(s, LegacyText.truncate(s, 4));
    }

    @Test
    void stripRemovesAllCodes() {
        assertEquals("ab", LegacyText.strip(S + "ca" + HEX + "b" + S + "r"));
    }

    @Test
    void recognisesOnlyCompleteHexSequences() {
        assertTrue(LegacyText.isHexSequence(HEX, 0));
        assertTrue(!LegacyText.isHexSequence(HEX.substring(0, 13), 0));
        assertTrue(!LegacyText.isHexSequence(S + "x" + S + "g" + S + "2" + S + "3" + S + "4" + S + "5" + S + "6", 0));
    }
}
