package com.akashic.kunftig.mixin.mixins.posture;

import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.play.server.S01PacketJoinGame;
import net.minecraft.network.play.server.S3FPacketCustomPayload;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.akashic.kunftig.client.posture.PostureClient;
import com.akashic.kunftig.posture.PostureWire;

/** Postures (API 15): register the channel on join and take the server's messages. */
@Mixin(NetHandlerPlayClient.class)
public abstract class NetHandlerPlayClientPostureMixin {

    @Inject(method = "handleJoinGame", at = @At("TAIL"))
    private void kunftig$postureJoinGame(S01PacketJoinGame packet, CallbackInfo ci) {
        if (!PostureClient.disabled()) {
            PostureClient.INSTANCE.onJoinGame((NetHandlerPlayClient) (Object) this);
        }
    }

    @Inject(method = "handleCustomPayload", at = @At("HEAD"), cancellable = true)
    private void kunftig$posturePayload(S3FPacketCustomPayload packet, CallbackInfo ci) {
        if (PostureWire.CHANNEL.equals(packet.func_149169_c())) {
            if (!PostureClient.disabled()) {
                PostureClient.INSTANCE.onPayload(packet.func_149168_d());
            }
            ci.cancel();
        }
    }
}
