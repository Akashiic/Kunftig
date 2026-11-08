package com.akashic.kunftig.mixin.mixins.flight;

import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.play.server.S01PacketJoinGame;
import net.minecraft.network.play.server.S3FPacketCustomPayload;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.akashic.kunftig.client.flight.FlightClient;
import com.akashic.kunftig.flight.FlightLock;

/** Flight lock: register the channel on join and take the server's lock messages. */
@Mixin(NetHandlerPlayClient.class)
public abstract class NetHandlerPlayClientFlightMixin {

    @Inject(method = "handleJoinGame", at = @At("TAIL"))
    private void kunftig$flightJoinGame(S01PacketJoinGame packet, CallbackInfo ci) {
        FlightClient.INSTANCE.onJoinGame((NetHandlerPlayClient) (Object) this);
    }

    @Inject(method = "handleCustomPayload", at = @At("HEAD"), cancellable = true)
    private void kunftig$flightPayload(S3FPacketCustomPayload packet, CallbackInfo ci) {
        if (FlightLock.CHANNEL.equals(packet.func_149169_c())) {
            FlightClient.INSTANCE.onPayload(packet.func_149168_d());
            ci.cancel();
        }
    }
}
