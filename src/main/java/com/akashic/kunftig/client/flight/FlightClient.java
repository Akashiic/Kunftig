package com.akashic.kunftig.client.flight;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.network.play.client.C17PacketCustomPayload;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;

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

    /**
     * Any liquid or cobweb in the blocks the player's box touches, a little wider and taller. Vanilla's
     * {@code isInWater} shrinks the box by 0.4 above and below before looking, so a short swimming box (modern swimming
     * or crawling from other mods, 0.6 high) never counts as in water.
     */
    private static boolean touchesLiquidOrWeb(EntityPlayer player) {
        AxisAlignedBB bb = player.boundingBox;
        if (bb == null || player.worldObj == null) {
            return false;
        }
        int x0 = MathHelper.floor_double(bb.minX - 0.1), x1 = MathHelper.floor_double(bb.maxX + 0.1);
        int y0 = MathHelper.floor_double(bb.minY - 0.2), y1 = MathHelper.floor_double(bb.maxY + 0.1);
        int z0 = MathHelper.floor_double(bb.minZ - 0.1), z1 = MathHelper.floor_double(bb.maxZ + 0.1);
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    Block b = player.worldObj.getBlock(x, y, z);
                    if (b.getMaterial()
                        .isLiquid() || b == Blocks.web) {
                        return true;
                    }
                }
            }
        }
        return false;
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
            || player.ridingEntity != null
            || touchesLiquidOrWeb(player);
        player.motionY = envelope.next(player.onGround, player.motionY, exempt);
    }
}
