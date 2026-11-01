package com.akashic.kunftig.common.protocol;

import java.util.Objects;

import com.akashic.kunftig.common.text.NameStyle;

/** All KNP messages. Immutable value classes. */
public final class KnpMessage {

    private KnpMessage() {}

    /** Marker for messages sent by the server. */
    public interface Clientbound {}

    /** Marker for messages sent by the client. */
    public interface Serverbound {}

    /** Client announces itself and its protocol range. Sent on every JoinGame and in reply to {@link Probe}. */
    public static final class Hello implements Serverbound {

        public final int minVersion;
        public final int maxVersion;
        public final int capabilities;
        public final String clientVersion;

        public Hello(int minVersion, int maxVersion, int capabilities, String clientVersion) {
            this.minVersion = minVersion;
            this.maxVersion = maxVersion;
            this.capabilities = capabilities;
            this.clientVersion = Objects.requireNonNull(clientVersion);
        }

        public boolean has(int capability) {
            return (capabilities & capability) == capability;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Hello)) return false;
            Hello h = (Hello) o;
            return minVersion == h.minVersion && maxVersion == h.maxVersion && capabilities == h.capabilities
                && clientVersion.equals(h.clientVersion);
        }

        @Override
        public int hashCode() {
            return Objects.hash(minVersion, maxVersion, capabilities, clientVersion);
        }

        @Override
        public String toString() {
            return "Hello{v" + minVersion + "-" + maxVersion + ", caps=" + capabilities + ", " + clientVersion + '}';
        }
    }

    /** Server accepts the session in {@code version}. Styles follow; the client first clears what it had. */
    public static final class HelloAck implements Clientbound {

        public final int version;
        public final String serverVersion;

        public HelloAck(int version, String serverVersion) {
            this.version = version;
            this.serverVersion = Objects.requireNonNull(serverVersion);
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof HelloAck && ((HelloAck) o).version == version
                && ((HelloAck) o).serverVersion.equals(serverVersion);
        }

        @Override
        public int hashCode() {
            return Objects.hash(version, serverVersion);
        }

        @Override
        public String toString() {
            return "HelloAck{v" + version + ", " + serverVersion + '}';
        }
    }

    /** Server asks the client to (re)send {@link Hello}, e.g. after a plugin reload. */
    public static final class Probe implements Clientbound {

        public static final Probe INSTANCE = new Probe();

        private Probe() {}

        @Override
        public String toString() {
            return "Probe";
        }
    }

    /** Client drops every style it holds. */
    public static final class Reset implements Clientbound {

        public static final Reset INSTANCE = new Reset();

        private Reset() {}

        @Override
        public String toString() {
            return "Reset";
        }
    }

    /**
     * Replaces the Kunftig style of a scoreboard team (by name). Each field is optional: an absent field leaves
     * that part to the vanilla team data. The style applies whenever a team with this name exists on the client
     * scoreboard, whether the team packet arrives before or after this message.
     */
    public static final class TeamStyle implements Clientbound {

        public final String team;
        /** JSON text component or {@code null}. */
        public final String prefixJson;
        /** JSON text component or {@code null}. */
        public final String suffixJson;
        /**
         * Style of the member name or {@code null}. When present the name starts from a reset style (modern
         * semantics); when absent the prefix's trailing formatting carries into the name (1.7 semantics).
         */
        public final NameStyle nameStyle;
        /** Nametag visibility or {@code null} for vanilla behavior. */
        public final NametagVisibility visibility;

        public TeamStyle(String team, String prefixJson, String suffixJson, NameStyle nameStyle,
            NametagVisibility visibility) {
            this.team = Objects.requireNonNull(team);
            this.prefixJson = prefixJson;
            this.suffixJson = suffixJson;
            this.nameStyle = nameStyle;
            this.visibility = visibility;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof TeamStyle)) return false;
            TeamStyle t = (TeamStyle) o;
            return team.equals(t.team) && Objects.equals(prefixJson, t.prefixJson)
                && Objects.equals(suffixJson, t.suffixJson) && Objects.equals(nameStyle, t.nameStyle)
                && visibility == t.visibility;
        }

        @Override
        public int hashCode() {
            return Objects.hash(team, prefixJson, suffixJson, nameStyle, visibility);
        }

        @Override
        public String toString() {
            return "TeamStyle{" + team + ", prefix=" + prefixJson + ", suffix=" + suffixJson + ", name=" + nameStyle
                + ", visibility=" + visibility + '}';
        }
    }

    /** Removes the Kunftig style of a team; vanilla team data applies again. */
    public static final class TeamStyleRemove implements Clientbound {

        public final String team;

        public TeamStyleRemove(String team) {
            this.team = Objects.requireNonNull(team);
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof TeamStyleRemove && ((TeamStyleRemove) o).team.equals(team);
        }

        @Override
        public int hashCode() {
            return team.hashCode();
        }

        @Override
        public String toString() {
            return "TeamStyleRemove{" + team + '}';
        }
    }
}
