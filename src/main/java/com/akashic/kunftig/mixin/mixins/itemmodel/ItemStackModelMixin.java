package com.akashic.kunftig.mixin.mixins.itemmodel;

import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.akashic.kunftig.client.itemmodel.ItemModels;

/** Item models in the inventory, on the ground and in item frames ({@code RenderItem} asks the stack). */
@Mixin(ItemStack.class)
public abstract class ItemStackModelMixin {

    @Inject(method = "getIconIndex", at = @At("HEAD"), cancellable = true)
    private void kunftig$model(CallbackInfoReturnable<IIcon> cir) {
        IIcon icon = ItemModels.iconFor((ItemStack) (Object) this);
        if (icon != null) {
            cir.setReturnValue(icon);
        }
    }
}
