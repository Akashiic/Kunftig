package com.akashic.kunftig.bukkit.api;

/** Entry point of the nametag API. */
public final class KunftigNametags {

    private static volatile NametagApi api;

    private KunftigNametags() {}

    /**
     * @throws IllegalStateException if Kunftig is not enabled (add {@code softdepend: [Kunftig]} to your plugin.yml)
     */
    public static NametagApi api() {
        NametagApi current = api;
        if (current == null) {
            throw new IllegalStateException("Kunftig is not enabled");
        }
        return current;
    }

    public static boolean isAvailable() {
        return api != null;
    }

    /** Internal: set by the plugin on enable/disable. */
    public static void install(NametagApi implementation) {
        api = implementation;
    }
}
