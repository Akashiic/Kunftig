package com.akashic.kunftig.bukkit.nametag;

import java.util.Objects;

import com.akashic.kunftig.bukkit.text.TextParseException;
import com.akashic.kunftig.bukkit.text.TextParser;
import com.akashic.kunftig.common.protocol.KnpProtocol;
import com.akashic.kunftig.common.protocol.NametagVisibility;
import com.akashic.kunftig.common.text.Component;
import com.akashic.kunftig.common.text.ComponentJson;
import com.akashic.kunftig.common.text.NameStyle;

/**
 * A nametag: prefix, suffix, name style, visibility and tab priority. Every field may be {@code null}, so a spec is
 * also a partial layer: {@link #over(NametagSpec)} stacks a player override on top of a group, field by field.
 */
public final class NametagSpec {

    public static final NametagSpec EMPTY = new NametagSpec(null, null, null, null, null, null, null);

    /** Tab ordering: higher priority is listed first. Clamped to [0, 999]. */
    public static final int MAX_PRIORITY = 999;

    private final Component prefix;
    private final Component suffix;
    private final NameStyle name;
    private final NametagVisibility visibility;
    private final Integer priority;
    private final Component vanillaPrefix;
    private final Component vanillaSuffix;

    /**
     * @throws IllegalArgumentException if a component is too large to send in one plugin message
     */
    public NametagSpec(Component prefix, Component suffix, NameStyle name, NametagVisibility visibility,
        Integer priority, Component vanillaPrefix, Component vanillaSuffix) {
        checkSize("prefix", prefix);
        checkSize("suffix", suffix);
        this.prefix = prefix;
        this.suffix = suffix;
        this.name = name;
        this.visibility = visibility;
        this.priority = priority == null ? null : Math.max(0, Math.min(MAX_PRIORITY, priority));
        this.vanillaPrefix = vanillaPrefix;
        this.vanillaSuffix = vanillaSuffix;
    }

    private static void checkSize(String field, Component component) {
        if (component != null) {
            int length = ComponentJson.write(component).length();
            if (length > KnpProtocol.MAX_COMPONENT_JSON) {
                throw new IllegalArgumentException("The " + field + " is too long: " + length
                    + " chars as JSON, the limit is " + KnpProtocol.MAX_COMPONENT_JSON);
            }
        }
    }

    public Component prefix() {
        return prefix;
    }

    public Component suffix() {
        return suffix;
    }

    public NameStyle name() {
        return name;
    }

    public NametagVisibility visibility() {
        return visibility;
    }

    /** Text for the vanilla prefix instead of the automatically shortened {@link #prefix()}, or {@code null}. */
    public Component vanillaPrefix() {
        return vanillaPrefix;
    }

    /** Text for the vanilla suffix instead of the automatically shortened {@link #suffix()}, or {@code null}. */
    public Component vanillaSuffix() {
        return vanillaSuffix;
    }

    /** Effective priority (0 when unset). */
    public int priority() {
        return priority == null ? 0 : priority;
    }

    public boolean isEmpty() {
        return prefix == null && suffix == null && name == null && visibility == null && priority == null
            && vanillaPrefix == null && vanillaSuffix == null;
    }

    /** This layer on top of {@code base}: fields set here win. */
    public NametagSpec over(NametagSpec base) {
        if (base == null || base.isEmpty()) {
            return this;
        }
        return new NametagSpec(prefix != null ? prefix : base.prefix, suffix != null ? suffix : base.suffix,
            name != null ? name : base.name, visibility != null ? visibility : base.visibility,
            priority != null ? priority : base.priority, vanillaPrefix != null ? vanillaPrefix : base.vanillaPrefix,
            vanillaSuffix != null ? vanillaSuffix : base.vanillaSuffix);
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Builds specs from text: MiniMessage with legacy codes, or a JSON component. */
    public static final class Builder {

        private Component prefix;
        private Component suffix;
        private NameStyle name;
        private NametagVisibility visibility;
        private Integer priority;
        private Component vanillaPrefix;
        private Component vanillaSuffix;

        /** What clients without Kunftig see instead of the shortened prefix (still cut to the server's limit). */
        public Builder vanillaPrefix(String text) throws TextParseException {
            this.vanillaPrefix = text == null ? null : TextParser.parse(text);
            return this;
        }

        /** What clients without Kunftig see instead of the shortened suffix (still cut to the server's limit). */
        public Builder vanillaSuffix(String text) throws TextParseException {
            this.vanillaSuffix = text == null ? null : TextParser.parse(text);
            return this;
        }

        public Builder prefix(String text) throws TextParseException {
            this.prefix = text == null ? null : TextParser.parse(text);
            return this;
        }

        public Builder suffix(String text) throws TextParseException {
            this.suffix = text == null ? null : TextParser.parse(text);
            return this;
        }

        public Builder prefix(Component component) {
            this.prefix = component;
            return this;
        }

        public Builder suffix(Component component) {
            this.suffix = component;
            return this;
        }

        /** Name style tokens, see {@link NameStyleParser}. */
        public Builder name(String tokens) throws TextParseException {
            this.name = tokens == null ? null : NameStyleParser.parse(tokens);
            return this;
        }

        public Builder name(NameStyle style) {
            this.name = style;
            return this;
        }

        public Builder visibility(String value) throws TextParseException {
            if (value == null) {
                this.visibility = null;
                return this;
            }
            NametagVisibility parsed = NametagVisibility.parse(value);
            if (parsed == null) {
                throw new TextParseException(
                    "Unknown visibility '" + value + "' (always, never, hideForOtherTeams, hideForOwnTeam)");
            }
            this.visibility = parsed;
            return this;
        }

        public Builder visibility(NametagVisibility value) {
            this.visibility = value;
            return this;
        }

        public Builder priority(Integer value) {
            this.priority = value;
            return this;
        }

        /**
         * @throws TextParseException if a component is too large to be sent
         */
        public NametagSpec build() throws TextParseException {
            try {
                return new NametagSpec(prefix, suffix, name, visibility, priority, vanillaPrefix, vanillaSuffix);
            } catch (IllegalArgumentException e) {
                throw new TextParseException(e.getMessage());
            }
        }
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof NametagSpec)) {
            return false;
        }
        NametagSpec that = (NametagSpec) other;
        return Objects.equals(prefix, that.prefix) && Objects.equals(suffix, that.suffix)
            && Objects.equals(name, that.name) && visibility == that.visibility
            && Objects.equals(priority, that.priority) && Objects.equals(vanillaPrefix, that.vanillaPrefix)
            && Objects.equals(vanillaSuffix, that.vanillaSuffix);
    }

    @Override
    public int hashCode() {
        return Objects.hash(prefix, suffix, name, visibility, priority, vanillaPrefix, vanillaSuffix);
    }
}
