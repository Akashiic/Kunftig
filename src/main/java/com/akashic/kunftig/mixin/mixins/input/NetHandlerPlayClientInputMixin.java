package com.akashic.kunftig.mixin.mixins.input;

import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.play.server.S01PacketJoinGame;
import net.minecraft.network.play.server.S3FPacketCustomPayload;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.akashic.kunftig.client.input.InputClient;
import com.akashic.kunftig.input.InputWire;

/** Input channel (API 9): register it on join and take the server's BIND messages. */
@Mixin(NetHandlerPlayClient.class)
public abstract class NetHandlerPlayClientInputMixin {

    @Inject(method = "handleJoinGame", at = @At("TAIL"))
    private void kunftig$inputJoinGame(S01PacketJoinGame packet, CallbackInfo ci) {
        if (!InputClient.disabled()) {
            InputClient.INSTANCE.onJoinGame((NetHandlerPlayClient) (Object) this);
        }
    }

    @Inject(method = "handleCustomPayload", at = @At("HEAD"), cancellable = true)
    private void kunftig$inputPayload(S3FPacketCustomPayload packet, CallbackInfo ci) {
        if (InputWire.CHANNEL.equals(packet.func_149169_c())) {
            if (!InputClient.disabled()) {
                InputClient.INSTANCE.onPayload(packet.func_149168_d());
            }
            ci.cancel();
        }
    }
}
