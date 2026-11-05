package com.akashic.kunftig.mixin.mixins.itemmodel;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;

import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.akashic.kunftig.client.itemmodel.ItemModels;

/**
 * Item models with a slot cell ({@code celula} in {@code item_models.json}): container slots draw the cell over the
 * whole 18x18 slot (1 pixel past the 16x16 face on every side), so the items of neighboring slots join into one picture
 * without the slot borders between them. {@code GuiContainer} draws every slot through this method; other places that
 * draw items (the hotbar, the hand, the ground) keep the 16x16 icon.
 */
@Mixin(RenderItem.class)
public abstract class RenderItemSlotCellMixin {

    /** The cell's share of its 32x32 texture. */
    private static final double KUNFTIG$CELL = 18 / 32.0;

    @Shadow
    public float zLevel;

    @Inject(method = "renderItemAndEffectIntoGUI", at = @At("HEAD"), cancellable = true)
    private void kunftig$slotCell(FontRenderer font, TextureManager textures, ItemStack stack, int x, int y,
        CallbackInfo ci) {
        IIcon cell = ItemModels.cellFor(stack);
        if (cell == null) {
            return;
        }
        float z = zLevel + 50.0F;
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_BLEND);
        OpenGlHelper.glBlendFunc(770, 771, 1, 0);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        textures.bindTexture(TextureMap.locationItemsTexture);
        double u0 = cell.getMinU(), v0 = cell.getMinV();
        double u1 = u0 + (cell.getMaxU() - u0) * KUNFTIG$CELL, v1 = v0 + (cell.getMaxV() - v0) * KUNFTIG$CELL;
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        t.addVertexWithUV(x - 1, y + 17, z, u0, v1);
        t.addVertexWithUV(x + 17, y + 17, z, u1, v1);
        t.addVertexWithUV(x + 17, y - 1, z, u1, v0);
        t.addVertexWithUV(x - 1, y - 1, z, u0, v0);
        t.draw();
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_CULL_FACE);
        ci.cancel();
    }
}
