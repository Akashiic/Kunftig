package com.akashic.kunftig.bukkit.nametag;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import com.akashic.kunftig.bukkit.text.TextParseException;

/**
 * Per-player overrides set with {@code /kunftig set}, stored in {@code players.yml} as the text the admin typed
 * (so they can be shown and edited again). Each field overrides the player's group field by field.
 */
public final class PlayerOverrides {

    public static final String[] FIELDS = { "prefix", "suffix", "name", "visibility", "priority", "vanilla-prefix",
        "vanilla-suffix" };

    private final File file;
    private final Logger logger;
    private final YamlConfiguration yaml = new YamlConfiguration();
    private final Map<UUID, NametagSpec> parsed = new HashMap<UUID, NametagSpec>();

    public PlayerOverrides(File file, Logger logger) {
        this.file = file;
        this.logger = logger;
    }

    public void load() {
        parsed.clear();
        if (file.exists()) {
            try {
                yaml.load(file);
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Could not read " + file.getName() + "; player overrides disabled", e);
                return;
            }
        }
        for (String key : yaml.getKeys(false)) {
            ConfigurationSection section = yaml.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            try {
                parsed.put(UUID.fromString(key), NametagConfig.readSpec(section));
            } catch (IllegalArgumentException e) {
                logger.warning(file.getName() + ": '" + key + "' is not a UUID, skipped");
            } catch (TextParseException e) {
                logger.warning(file.getName() + ": override of " + key + " skipped: " + e.getMessage());
            }
        }
    }

    public NametagSpec get(UUID player) {
        return parsed.get(player);
    }

    /** Raw stored value of a field, for display. */
    public String raw(UUID player, String field) {
        return yaml.getString(player + "." + field);
    }

    /**
     * Sets one field (validated first) or clears it with {@code null}, and saves.
     *
     * @throws TextParseException when the value does not parse; nothing is changed then
     */
    public void set(UUID player, String field, String value) throws TextParseException {
        String path = player + "." + field;
        Object previous = yaml.get(path);
        if ("priority".equals(field) && value != null) {
            try {
                yaml.set(path, Integer.parseInt(value.trim()));
            } catch (NumberFormatException e) {
                throw new TextParseException("Priority must be a number between 0 and " + NametagSpec.MAX_PRIORITY);
            }
        } else {
            yaml.set(path, value);
        }
        ConfigurationSection section = yaml.getConfigurationSection(player.toString());
        try {
            NametagSpec spec = section == null ? null : NametagConfig.readSpec(section);
            if (spec == null || spec.isEmpty()) {
                yaml.set(player.toString(), null);
                parsed.remove(player);
            } else {
                parsed.put(player, spec);
            }
        } catch (TextParseException e) {
            yaml.set(path, previous);
            throw e;
        }
        save();
    }

    public void clear(UUID player) {
        yaml.set(player.toString(), null);
        parsed.remove(player);
        save();
    }

    private void save() {
        try {
            yaml.save(file);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Could not save " + file.getName(), e);
        }
    }
}
