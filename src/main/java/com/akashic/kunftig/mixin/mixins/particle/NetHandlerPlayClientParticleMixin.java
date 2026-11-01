package com.akashic.kunftig.mixin.mixins.particle;

import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.play.server.S01PacketJoinGame;
import net.minecraft.network.play.server.S2APacketParticles;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.akashic.kunftig.client.particle.ParticleClient;

/**
 * Kunftig particles: register the channel on join, and take over {@code kunftig:*} particle packets (vanilla would
 * spawn nothing for them). Every other particle packet runs the vanilla code untouched.
 */
@Mixin(NetHandlerPlayClient.class)
public abstract class NetHandlerPlayClientParticleMixin {

    @Inject(method = "handleJoinGame", at = @At("TAIL"))
    private void kunftig$particlesJoinGame(S01PacketJoinGame packet, CallbackInfo ci) {
        ParticleClient.onJoinGame((NetHandlerPlayClient) (Object) this);
    }

    @Inject(method = "handleParticles", at = @At("HEAD"), cancellable = true)
    private void kunftig$particles(S2APacketParticles packet, CallbackInfo ci) {
        if (ParticleClient.onParticles(packet)) {
            ci.cancel();
        }
    }
}
