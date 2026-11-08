package com.akashic.kunftig.mixin.mixins.flight;

import net.minecraft.entity.player.EntityPlayer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.akashic.kunftig.client.flight.FlightClient;

/**
 * Flight lock: at the start of the player's movement (after the items' ticks, before EntityPlayer checks whether it
 * flies and moves it), switch flying off and cut thrust. Client only; only the local player is touched.
 */
@Mixin(EntityPlayer.class)
public abstract class EntityPlayerFlightMixin {

    @Inject(method = "moveEntityWithHeading", at = @At("HEAD"))
    private void kunftig$flightLock(float strafe, float forward, CallbackInfo ci) {
        EntityPlayer self = (EntityPlayer) (Object) this;
        if (self.worldObj != null && self.worldObj.isRemote) {
            FlightClient.INSTANCE.beforeMove(self);
        }
    }
}
