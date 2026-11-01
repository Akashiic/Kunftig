package com.akashic.kunftig.bukkit.api;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

/**
 * Fired on the main thread when a player's client completes the Kunftig handshake: it renders long, RGB and
 * gradient nametags from now on. Fired again after a server switch behind a proxy or a reconnect.
 */
public final class KunftigClientReadyEvent extends PlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final int protocolVersion;
    private final String clientVersion;
    private final int capabilities;

    public KunftigClientReadyEvent(Player player, int protocolVersion, String clientVersion, int capabilities) {
        super(player);
        this.protocolVersion = protocolVersion;
        this.clientVersion = clientVersion;
        this.capabilities = capabilities;
    }

    public int getProtocolVersion() {
        return protocolVersion;
    }

    public String getClientVersion() {
        return clientVersion;
    }

    /** Bit set of {@code KnpProtocol.CAP_*}. */
    public int getCapabilities() {
        return capabilities;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
