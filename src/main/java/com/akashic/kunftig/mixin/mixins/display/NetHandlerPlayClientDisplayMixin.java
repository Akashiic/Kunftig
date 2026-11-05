package com.akashic.kunftig.mixin.mixins.display;

import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.play.server.S01PacketJoinGame;
import net.minecraft.network.play.server.S07PacketRespawn;
import net.minecraft.network.play.server.S3FPacketCustomPayload;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.akashic.kunftig.client.display.DisplayClient;
import com.akashic.kunftig.display.DisplayWire;

/**
 * Displays: register the channel on join, forget them on respawn or dimension change, and take the display plugin
 * messages (vanilla would hand an unknown channel to FML, which ignores it).
 */
@Mixin(NetHandlerPlayClient.class)
public abstract class NetHandlerPlayClientDisplayMixin {

    @Inject(method = "handleJoinGame", at = @At("TAIL"))
    private void kunftig$displaysJoinGame(S01PacketJoinGame packet, CallbackInfo ci) {
        DisplayClient.INSTANCE.onJoinGame((NetHandlerPlayClient) (Object) this);
    }

    @Inject(method = "handleRespawn", at = @At("HEAD"))
    private void kunftig$displaysRespawn(S07PacketRespawn packet, CallbackInfo ci) {
        DisplayClient.INSTANCE.clear();
    }

    @Inject(method = "handleCustomPayload", at = @At("HEAD"), cancellable = true)
    private void kunftig$displayPayload(S3FPacketCustomPayload packet, CallbackInfo ci) {
        if (DisplayWire.CHANNEL.equals(packet.func_149169_c())) {
            DisplayClient.INSTANCE.onPayload(packet.func_149168_d());
            ci.cancel();
        }
    }
}
