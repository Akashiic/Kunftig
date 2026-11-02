package com.akashic.kunftig.client.itemmodel;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.IIcon;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.TextureStitchEvent;

import org.apache.commons.io.IOUtils;

import com.akashic.kunftig.Kunftig;
import com.akashic.kunftig.itemmodel.ItemModelIndex;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Client side of item models (the {@code CustomModelData} idea for 1.7.10, issue #7). 1.7.10 must know every icon when
 * the items atlas is stitched, so the icons listed by each resource pack's {@code kunftig/item_models.json} are
 * registered on {@link TextureStitchEvent.Pre}, again on every resource reload (server resource packs included). The
 * icon mixins then ask {@link #iconFor} for each stack.
 * <p>
 * Applies to items drawn from the items atlas with a single layer: blocks use the blocks atlas, and multi-layer items
 * (potions, leather armor, spawn eggs) tint each layer. Disabled with {@code -Dkunftig.itemModels.disable=true}.
 */
public final class ItemModels {

    public static final ItemModels INSTANCE = new ItemModels();
    private static final int ITEMS_ATLAS = 1;
    private static final int MAX_LOGGED_ERRORS = 20;

    private static volatile Map<String, IIcon> icons = Collections.emptyMap();

    private ItemModels() {}

    public static boolean disabled() {
        return Boolean.getBoolean("kunftig.itemModels.disable");
    }

    /** The model icon of a stack, or null for the item's own icon. Called for every rendered item; cheap. */
    public static IIcon iconFor(ItemStack stack) {
        Map<String, IIcon> current = icons;
        if (current.isEmpty() || stack == null || stack.stackTagCompound == null) {
            return null;
        }
        Item item = stack.getItem();
        if (item == null || item.getSpriteNumber() != ITEMS_ATLAS || item.requiresMultipleRenderPasses()) {
            return null;
        }
        String model = modelOf(stack.stackTagCompound);
        return model == null ? null : current.get(model);
    }

    /** {@code PublicBukkitValues."kunftig:model"} of an item tag, or null. */
    static String modelOf(NBTTagCompound tag) {
        if (tag == null || !tag.hasKey(ItemModelIndex.NBT_ROOT, 10)) {
            return null;
        }
        NBTTagCompound values = tag.getCompoundTag(ItemModelIndex.NBT_ROOT);
        if (!values.hasKey(ItemModelIndex.NBT_KEY, 8)) {
            return null;
        }
        String model = values.getString(ItemModelIndex.NBT_KEY);
        return model.isEmpty() ? null : model;
    }

    @SubscribeEvent
    public void onStitch(TextureStitchEvent.Pre event) {
        if (event.map.getTextureType() != ITEMS_ATLAS) {
            return;
        }
        ItemModelIndex index = read(
            Minecraft.getMinecraft()
                .getResourceManager());
        Map<String, IIcon> registered = new HashMap<>();
        for (Map.Entry<String, String> model : index.icons()
            .entrySet()) {
            registered.put(model.getKey(), event.map.registerIcon(model.getValue()));
        }
        icons = registered.isEmpty() ? Collections.<String, IIcon>emptyMap() : registered;
        List<String> errors = index.errors();
        for (int i = 0; i < errors.size() && i < MAX_LOGGED_ERRORS; i++) {
            Kunftig.LOG.warn("Kunftig item models: {}", errors.get(i));
        }
        if (!registered.isEmpty()) {
            Kunftig.LOG.info("Kunftig item models: {} model(s) registered", registered.size());
        }
    }

    @SuppressWarnings("unchecked")
    private static ItemModelIndex read(IResourceManager manager) {
        ItemModelIndex index = new ItemModelIndex();
        for (String namespace : (Iterable<String>) manager.getResourceDomains()) {
            List<IResource> files;
            try {
                files = manager.getAllResources(new ResourceLocation(namespace, ItemModelIndex.PATH));
            } catch (IOException e) {
                continue; // this namespace declares no models
            }
            for (IResource file : files) {
                String source = namespace + ":" + ItemModelIndex.PATH;
                try (InputStream in = file.getInputStream()) {
                    index.add(namespace, IOUtils.toString(in, StandardCharsets.UTF_8), source);
                } catch (IOException | RuntimeException e) {
                    Kunftig.LOG.warn("Kunftig item models: could not read {}", source, e);
                }
            }
        }
        return index;
    }
}
