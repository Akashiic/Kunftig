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
import com.akashic.kunftig.rodmodel.RodArmPose;

/**
 * 3D rods: the arms follow the rod's state ({@code animation.braco.<state>}, issue #13). Added after vanilla's pose, so
 * walking, sneaking and the swing still show; vanilla's lift for a held item ({@code -PI/10}) is taken back, because
 * the arm animations already hold the rod forward (as in the previews). Armor models get the same pose.
 * <p>
 * The left arm ({@code braco_esquerdo}) only when the off hand is empty (vanilla's {@code heldItemLeft} and Backhand's
 * off hand): then the rod is held with both hands where the state says so ({@code duas_maos}), and vanilla's walking
 * swing leaves both arms, or the hands would let go of the rod while walking. The Akashic Player Backport touches the
 * arms only for its BLOCK pose (and only while they hold vanilla's values) and the legs at the end of this method, so
 * the two do not compete.
 */
@Mixin(ModelBiped.class)
public abstract class ModelBipedRodArmMixin {

    @Shadow
    public ModelRenderer bipedRightArm;

    @Shadow
    public ModelRenderer bipedLeftArm;

    @Shadow
    public int heldItemRight;

    @Shadow
    public int heldItemLeft;

    @Shadow
    public boolean aimedBow;

    @Inject(method = "setRotationAngles", at = @At("TAIL"))
    private void kunftig$rodArm(float swing, float swingAmount, float age, float yaw, float pitch, float scale,
        Entity entity, CallbackInfo ci) {
        if (heldItemRight == 0 || aimedBow || !(entity instanceof EntityPlayer)) {
            return;
        }
        EntityPlayer player = (EntityPlayer) entity;
        float[] r = RodModels.INSTANCE.armRotation(player);
        if (r == null) {
            return;
        }
        float[] l = heldItemLeft == 0 && !RodModels.offhandBusy(player)
            ? RodModels.INSTANCE.armRotation(player, RodModels.LEFT_ARM_BONE)
            : null;
        if (l != null && RodModels.INSTANCE.twoHanded(player)) {
            bipedRightArm.rotateAngleX -= RodArmPose.rightWalkSwing(swing, swingAmount, heldItemRight);
            bipedLeftArm.rotateAngleX -= RodArmPose.leftWalkSwing(swing, swingAmount, heldItemLeft);
        }
        bipedRightArm.rotateAngleX += (float) Math.PI / 10F * heldItemRight + r[0];
        bipedRightArm.rotateAngleY += r[1];
        bipedRightArm.rotateAngleZ += r[2];
        if (l != null) {
            bipedLeftArm.rotateAngleX += l[0];
            bipedLeftArm.rotateAngleY += l[1];
            bipedLeftArm.rotateAngleZ += l[2];
        }
    }
}
