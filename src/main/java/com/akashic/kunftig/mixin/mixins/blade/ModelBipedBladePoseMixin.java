package com.akashic.kunftig.mixin.mixins.blade;

import net.minecraft.client.model.ModelBiped;
import net.minecraft.entity.Entity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.akashic.kunftig.client.blade.BladePose;

/** Blades: the player's body takes the blade's player motion after vanilla posed it. */
@Mixin(ModelBiped.class)
public abstract class ModelBipedBladePoseMixin {

    @Inject(method = "setRotationAngles", at = @At("TAIL"))
    private void kunftig$bladePose(float limbSwing, float limbSwingAmount, float age, float yaw, float pitch,
        float scale, Entity entity, CallbackInfo ci) {
        BladePose.afterVanilla((ModelBiped) (Object) this, entity);
    }
}
