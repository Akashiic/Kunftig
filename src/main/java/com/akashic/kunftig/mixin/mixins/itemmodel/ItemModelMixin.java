package com.akashic.kunftig.mixin.mixins.itemmodel;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.akashic.kunftig.client.itemmodel.ItemModels;

/**
 * Item models in the hand: {@code EntityPlayer#getItemIcon} replaces the stack's icon with
 * {@code Item#getIcon(stack, pass, player, using, remaining)}, which ends in this Forge method. Only the base
 * implementation is hooked; items that override it keep their own icons.
 */
@Mixin(Item.class)
public abstract class ItemModelMixin {

    @Inject(
        method = "getIcon(Lnet/minecraft/item/ItemStack;I)Lnet/minecraft/util/IIcon;",
        at = @At("HEAD"),
        cancellable = true,
        remap = false) // added by Forge: same name in every environment
    private void kunftig$model(ItemStack stack, int pass, CallbackInfoReturnable<IIcon> cir) {
        IIcon icon = ItemModels.iconFor(stack);
        if (icon != null) {
            cir.setReturnValue(icon);
        }
    }
}
