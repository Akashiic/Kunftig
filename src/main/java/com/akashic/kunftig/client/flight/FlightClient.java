package com.akashic.kunftig.client.flight;

import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.client.C17PacketCustomPayload;

import com.akashic.kunftig.flight.FlightEnvelope;
import com.akashic.kunftig.flight.FlightLock;

/**
 * The flight lock on the client (API 9): while the server says so, the local player does not fly (capabilities off)
 * and gains no upward speed other than a jump or a push ({@link FlightEnvelope}). Applied at the start of the
 * player's movement each tick ({@code EntityPlayerFlightMixin}). Client thread.
 */
public final class FlightClient {

    public static final FlightClient INSTANCE = new FlightClient();

    private volatile boolean locked;
    private final FlightEnvelope envelope = new FlightEnvelope();

    private FlightClient() {}

    public boolean locked() {
        return locked;
    }

    /** A fresh connection: free until the server says otherwise; register the channel. */
    public void onJoinGame(NetHandlerPlayClient handler) {
        locked = false;
        envelope.reset();
        handler.addToSendQueue(new C17PacketCustomPayload("REGISTER", FlightLock.registration()));
    }

    public void onPayload(byte[] data) {
        boolean lock = data != null && data.length > 0 && data[0] != 0;
        if (lock != locked) {
            envelope.reset();
        }
        locked = lock;
    }

    /** At the start of the local player's movement this tick. */
    public void beforeMove(EntityPlayer player) {
        if (!locked || player != Minecraft.getMinecraft().thePlayer || player.capabilities.isCreativeMode) {
            return;
        }
        player.capabilities.isFlying = false;
        player.capabilities.allowFlying = false;
        boolean exempt = player.isInWater() || player.handleLavaMovement()
            || player.isOnLadder()
            || player.ridingEntity != null;
        player.motionY = envelope.next(player.onGround, player.motionY, exempt);
    }
}
