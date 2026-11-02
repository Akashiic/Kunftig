package com.akashic.kunftig.fishing;

/**
 * Which bobber physics the client should predict. The server decides ({@code fishing.modernBobber}) and tells Kunftig
 * clients on join with one plugin message on {@link #CHANNEL} (payload: 1 byte, 1 = modern); without it, and on every
 * new connection until it arrives, clients use 1.7.10's physics, so a server without Kunftig is never mispredicted.
 */
public final class ModernBobberMode {

    public static final String CHANNEL = "kunftig:fishing";
    public static final byte MODERN = 1;

    private static volatile boolean client;

    private ModernBobberMode() {}

    public static boolean client() {
        return client;
    }

    /** Client: a new connection starts with vanilla physics. */
    public static void reset() {
        client = false;
    }

    /** Client: the server's announcement. */
    public static void receive(byte[] payload) {
        client = payload != null && payload.length >= 1 && payload[0] == MODERN;
    }
}
