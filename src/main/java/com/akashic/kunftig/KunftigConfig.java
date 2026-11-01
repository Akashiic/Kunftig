package com.akashic.kunftig;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

import com.akashic.kunftig.fishing.FishingProfile;

/** {@code config/kunftig.cfg}. Plain Forge configuration: Kunftig has no library dependency. */
public final class KunftigConfig {

    public enum ColorModeSetting {
        /** RGB when Angelica's font renderer runs with RGB colors enabled, 16 colors otherwise. */
        AUTO,
        /** Always send {@code §x} colors (for a renderer that understands them but is not detected). */
        RGB,
        /** Always reduce colors to the 16 legacy colors. */
        LEGACY
    }

    public static boolean nametagEnabled = true;
    public static boolean nametagVisibility = true;
    public static ColorModeSetting nametagColorMode = ColorModeSetting.AUTO;
    public static boolean debug = false;

    public static boolean fishingEnabled = true;
    public static FishingProfile fishingProfile = FishingProfile.VANILLA_1_7_10;

    private KunftigConfig() {}

    static void load(File file) {
        Configuration config = new Configuration(file);
        config.load();

        String category = "nametag";
        config.setCategoryComment(
            category,
            "Long and RGB team prefixes/suffixes and name styles, sent by a server running the Kunftig plugin.");
        nametagEnabled = config.getBoolean(
            "enabled",
            category,
            true,
            "Announce Kunftig to servers and apply the nametag/tab styles they send.");
        nametagVisibility = config.getBoolean(
            "visibility",
            category,
            true,
            "Apply the nametag visibility (never / hideForOtherTeams / hideForOwnTeam) sent by the server.");
        String mode = config.getString(
            "colorMode",
            category,
            ColorModeSetting.AUTO.name(),
            "AUTO: 24-bit colors when Angelica's font renderer has RGB enabled, else the nearest of the 16 colors. "
                + "RGB: always 24-bit (only if your font renderer understands the Spigot \u00a7x format). "
                + "LEGACY: always 16 colors.",
            new String[] { "AUTO", "RGB", "LEGACY" });
        nametagColorMode = ColorModeSetting.valueOf(mode);

        debug = config
            .getBoolean("debug", Configuration.CATEGORY_GENERAL, false, "Log every Kunftig protocol message.");

        if (config.hasChanged()) {
            config.save();
        }
    }
}
