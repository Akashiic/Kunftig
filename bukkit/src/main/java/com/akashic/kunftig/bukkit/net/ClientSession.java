package com.akashic.kunftig.bukkit.net;

import com.akashic.kunftig.common.protocol.KnpProtocol;

/** A player whose client completed the Kunftig handshake. Immutable. */
public final class ClientSession {

    private final int version;
    private final int capabilities;
    private final String clientVersion;

    ClientSession(int version, int capabilities, String clientVersion) {
        this.version = version;
        this.capabilities = capabilities;
        this.clientVersion = clientVersion;
    }

    public int version() {
        return version;
    }

    public int capabilities() {
        return capabilities;
    }

    public String clientVersion() {
        return clientVersion;
    }

    public boolean rendersRgb() {
        return (capabilities & KnpProtocol.CAP_RGB) != 0;
    }

    @Override
    public String toString() {
        return clientVersion + " (protocol " + version + ", " + (rendersRgb() ? "RGB" : "16 colors") + ")";
    }
}
