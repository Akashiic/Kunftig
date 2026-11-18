package com.akashic.kunftig.client.input;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.util.MovementInput;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Keeps the local player's held keys when a mod swaps {@code player.movementInput} for a new object.
 * <p>
 * The game finds a double tap by comparing the input before and after it is updated in the player's tick: jump
 * that was up and is now down, twice within seven ticks, toggles creative flight, and the same with forward starts
 * a sprint. A new input object is born with everything up. Two mods of the TEOF pack each insist on their own
 * object: the Aether puts an {@code AetherMovementInput} there at the end of every client tick unless it finds one,
 * and the pool table mod wraps whatever it finds at the start of every tick unless it is its own wrapper. Together
 * the player gets a blank object every tick, so a held Space reads as a fresh press each tick and flight toggles on
 * and off for as long as it is held (and a held W as a double tap, which sprints by itself).
 * <p>
 * Here a new object receives what the last one held as soon as it is seen: at both ends of the client tick and at the
 * start of the player's own tick (after every client tick handler). Inside a tick the values are only the "before"
 * of the comparison (the object's own update overwrites them); between ticks they are what the frames read. Client
 * thread.
 */
public final class InputCarry {

    public static final InputCarry INSTANCE = new InputCarry();

    private EntityClientPlayerMP owner;
    private MovementInput last;

    private InputCarry() {}

    /** The start of the local player's own tick: after every client tick handler, right before the comparison. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc != null && event.player == mc.thePlayer) {
                look(mc.thePlayer);
            }
        }
    }

    /**
     * Both ends of the client tick, after the other handlers: the object a mod has just put there is what the game
     * reads until the next tick. Between ticks the frames are drawn, and {@code isSneaking()} of the local player is
     * {@code movementInput.sneak}: with a blank object there, a player holding Shift was drawn standing (and sneaking
     * again within each tick), and anything asking for his held keys between ticks was told "none".
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        look(mc == null ? null : mc.thePlayer);
    }

    private void look(EntityClientPlayerMP player) {
        MovementInput now = player == null ? null : player.movementInput;
        if (now == null) {
            owner = null;
            last = null;
            return;
        }
        if (player == owner && last != null && now != last) {
            carry(last, now);
        }
        owner = player;
        last = now;
    }

    /** Gives a new input object the state of the one it replaced. */
    static void carry(MovementInput from, MovementInput to) {
        to.moveForward = from.moveForward;
        to.moveStrafe = from.moveStrafe;
        to.jump = from.jump;
        to.sneak = from.sneak;
    }
}
