package com.akashic.kunftig.api.bukkit.input;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

/**
 * A key of a {@link KunftigInput} action was pressed or released on the player's client. Fired on the main thread,
 * once per message, in the order the client sent them. Only bound actions arrive ({@link KunftigInput#bind}).
 */
public final class KunftigInputEvent extends PlayerEvent {

    /** Movement keys held when the action key changed (bits of {@link #getMovement()}). */
    public static final int FORWARD = 1;
    public static final int BACK = 1 << 1;
    public static final int LEFT = 1 << 2;
    public static final int RIGHT = 1 << 3;
    public static final int JUMP = 1 << 4;
    public static final int SNEAK = 1 << 5;
    public static final int SPRINT = 1 << 6;

    private static final HandlerList HANDLERS = new HandlerList();

    private final String action;
    private final boolean pressed;
    private final int sequence;
    private final int clientTick;
    private final int movement;
    private final float yaw;
    private final float pitch;

    public KunftigInputEvent(Player player, String action, boolean pressed, int sequence, int clientTick,
        int movement, float yaw, float pitch) {
        super(player);
        this.action = action;
        this.pressed = pressed;
        this.sequence = sequence;
        this.clientTick = clientTick;
        this.movement = movement;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    /** One of {@link KunftigInput#ACTIONS}. */
    public String getAction() {
        return action;
    }

    /** True: the key went down; false: it came up. */
    public boolean isPressed() {
        return pressed;
    }

    /** Grows by one per message of this connection. */
    public int getSequence() {
        return sequence;
    }

    /**
     * The client's tick counter when the key changed: the gap between two events of the same player, in client ticks,
     * is how far apart he pressed them, whatever the network did in between (for timing windows).
     */
    public int getClientTick() {
        return clientTick;
    }

    /** The movement keys held, as bits ({@link #FORWARD} ...). */
    public int getMovement() {
        return movement;
    }

    public boolean isForward() {
        return (movement & FORWARD) != 0;
    }

    public boolean isBack() {
        return (movement & BACK) != 0;
    }

    public boolean isLeft() {
        return (movement & LEFT) != 0;
    }

    public boolean isRight() {
        return (movement & RIGHT) != 0;
    }

    public boolean isJumping() {
        return (movement & JUMP) != 0;
    }

    public boolean isSneaking() {
        return (movement & SNEAK) != 0;
    }

    public boolean isSprinting() {
        return (movement & SPRINT) != 0;
    }

    /** Where the client was looking when the key changed (degrees, Minecraft's convention). */
    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
