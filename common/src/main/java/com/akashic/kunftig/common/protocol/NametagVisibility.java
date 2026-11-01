package com.akashic.kunftig.common.protocol;

/** Who sees a team member's nametag; same semantics as the 1.8+ team option {@code nametagVisibility}. */
public enum NametagVisibility {

    ALWAYS("always"),
    NEVER("never"),
    HIDE_FOR_OTHER_TEAMS("hideForOtherTeams"),
    HIDE_FOR_OWN_TEAM("hideForOwnTeam");

    private static final NametagVisibility[] VALUES = values();

    private final String id;

    NametagVisibility(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    /** Whether a viewer sees the nametag, given whether the viewer is in the same team as its owner. */
    public boolean isVisibleTo(boolean viewerInSameTeam) {
        switch (this) {
            case NEVER:
                return false;
            case HIDE_FOR_OTHER_TEAMS:
                return viewerInSameTeam;
            case HIDE_FOR_OWN_TEAM:
                return !viewerInSameTeam;
            case ALWAYS:
            default:
                return true;
        }
    }

    static NametagVisibility byOrdinal(int ordinal) throws KnpDecodeException {
        if (ordinal < 0 || ordinal >= VALUES.length) {
            throw new KnpDecodeException("Unknown nametag visibility " + ordinal);
        }
        return VALUES[ordinal];
    }

    /** Parses {@link #id()} or the enum name, case-insensitively; returns {@code null} if unknown. */
    public static NametagVisibility parse(String value) {
        for (NametagVisibility visibility : VALUES) {
            if (visibility.id.equalsIgnoreCase(value) || visibility.name().equalsIgnoreCase(value)) {
                return visibility;
            }
        }
        return null;
    }
}
