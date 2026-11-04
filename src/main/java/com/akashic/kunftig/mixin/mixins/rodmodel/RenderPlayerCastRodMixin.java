package com.akashic.kunftig.mixin.mixins.rodmodel;

import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.entity.projectile.EntityFishHook;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.akashic.kunftig.client.rodmodel.RodModels;

/**
 * 3D rods: in third person, 1.7.10 draws a stick in place of a rod whose line is out ({@code renderEquippedItems}
 * reads {@code fishEntity} once, for that). A 3D rod stays in the hand: it hides its own hanging line instead.
 */
@Mixin(RenderPlayer.class)
public abstract class RenderPlayerCastRodMixin {

    @Redirect(
        method = "renderEquippedItems(Lnet/minecraft/client/entity/AbstractClientPlayer;F)V",
        at = @At(
            value = "FIELD",
            target = "Lnet/minecraft/client/entity/AbstractClientPlayer;fishEntity:Lnet/minecraft/entity/projectile/EntityFishHook;"))
    private EntityFishHook kunftig$keepRod(AbstractClientPlayer player) {
        return player.fishEntity != null && RodModels.INSTANCE.keepsRodWhileCast(player) ? null : player.fishEntity;
    }
}
