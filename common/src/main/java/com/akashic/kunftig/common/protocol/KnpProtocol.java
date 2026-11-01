package com.akashic.kunftig.common.protocol;

/**
 * Constants of the Kunftig Nametag Protocol (KNP), carried in plugin messages on {@link #CHANNEL}.
 * See {@code docs/nametags/PROTOCOL.md} for the full specification.
 */
public final class KnpProtocol {

    /**
     * Plugin channel. 15 chars: fits Bukkit 1.7's 16-char limit and 1.7's 20-char packet limit, and is a valid
     * lowercase namespaced key, so ViaVersion passes it through unchanged between 1.7 clients and 1.13+ servers.
     */
    public static final String CHANNEL = "kunftig:nametag";

    /** Highest protocol version this code speaks. */
    public static final int VERSION = 1;
    /** Lowest protocol version this code still speaks. */
    public static final int MIN_VERSION = 1;

    /** Largest payload Bukkit accepts in {@code Player#sendPluginMessage} (Messenger.MAX_MESSAGE_SIZE). */
    public static final int MAX_PAYLOAD_BYTES = 32766;
    /** Team names: modern servers allow long names; 1.7 clients only ever see 16. */
    public static final int MAX_TEAM_NAME = 64;
    /** Per-field limit for serialized JSON components. */
    public static final int MAX_COMPONENT_JSON = 8192;
    public static final int MAX_VERSION_STRING = 64;

    // Serverbound (client -> server) packet ids.
    public static final int SB_HELLO = 0x00;

    // Clientbound (server -> client) packet ids.
    public static final int CB_HELLO_ACK = 0x00;
    public static final int CB_PROBE = 0x01;
    public static final int CB_RESET = 0x02;
    public static final int CB_TEAM_STYLE = 0x03;
    public static final int CB_TEAM_STYLE_REMOVE = 0x04;

    /** Client capability: renders {@code §x} 24-bit colors (otherwise colors are reduced to 16). */
    public static final int CAP_RGB = 1;
    /** Client capability: applies {@code nameStyle}, including gradients. */
    public static final int CAP_NAME_STYLE = 1 << 1;
    /** Client capability: applies nametag visibility. */
    public static final int CAP_VISIBILITY = 1 << 2;

    private KnpProtocol() {}

    /**
     * Picks the version both sides speak: the highest common one, or {@code -1} when the ranges do not overlap.
     */
    public static int negotiate(int peerMin, int peerMax) {
        int version = Math.min(VERSION, peerMax);
        return version >= Math.max(MIN_VERSION, peerMin) ? version : -1;
    }
}
