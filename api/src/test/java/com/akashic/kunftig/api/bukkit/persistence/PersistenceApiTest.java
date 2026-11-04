package com.akashic.kunftig.api.bukkit.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.akashic.kunftig.api.KunftigApi;

class PersistenceApiTest {

    @Test
    void keysFollowBukkitRules() {
        NamespacedKey key = new NamespacedKey("akashicfishing", "fish/weight_kg");
        assertEquals("akashicfishing:fish/weight_kg", key.toString());
        assertEquals(key, NamespacedKey.fromString("akashicfishing:fish/weight_kg"));
        assertThrows(IllegalArgumentException.class, () -> new NamespacedKey("Upper", "x"));
        assertThrows(IllegalArgumentException.class, () -> new NamespacedKey("ns", "with space"));
        assertThrows(IllegalArgumentException.class, () -> new NamespacedKey("ns/slash", "x"));
        assertNull(NamespacedKey.fromString("no-colon"));
        assertNull(NamespacedKey.fromString("a:b:c"));
        assertNull(NamespacedKey.fromString(":x"));
        assertNull(NamespacedKey.fromString("x:"));
    }

    @Test
    void booleanIsAByte() {
        assertEquals(Byte.class, PersistentDataType.BOOLEAN.getPrimitiveType());
        assertEquals(Byte.valueOf((byte) 1), PersistentDataType.BOOLEAN.toPrimitive(true, null));
        assertFalse(PersistentDataType.BOOLEAN.fromPrimitive((byte) 0, null));
        assertTrue(PersistentDataType.BOOLEAN.fromPrimitive((byte) 5, null));
    }

    @Test
    void withoutTheModEverythingSaysSo() {
        assertEquals(7, KunftigApi.apiVersion());
        assertFalse(KunftigApi.isItemDataAvailable());
        assertFalse(KunftigApi.isFishingAvailable());
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> KunftigItems.getData(null));
        assertTrue(e.getMessage().contains("not available"));
    }
}
