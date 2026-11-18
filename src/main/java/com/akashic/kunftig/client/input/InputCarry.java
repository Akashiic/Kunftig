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
 * Here, at the start of the player's own tick (after every client tick handler), a new object receives what the last
 * one held. The values are only
 * the "before" of the comparison: the object's own update overwrites them in the same tick. Client thread.
 */
public final class InputCarry {

    public static final InputCarry INSTANCE = new InputCarry();

    private EntityClientPlayerMP owner;
    private MovementInput last;

    private InputCarry() {}

    /** The start of the local player's own tick: after every client tick handler, right before the comparison. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || event.player != mc.thePlayer) {
            return;
        }
        EntityClientPlayerMP player = mc.thePlayer;
        MovementInput now = player.movementInput;
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
