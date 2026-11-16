package com.akashic.kunftig.mixin.mixins.posture;

import net.minecraft.client.model.ModelBiped;
import net.minecraft.entity.Entity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.akashic.kunftig.client.posture.PostureClient;

/**
 * Postures (API 15) on the vanilla biped, after vanilla posed it: one who sits takes vanilla's riding pose (the legs
 * forward, the arms a little), one who lies rests (the head straight, the limbs along the body). The armour's bipeds
 * are posed by the same method.
 */
@Mixin(ModelBiped.class)
public abstract class ModelBipedPostureMixin {

    @Inject(method = "setRotationAngles", at = @At("TAIL"), require = 0)
    private void kunftig$posture(float limbSwing, float limbSwingAmount, float age, float yaw, float pitch, float scale,
        Entity entity, CallbackInfo ci) {
        PostureClient.Held h = PostureClient.INSTANCE.of(entity);
        if (h == null) {
            return;
        }
        ModelBiped m = (ModelBiped) (Object) this;
        if (h.sits()) {
            if (!m.isRiding) { // vanilla already sat one on a vehicle
                m.bipedRightArm.rotateAngleX += -(float) Math.PI / 5F;
                m.bipedLeftArm.rotateAngleX += -(float) Math.PI / 5F;
                m.bipedRightLeg.rotateAngleX = -(float) Math.PI * 2F / 5F;
                m.bipedLeftLeg.rotateAngleX = -(float) Math.PI * 2F / 5F;
                m.bipedRightLeg.rotateAngleY = (float) Math.PI / 10F;
                m.bipedLeftLeg.rotateAngleY = -(float) Math.PI / 10F;
            }
        } else {
            m.bipedHead.rotateAngleX = 0;
            m.bipedHead.rotateAngleY = 0;
            m.bipedHeadwear.rotateAngleX = 0;
            m.bipedHeadwear.rotateAngleY = 0;
            m.bipedRightArm.rotateAngleX = 0;
            m.bipedLeftArm.rotateAngleX = 0;
            m.bipedRightArm.rotateAngleZ = 0.05F;
            m.bipedLeftArm.rotateAngleZ = -0.05F;
            m.bipedRightLeg.rotateAngleX = 0;
            m.bipedLeftLeg.rotateAngleX = 0;
            m.bipedRightLeg.rotateAngleY = 0;
            m.bipedLeftLeg.rotateAngleY = 0;
        }
    }
}
