package com.akashic.kunftig.bukkit.data;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import net.minecraft.nbt.NBTTagCompound;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import com.akashic.kunftig.Kunftig;
import com.akashic.kunftig.api.bukkit.persistence.KunftigItems;
import com.akashic.kunftig.api.bukkit.persistence.PersistentDataContainer;

/**
 * {@link KunftigItems} on Thermos/Crucible. A server-backed Bukkit stack is a {@code CraftItemStack} whose
 * {@code handle} field is the Minecraft stack; it is reached reflectively (the package is versioned, and the field is
 * not public in every Cauldron fork). Loaded only where Bukkit exists.
 */
public final class ItemDataBridge implements KunftigItems.Provider {

    /** Resolved on first use: plugins only call in while the Bukkit server exists. */
    private volatile Class<?> craftItemStack;
    private volatile Field handle;
    private volatile Method asCraftCopy;

    private ItemDataBridge() {}

    /** Called reflectively by the mod at start-up (the Bukkit server may not exist yet). */
    public static void install() {
        KunftigItems.install(new ItemDataBridge());
        Kunftig.LOG.info("Kunftig item data: Bukkit API enabled (KunftigItems)");
    }

    private synchronized void resolve() {
        if (craftItemStack != null) {
            return;
        }
        try {
            String craftPackage = Bukkit.getServer()
                .getClass()
                .getPackage()
                .getName();
            Class<?> type = Class.forName(craftPackage + ".inventory.CraftItemStack");
            Field field = type.getDeclaredField("handle");
            field.setAccessible(true);
            asCraftCopy = type.getMethod("asCraftCopy", ItemStack.class);
            handle = field;
            craftItemStack = type;
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw new IllegalStateException("Kunftig item data: CraftItemStack not found on this server", e);
        }
    }

    private net.minecraft.item.ItemStack handleOf(ItemStack item) {
        resolve();
        if (item == null || !craftItemStack.isInstance(item)) {
            return null;
        }
        try {
            return (net.minecraft.item.ItemStack) handle.get(item);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public boolean canHoldData(ItemStack item) {
        return handleOf(item) != null;
    }

    @Override
    public ItemStack asDataCapable(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            throw new IllegalArgumentException("Air cannot hold data");
        }
        if (canHoldData(item)) {
            return item;
        }
        try {
            return (ItemStack) asCraftCopy.invoke(null, item);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("CraftItemStack.asCraftCopy failed", e);
        }
    }

    @Override
    public PersistentDataContainer getData(ItemStack item) {
        return containerOf(item);
    }

    private NbtDataContainer containerOf(ItemStack item) {
        final net.minecraft.item.ItemStack stack = handleOf(item);
        if (stack == null) {
            throw new IllegalArgumentException(
                "This ItemStack is not server-backed and cannot hold data; use KunftigItems.asDataCapable(item)");
        }
        return NbtDataContainer.of(new NbtDataContainer.TagHolder() {

            @Override
            public NBTTagCompound getTag() {
                return stack.stackTagCompound;
            }

            @Override
            public void setTag(NBTTagCompound tag) {
                stack.stackTagCompound = tag;
            }
        });
    }

    @Override
    public boolean setItemMeta(ItemStack item, ItemMeta meta) {
        if (!canHoldData(item)) {
            return item.setItemMeta(meta);
        }
        NbtDataContainer data = containerOf(item);
        NBTTagCompound snapshot = data.snapshot();
        boolean result = item.setItemMeta(meta);
        // Cauldron clears the whole tag for an empty meta; put the data back.
        data.restore(snapshot);
        return result;
    }
}
