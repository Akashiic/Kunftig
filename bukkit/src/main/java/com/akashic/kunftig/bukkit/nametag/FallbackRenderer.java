package com.akashic.kunftig.bukkit.nametag;

import com.akashic.kunftig.common.text.ColorMode;
import com.akashic.kunftig.common.text.Component;
import com.akashic.kunftig.common.text.LegacyRenderer;
import com.akashic.kunftig.common.text.NameStyle;
import com.akashic.kunftig.common.text.Style;

/**
 * Builds the vanilla prefix/suffix strings every client sees (and the only ones non-Kunftig clients see).
 * <p>
 * The string is built by appending one code point at a time and keeping the longest result that still fits the
 * limit together with the name's color codes. Cutting a finished string instead would compute the name's codes
 * from the wrong style state, and could leave half a {@code §x} sequence behind.
 */
public final class FallbackRenderer {

    private final ServerEnvironment environment;

    public FallbackRenderer(ServerEnvironment environment) {
        this.environment = environment;
    }

    /**
     * Vanilla prefix: the prefix text, then the codes that color the player name. Those trailing codes are what
     * colors the name below 1.13; from 1.13 on servers drop them (no text follows) and clients use the team color,
     * which {@link VanillaTeamOptions} sets.
     */
    public String prefix(NametagSpec spec) {
        ColorMode mode = environment.fallbackColorMode();
        Style nameStyle = spec.name() == null ? null : nameStyle(spec.name());
        LegacyRenderer best = new LegacyRenderer(mode);
        Component text = spec.vanillaPrefix() != null ? spec.vanillaPrefix() : spec.prefix();
        if (text != null) {
            best = fit(best, text, nameStyle);
        }
        if (nameStyle != null) {
            LegacyRenderer withName = best.copy().applyStyle(nameStyle);
            if (withName.length() <= environment.affixLimit()) {
                best = withName;
            }
        }
        return best.toString();
    }

    /** Vanilla suffix. After a styled name it starts from a reset, so it does not inherit the name's color. */
    public String suffix(NametagSpec spec) {
        Component text = spec.vanillaSuffix() != null ? spec.vanillaSuffix() : spec.suffix();
        if (text == null) {
            return "";
        }
        LegacyRenderer renderer = new LegacyRenderer(environment.fallbackColorMode());
        if (spec.name() != null && !nameStyle(spec.name()).isEmpty()) {
            renderer.assumeUnknownStyle();
        }
        return fit(renderer, text, null).toString();
    }

    /** The name's style as one legacy style: gradients fall back to their first color. */
    static Style nameStyle(NameStyle name) {
        return name.baseStyle().withColor(name.primaryColor());
    }

    private LegacyRenderer fit(LegacyRenderer start, Component component, Style reserveFor) {
        final int limit = environment.affixLimit();
        final LegacyRenderer[] best = { start };
        final boolean[] full = { false };
        component.forEachRun((text, style) -> {
            int offset = 0;
            while (!full[0] && offset < text.length()) {
                int end = offset + Character.charCount(text.codePointAt(offset));
                LegacyRenderer attempt = best[0].copy().appendRun(text.substring(offset, end), style);
                int needed = attempt.length();
                if (reserveFor != null) {
                    needed = attempt.copy().applyStyle(reserveFor).length();
                }
                if (needed > limit) {
                    full[0] = true;
                    return;
                }
                best[0] = attempt;
                offset = end;
            }
        });
        return best[0];
    }
}
