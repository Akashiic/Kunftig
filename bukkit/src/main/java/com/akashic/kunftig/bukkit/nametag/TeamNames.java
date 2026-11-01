package com.akashic.kunftig.bukkit.nametag;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Names of the teams Kunftig manages: {@code kf} + 3-digit inverted priority + 6-char id (11 chars, within 1.7's
 * 16-char team name limit). Clients that sort the tab list by team name (1.8+, NecroTempus) list higher priorities
 * first. The fixed shape lets the plugin recognise and clean up its own teams, e.g. after a crash left some in the
 * main scoreboard's saved data.
 */
public final class TeamNames {

    public static final Pattern MANAGED = Pattern.compile("^kf\\d{3}[0-9a-z]{6}$");
    /** Ids are rendered as 6 base-36 digits; 36^6 does not fit an int, Integer.MAX_VALUE does and needs 6 digits. */
    private static final int ID_SPACE = Integer.MAX_VALUE;

    private final Map<UUID, String> ids = new HashMap<UUID, String>();
    /**
     * Random start: after a plugin reload, ids (and so team names) are not reused. Kunftig clients may still hold a
     * style for an old name, because a disabling plugin can no longer send plugin messages to tell them.
     */
    private int next = new SecureRandom().nextInt(ID_SPACE);

    /** Team name for a player at a priority. The id part is stable for the player while the plugin runs. */
    public String nameFor(UUID player, int priority) {
        String id = ids.get(player);
        if (id == null) {
            id = String.format(Locale.ROOT, "%6s", Integer.toString(next, 36)).replace(' ', '0');
            next = next == ID_SPACE - 1 ? 0 : next + 1;
            ids.put(player, id);
        }
        int clamped = Math.max(0, Math.min(NametagSpec.MAX_PRIORITY, priority));
        return String.format(Locale.ROOT, "kf%03d%s", NametagSpec.MAX_PRIORITY - clamped, id);
    }

    public void forget(UUID player) {
        ids.remove(player);
    }

    public static boolean isManaged(String teamName) {
        return MANAGED.matcher(teamName).matches();
    }
}
