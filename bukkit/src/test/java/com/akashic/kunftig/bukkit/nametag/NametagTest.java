package com.akashic.kunftig.bukkit.nametag;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import com.akashic.kunftig.bukkit.text.TextParseException;
import com.akashic.kunftig.common.protocol.NametagVisibility;
import com.akashic.kunftig.common.text.LegacyText;
import com.akashic.kunftig.common.text.NameStyle;
import com.akashic.kunftig.common.text.TextColor;
import com.akashic.kunftig.common.text.TextDecoration;

class NametagTest {

    private static final String S = "\u00a7";
    private static final ServerEnvironment LEGACY_SERVER = new ServerEnvironment("1.7.10-R0.1-SNAPSHOT", null);
    private static final ServerEnvironment MODERN_SERVER = new ServerEnvironment("1.21.1-R0.1-SNAPSHOT", null);

    @Test
    void environmentFromVersion() {
        assertEquals(16, LEGACY_SERVER.affixLimit());
        assertEquals(ServerEnvironment.UNLIMITED, MODERN_SERVER.affixLimit());
        assertEquals(16, new ServerEnvironment("1.12.2-R0.1-SNAPSHOT", null).affixLimit());
        assertEquals(ServerEnvironment.UNLIMITED, new ServerEnvironment("1.13-R0.1-SNAPSHOT", null).affixLimit());
        assertEquals(32, new ServerEnvironment("1.7.10-R0.1-SNAPSHOT", 32).affixLimit());
        assertEquals(16, new ServerEnvironment("garbage", null).affixLimit(), "unknown = most restrictive");
        assertTrue(new ServerEnvironment("1.16.5", null).isAtLeast(1, 16));
        assertFalse(new ServerEnvironment("1.15.2", null).isAtLeast(1, 16));
    }

    @Test
    void fallbackFitsSixteenCharsAndKeepsTheNameColor() throws TextParseException {
        FallbackRenderer fallback = new FallbackRenderer(LEGACY_SERVER);
        NametagSpec spec = NametagSpec.builder()
            .prefix("<gradient:#ff5555:#ffaa00><bold>Administrador</bold></gradient> | ")
            .name("#ffaa00 #ff5555")
            .build();
        String prefix = fallback.prefix(spec);
        assertTrue(prefix.length() <= 16, prefix + " (" + prefix.length() + ")");
        assertFalse(prefix.contains(S + "x"), "no hex below 1.16");
        // The prefix deliberately ends with the name's color code; the gradient starts at #ffaa00 = gold.
        assertTrue(prefix.endsWith(S + "6"), "name gets the first gradient stop as legacy gold: " + prefix);
        assertEquals(prefix + "x", LegacyText.truncate(prefix + "x", prefix.length() + 1), "no broken code");
    }

    @Test
    void fallbackNeverSplitsCodesAtAnyLimit() throws TextParseException {
        NametagSpec spec = NametagSpec.builder().prefix("&c&l[&6&lV&e&lI&a&lP&c&l] &#123456Long tail text")
            .name("aqua bold").build();
        for (int limit = 2; limit <= 40; limit++) {
            String prefix = new FallbackRenderer(new ServerEnvironment("1.7.10", limit)).prefix(spec);
            assertTrue(prefix.length() <= limit, limit + ": " + prefix);
            assertEquals(LegacyText.strip(prefix), LegacyText.strip(LegacyText.truncate(prefix + "!", limit + 1))
                .replace("!", ""), limit + ": " + prefix);
            assertFalse(prefix.endsWith(S), limit + ": " + prefix);
        }
    }

    @Test
    void modernServersKeepEverythingWithHex() throws TextParseException {
        NametagSpec spec = NametagSpec.builder().prefix("&#ff8800" + repeat("x", 60)).suffix(" <#00ff00>*").build();
        FallbackRenderer fallback = new FallbackRenderer(MODERN_SERVER);
        String prefix = fallback.prefix(spec);
        assertTrue(prefix.startsWith(S + "x" + S + "f" + S + "f" + S + "8" + S + "8" + S + "0" + S + "0"));
        assertEquals(60, LegacyText.strip(prefix).length());
        assertEquals(" " + S + "x" + S + "0" + S + "0" + S + "f" + S + "f" + S + "0" + S + "0*", fallback.suffix(spec));
    }

    @Test
    void suffixAfterStyledNameStartsFromReset() throws TextParseException {
        NametagSpec spec = NametagSpec.builder().name("gold").suffix(" [AFK]").build();
        assertEquals(S + "r [AFK]", new FallbackRenderer(LEGACY_SERVER).suffix(spec));
        NametagSpec unstyled = NametagSpec.builder().suffix(" [AFK]").build();
        assertEquals(" [AFK]", new FallbackRenderer(LEGACY_SERVER).suffix(unstyled));
    }

    @Test
    void vanillaOverridesReplaceTheAutomaticShortening() throws TextParseException {
        NametagSpec spec = NametagSpec.builder()
            .prefix("<gradient:#ff5555:#ffaa00><bold>Administrador</bold></gradient> | ")
            .vanillaPrefix("&c&lADM &8| ")
            .suffix(" <yellow>a very long suffix that will not fit")
            .vanillaSuffix(" &e*")
            .name("gold")
            .build();
        FallbackRenderer fallback = new FallbackRenderer(LEGACY_SERVER);
        assertEquals(S + "c" + S + "lADM " + S + "8| " + S + "6", fallback.prefix(spec));
        assertEquals(S + "r " + S + "e*", fallback.suffix(spec));
        // Layering keeps them per field.
        NametagSpec personal = NametagSpec.builder().prefix("[X] ").build();
        assertEquals("ADM | ", personal.over(spec).vanillaPrefix().plainText());
    }

    @Test
    void nameStyleTokens() throws TextParseException {
        NameStyle style = NameStyleParser.parse("#ff5555, gold bold &o");
        assertEquals(2, style.colors().size());
        assertEquals(TextColor.legacy('6'), style.colors().get(1));
        assertEquals(TextDecoration.BOLD.bit() | TextDecoration.ITALIC.bit(), style.decorationMask());
        assertEquals(NameStyle.PLAIN, NameStyleParser.parse("plain"));
        assertEquals(TextColor.rgb(0xabcdef), NameStyleParser.parse("&#abcdef").primaryColor());
        assertThrows(TextParseException.class, () -> NameStyleParser.parse("gold sparkly"));
        assertEquals(style, NameStyleParser.parse(NameStyleParser.format(style)), "format/parse round trip");
    }

    @Test
    void specsLayerFieldByField() throws TextParseException {
        NametagSpec group = NametagSpec.builder().prefix("[VIP] ").suffix(" *").priority(50).build();
        NametagSpec personal = NametagSpec.builder().suffix(" [AFK]").visibility("never").build();
        NametagSpec merged = personal.over(group);
        assertEquals("[VIP] ", merged.prefix().plainText());
        assertEquals(" [AFK]", merged.suffix().plainText());
        assertEquals(NametagVisibility.NEVER, merged.visibility());
        assertEquals(50, merged.priority());
        assertEquals(personal, personal.over(null));
        assertThrows(TextParseException.class, () -> NametagSpec.builder().visibility("sometimes"));
        assertEquals(999, NametagSpec.builder().priority(5000).build().priority());
    }

    @Test
    void oversizedComponentsAreRejectedWhenBuilt() {
        // MiniMessage merges neighbouring characters of equal color, so even a 400-char two-stop gradient stays
        // under the limit (~255 runs, ~8.1k chars of JSON); many stops over a long text do not.
        String fits = "<gradient:#ff0000:#0000ff>" + repeat("x", 400) + "</gradient>";
        assertDoesNotThrow(() -> NametagSpec.builder().prefix(fits).build());
        String huge = "<gradient:#ff0000:#00ff00:#0000ff:#ff00ff:#00ffff>" + repeat("x", 1200) + "</gradient>";
        TextParseException e = assertThrows(TextParseException.class, () -> NametagSpec.builder().prefix(huge).build());
        assertTrue(e.getMessage().contains("too long"), e.getMessage());
    }

    @Test
    void teamNamesAreShortSortableAndRecognisable() {
        TeamNames names = new TeamNames();
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        String high = names.nameFor(a, 100), low = names.nameFor(b, 5);
        assertTrue(high.length() <= 16 && low.length() <= 16);
        assertTrue(high.compareTo(low) < 0, "higher priority sorts first");
        assertTrue(TeamNames.isManaged(high) && TeamNames.isManaged(low));
        assertFalse(TeamNames.isManaged("admins"));
        assertEquals(high.substring(5), names.nameFor(a, 7).substring(5), "id stable across priority changes");
        for (int i = 0; i < 50; i++) {
            assertTrue(TeamNames.isManaged(new TeamNames().nameFor(UUID.randomUUID(), i)), "any random start is valid");
        }
        Set<String> ids = new HashSet<String>();
        for (int i = 0; i < 1000; i++) {
            assertTrue(ids.add(names.nameFor(UUID.randomUUID(), 0)), "unique");
        }
    }

    @Test
    void configGroupsLoadSortAndReportErrors() throws InvalidConfigurationException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("nametags:\n  fallback:\n    max-length: 32\n  groups:\n"
            + "    vip:\n      permission: kunftig.group.vip\n      priority: 50\n      prefix: '&a[VIP] '\n"
            + "      name: [green, bold]\n"
            + "    admin:\n      permission: kunftig.group.admin\n      priority: 100\n      prefix: '<red>[A] '\n"
            + "    broken:\n      priority: 10\n      name: 'not-a-color'\n"
            + "    everyone:\n      prefix: '<gray>'\n");
        NametagConfig config = NametagConfig.load(yaml, Logger.getLogger("test"));
        assertEquals(Integer.valueOf(32), config.fallbackMaxLength);
        assertEquals(3, config.groups.size(), "broken group skipped");
        assertEquals("admin", config.groups.get(0).name());
        assertEquals("vip", config.groups.get(1).name());
        assertNull(config.groups.get(2).permission());
        assertEquals(TextDecoration.BOLD.bit(), config.groups.get(1).spec().name().decorationMask());
    }

    private static String repeat(String s, int times) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < times; i++) {
            out.append(s);
        }
        return out.toString();
    }
}
