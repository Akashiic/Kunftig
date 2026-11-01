package com.akashic.kunftig.client.nametag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.akashic.kunftig.common.protocol.NametagVisibility;
import com.akashic.kunftig.common.text.ColorMode;
import com.akashic.kunftig.common.text.Component;
import com.akashic.kunftig.common.text.ComponentJson;
import com.akashic.kunftig.common.text.Json;
import com.akashic.kunftig.common.text.LegacyText;
import com.akashic.kunftig.common.text.NameStyle;
import com.akashic.kunftig.common.text.Style;
import com.akashic.kunftig.common.text.TextColor;
import com.akashic.kunftig.common.text.TextDecoration;

class TeamStyleStateTest {

    private static final String S = "\u00a7";

    /** What vanilla formatString does with our getters, plus our formatString override when it applies. */
    private static String format(TeamStyleState state, String vanillaPrefix, String name, String vanillaSuffix,
        ColorMode mode) {
        String custom = state.format(vanillaPrefix, name, vanillaSuffix, mode);
        return custom != null ? custom : state.prefix(vanillaPrefix, mode) + name + state.suffix(vanillaSuffix, mode);
    }

    private static Component json(String json) {
        try {
            return ComponentJson.parse(json);
        } catch (Json.JsonException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void longPrefixReplacesTheTruncatedVanillaOne() {
        TeamStyleState state = new TeamStyleState(
            json("{\"text\":\"[Administrador Supremo] \",\"color\":\"#ff8800\"}"),
            null,
            null,
            null);
        String prefix = state.prefix(S + "6[Administrad", ColorMode.RGB);
        assertEquals("[Administrador Supremo] ", LegacyText.strip(prefix));
        assertTrue(prefix.length() > 16);
        assertEquals(S + "6[Administrador Supremo] ", state.prefix("", ColorMode.LEGACY));
    }

    @Test
    void withoutNameStyleThePrefixColorCarriesIntoTheName() {
        TeamStyleState state = new TeamStyleState(json("{\"text\":\"[VIP] \",\"color\":\"green\"}"), null, null, null);
        assertEquals(S + "a[VIP] Steve", format(state, "", "Steve", "", ColorMode.RGB));
    }

    @Test
    void absentFieldsFallBackToVanilla() {
        TeamStyleState state = new TeamStyleState(null, json("\" *\""), null, null);
        assertEquals(S + "c[A] ", state.prefix(S + "c[A] ", ColorMode.RGB));
        assertEquals(" *", state.suffix(S + "7 old", ColorMode.RGB));
    }

    @Test
    void solidNameStyleResetsAfterThePrefixAndBeforeTheSuffix() {
        TeamStyleState state = new TeamStyleState(
            json("{\"text\":\"[Mod] \",\"color\":\"gold\",\"bold\":true}"),
            json("\" ★\""),
            NameStyle.solid(TextColor.legacy('b')),
            null);
        // The name and the suffix each start from §r: a server prefix may hold any code (e.g. Angelica's rainbow
        // §q) that a color code is not guaranteed to end, and the override has no length limit to save.
        assertEquals(
            S + "6" + S + "l[Mod] " + S + "r" + S + "bSteve" + S + "r ★",
            format(state, "", "Steve", "", ColorMode.RGB));
    }

    @Test
    void plainNameStyleStillStopsTheCarryOver() {
        TeamStyleState state = new TeamStyleState(
            json("{\"text\":\"[VIP] \",\"color\":\"green\"}"),
            null,
            NameStyle.PLAIN,
            null);
        assertEquals(S + "a[VIP] " + S + "rSteve", format(state, "", "Steve", "", ColorMode.RGB));
        TeamStyleState noPrefix = new TeamStyleState(null, null, NameStyle.PLAIN, null);
        assertEquals("Steve", format(noPrefix, "", "Steve", "", ColorMode.RGB), "nothing to reset at the start");
    }

    @Test
    void vanillaSuffixDoesNotInheritAStyledName() {
        TeamStyleState state = new TeamStyleState(null, null, NameStyle.solid(TextColor.legacy('d')), null);
        assertEquals(S + "dSteve" + S + "r" + S + "7 [AFK]", format(state, "", "Steve", S + "7 [AFK]", ColorMode.RGB));
    }

    @Test
    void gradientNameIsRenderedPerCharacter() {
        NameStyle gradient = NameStyle
            .gradient(TextDecoration.BOLD.bit(), TextColor.rgb(0xFF0000), TextColor.rgb(0x0000FF));
        TeamStyleState state = new TeamStyleState(json("\"[Dono] \""), null, gradient, null);
        String rgb = format(state, "", "Akashiic", "", ColorMode.RGB);
        assertEquals("[Dono] Akashiic", LegacyText.strip(rgb));
        assertTrue(rgb.contains(S + "x" + S + "f" + S + "f" + S + "0" + S + "0" + S + "0" + S + "0" + S + "lA"), rgb);
        String legacy = format(state, "", "Akashiic", "", ColorMode.LEGACY);
        assertEquals("[Dono] Akashiic", LegacyText.strip(legacy));
        assertFalse(legacy.contains(S + "x"), legacy);
        // Getter-only consumers get the first stop.
        assertTrue(
            state.prefix("", ColorMode.RGB)
                .endsWith(S + "x" + S + "f" + S + "f" + S + "0" + S + "0" + S + "0" + S + "0" + S + "l"));
    }

    @Test
    void cachedRendersFollowTheColorMode() {
        TeamStyleState state = new TeamStyleState(
            Component.text("X", Style.of(TextColor.rgb(0x123456))),
            null,
            null,
            null);
        String rgb = state.prefix("", ColorMode.RGB);
        String legacy = state.prefix("", ColorMode.LEGACY);
        assertTrue(rgb.startsWith(S + "x"));
        assertEquals(3, legacy.length());
        assertEquals(rgb, state.prefix("", ColorMode.RGB));
    }

    @Test
    void visibilityOnlyStyleLeavesTextAlone() {
        TeamStyleState state = new TeamStyleState(null, null, null, NametagVisibility.NEVER);
        assertFalse(state.affectsText());
        assertNull(state.format("a", "b", "c", ColorMode.RGB));
        assertFalse(
            state.visibility()
                .isVisibleTo(true));
    }
}
