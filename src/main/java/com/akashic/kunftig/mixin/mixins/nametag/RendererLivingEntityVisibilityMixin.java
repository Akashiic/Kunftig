package com.akashic.kunftig.mixin.mixins.nametag;

import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.akashic.kunftig.client.nametag.NametagClient;

/**
 * 1.8+ team option {@code nametagVisibility}. {@code func_110813_b} ("can render name") guards the whole label in
 * {@code passSpecialRender}, including the below-name score. Only ever hides a nametag, never forces one visible,
 * so it composes with other mods injecting here (NecroTempus does, for its glow pass).
 */
@Mixin(RendererLivingEntity.class)
public abstract class RendererLivingEntityVisibilityMixin {

    @Inject(method = "func_110813_b", at = @At("HEAD"), cancellable = true)
    private void kunftig$nametagVisibility(EntityLivingBase entity, CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof EntityPlayer && !NametagClient.isNametagVisible((EntityPlayer) entity)) {
            cir.setReturnValue(false);
        }
    }
}
