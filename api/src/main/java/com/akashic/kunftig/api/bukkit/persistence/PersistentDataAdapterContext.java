package com.akashic.kunftig.api.bukkit.persistence;

/** Bukkit 1.14+'s {@code PersistentDataAdapterContext}: lets custom types create nested containers. */
public interface PersistentDataAdapterContext {

    /** A new, empty container, to be stored as a {@link PersistentDataType#TAG_CONTAINER} value. */
    PersistentDataContainer newPersistentDataContainer();
}
