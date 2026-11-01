package com.akashic.kunftig.api.bukkit.persistence;

import java.util.Set;

/**
 * Bukkit 1.14+'s {@code PersistentDataContainer} for 1.7.10: typed, namespaced values stored in NBT.
 * <p>
 * Containers from {@link KunftigItems#getData} are live: every change is written to the item at once. Values are
 * stored as on 1.14+ ({@code PublicBukkitValues -> "namespace:key"}), so items keep the same layout on modern servers.
 */
public interface PersistentDataContainer {

    /** Stores a value, replacing any value under the key. */
    <P, C> void set(NamespacedKey key, PersistentDataType<P, C> type, C value);

    /** Whether a value exists under the key and is stored as {@code type}'s primitive type. */
    <P, C> boolean has(NamespacedKey key, PersistentDataType<P, C> type);

    /** Whether any value exists under the key. */
    boolean has(NamespacedKey key);

    /**
     * The value under the key, or {@code null} if there is none.
     *
     * @throws IllegalArgumentException if a value exists but is stored as another primitive type
     */
    <P, C> C get(NamespacedKey key, PersistentDataType<P, C> type);

    <P, C> C getOrDefault(NamespacedKey key, PersistentDataType<P, C> type, C defaultValue);

    /** The keys present (a copy). */
    Set<NamespacedKey> getKeys();

    void remove(NamespacedKey key);

    boolean isEmpty();

    PersistentDataAdapterContext getAdapterContext();
}
