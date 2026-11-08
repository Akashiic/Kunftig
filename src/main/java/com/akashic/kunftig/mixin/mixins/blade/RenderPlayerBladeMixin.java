package com.akashic.kunftig.mixin.mixins.blade;

import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.RenderPlayer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.akashic.kunftig.client.blade.BladePose;

/** Blades: the whole body's motion, at the end of the player's corpse rotation (frame F, before the model flip). */
@Mixin(RenderPlayer.class)
public abstract class RenderPlayerBladeMixin {

    @Inject(method = "rotateCorpse(Lnet/minecraft/client/entity/AbstractClientPlayer;FFF)V", at = @At("TAIL"))
    private void kunftig$bladeBody(AbstractClientPlayer player, float age, float yaw, float partial, CallbackInfo ci) {
        BladePose.afterRotateCorpse(player, partial);
    }
}
