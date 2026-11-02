package com.akashic.kunftig.client.itemmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

class ItemModelsTest {

    @Test
    void readsTheModelTheServerWrote() {
        NBTTagCompound tag = new NBTTagCompound();
        assertNull(ItemModels.modelOf(tag));
        assertNull(ItemModels.modelOf(null));

        NBTTagCompound values = new NBTTagCompound();
        values.setString("akashicfishing:weight", "3.7");
        tag.setTag("PublicBukkitValues", values);
        assertNull(ItemModels.modelOf(tag), "other persistent data only");

        values.setString("kunftig:model", "akashicfishing:tucunare");
        assertEquals("akashicfishing:tucunare", ItemModels.modelOf(tag));

        values.setInteger("kunftig:model", 7);
        assertNull(ItemModels.modelOf(tag), "not a string");
        values.setString("kunftig:model", "");
        assertNull(ItemModels.modelOf(tag));
    }

    // ------------------------------------------------------------------ the 1.7.10 render paths being hooked

    private static List<String> calls(String owner, String name, String desc) throws IOException {
        try (InputStream in = ItemModelsTest.class.getClassLoader()
            .getResourceAsStream(owner + ".class")) {
            ClassNode node = new ClassNode();
            new ClassReader(in).accept(node, 0);
            for (MethodNode method : node.methods) {
                if (method.name.equals(name) && method.desc.equals(desc)) {
                    List<String> calls = new ArrayList<>();
                    for (AbstractInsnNode insn : method.instructions.toArray()) {
                        if (insn instanceof MethodInsnNode) {
                            MethodInsnNode call = (MethodInsnNode) insn;
                            calls.add(call.owner + "." + call.name + call.desc);
                        }
                    }
                    return calls;
                }
            }
            throw new AssertionError("missing " + owner + "." + name + desc);
        }
    }

    private static final String STACK_ICON = "net/minecraft/item/ItemStack.getIconIndex()Lnet/minecraft/util/IIcon;";
    private static final String ITEM_ICON = "net/minecraft/item/Item.getIcon(Lnet/minecraft/item/ItemStack;I)Lnet/minecraft/util/IIcon;";

    @Test
    void guiAndDroppedItemsAskTheStack() throws IOException {
        assertTrue(
            calls(
                "net/minecraft/client/renderer/entity/RenderItem",
                "renderItemIntoGUI",
                "(Lnet/minecraft/client/gui/FontRenderer;Lnet/minecraft/client/renderer/texture/TextureManager;Lnet/minecraft/item/ItemStack;IIZ)V")
                    .contains(STACK_ICON));
        assertTrue(
            calls(
                "net/minecraft/client/renderer/entity/RenderItem",
                "doRender",
                "(Lnet/minecraft/entity/item/EntityItem;DDDFF)V").contains(STACK_ICON));
    }

    @Test
    void heldItemsEndInItemGetIcon() throws IOException {
        // ItemRenderer -> EntityLivingBase/EntityPlayer#getItemIcon
        assertTrue(
            calls(
                "net/minecraft/client/renderer/ItemRenderer",
                "renderItem",
                "(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/item/ItemStack;ILnet/minecraftforge/client/IItemRenderer$ItemRenderType;)V")
                    .contains(
                        "net/minecraft/entity/EntityLivingBase.getItemIcon(Lnet/minecraft/item/ItemStack;I)Lnet/minecraft/util/IIcon;"));
        assertTrue(
            calls(
                "net/minecraft/entity/EntityLivingBase",
                "getItemIcon",
                "(Lnet/minecraft/item/ItemStack;I)Lnet/minecraft/util/IIcon;").contains(STACK_ICON));
        // EntityPlayer overrides the stack's icon with Item#getIcon(stack, pass, player, using, remaining)...
        String playerIcon = "net/minecraft/item/Item.getIcon(Lnet/minecraft/item/ItemStack;ILnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/item/ItemStack;I)Lnet/minecraft/util/IIcon;";
        assertTrue(
            calls(
                "net/minecraft/entity/player/EntityPlayer",
                "getItemIcon",
                "(Lnet/minecraft/item/ItemStack;I)Lnet/minecraft/util/IIcon;").contains(playerIcon));
        // ...which, unless overridden, is Item#getIcon(stack, pass): the second hook.
        assertTrue(
            calls(
                "net/minecraft/item/Item",
                "getIcon",
                "(Lnet/minecraft/item/ItemStack;ILnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/item/ItemStack;I)Lnet/minecraft/util/IIcon;")
                    .contains(ITEM_ICON));
    }
}
