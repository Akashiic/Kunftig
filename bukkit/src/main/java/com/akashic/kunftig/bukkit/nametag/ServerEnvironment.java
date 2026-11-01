package com.akashic.kunftig.bukkit.nametag;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.akashic.kunftig.common.text.ColorMode;

/**
 * What the server's own team API accepts, derived from the Minecraft version:
 * <ul>
 * <li>below 1.13 team prefixes/suffixes are limited to 16 chars (Bukkit throws, and vanilla 1.7 clients disconnect
 * on longer ones); from 1.13 on they are unlimited components;</li>
 * <li>from 1.16 on legacy strings may carry {@code §x} 24-bit colors (CraftChatMessage parses them).</li>
 * </ul>
 */
public final class ServerEnvironment {

    public static final int UNLIMITED = Integer.MAX_VALUE;
    private static final Pattern VERSION = Pattern.compile("^(\\d+)\\.(\\d+)(?:\\.(\\d+))?");

    private final int major;
    private final int minor;
    private final int patch;
    private final int affixLimit;

    /**
     * @param bukkitVersion   {@code Bukkit.getBukkitVersion()}, e.g. {@code 1.7.10-R0.1-SNAPSHOT}
     * @param configuredLimit max fallback length from the config, or {@code null} for automatic
     */
    public ServerEnvironment(String bukkitVersion, Integer configuredLimit) {
        Matcher matcher = VERSION.matcher(bukkitVersion);
        if (matcher.find()) {
            major = Integer.parseInt(matcher.group(1));
            minor = Integer.parseInt(matcher.group(2));
            patch = matcher.group(3) == null ? 0 : Integer.parseInt(matcher.group(3));
        } else {
            // Unknown format: assume the most restrictive (1.7) behavior.
            major = 1;
            minor = 7;
            patch = 10;
        }
        int automatic = isAtLeast(1, 13) ? UNLIMITED : 16;
        affixLimit = configuredLimit == null ? automatic : Math.max(2, configuredLimit);
    }

    public boolean isAtLeast(int wantedMajor, int wantedMinor) {
        return major > wantedMajor || (major == wantedMajor && minor >= wantedMinor);
    }

    /** Max length of the vanilla prefix/suffix strings the plugin writes. */
    public int affixLimit() {
        return affixLimit;
    }

    /** Colors used in the vanilla (non-Kunftig) prefix/suffix strings. */
    public ColorMode fallbackColorMode() {
        return isAtLeast(1, 16) ? ColorMode.RGB : ColorMode.LEGACY;
    }

    public String version() {
        return major + "." + minor + "." + patch;
    }
}
