package com.akashic.kunftig.mixin.mixins.hologram;

import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.play.server.S01PacketJoinGame;
import net.minecraft.network.play.server.S07PacketRespawn;
import net.minecraft.network.play.server.S3FPacketCustomPayload;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.akashic.kunftig.client.hologram.HologramClient;
import com.akashic.kunftig.hologram.HologramWire;

/**
 * Holograms: register the channel on join, forget them on respawn or dimension change, and take the hologram plugin
 * messages (vanilla would hand an unknown channel to FML, which ignores it).
 */
@Mixin(NetHandlerPlayClient.class)
public abstract class NetHandlerPlayClientHologramMixin {

    @Inject(method = "handleJoinGame", at = @At("TAIL"))
    private void kunftig$hologramsJoinGame(S01PacketJoinGame packet, CallbackInfo ci) {
        HologramClient.INSTANCE.onJoinGame((NetHandlerPlayClient) (Object) this);
    }

    @Inject(method = "handleRespawn", at = @At("HEAD"))
    private void kunftig$hologramsRespawn(S07PacketRespawn packet, CallbackInfo ci) {
        HologramClient.INSTANCE.clear();
    }

    @Inject(method = "handleCustomPayload", at = @At("HEAD"), cancellable = true)
    private void kunftig$hologramPayload(S3FPacketCustomPayload packet, CallbackInfo ci) {
        if (HologramWire.CHANNEL.equals(packet.func_149169_c())) {
            HologramClient.INSTANCE.onPayload(packet.func_149168_d());
            ci.cancel();
        }
    }
}
