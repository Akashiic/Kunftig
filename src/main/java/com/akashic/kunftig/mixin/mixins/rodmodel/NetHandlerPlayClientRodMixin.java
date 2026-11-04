package com.akashic.kunftig.mixin.mixins.rodmodel;

import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.play.server.S01PacketJoinGame;
import net.minecraft.network.play.server.S07PacketRespawn;
import net.minecraft.network.play.server.S3FPacketCustomPayload;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.akashic.kunftig.client.rodmodel.RodModels;
import com.akashic.kunftig.rodmodel.RodWire;

/**
 * 3D rods: register {@link RodWire#CHANNEL} on join, forget the states on respawn or dimension change, and take the
 * rod state messages (vanilla would hand an unknown channel to FML, which ignores it).
 */
@Mixin(NetHandlerPlayClient.class)
public abstract class NetHandlerPlayClientRodMixin {

    @Inject(method = "handleJoinGame", at = @At("TAIL"))
    private void kunftig$rodsJoinGame(S01PacketJoinGame packet, CallbackInfo ci) {
        RodModels.INSTANCE.onJoinGame((NetHandlerPlayClient) (Object) this);
    }

    @Inject(method = "handleRespawn", at = @At("HEAD"))
    private void kunftig$rodsRespawn(S07PacketRespawn packet, CallbackInfo ci) {
        RodModels.INSTANCE.onRespawn();
    }

    @Inject(method = "handleCustomPayload", at = @At("HEAD"), cancellable = true)
    private void kunftig$rodPayload(S3FPacketCustomPayload packet, CallbackInfo ci) {
        if (RodWire.CHANNEL.equals(packet.func_149169_c())) {
            RodModels.INSTANCE.onPayload(packet.func_149168_d());
            ci.cancel();
        }
    }
}
