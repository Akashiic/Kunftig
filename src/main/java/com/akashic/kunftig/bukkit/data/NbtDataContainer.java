package com.akashic.kunftig.bukkit.data;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import com.akashic.kunftig.api.bukkit.persistence.NamespacedKey;
import com.akashic.kunftig.api.bukkit.persistence.PersistentDataAdapterContext;
import com.akashic.kunftig.api.bukkit.persistence.PersistentDataContainer;
import com.akashic.kunftig.api.bukkit.persistence.PersistentDataType;

/**
 * {@link PersistentDataContainer} over 1.7.10 NBT, laid out like CraftBukkit 1.14+: a compound named
 * {@value #ROOT} in the owner's tag, holding one entry per {@code "namespace:key"}. Arrays and nested containers are
 * copied in and out, so callers never alias the stored NBT. When the last value is removed the compound is removed,
 * and an owner tag left empty is cleared (an empty tag would stop the item from stacking with clean ones).
 */
public final class NbtDataContainer implements PersistentDataContainer, PersistentDataAdapterContext {

    /** Same name as CraftBukkit's persistent data compound. */
    public static final String ROOT = "PublicBukkitValues";

    private static final int TAG_BYTE = 1;
    private static final int TAG_SHORT = 2;
    private static final int TAG_INT = 3;
    private static final int TAG_LONG = 4;
    private static final int TAG_FLOAT = 5;
    private static final int TAG_DOUBLE = 6;
    private static final int TAG_BYTE_ARRAY = 7;
    private static final int TAG_STRING = 8;
    private static final int TAG_LIST = 9;
    private static final int TAG_COMPOUND = 10;
    private static final int TAG_INT_ARRAY = 11;

    /** Where the owner's NBT tag lives (an item stack's tag, or a standalone compound). */
    public interface TagHolder {

        NBTTagCompound getTag();

        void setTag(NBTTagCompound tag);
    }

    private final TagHolder holder;
    /** Standalone containers keep their values directly, without the {@value #ROOT} wrapper. */
    private final boolean standalone;

    private NbtDataContainer(TagHolder holder, boolean standalone) {
        this.holder = holder;
        this.standalone = standalone;
    }

    /** A live view of the persistent data stored in a holder's tag. */
    public static NbtDataContainer of(TagHolder holder) {
        return new NbtDataContainer(holder, false);
    }

    /** A standalone container (a nested value, or a new one from the adapter context). */
    public static NbtDataContainer standalone() {
        return standalone(new NBTTagCompound());
    }

    private static NbtDataContainer standalone(final NBTTagCompound values) {
        return new NbtDataContainer(new TagHolder() {

            private NBTTagCompound tag = values;

            @Override
            public NBTTagCompound getTag() {
                return tag;
            }

            @Override
            public void setTag(NBTTagCompound newTag) {
                tag = newTag == null ? new NBTTagCompound() : newTag;
            }
        }, true);
    }

    /** The values compound, or null if absent and not created. */
    private NBTTagCompound values(boolean create) {
        if (standalone) {
            return holder.getTag();
        }
        NBTTagCompound tag = holder.getTag();
        if (tag == null) {
            if (!create) {
                return null;
            }
            tag = new NBTTagCompound();
            holder.setTag(tag);
        }
        if (!tag.hasKey(ROOT, TAG_COMPOUND)) {
            if (!create) {
                return null;
            }
            tag.setTag(ROOT, new NBTTagCompound());
        }
        return tag.getCompoundTag(ROOT);
    }

    private void cleanup() {
        if (standalone) {
            return;
        }
        NBTTagCompound tag = holder.getTag();
        if (tag == null) {
            return;
        }
        if (tag.hasKey(ROOT, TAG_COMPOUND) && tag.getCompoundTag(ROOT)
            .hasNoTags()) {
            tag.removeTag(ROOT);
        }
        if (tag.hasNoTags()) {
            holder.setTag(null);
        }
    }

    /** A copy of the raw values compound, or null (used to carry data across {@code setItemMeta}). */
    NBTTagCompound snapshot() {
        NBTTagCompound values = values(false);
        return values == null || values.hasNoTags() ? null : (NBTTagCompound) values.copy();
    }

    /** Replaces all values with a snapshot taken by {@link #snapshot()}. */
    void restore(NBTTagCompound snapshot) {
        if (snapshot == null) {
            return;
        }
        NBTTagCompound tag = holder.getTag();
        if (tag == null) {
            tag = new NBTTagCompound();
            holder.setTag(tag);
        }
        tag.setTag(ROOT, snapshot.copy());
    }

    // ------------------------------------------------------------------ PersistentDataContainer

    @Override
    public <P, C> void set(NamespacedKey key, PersistentDataType<P, C> type, C value) {
        if (key == null || type == null || value == null) {
            throw new IllegalArgumentException("key, type and value must not be null");
        }
        NBTBase tag = toTag(type.getPrimitiveType(), type.toPrimitive(value, this));
        values(true).setTag(key.toString(), tag);
    }

    @Override
    public <P, C> boolean has(NamespacedKey key, PersistentDataType<P, C> type) {
        NBTTagCompound values = values(false);
        return values != null && values.hasKey(key.toString())
            && matches(values, key.toString(), type.getPrimitiveType());
    }

    @Override
    public boolean has(NamespacedKey key) {
        NBTTagCompound values = values(false);
        return values != null && values.hasKey(key.toString());
    }

    @Override
    public <P, C> C get(NamespacedKey key, PersistentDataType<P, C> type) {
        NBTTagCompound values = values(false);
        String name = key.toString();
        if (values == null || !values.hasKey(name)) {
            return null;
        }
        Class<P> primitiveType = type.getPrimitiveType();
        if (!matches(values, name, primitiveType)) {
            throw new IllegalArgumentException(
                "The value of " + key + " is not stored as " + primitiveType.getSimpleName());
        }
        return type.fromPrimitive(primitiveType.cast(fromTag(values, name, primitiveType)), this);
    }

    @Override
    public <P, C> C getOrDefault(NamespacedKey key, PersistentDataType<P, C> type, C defaultValue) {
        C value = has(key, type) ? get(key, type) : null;
        return value != null ? value : defaultValue;
    }

    @Override
    public Set<NamespacedKey> getKeys() {
        NBTTagCompound values = values(false);
        if (values == null) {
            return Collections.emptySet();
        }
        Set<NamespacedKey> keys = new LinkedHashSet<NamespacedKey>();
        for (Object name : values.func_150296_c()) {
            NamespacedKey key = NamespacedKey.fromString((String) name);
            if (key != null) {
                keys.add(key);
            }
        }
        return keys;
    }

    @Override
    public void remove(NamespacedKey key) {
        NBTTagCompound values = values(false);
        if (values != null) {
            values.removeTag(key.toString());
            cleanup();
        }
    }

    @Override
    public boolean isEmpty() {
        NBTTagCompound values = values(false);
        return values == null || values.hasNoTags();
    }

    @Override
    public PersistentDataAdapterContext getAdapterContext() {
        return this;
    }

    @Override
    public PersistentDataContainer newPersistentDataContainer() {
        return standalone();
    }

    // ------------------------------------------------------------------ NBT conversion

    private static int expectedTag(Class<?> primitive) {
        if (primitive == Byte.class) return TAG_BYTE;
        if (primitive == Short.class) return TAG_SHORT;
        if (primitive == Integer.class) return TAG_INT;
        if (primitive == Long.class) return TAG_LONG;
        if (primitive == Float.class) return TAG_FLOAT;
        if (primitive == Double.class) return TAG_DOUBLE;
        if (primitive == String.class) return TAG_STRING;
        if (primitive == byte[].class) return TAG_BYTE_ARRAY;
        if (primitive == int[].class) return TAG_INT_ARRAY;
        if (primitive == PersistentDataContainer.class) return TAG_COMPOUND;
        if (primitive == PersistentDataContainer[].class) return TAG_LIST;
        throw new IllegalArgumentException(
            "Unsupported primitive type " + primitive.getName()
                + " (1.7.10 NBT has no long arrays; build custom types on the primitive types)");
    }

    private static boolean matches(NBTTagCompound values, String name, Class<?> primitive) {
        int stored = values.func_150299_b(name);
        int expected = expectedTag(primitive);
        if (stored != expected) {
            return false;
        }
        if (expected == TAG_LIST) {
            int elements = ((NBTTagList) values.getTag(name)).func_150303_d();
            return elements == TAG_COMPOUND || ((NBTTagList) values.getTag(name)).tagCount() == 0;
        }
        return true;
    }

    private static NBTBase toTag(Class<?> primitive, Object value) {
        NBTTagCompound scratch = new NBTTagCompound();
        switch (expectedTag(primitive)) {
            case TAG_BYTE:
                scratch.setByte("v", (Byte) value);
                break;
            case TAG_SHORT:
                scratch.setShort("v", (Short) value);
                break;
            case TAG_INT:
                scratch.setInteger("v", (Integer) value);
                break;
            case TAG_LONG:
                scratch.setLong("v", (Long) value);
                break;
            case TAG_FLOAT:
                scratch.setFloat("v", (Float) value);
                break;
            case TAG_DOUBLE:
                scratch.setDouble("v", (Double) value);
                break;
            case TAG_STRING:
                scratch.setString("v", (String) value);
                break;
            case TAG_BYTE_ARRAY:
                scratch.setByteArray("v", ((byte[]) value).clone());
                break;
            case TAG_INT_ARRAY:
                scratch.setIntArray("v", ((int[]) value).clone());
                break;
            case TAG_COMPOUND:
                return containerTag((PersistentDataContainer) value);
            case TAG_LIST:
            default:
                NBTTagList list = new NBTTagList();
                for (PersistentDataContainer element : (PersistentDataContainer[]) value) {
                    list.appendTag(containerTag(element));
                }
                return list;
        }
        return scratch.getTag("v");
    }

    private static NBTTagCompound containerTag(PersistentDataContainer container) {
        if (!(container instanceof NbtDataContainer)) {
            throw new IllegalArgumentException("Nested containers must come from getAdapterContext()");
        }
        NBTTagCompound values = ((NbtDataContainer) container).values(false);
        return values == null ? new NBTTagCompound() : (NBTTagCompound) values.copy();
    }

    private static Object fromTag(NBTTagCompound values, String name, Class<?> primitive) {
        switch (expectedTag(primitive)) {
            case TAG_BYTE:
                return values.getByte(name);
            case TAG_SHORT:
                return values.getShort(name);
            case TAG_INT:
                return values.getInteger(name);
            case TAG_LONG:
                return values.getLong(name);
            case TAG_FLOAT:
                return values.getFloat(name);
            case TAG_DOUBLE:
                return values.getDouble(name);
            case TAG_STRING:
                return values.getString(name);
            case TAG_BYTE_ARRAY:
                return values.getByteArray(name)
                    .clone();
            case TAG_INT_ARRAY:
                return values.getIntArray(name)
                    .clone();
            case TAG_COMPOUND:
                return standalone(
                    (NBTTagCompound) values.getCompoundTag(name)
                        .copy());
            case TAG_LIST:
            default:
                NBTTagList list = values.getTagList(name, TAG_COMPOUND);
                PersistentDataContainer[] containers = new PersistentDataContainer[list.tagCount()];
                for (int i = 0; i < containers.length; i++) {
                    containers[i] = standalone(
                        (NBTTagCompound) list.getCompoundTagAt(i)
                            .copy());
                }
                return containers;
        }
    }
}
