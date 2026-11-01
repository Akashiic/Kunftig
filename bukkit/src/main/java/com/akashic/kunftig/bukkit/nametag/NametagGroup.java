package com.akashic.kunftig.bukkit.nametag;

/** A configured group: players with {@link #permission()} get {@link #spec()}; the highest priority group wins. */
public final class NametagGroup {

    private final String name;
    private final String permission;
    private final NametagSpec spec;

    public NametagGroup(String name, String permission, NametagSpec spec) {
        this.name = name;
        this.permission = permission;
        this.spec = spec;
    }

    public String name() {
        return name;
    }

    /** Permission node, or {@code null} when the group applies to everyone. */
    public String permission() {
        return permission;
    }

    public NametagSpec spec() {
        return spec;
    }
}
