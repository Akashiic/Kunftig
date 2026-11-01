package com.akashic.kunftig.client.nametag;

import java.util.LinkedHashMap;
import java.util.Map;

import com.akashic.kunftig.common.protocol.NametagVisibility;
import com.akashic.kunftig.common.text.ColorMode;
import com.akashic.kunftig.common.text.Component;
import com.akashic.kunftig.common.text.LegacyRenderer;
import com.akashic.kunftig.common.text.NameStyle;

/**
 * The Kunftig style of one team, and how it combines with the team's vanilla prefix/suffix into the legacy
 * strings that 1.7.10 consumers read ({@code getColorPrefix}, {@code getColorSuffix}, {@code formatString}).
 * <p>
 * Composition rules:
 * <ul>
 * <li>An absent prefix/suffix falls back to the vanilla (16-char) value.</li>
 * <li>Without a {@link NameStyle} the prefix's trailing formatting carries into the name, as in 1.7.</li>
 * <li>With a {@link NameStyle} the name and the suffix each start from a reset style, as in 1.13+.
 * {@code getColorPrefix} then ends with the name's codes (gradients use their first stop), so consumers that
 * build {@code prefix + name + suffix} themselves still get the right color; {@code formatString} renders the
 * full gradient.</li>
 * </ul>
 * Not thread-safe: used on the client thread only. Rendered strings are cached per {@link ColorMode}.
 */
public final class TeamStyleState {

    private static final int NAME_CACHE_SIZE = 64;

    final Component prefix;
    final Component suffix;
    final NameStyle nameStyle;
    final NametagVisibility visibility;

    private final String[] prefixCache = new String[ColorMode.values().length];
    private final String[] suffixCache = new String[ColorMode.values().length];
    private final String[] nameCodesAtStart = new String[ColorMode.values().length];
    private final String[] nameCodesAfterText = new String[ColorMode.values().length];
    @SuppressWarnings("unchecked")
    private final Map<String, String>[] gradientCache = new Map[ColorMode.values().length];

    public TeamStyleState(Component prefix, Component suffix, NameStyle nameStyle, NametagVisibility visibility) {
        this.prefix = prefix;
        this.suffix = suffix;
        this.nameStyle = nameStyle;
        this.visibility = visibility;
    }

    public NametagVisibility visibility() {
        return visibility;
    }

    public boolean hasGradientName() {
        return nameStyle != null && nameStyle.isGradient();
    }

    /** True when this style changes nothing a getter returns (e.g. a visibility-only style). */
    public boolean affectsText() {
        return prefix != null || suffix != null || nameStyle != null;
    }

    private String prefixBase(String vanillaPrefix, ColorMode mode) {
        if (prefix == null) {
            return vanillaPrefix;
        }
        String cached = prefixCache[mode.ordinal()];
        if (cached == null) {
            cached = LegacyRenderer.render(prefix, mode);
            prefixCache[mode.ordinal()] = cached;
        }
        return cached;
    }

    /** Replacement for {@code getColorPrefix()}. */
    public String prefix(String vanillaPrefix, ColorMode mode) {
        String base = prefixBase(vanillaPrefix, mode);
        if (nameStyle == null) {
            return base;
        }
        return base + nameCodes(!base.isEmpty(), mode);
    }

    private String nameCodes(boolean afterText, ColorMode mode) {
        String[] cache = afterText ? nameCodesAfterText : nameCodesAtStart;
        String cached = cache[mode.ordinal()];
        if (cached == null) {
            LegacyRenderer renderer = new LegacyRenderer(mode);
            if (afterText) {
                renderer.assumeUnknownStyle();
            }
            // After prefix text the state is unknown, so this starts with §r: the name never inherits the prefix.
            renderer.applyStyle(
                nameStyle.baseStyle()
                    .withColor(nameStyle.primaryColor()));
            cached = renderer.toString();
            cache[mode.ordinal()] = cached;
        }
        return cached;
    }

    /** Replacement for {@code getColorSuffix()}. */
    public String suffix(String vanillaSuffix, ColorMode mode) {
        if (suffix == null) {
            if (nameStyle == null || vanillaSuffix.isEmpty()) {
                return vanillaSuffix;
            }
            return "\u00a7r" + vanillaSuffix;
        }
        String cached = suffixCache[mode.ordinal()];
        if (cached == null) {
            LegacyRenderer renderer = new LegacyRenderer(mode);
            if (nameStyle != null) {
                // The suffix must not inherit the name's style (modern semantics).
                renderer.assumeUnknownStyle();
            }
            cached = renderer.append(suffix)
                .toString();
            suffixCache[mode.ordinal()] = cached;
        }
        return cached;
    }

    /**
     * Replacement for {@code formatString(name)} when the name has a gradient; {@code null} otherwise, meaning
     * vanilla's {@code getColorPrefix() + name + getColorSuffix()} is already correct.
     */
    public String format(String vanillaPrefix, String name, String vanillaSuffix, ColorMode mode) {
        if (!hasGradientName()) {
            return null;
        }
        String base = prefixBase(vanillaPrefix, mode);
        return base + gradientName(name, !base.isEmpty(), mode) + suffix(vanillaSuffix, mode);
    }

    private String gradientName(String name, boolean afterText, ColorMode mode) {
        Map<String, String> cache = gradientCache[mode.ordinal()];
        if (cache == null) {
            cache = new LinkedHashMap<String, String>(16, 0.75f, true) {

                private static final long serialVersionUID = 1L;

                @Override
                protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                    return size() > NAME_CACHE_SIZE;
                }
            };
            gradientCache[mode.ordinal()] = cache;
        }
        String key = afterText ? "1" + name : "0" + name;
        String cached = cache.get(key);
        if (cached == null) {
            LegacyRenderer renderer = new LegacyRenderer(mode);
            if (afterText) {
                renderer.assumeUnknownStyle();
            }
            cached = renderer.append(nameStyle.apply(name))
                .toString();
            cache.put(key, cached);
        }
        return cached;
    }
}
