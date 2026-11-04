package com.akashic.kunftig.mixin.mixins.rodmodel;

import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.akashic.kunftig.client.rodmodel.RodModels;

/**
 * 3D rods: the right arm follows the rod's state ({@code animation.braco.<state>}, issue #13). Added after vanilla's
 * pose, so walking, sneaking and the swing still show; vanilla's lift for a held item ({@code -PI/10}) is taken back,
 * because the arm animations already hold the rod forward (as in the previews). Armor models get the same pose.
 */
@Mixin(ModelBiped.class)
public abstract class ModelBipedRodArmMixin {

    @Shadow
    public ModelRenderer bipedRightArm;

    @Shadow
    public int heldItemRight;

    @Shadow
    public boolean aimedBow;

    @Inject(method = "setRotationAngles", at = @At("TAIL"))
    private void kunftig$rodArm(float swing, float swingAmount, float age, float yaw, float pitch, float scale,
        Entity entity, CallbackInfo ci) {
        if (heldItemRight == 0 || aimedBow || !(entity instanceof EntityPlayer)) {
            return;
        }
        float[] r = RodModels.INSTANCE.armRotation((EntityPlayer) entity);
        if (r != null) {
            bipedRightArm.rotateAngleX += (float) Math.PI / 10F * heldItemRight + r[0];
            bipedRightArm.rotateAngleY += r[1];
            bipedRightArm.rotateAngleZ += r[2];
        }
    }
}
