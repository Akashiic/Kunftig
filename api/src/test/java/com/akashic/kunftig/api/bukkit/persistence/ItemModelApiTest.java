package com.akashic.kunftig.api.bukkit.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ItemModelApiTest {

    /** Stores what the mod would store, keyed like PublicBukkitValues ("namespace:key"). */
    private static final class MapContainer implements PersistentDataContainer {

        final Map<String, Object> values = new HashMap<>();

        @Override
        public <P, C> void set(NamespacedKey key, PersistentDataType<P, C> type, C value) {
            values.put(key.toString(), value);
        }

        @Override
        public <P, C> boolean has(NamespacedKey key, PersistentDataType<P, C> type) {
            return values.containsKey(key.toString());
        }

        @Override
        public boolean has(NamespacedKey key) {
            return values.containsKey(key.toString());
        }

        @Override
        public <P, C> C get(NamespacedKey key, PersistentDataType<P, C> type) {
            return type.getComplexType()
                .cast(values.get(key.toString()));
        }

        @Override
        public <P, C> C getOrDefault(NamespacedKey key, PersistentDataType<P, C> type, C defaultValue) {
            C value = get(key, type);
            return value == null ? defaultValue : value;
        }

        @Override
        public Set<NamespacedKey> getKeys() {
            Set<NamespacedKey> keys = new HashSet<>();
            for (String key : values.keySet()) {
                keys.add(NamespacedKey.fromString(key));
            }
            return keys;
        }

        @Override
        public void remove(NamespacedKey key) {
            values.remove(key.toString());
        }

        @Override
        public boolean isEmpty() {
            return values.isEmpty();
        }

        @Override
        public PersistentDataAdapterContext getAdapterContext() {
            throw new UnsupportedOperationException();
        }
    }

    private final MapContainer container = new MapContainer();
    private final ItemStack capable = new ItemStack(Material.RAW_FISH);
    private final ItemStack plain = new ItemStack(Material.RAW_FISH);

    private void install() {
        KunftigItems.install(new KunftigItems.Provider() {

            @Override
            public boolean canHoldData(ItemStack item) {
                return item == capable;
            }

            @Override
            public ItemStack asDataCapable(ItemStack item) {
                return capable;
            }

            @Override
            public PersistentDataContainer getData(ItemStack item) {
                if (item != capable) {
                    throw new IllegalArgumentException("not data capable");
                }
                return container;
            }

            @Override
            public boolean setItemMeta(ItemStack item, ItemMeta meta) {
                return true;
            }
        });
    }

    @AfterEach
    void uninstall() {
        KunftigItems.install(null);
    }

    @Test
    void theModelIsAStringInTheItemsPersistentData() {
        install();
        NamespacedKey tucunare = new NamespacedKey("akashicfishing", "tucunare");
        KunftigItems.setModel(capable, tucunare);
        assertEquals("akashicfishing:tucunare", container.values.get("kunftig:model"));
        assertEquals(tucunare, KunftigItems.getModel(capable));

        KunftigItems.setModel(capable, null);
        assertNull(KunftigItems.getModel(capable));
        assertEquals(0, container.values.size());
    }

    @Test
    void plainStacksHaveNoModelAndCannotTakeOne() {
        install();
        assertNull(KunftigItems.getModel(plain));
        assertNull(KunftigItems.getModel(null));
        assertThrows(
            IllegalArgumentException.class,
            () -> KunftigItems.setModel(plain, new NamespacedKey("akashicfishing", "tucunare")));
    }

    @Test
    void aMalformedStoredValueReadsAsNoModel() {
        install();
        container.values.put("kunftig:model", "Not A Key");
        assertNull(KunftigItems.getModel(capable));
    }
}
