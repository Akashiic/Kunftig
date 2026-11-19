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
    /** The object left on the player when the last client tick ended, and for how many ticks it has been replaced. */
    private MovementInput settled;
    private int churn;
    /** Ticks in a row with a replaced object before this class steps in (a mod that wraps once is left alone). */
    private static final int CHURN = 3;

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
     * The start of the client tick, after the other handlers: the object a mod has just put there (the pool table's
     * wrapper) is what handlers of the player's tick read.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityClientPlayerMP player = mc == null ? null : mc.thePlayer;
        look(player);
        if (event.phase == TickEvent.Phase.END) {
            settled = player == null ? null : player.movementInput;
        }
    }

    /**
     * The end of the client tick, before the other handlers. Mods that act on a key going down read the input here
     * (EnderIO's travel anchors and OpenBlocks' elevators: jump and sneak), and some run after the mod that swaps
     * the object at this very moment (the Aether): they were handed a blank one, tick after tick, and Shift and
     * Space did nothing on an anchor or an elevator.
     * <p>
     * So once the object is seen to change every tick, the one that was there when the last tick ended goes back
     * before anybody else looks, with the state of the one being taken out. If the mod that swaps at the end of the
     * tick finds its own object, it leaves it alone, and everyone after it reads real keys; if it swaps it anyway,
     * nothing was lost ({@link #onClientTick} carries the state over as before). The wrapper comes back at the start
     * of the next tick, from its own mod.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void beforeClientTickEnds(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        EntityClientPlayerMP player = mc == null ? null : mc.thePlayer;
        MovementInput now = player == null ? null : player.movementInput;
        if (now == null || player != owner || settled == null) {
            churn = 0;
            return;
        }
        if (now == settled) {
            churn = 0;
            return;
        }
        if (++churn > CHURN) {
            carry(now, settled);
            player.movementInput = settled;
            last = settled;
        }
    }

    private void look(EntityClientPlayerMP player) {
        MovementInput now = player == null ? null : player.movementInput;
        if (now == null) {
            owner = null;
            last = null;
            settled = null;
            return;
        }
        if (player != owner) {
            settled = null;
        } else if (last != null && now != last) {
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
