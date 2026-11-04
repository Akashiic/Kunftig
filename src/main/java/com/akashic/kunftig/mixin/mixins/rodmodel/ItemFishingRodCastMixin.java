package com.akashic.kunftig.mixin.mixins.rodmodel;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemFishingRod;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.akashic.kunftig.client.rodmodel.RodModels;

/**
 * 3D rods: the local player's cast animation starts on the click, not when the server's bobber arrives (a ping
 * later). Client side only ({@code world.isRemote}); the bobber appearing is the same cast and does not restart it.
 */
@Mixin(ItemFishingRod.class)
public abstract class ItemFishingRodCastMixin {

    @Inject(method = "onItemRightClick", at = @At("HEAD"))
    private void kunftig$localCast(ItemStack stack, World world, EntityPlayer player,
        CallbackInfoReturnable<ItemStack> cir) {
        if (world.isRemote) {
            RodModels.INSTANCE.onLocalRightClick(player);
        }
    }
}
