package com.akashic.kunftig.bukkit.nametag;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.ChatColor;
import org.bukkit.scoreboard.Team;

import com.akashic.kunftig.common.protocol.NametagVisibility;
import com.akashic.kunftig.common.text.TextColor;

/**
 * Team settings that newer Bukkit APIs have and 1.7.10 lacks, applied reflectively so one jar runs everywhere:
 * <ul>
 * <li>{@code Team#setColor(ChatColor)} (1.12+): from 1.13 on, clients color the member name with the team color
 * instead of the prefix's last color code;</li>
 * <li>{@code Team#setOption(NAME_TAG_VISIBILITY, ...)} (1.9+).</li>
 * </ul>
 * On servers without them this is a no-op; Kunftig clients still get both through the protocol.
 */
final class VanillaTeamOptions {

    private final Logger logger;
    private MethodHandle setColor;
    private MethodHandle setOption;
    private Object nameTagVisibilityOption;
    private Class<? extends Enum<?>> optionStatus;
    private boolean failed;

    @SuppressWarnings("unchecked")
    VanillaTeamOptions(Logger logger) {
        this.logger = logger;
        // Method handles resolve only the method asked for; Class#getMethod resolves every signature of the type,
        // which breaks on Crucible when one of them names an obfuscated class (Kunftig issue #3).
        MethodHandles.Lookup lookup = MethodHandles.publicLookup();
        try {
            setColor = lookup.findVirtual(Team.class, "setColor", MethodType.methodType(void.class, ChatColor.class));
        } catch (NoSuchMethodException | IllegalAccessException e) {
            setColor = null;
        }
        try {
            Class<?> option = Class.forName("org.bukkit.scoreboard.Team$Option");
            Class<?> status = Class.forName("org.bukkit.scoreboard.Team$OptionStatus");
            setOption = lookup.findVirtual(Team.class, "setOption", MethodType.methodType(void.class, option, status));
            nameTagVisibilityOption = enumConstant(option, "NAME_TAG_VISIBILITY");
            optionStatus = (Class<? extends Enum<?>>) status;
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | IllegalArgumentException e) {
            setOption = null;
        }
    }

    private static Object enumConstant(Class<?> type, String name) {
        for (Object constant : type.getEnumConstants()) {
            if (((Enum<?>) constant).name().equals(name)) {
                return constant;
            }
        }
        throw new IllegalArgumentException(type.getName() + "." + name);
    }

    boolean supportsColor() {
        return setColor != null;
    }

    /** Sets the team color to the nearest legacy color of the name's (first) color, or RESET. */
    void applyColor(Team team, TextColor nameColor) {
        if (setColor == null || failed) {
            return;
        }
        ChatColor color = nameColor == null ? ChatColor.RESET
            : ChatColor.getByChar(nameColor.nearestLegacy().legacyCode());
        invoke(setColor, "setColor", team, color);
    }

    void applyVisibility(Team team, NametagVisibility visibility) {
        if (setOption == null || failed) {
            return;
        }
        // Bukkit names the status after who SEES the tag; the protocol after who it is HIDDEN from.
        String status;
        switch (visibility == null ? NametagVisibility.ALWAYS : visibility) {
            case NEVER:
                status = "NEVER";
                break;
            case HIDE_FOR_OTHER_TEAMS:
                status = "FOR_OWN_TEAM";
                break;
            case HIDE_FOR_OWN_TEAM:
                status = "FOR_OTHER_TEAMS";
                break;
            case ALWAYS:
            default:
                status = "ALWAYS";
        }
        invoke(setOption, "setOption", team, nameTagVisibilityOption, enumConstant(optionStatus, status));
    }

    private void invoke(MethodHandle method, String name, Team team, Object... args) {
        Object[] all = new Object[args.length + 1];
        all[0] = team;
        System.arraycopy(args, 0, all, 1, args.length);
        try {
            method.invokeWithArguments(all);
        } catch (Throwable e) {
            failed = true;
            logger.log(Level.WARNING, "Could not call Team#" + name
                + "; vanilla team color/visibility disabled (Kunftig clients are not affected)", e);
        }
    }
}
