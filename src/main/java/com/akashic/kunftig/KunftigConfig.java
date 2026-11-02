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
    public static boolean fishingLava = false;
    public static boolean fishingCatchAim = false;
    public static boolean fishingModernBobber = false;

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

        category = "fishing";
        config.setCategoryComment(
            category,
            "Server side. The 1.21.1 fishing cycle and its Bukkit API (KunftigFishEvent, ModernFishHook).");
        fishingEnabled = config.getBoolean(
            "enabled",
            category,
            true,
            "Run the bite cycle in Kunftig's engine and fire the 1.21.1 events. false: vanilla code, no events.");
        String profile = config.getString(
            "profile",
            category,
            FishingProfile.VANILLA_1_7_10.name(),
            "Initial settings of every hook (plugins can change them per hook). VANILLA_1_7_10: identical to vanilla. "
                + "MODERN_1_21_1: 1.21.1's timings and treasure only in open water.",
            new String[] { FishingProfile.VANILLA_1_7_10.name(), FishingProfile.MODERN_1_21_1.name() });
        fishingProfile = FishingProfile.valueOf(profile);
        fishingLava = config.getBoolean(
            "lava",
            category,
            false,
            "Let every hook float and catch fish in lava (plugins can enable it per hook with setLavaFishing).");
        fishingCatchAim = config.getBoolean(
            "catchAim",
            category,
            false,
            "Aim caught items at the angler: vanilla's arc falls short when the angler stands above the water "
                + "(plugins can set it per hook with setCatchAimedAtAngler).");
        fishingModernBobber = config.getBoolean(
            "modernBobber",
            category,
            false,
            "Bobber physics of 1.16+: brakes when it hits the water (no deep dive) and floats at the drawn surface "
                + "with a gentle bob. Kunftig clients are told on join and predict the same motion.");

        debug = config
            .getBoolean("debug", Configuration.CATEGORY_GENERAL, false, "Log every Kunftig protocol message.");

        if (config.hasChanged()) {
            config.save();
        }
    }
}
