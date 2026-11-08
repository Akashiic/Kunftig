package com.akashic.kunftig.client.blade;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.IItemRenderer;
import net.minecraftforge.client.MinecraftForgeClient;

import com.akashic.kunftig.Kunftig;
import com.akashic.kunftig.blade.BladeIndex;

/**
 * The item renderer put on the items blades are made of: in third person the held blade is drawn by
 * {@link BladeRenderer} from the player's model (not from the arm), so here nothing; in first person, SlashBlade's
 * first-person draw; elsewhere (inventory, ground, frame) the item's icon for now. Stacks that are not blades go to the
 * renderer the item had before. Client thread only.
 */
final class BladeItemRenderer implements IItemRenderer {

    private static final Set<Item> INSTALLED = new HashSet<>();
    private static final Set<String> UNKNOWN = new HashSet<>();

    private final IItemRenderer previous;

    private BladeItemRenderer(IItemRenderer previous) {
        this.previous = previous;
    }

    /** Puts the renderer on every item some pack's blade is drawn on (once per item). */
    static void installOn(Set<String> names) {
        for (String name : names) {
            Object item = Item.itemRegistry.getObject(name);
            if (!(item instanceof Item)) {
                if (UNKNOWN.add(name)) {
                    Kunftig.LOG.warn("Kunftig blades: item {} does not exist here", name);
                }
                continue;
            }
            if (INSTALLED.add((Item) item)) {
                MinecraftForgeClient.registerItemRenderer((Item) item, new BladeItemRenderer(registered((Item) item)));
                Kunftig.LOG.info("Kunftig blades: renderer installed on {}", name);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static IItemRenderer registered(Item item) {
        try {
            Field f = MinecraftForgeClient.class.getDeclaredField("customItemRenderers");
            f.setAccessible(true);
            return ((Map<Item, IItemRenderer>) f.get(null)).get(item);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static boolean ours(ItemStack stack, ItemRenderType type) {
        return (type == ItemRenderType.EQUIPPED || type == ItemRenderType.EQUIPPED_FIRST_PERSON)
            && BladeClient.INSTANCE.resolve(stack) != null;
    }

    @Override
    public boolean handleRenderType(ItemStack stack, ItemRenderType type) {
        return ours(stack, type) || previous != null && previous.handleRenderType(stack, type);
    }

    @Override
    public boolean shouldUseRenderHelper(ItemRenderType type, ItemStack stack, ItemRendererHelper helper) {
        if (ours(stack, type)) {
            return false;
        }
        return previous != null && previous.shouldUseRenderHelper(type, stack, helper);
    }

    @Override
    public void renderItem(ItemRenderType type, ItemStack stack, Object... data) {
        if (!ours(stack, type)) {
            if (previous != null) {
                previous.renderItem(type, stack, data);
            }
            return;
        }
        if (type != ItemRenderType.EQUIPPED_FIRST_PERSON) {
            return; // third person: BladeRenderer draws it from the model, through the blade rig
        }
        EntityPlayer player = data.length > 1 && data[1] instanceof EntityPlayer ? (EntityPlayer) data[1] : null;
        BladeIndex.Blade blade = BladeClient.INSTANCE.resolve(stack);
        if (player != null && blade != null) {
            BladeRenderer.INSTANCE.renderFirstPerson(player, stack, blade);
        }
    }
}
