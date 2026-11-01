package com.akashic.kunftig.mixin.mixins.nametag;

import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.play.server.S01PacketJoinGame;
import net.minecraft.network.play.server.S3FPacketCustomPayload;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.akashic.kunftig.client.nametag.NametagClient;

/**
 * Session start and plugin-message intake.
 * <p>
 * Plugin messages on channels FML does not know reach {@code handleCustomPayload} on the client thread
 * ({@code NetworkDispatcher.handleClientSideCustomPacket} returns false for them). Kunftig deliberately registers
 * no FML channel: a hybrid server that has the mod installed would otherwise route the client's messages to FML.
 */
@Mixin(NetHandlerPlayClient.class)
public abstract class NetHandlerPlayClientMixin {

    @Inject(method = "handleJoinGame", at = @At("TAIL"))
    private void kunftig$joinGame(S01PacketJoinGame packet, CallbackInfo ci) {
        NametagClient.onJoinGame((NetHandlerPlayClient) (Object) this);
    }

    @Inject(method = "handleCustomPayload", at = @At("HEAD"), cancellable = true)
    private void kunftig$customPayload(S3FPacketCustomPayload packet, CallbackInfo ci) {
        if (NametagClient
            .onCustomPayload((NetHandlerPlayClient) (Object) this, packet.func_149169_c(), packet.func_149168_d())) {
            ci.cancel();
        }
    }
}
