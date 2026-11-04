package com.akashic.kunftig.mixin.mixins.rodmodel;

import net.minecraft.client.renderer.entity.RenderFish;
import net.minecraft.entity.projectile.EntityFishHook;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.akashic.kunftig.client.rodmodel.RodModels;

/**
 * 3D rods: the fishing line starts at the rod's tip, in the rod's line color (issue #13). Vanilla computes the start
 * from fixed offsets of the hand; where it starts drawing the line (its only {@code startDrawing(int)}), the line is
 * drawn here instead when the angler holds a 3D rod whose tip was captured. Uses only the method's arguments, never
 * its locals.
 */
@Mixin(RenderFish.class)
public abstract class RenderFishLineMixin {

    @Inject(
        method = "doRender(Lnet/minecraft/entity/projectile/EntityFishHook;DDDFF)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/Tessellator;startDrawing(I)V"),
        cancellable = true)
    private void kunftig$lineFromRodTip(EntityFishHook hook, double x, double y, double z, float yaw, float partial,
        CallbackInfo ci) {
        if (RodModels.INSTANCE.drawLine(hook, x, y, z, partial)) {
            ci.cancel();
        }
    }
}
