package com.akashic.kunftig.bukkit.data;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;

import org.junit.jupiter.api.Test;

import com.akashic.kunftig.api.bukkit.persistence.NamespacedKey;
import com.akashic.kunftig.api.bukkit.persistence.PersistentDataAdapterContext;
import com.akashic.kunftig.api.bukkit.persistence.PersistentDataContainer;
import com.akashic.kunftig.api.bukkit.persistence.PersistentDataType;

/** On real 1.7.10 NBT classes. */
class NbtDataContainerTest {

    private static final NamespacedKey WEIGHT = new NamespacedKey("fishing", "weight");
    private static final NamespacedKey NAME = new NamespacedKey("fishing", "species");

    /** Stands in for an item stack's tag field. */
    static final class Holder implements NbtDataContainer.TagHolder {

        NBTTagCompound tag;

        @Override
        public NBTTagCompound getTag() {
            return tag;
        }

        @Override
        public void setTag(NBTTagCompound tag) {
            this.tag = tag;
        }
    }

    /** A custom type, the way plugins store UUIDs (here: the catcher of a fish). */
    static final PersistentDataType<byte[], UUID> UUID_TYPE = new PersistentDataType<byte[], UUID>() {

        @Override
        public Class<byte[]> getPrimitiveType() {
            return byte[].class;
        }

        @Override
        public Class<UUID> getComplexType() {
            return UUID.class;
        }

        @Override
        public byte[] toPrimitive(UUID complex, PersistentDataAdapterContext context) {
            return ByteBuffer.allocate(16)
                .putLong(complex.getMostSignificantBits())
                .putLong(complex.getLeastSignificantBits())
                .array();
        }

        @Override
        public UUID fromPrimitive(byte[] primitive, PersistentDataAdapterContext context) {
            ByteBuffer buffer = ByteBuffer.wrap(primitive);
            return new UUID(buffer.getLong(), buffer.getLong());
        }
    };

    @Test
    void usesTheModernLayout() {
        Holder holder = new Holder();
        NbtDataContainer.of(holder)
            .set(WEIGHT, PersistentDataType.DOUBLE, 3.75);
        assertTrue(holder.tag.hasKey(NbtDataContainer.ROOT, 10));
        assertEquals(
            3.75,
            holder.tag.getCompoundTag(NbtDataContainer.ROOT)
                .getDouble("fishing:weight"),
            0.0);
    }

    @Test
    void everyPrimitiveTypeRoundTrips() {
        PersistentDataContainer data = NbtDataContainer.of(new Holder());
        NamespacedKey k = new NamespacedKey("t", "k");
        data.set(k, PersistentDataType.BYTE, (byte) -7);
        assertEquals(Byte.valueOf((byte) -7), data.get(k, PersistentDataType.BYTE));
        data.set(k, PersistentDataType.SHORT, (short) 1234);
        assertEquals(Short.valueOf((short) 1234), data.get(k, PersistentDataType.SHORT));
        data.set(k, PersistentDataType.INTEGER, Integer.MIN_VALUE);
        assertEquals(Integer.valueOf(Integer.MIN_VALUE), data.get(k, PersistentDataType.INTEGER));
        data.set(k, PersistentDataType.LONG, Long.MAX_VALUE);
        assertEquals(Long.valueOf(Long.MAX_VALUE), data.get(k, PersistentDataType.LONG));
        data.set(k, PersistentDataType.FLOAT, 1.5F);
        assertEquals(Float.valueOf(1.5F), data.get(k, PersistentDataType.FLOAT));
        data.set(k, PersistentDataType.DOUBLE, Math.PI);
        assertEquals(Double.valueOf(Math.PI), data.get(k, PersistentDataType.DOUBLE));
        data.set(k, PersistentDataType.STRING, "Tucunaré ★");
        assertEquals("Tucunaré ★", data.get(k, PersistentDataType.STRING));
        data.set(k, PersistentDataType.BOOLEAN, true);
        assertEquals(Boolean.TRUE, data.get(k, PersistentDataType.BOOLEAN));
        assertEquals(Byte.valueOf((byte) 1), data.get(k, PersistentDataType.BYTE), "boolean is stored as a byte");
        UUID catcher = UUID.randomUUID();
        data.set(k, UUID_TYPE, catcher);
        assertEquals(catcher, data.get(k, UUID_TYPE));
        data.set(k, PersistentDataType.INTEGER_ARRAY, new int[] { 1, 2, 3 });
        assertArrayEquals(new int[] { 1, 2, 3 }, data.get(k, PersistentDataType.INTEGER_ARRAY));
    }

    @Test
    void arraysAreCopiedInAndOut() {
        PersistentDataContainer data = NbtDataContainer.of(new Holder());
        byte[] stored = { 1, 2, 3 };
        data.set(WEIGHT, PersistentDataType.BYTE_ARRAY, stored);
        stored[0] = 99;
        byte[] read = data.get(WEIGHT, PersistentDataType.BYTE_ARRAY);
        assertEquals(1, read[0], "changing the array after set does not change the item");
        read[1] = 99;
        assertEquals(2, data.get(WEIGHT, PersistentDataType.BYTE_ARRAY)[1], "changing a read array does not either");
    }

    @Test
    void typeMismatchIsReportedLikeBukkit() {
        PersistentDataContainer data = NbtDataContainer.of(new Holder());
        data.set(WEIGHT, PersistentDataType.DOUBLE, 1.0);
        assertTrue(data.has(WEIGHT));
        assertTrue(data.has(WEIGHT, PersistentDataType.DOUBLE));
        assertFalse(data.has(WEIGHT, PersistentDataType.INTEGER));
        assertThrows(IllegalArgumentException.class, () -> data.get(WEIGHT, PersistentDataType.INTEGER));
        assertEquals(Integer.valueOf(5), data.getOrDefault(WEIGHT, PersistentDataType.INTEGER, 5));
        assertNull(data.get(NAME, PersistentDataType.STRING));
        assertThrows(IllegalArgumentException.class, () -> data.set(NAME, PersistentDataType.STRING, null));
    }

    @Test
    void nestedContainersAreCopies() {
        PersistentDataContainer data = NbtDataContainer.of(new Holder());
        PersistentDataContainer stats = data.getAdapterContext()
            .newPersistentDataContainer();
        stats.set(WEIGHT, PersistentDataType.DOUBLE, 2.0);
        NamespacedKey statsKey = new NamespacedKey("fishing", "stats");
        data.set(statsKey, PersistentDataType.TAG_CONTAINER, stats);
        stats.set(WEIGHT, PersistentDataType.DOUBLE, 9.0);
        PersistentDataContainer read = data.get(statsKey, PersistentDataType.TAG_CONTAINER);
        assertEquals(Double.valueOf(2.0), read.get(WEIGHT, PersistentDataType.DOUBLE));
        read.set(WEIGHT, PersistentDataType.DOUBLE, 7.0);
        assertEquals(
            Double.valueOf(2.0),
            data.get(statsKey, PersistentDataType.TAG_CONTAINER)
                .get(WEIGHT, PersistentDataType.DOUBLE),
            "write the nested container back with set to change it");

        PersistentDataContainer second = data.getAdapterContext()
            .newPersistentDataContainer();
        second.set(NAME, PersistentDataType.STRING, "pirarucu");
        NamespacedKey bag = new NamespacedKey("fishing", "bag");
        data.set(bag, PersistentDataType.TAG_CONTAINER_ARRAY, new PersistentDataContainer[] { stats, second });
        PersistentDataContainer[] fishes = data.get(bag, PersistentDataType.TAG_CONTAINER_ARRAY);
        assertEquals(2, fishes.length);
        assertEquals("pirarucu", fishes[1].get(NAME, PersistentDataType.STRING));
        data.set(bag, PersistentDataType.TAG_CONTAINER_ARRAY, new PersistentDataContainer[0]);
        assertEquals(0, data.get(bag, PersistentDataType.TAG_CONTAINER_ARRAY).length, "empty lists are valid");
    }

    @Test
    void removingTheLastValueLeavesNoTraceSoItemsStillStack() {
        Holder holder = new Holder();
        PersistentDataContainer data = NbtDataContainer.of(holder);
        assertTrue(data.isEmpty());
        assertNull(holder.tag, "reading never creates a tag");
        data.set(WEIGHT, PersistentDataType.DOUBLE, 1.0);
        data.remove(WEIGHT);
        assertNull(holder.tag, "an item tag left empty is cleared");

        holder.tag = new NBTTagCompound();
        holder.tag.setTag("display", new NBTTagCompound());
        data.set(WEIGHT, PersistentDataType.DOUBLE, 1.0);
        data.remove(WEIGHT);
        assertFalse(holder.tag.hasKey(NbtDataContainer.ROOT));
        assertTrue(holder.tag.hasKey("display"), "other NBT is never touched");
    }

    @Test
    void keysAreListedAndForeignEntriesIgnored() {
        Holder holder = new Holder();
        PersistentDataContainer data = NbtDataContainer.of(holder);
        data.set(WEIGHT, PersistentDataType.DOUBLE, 1.0);
        data.set(NAME, PersistentDataType.STRING, "tilapia");
        holder.tag.getCompoundTag(NbtDataContainer.ROOT)
            .setString("Not A Key", "x");
        assertEquals(
            2,
            data.getKeys()
                .size());
        assertTrue(
            data.getKeys()
                .contains(NAME));
    }

    @Test
    void snapshotSurvivesCauldronsEmptyMetaWipe() {
        Holder holder = new Holder();
        NbtDataContainer data = NbtDataContainer.of(holder);
        data.set(WEIGHT, PersistentDataType.DOUBLE, 4.2);
        NBTTagCompound snapshot = data.snapshot();
        holder.tag = null; // what CraftItemStack.setItemMeta does for an empty meta on Cauldron
        data.restore(snapshot);
        assertEquals(Double.valueOf(4.2), data.get(WEIGHT, PersistentDataType.DOUBLE));
    }
}
