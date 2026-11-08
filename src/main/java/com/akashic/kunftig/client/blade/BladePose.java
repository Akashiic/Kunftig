package com.akashic.kunftig.client.blade;

import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;

import org.lwjgl.opengl.GL11;

import com.akashic.kunftig.blade.BladeIndex;
import com.akashic.kunftig.blade.BladeWire;
import com.akashic.kunftig.blade.BodyPose;

/**
 * The player body posed by the blade's player motion, as SlashBlade's PlayerAnimator path does: after vanilla sets the
 * angles, the arms take the motion's rotation, the head and torso add it, the legs add it (or take it in aerial
 * moves), every part's pivot moves by the motion's offset, and the whole model turns about a pivot 0.7 block up. The
 * model is shared by every player, and 1.7.10 does not reset some pivots each frame, so those are put back first.
 */
public final class BladePose {

    /** Models posed on their last call: their extra pivots must go back to vanilla before the next use. */
    private static final Map<ModelBiped, Boolean> POSED = new WeakHashMap<>();

    private BladePose() {}

    /** The pose of a player's body clip now, or null when he plays none (vanilla body). */
    static BodyPose pose(Entity e, float partial) {
        if (!(e instanceof EntityPlayer) || !BladeClient.INSTANCE.active()) {
            return null;
        }
        BladeClient c = BladeClient.INSTANCE;
        BladeIndex.Blade blade = c.resolve(((EntityPlayer) e).getHeldItem());
        if (blade == null || blade.playerMotion == null) {
            return null;
        }
        return c.bodyPose(e, c.motion(blade.playerMotion), partial);
    }

    /** End of {@code ModelBiped.setRotationAngles}. */
    public static void afterVanilla(ModelBiped m, Entity e) {
        if (POSED.remove(m) != null) {
            reset(m);
        }
        BodyPose p = pose(e, BladeClient.INSTANCE.partialTicks());
        if (p == null) {
            return;
        }
        BladeWire.Body clip = BladeClient.INSTANCE.body(e);
        boolean blendLegs = clip == null || clip.blendLegs;
        add(m.bipedHead, p, BodyPose.HEAD, true);
        add(m.bipedBody, p, BodyPose.TORSO, true);
        add(m.bipedRightArm, p, BodyPose.RIGHT_ARM, false);
        add(m.bipedLeftArm, p, BodyPose.LEFT_ARM, false);
        add(m.bipedRightLeg, p, BodyPose.RIGHT_LEG, blendLegs);
        add(m.bipedLeftLeg, p, BodyPose.LEFT_LEG, blendLegs);
        copy(m.bipedHead, m.bipedHeadwear);
        POSED.put(m, Boolean.TRUE);
    }

    private static void add(ModelRenderer part, BodyPose p, int i, boolean blend) {
        float[] r = p.rotation[i];
        part.rotateAngleX = (blend ? part.rotateAngleX : 0) + r[0];
        part.rotateAngleY = (blend ? part.rotateAngleY : 0) + r[1];
        part.rotateAngleZ = (blend ? part.rotateAngleZ : 0) + r[2];
        float[] o = p.offset[i];
        part.rotationPointX += o[0];
        part.rotationPointY += o[1];
        part.rotationPointZ += o[2];
    }

    private static void copy(ModelRenderer from, ModelRenderer to) {
        to.rotateAngleX = from.rotateAngleX;
        to.rotateAngleY = from.rotateAngleY;
        to.rotateAngleZ = from.rotateAngleZ;
        to.rotationPointX = from.rotationPointX;
        to.rotationPointY = from.rotationPointY;
        to.rotationPointZ = from.rotationPointZ;
    }

    /** Puts back what 1.7.10's setRotationAngles does not set every frame. */
    private static void reset(ModelBiped m) {
        m.bipedHead.rotationPointX = 0;
        m.bipedHead.rotationPointZ = 0;
        m.bipedHead.rotateAngleZ = 0;
        m.bipedHeadwear.rotationPointX = 0;
        m.bipedHeadwear.rotationPointZ = 0;
        m.bipedHeadwear.rotateAngleZ = 0;
        m.bipedBody.rotationPointX = 0;
        m.bipedBody.rotationPointY = 0;
        m.bipedBody.rotationPointZ = 0;
        m.bipedBody.rotateAngleZ = 0;
        m.bipedRightArm.rotationPointY = 2;
        m.bipedLeftArm.rotationPointY = 2;
        m.bipedRightLeg.rotationPointX = -1.9F;
        m.bipedLeftLeg.rotationPointX = 1.9F;
    }

    /** End of {@code RenderPlayer.rotateCorpse}: the whole body's motion, about a pivot 0.7 block up. */
    public static void afterRotateCorpse(EntityLivingBase e, float partial) {
        BodyPose p = pose(e, partial);
        if (p == null) {
            return;
        }
        GL11.glTranslatef(p.bodyPosition[0], p.bodyPosition[1] + BodyPose.BODY_PIVOT, p.bodyPosition[2]);
        GL11.glRotatef((float) Math.toDegrees(p.bodyRotation[2]), 0, 0, 1);
        GL11.glRotatef((float) Math.toDegrees(p.bodyRotation[1]), 0, 1, 0);
        GL11.glRotatef((float) Math.toDegrees(p.bodyRotation[0]), 1, 0, 0);
        GL11.glTranslatef(0, -BodyPose.BODY_PIVOT, 0);
    }
}
