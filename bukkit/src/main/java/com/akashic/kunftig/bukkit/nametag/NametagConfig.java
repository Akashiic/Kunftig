package com.akashic.kunftig.bukkit.nametag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Logger;

import org.bukkit.configuration.ConfigurationSection;

import com.akashic.kunftig.bukkit.text.TextParseException;

/** The {@code nametags} section of {@code config.yml}. Invalid groups are reported and skipped, never fatal. */
public final class NametagConfig {

    public final boolean enabled;
    public final int syncIntervalTicks;
    public final boolean overrideOtherTeams;
    /** {@code null} = automatic (16 below 1.13, unlimited from 1.13). */
    public final Integer fallbackMaxLength;
    /** Sorted by priority, highest first. */
    public final List<NametagGroup> groups;

    private NametagConfig(boolean enabled, int syncIntervalTicks, boolean overrideOtherTeams, Integer fallbackMaxLength,
        List<NametagGroup> groups) {
        this.enabled = enabled;
        this.syncIntervalTicks = syncIntervalTicks;
        this.overrideOtherTeams = overrideOtherTeams;
        this.fallbackMaxLength = fallbackMaxLength;
        this.groups = groups;
    }

    public static NametagConfig load(ConfigurationSection root, Logger logger) {
        ConfigurationSection section = root.getConfigurationSection("nametags");
        if (section == null) {
            return new NametagConfig(false, 10, true, null, Collections.<NametagGroup>emptyList());
        }
        boolean enabled = section.getBoolean("enabled", true);
        int interval = Math.max(1, section.getInt("sync-interval-ticks", 10));
        boolean override = section.getBoolean("override-other-teams", true);

        Integer maxLength = null;
        String rawLength = section.getString("fallback.max-length", "auto");
        if (!"auto".equalsIgnoreCase(rawLength)) {
            try {
                maxLength = Math.max(2, Integer.parseInt(rawLength.trim()));
            } catch (NumberFormatException e) {
                logger.warning("nametags.fallback.max-length must be 'auto' or a number; using auto");
            }
        }

        List<NametagGroup> groups = new ArrayList<NametagGroup>();
        ConfigurationSection groupsSection = section.getConfigurationSection("groups");
        if (groupsSection != null) {
            for (String name : groupsSection.getKeys(false)) {
                ConfigurationSection group = groupsSection.getConfigurationSection(name);
                if (group == null) {
                    continue;
                }
                try {
                    groups.add(new NametagGroup(name, emptyToNull(group.getString("permission")), readSpec(group)));
                } catch (TextParseException e) {
                    logger.warning("Nametag group '" + name + "' skipped: " + e.getMessage());
                }
            }
        }
        Collections.sort(groups, new Comparator<NametagGroup>() {

            @Override
            public int compare(NametagGroup a, NametagGroup b) {
                return Integer.compare(b.spec().priority(), a.spec().priority());
            }
        });
        return new NametagConfig(enabled, interval, override, maxLength, Collections.unmodifiableList(groups));
    }

    /** Reads prefix / suffix / name / visibility / priority / vanilla-prefix / vanilla-suffix; absent keys stay null. */
    public static NametagSpec readSpec(ConfigurationSection section) throws TextParseException {
        NametagSpec.Builder builder = NametagSpec.builder()
            .prefix(section.isSet("prefix") ? section.getString("prefix") : null)
            .suffix(section.isSet("suffix") ? section.getString("suffix") : null)
            .visibility(section.isSet("visibility") ? section.getString("visibility") : null)
            .priority(section.isSet("priority") ? section.getInt("priority") : null)
            .vanillaPrefix(section.isSet("vanilla-prefix") ? section.getString("vanilla-prefix") : null)
            .vanillaSuffix(section.isSet("vanilla-suffix") ? section.getString("vanilla-suffix") : null);
        if (section.isList("name")) {
            StringBuilder tokens = new StringBuilder();
            for (Object token : section.getList("name")) {
                tokens.append(token).append(' ');
            }
            builder.name(tokens.toString());
        } else if (section.isSet("name")) {
            builder.name(section.getString("name"));
        }
        return builder.build();
    }

    private static String emptyToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }
}
