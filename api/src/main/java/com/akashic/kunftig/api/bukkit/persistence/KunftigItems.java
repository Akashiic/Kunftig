package com.akashic.kunftig.api.bukkit.persistence;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Persistent data on items for Bukkit plugins on Thermos/Crucible 1.7.10 (Bukkit's {@code ItemMeta}
 * {@code PersistentDataContainer} only exists from 1.14). Requires the Kunftig mod on the server. Main thread only.
 *
 * <pre>
 * NamespacedKey weight = new NamespacedKey(plugin, "weight");
 * ItemStack fish = KunftigItems.asDataCapable(new ItemStack(Material.RAW_FISH));
 * KunftigItems.getData(fish).set(weight, PersistentDataType.DOUBLE, 3.7);
 * </pre>
 *
 * <b>Only server-backed stacks can hold data.</b> Stacks you get from the server (inventories, events, item
 * entities) are; a stack created with {@code new ItemStack(...)} is not, and {@link #asDataCapable} turns it into one.
 * Data survives {@code setItemMeta} and stack copies, with one exception inherited from Cauldron: setting an
 * <i>empty</i> meta (no name, lore or enchantments) erases the whole item NBT. {@link #setItemMeta} avoids that.
 */
public final class KunftigItems {

    /** Implemented inside the mod; installed when the server starts. */
    public interface Provider {

        boolean canHoldData(ItemStack item);

        ItemStack asDataCapable(ItemStack item);

        PersistentDataContainer getData(ItemStack item);

        boolean setItemMeta(ItemStack item, ItemMeta meta);
    }

    private static volatile Provider provider;

    private KunftigItems() {}

    public static boolean isAvailable() {
        return provider != null;
    }

    private static Provider provider() {
        Provider current = provider;
        if (current == null) {
            throw new IllegalStateException("Kunftig item data is not available on this server");
        }
        return current;
    }

    /** Whether the stack is server-backed and can hold persistent data. */
    public static boolean canHoldData(ItemStack item) {
        return provider().canHoldData(item);
    }

    /** The stack itself if it can hold data, otherwise a server-backed copy of it. */
    public static ItemStack asDataCapable(ItemStack item) {
        return provider().asDataCapable(item);
    }

    /**
     * The live persistent data of a stack.
     *
     * @throws IllegalArgumentException if the stack cannot hold data (see {@link #asDataCapable})
     */
    public static PersistentDataContainer getData(ItemStack item) {
        return provider().getData(item);
    }

    /**
     * {@code item.setItemMeta(meta)} that keeps the item's persistent data even when {@code meta} is empty.
     *
     * @return what {@code setItemMeta} returned
     */
    public static boolean setItemMeta(ItemStack item, ItemMeta meta) {
        return provider().setItemMeta(item, meta);
    }

    /** Internal: called by the mod. */
    public static void install(Provider implementation) {
        provider = implementation;
    }
}
