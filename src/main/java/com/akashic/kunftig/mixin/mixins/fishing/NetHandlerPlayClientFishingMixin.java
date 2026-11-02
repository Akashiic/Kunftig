package com.akashic.kunftig.mixin.mixins.fishing;

import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.play.server.S01PacketJoinGame;
import net.minecraft.network.play.server.S3FPacketCustomPayload;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.akashic.kunftig.fishing.ModernBobberMode;

/** Client: which bobber physics this server uses ({@link ModernBobberMode}); every connection starts vanilla. */
@Mixin(NetHandlerPlayClient.class)
public abstract class NetHandlerPlayClientFishingMixin {

    @Inject(method = "handleJoinGame", at = @At("HEAD"))
    private void kunftig$fishingJoinGame(S01PacketJoinGame packet, CallbackInfo ci) {
        ModernBobberMode.reset();
    }

    @Inject(method = "handleCustomPayload", at = @At("HEAD"), cancellable = true)
    private void kunftig$fishingPayload(S3FPacketCustomPayload packet, CallbackInfo ci) {
        if (ModernBobberMode.CHANNEL.equals(packet.func_149169_c())) {
            ModernBobberMode.receive(packet.func_149168_d());
            ci.cancel();
        }
    }
}
