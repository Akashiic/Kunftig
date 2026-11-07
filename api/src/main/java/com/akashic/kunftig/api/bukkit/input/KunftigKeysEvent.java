package com.akashic.kunftig.api.bukkit.input;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

/**
 * The keys a player holds changed ({@link KunftigInput#KEYS} bound): SlashBlade's {@code InputCommandEvent}. Fired on
 * the main thread, once per message. Bits are {@link KunftigInput#KEY_FORWARD} ...
 */
public final class KunftigKeysEvent extends PlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final int before;
    private final int keys;
    private final int clientTick;

    public KunftigKeysEvent(Player player, int before, int keys, int clientTick) {
        super(player);
        this.before = before;
        this.keys = keys;
        this.clientTick = clientTick;
    }

    /** The keys held before. */
    public int getBefore() {
        return before;
    }

    /** The keys held now. */
    public int getKeys() {
        return keys;
    }

    /** Whether {@code key} went down with this change. */
    public boolean pressed(int key) {
        return (keys & key) != 0 && (before & key) == 0;
    }

    /** Whether {@code key} came up with this change. */
    public boolean released(int key) {
        return (keys & key) == 0 && (before & key) != 0;
    }

    public int getClientTick() {
        return clientTick;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
