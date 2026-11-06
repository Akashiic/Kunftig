package com.akashic.kunftig.mixin.mixins.blade;

import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.play.server.S01PacketJoinGame;
import net.minecraft.network.play.server.S07PacketRespawn;
import net.minecraft.network.play.server.S3FPacketCustomPayload;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.akashic.kunftig.blade.BladeWire;
import com.akashic.kunftig.client.blade.BladeClient;

/** Blades: register the channel on join, forget states on respawn, take the blade messages. */
@Mixin(NetHandlerPlayClient.class)
public abstract class NetHandlerPlayClientBladeMixin {

    @Inject(method = "handleJoinGame", at = @At("TAIL"))
    private void kunftig$bladeJoinGame(S01PacketJoinGame packet, CallbackInfo ci) {
        BladeClient.INSTANCE.onJoinGame((NetHandlerPlayClient) (Object) this);
    }

    @Inject(method = "handleRespawn", at = @At("HEAD"))
    private void kunftig$bladeRespawn(S07PacketRespawn packet, CallbackInfo ci) {
        BladeClient.INSTANCE.onRespawn();
    }

    @Inject(method = "handleCustomPayload", at = @At("HEAD"), cancellable = true)
    private void kunftig$bladePayload(S3FPacketCustomPayload packet, CallbackInfo ci) {
        if (BladeWire.CHANNEL.equals(packet.func_149169_c())) {
            BladeClient.INSTANCE.onPayload(packet.func_149168_d());
            ci.cancel();
        }
    }
}
