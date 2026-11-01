package com.akashic.kunftig.client.nametag;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.client.C17PacketCustomPayload;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.Team;

import com.akashic.kunftig.Kunftig;
import com.akashic.kunftig.KunftigConfig;
import com.akashic.kunftig.Tags;
import com.akashic.kunftig.common.protocol.KnpCodec;
import com.akashic.kunftig.common.protocol.KnpDecodeException;
import com.akashic.kunftig.common.protocol.KnpMessage;
import com.akashic.kunftig.common.protocol.KnpProtocol;
import com.akashic.kunftig.common.text.ColorMode;
import com.akashic.kunftig.common.text.Component;
import com.akashic.kunftig.common.text.ComponentJson;
import com.akashic.kunftig.common.text.Json;

/**
 * Client side of the Kunftig Nametag Protocol.
 * <p>
 * Threading: every protocol method runs on the client thread (1.7.10 queues {@code S01}, {@code S3F} and
 * {@code S3E} to it). The style lookups are also reached from the integrated server thread in singleplayer, because
 * {@code ScorePlayerTeam} is shared code; {@link #styleFor} rejects any team that does not belong to the client
 * world's scoreboard before touching the map, so that thread never reads it.
 * <p>
 * Styles are keyed by team name and live until the server removes them, resets, or a new {@code JoinGame} starts a
 * new session (new server or proxy switch). They deliberately survive vanilla team removal: Bukkit removes and
 * re-creates every team when a plugin swaps a player's scoreboard.
 */
public final class NametagClient {

    /** Upper bound on stored styles; a server needs one per team. */
    public static final int MAX_STYLES = 4096;
    private static final long HELLO_MIN_INTERVAL_NANOS = 500_000_000L;
    private static final int MAX_LOGGED_ERRORS = 20;

    private static final Map<String, TeamStyleState> STYLES = new HashMap<String, TeamStyleState>();

    /** Scoreboard of the current client world; teams of any other scoreboard are never styled. */
    private static volatile Scoreboard boundScoreboard;
    /** Negotiated protocol version, 0 while no server accepted the session. */
    private static int version;
    private static String serverVersion = "";
    private static long lastHelloNanos = Long.MIN_VALUE / 2;
    private static int loggedErrors;

    private NametagClient() {}

    // ------------------------------------------------------------------ session

    /** Called at the end of {@code handleJoinGame}: a new world and scoreboard, possibly a new server. */
    public static void onJoinGame(NetHandlerPlayClient handler) {
        STYLES.clear();
        version = 0;
        serverVersion = "";
        Minecraft minecraft = Minecraft.getMinecraft();
        boundScoreboard = minecraft.theWorld != null ? minecraft.theWorld.getScoreboard() : null;
        if (!KunftigConfig.nametagEnabled) {
            return;
        }
        // FML only registers its own channels; announce ours so Bukkit and Velocity forward it to us.
        handler.addToSendQueue(
            new C17PacketCustomPayload("REGISTER", KnpProtocol.CHANNEL.getBytes(StandardCharsets.UTF_8)));
        sendHello(handler, true);
    }

    private static void sendHello(NetHandlerPlayClient handler, boolean force) {
        long now = System.nanoTime();
        if (!force && now - lastHelloNanos < HELLO_MIN_INTERVAL_NANOS) {
            return;
        }
        lastHelloNanos = now;
        int capabilities = KnpProtocol.CAP_NAME_STYLE
            | (KunftigConfig.nametagVisibility && !Boolean.getBoolean("kunftig.nametag.visibility.disable")
                ? KnpProtocol.CAP_VISIBILITY
                : 0)
            | (RgbSupport.current() == ColorMode.RGB ? KnpProtocol.CAP_RGB : 0);
        KnpMessage.Hello hello = new KnpMessage.Hello(
            KnpProtocol.MIN_VERSION,
            KnpProtocol.VERSION,
            capabilities,
            Kunftig.NAME + "/" + Tags.VERSION);
        handler.addToSendQueue(new C17PacketCustomPayload(KnpProtocol.CHANNEL, KnpCodec.encode(hello)));
        debug("-> {}", hello);
    }

    /**
     * Handles a plugin message from {@code handleCustomPayload}.
     *
     * @return true if it was on the Kunftig channel (vanilla must then ignore it)
     */
    public static boolean onCustomPayload(NetHandlerPlayClient handler, String channel, byte[] data) {
        if (!KnpProtocol.CHANNEL.equals(channel)) {
            return false;
        }
        if (!KunftigConfig.nametagEnabled || data == null) {
            return true;
        }
        KnpMessage.Clientbound message;
        try {
            // Version 1 is the only codec; HELLO_ACK and PROBE are decoded the same way in every version.
            message = KnpCodec.decodeClientbound(data, version == 0 ? KnpProtocol.VERSION : version);
        } catch (KnpDecodeException e) {
            error("Ignoring malformed Kunftig message: {}", e.getMessage());
            return true;
        }
        if (message == null) {
            debug("<- unknown message id, ignored");
            return true;
        }
        debug("<- {}", message);
        handle(handler, message);
        return true;
    }

    private static void handle(NetHandlerPlayClient handler, KnpMessage.Clientbound message) {
        if (message instanceof KnpMessage.Probe) {
            sendHello(handler, false);
            return;
        }
        if (message instanceof KnpMessage.HelloAck) {
            KnpMessage.HelloAck ack = (KnpMessage.HelloAck) message;
            if (ack.version < KnpProtocol.MIN_VERSION || ack.version > KnpProtocol.VERSION) {
                error("Server chose unsupported Kunftig protocol version {}; ignoring it", ack.version);
                return;
            }
            version = ack.version;
            serverVersion = ack.serverVersion;
            STYLES.clear();
            Kunftig.LOG.info("Kunftig nametags active (protocol {}, server {})", version, serverVersion);
            return;
        }
        if (version == 0) {
            debug("<- {} before HELLO_ACK, ignored", message);
            return;
        }
        if (message instanceof KnpMessage.Reset) {
            STYLES.clear();
        } else if (message instanceof KnpMessage.TeamStyleRemove) {
            STYLES.remove(((KnpMessage.TeamStyleRemove) message).team);
        } else if (message instanceof KnpMessage.TeamStyle) {
            putStyle((KnpMessage.TeamStyle) message);
        }
    }

    private static void putStyle(KnpMessage.TeamStyle message) {
        if (!STYLES.containsKey(message.team) && STYLES.size() >= MAX_STYLES) {
            error("More than {} team styles; ignoring style of team '{}'", MAX_STYLES, message.team);
            return;
        }
        Component prefix = parseComponent(message.team, "prefix", message.prefixJson);
        Component suffix = parseComponent(message.team, "suffix", message.suffixJson);
        STYLES.put(message.team, new TeamStyleState(prefix, suffix, message.nameStyle, message.visibility));
    }

    private static Component parseComponent(String team, String field, String json) {
        if (json == null) {
            return null;
        }
        try {
            return ComponentJson.parse(json);
        } catch (Json.JsonException e) {
            // Fall back to the vanilla value for this field only.
            error("Invalid {} component for team '{}': {}", field, team, e.getMessage());
            return null;
        }
    }

    // ------------------------------------------------------------------ lookups (any thread)

    /** The style of a team, or {@code null} when the team is not on the client world's scoreboard or unstyled. */
    public static TeamStyleState styleFor(Scoreboard owner, String teamName) {
        if (owner == null || owner != boundScoreboard || version == 0) {
            return null;
        }
        return STYLES.get(teamName);
    }

    /** Whether the nametag of {@code player} should be drawn for the local player, per its team's visibility. */
    public static boolean isNametagVisible(EntityPlayer player) {
        if (!KunftigConfig.nametagVisibility) {
            return true;
        }
        Team team = player.getTeam();
        if (!(team instanceof ScorePlayerTeam)) {
            return true;
        }
        TeamStyleState style = styleFor(player.getWorldScoreboard(), ((ScorePlayerTeam) team).getRegisteredName());
        if (style == null || style.visibility() == null) {
            return true;
        }
        EntityPlayer viewer = Minecraft.getMinecraft().thePlayer;
        boolean sameTeam = viewer != null && team.isSameTeam(viewer.getTeam());
        return style.visibility()
            .isVisibleTo(sameTeam);
    }

    // ------------------------------------------------------------------ diagnostics

    public static int negotiatedVersion() {
        return version;
    }

    public static String serverVersion() {
        return serverVersion;
    }

    public static int styleCount() {
        return STYLES.size();
    }

    private static void debug(String format, Object... args) {
        if (KunftigConfig.debug) {
            Kunftig.LOG.info("[nametag] " + format, args);
        }
    }

    private static void error(String format, Object... args) {
        if (loggedErrors < MAX_LOGGED_ERRORS || KunftigConfig.debug) {
            loggedErrors++;
            Kunftig.LOG.warn("[nametag] " + format, args);
            if (loggedErrors == MAX_LOGGED_ERRORS) {
                Kunftig.LOG.warn("[nametag] Further protocol errors are only logged with debug=true");
            }
        }
    }
}
