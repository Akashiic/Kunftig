package com.akashic.kunftig.mixin.mixins.rodmodel;

import net.minecraft.client.entity.EntityClientPlayerMP;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.akashic.kunftig.client.rodmodel.RodModels;

/**
 * Attack prediction (API 9): the local player's swing, the very call that sends the server the arm swing a plugin
 * sees as {@code PlayerAnimationEvent}, so client and server count the same clicks.
 */
@Mixin(EntityClientPlayerMP.class)
public abstract class EntityClientPlayerMPSwingMixin {

    @Inject(method = "swingItem", at = @At("HEAD"))
    private void kunftig$predictAttack(CallbackInfo ci) {
        RodModels.INSTANCE.onLocalSwing((EntityClientPlayerMP) (Object) this);
    }
}
