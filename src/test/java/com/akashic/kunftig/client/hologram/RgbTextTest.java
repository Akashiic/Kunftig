package com.akashic.kunftig.client.hologram;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class RgbTextTest {

    private static String hex(String rrggbb) {
        StringBuilder b = new StringBuilder("§x");
        for (char c : rrggbb.toCharArray()) {
            b.append('§')
                .append(c);
        }
        return b.toString();
    }

    @Test
    void plainLinesAreOneRun() {
        List<RgbText.Run> runs = RgbText.runs("§aBuff §lde Sorte");
        assertEquals(1, runs.size());
        assertEquals(-1, runs.get(0).rgb);
        assertFalse(RgbText.hasRgb("§aBuff"));
    }

    @Test
    void hexColorsCutTheLine() {
        String line = hex("ff8800") + "§lB" + hex("00ff88") + "u" + "§f!";
        assertTrue(RgbText.hasRgb(line));
        List<RgbText.Run> runs = RgbText.runs(line);
        assertEquals(3, runs.size());
        assertEquals(0xff8800, runs.get(0).rgb);
        assertEquals("§lB", runs.get(0).text);
        assertEquals(0x00ff88, runs.get(1).rgb);
        assertEquals("u", runs.get(1).text);
        assertEquals(-1, runs.get(2).rgb);
        assertEquals("§f!", runs.get(2).text);
        assertEquals("§lBu§f!", RgbText.strip(line));
    }

    @Test
    void aBrokenHexCodeIsText() {
        assertEquals(-1, RgbText.hexAt("§x§f§z", 0));
        assertEquals("§xabc", RgbText.strip("§xabc"));
    }
}
