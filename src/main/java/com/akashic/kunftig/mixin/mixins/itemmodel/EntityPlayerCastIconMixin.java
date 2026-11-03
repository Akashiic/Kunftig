package com.akashic.kunftig.mixin.mixins.itemmodel;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.akashic.kunftig.client.itemmodel.ItemModels;

/**
 * Item models on a cast fishing rod: while the line is out, {@code EntityPlayer#getItemIcon} replaces the held rod's
 * icon with vanilla's cast rod ({@code ItemFishingRod#func_94597_g}), without asking the item, so a rod with a model
 * turned into the plain cast rod until the line came back. Here it keeps its model, or the model's {@code .cast}
 * variant when the pack has one.
 */
@Mixin(EntityPlayer.class)
public abstract class EntityPlayerCastIconMixin {

    @Inject(method = "getItemIcon", at = @At("RETURN"), cancellable = true)
    private void kunftig$castRodModel(ItemStack stack, int pass, CallbackInfoReturnable<IIcon> cir) {
        EntityPlayer player = (EntityPlayer) (Object) this;
        if (stack != null && stack.getItem() == Items.fishing_rod && player.fishEntity != null) {
            IIcon icon = ItemModels.castIconFor(stack);
            if (icon != null) {
                cir.setReturnValue(icon);
            }
        }
    }
}
