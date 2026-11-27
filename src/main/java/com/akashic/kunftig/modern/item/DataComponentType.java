package com.akashic.kunftig.modern.item;

import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;

import com.akashic.kunftig.modern.codec.Codec;
import com.google.common.collect.MapMaker;

/**
 * Typed data on an item stack, as the data components of 1.20.5+: a value stored in the stack's NBT under
 * {@code <namespace>.<id>} through a {@link Codec}.
 * <p>
 * Values must be immutable. Decoded values are cached per NBT tag instance (weak keys, identity), so reading an
 * unchanged stack again does not parse NBT, and a copied stack (new tags) is parsed once. Every write installs a
 * fresh tag. 1.7.10 uses {@code null} for an empty stack: every method accepts it.
 */
public final class DataComponentType<T> {

    private final String namespace;
    private final String id;
    private final Codec<T> codec;
    private final Map<NBTBase, Object> cache = new MapMaker().weakKeys()
        .concurrencyLevel(2)
        .makeMap();

    /**
     * @param namespace the root compound in the stack tag (usually the mod id)
     * @param id        the key inside it
     */
    public DataComponentType(String namespace, String id, Codec<T> codec) {
        this.namespace = namespace;
        this.id = id;
        this.codec = codec;
    }

    public String namespace() {
        return namespace;
    }

    public String id() {
        return id;
    }

    public Codec<T> codec() {
        return codec;
    }

    private NBTBase tag(ItemStack stack) {
        if (stack == null || stack.stackTagCompound == null || !stack.stackTagCompound.hasKey(namespace, 10)) {
            return null;
        }
        NBTTagCompound root = stack.stackTagCompound.getCompoundTag(namespace);
        return root.hasKey(id) ? root.getTag(id) : null;
    }

    public boolean has(ItemStack stack) {
        return tag(stack) != null;
    }

    /** The value, or {@code null} when absent or malformed. */
    @SuppressWarnings("unchecked")
    public T get(ItemStack stack) {
        NBTBase tag = tag(stack);
        if (tag == null) {
            return null;
        }
        Object cached = cache.get(tag);
        if (cached != null) {
            return (T) cached;
        }
        Optional<T> parsed = codec.parse(tag);
        if (!parsed.isPresent()) {
            return null;
        }
        cache.put(tag, parsed.get());
        return parsed.get();
    }

    public T getOrDefault(ItemStack stack, T defaultValue) {
        T value = get(stack);
        return value == null ? defaultValue : value;
    }

    /** Writes the value; {@code null} removes it. */
    public void set(ItemStack stack, T value) {
        if (stack == null) {
            return;
        }
        if (value == null) {
            remove(stack);
            return;
        }
        if (stack.stackTagCompound == null) {
            stack.setTagCompound(new NBTTagCompound());
        }
        if (!stack.stackTagCompound.hasKey(namespace, 10)) {
            stack.stackTagCompound.setTag(namespace, new NBTTagCompound());
        }
        NBTBase tag = codec.encode(value);
        stack.stackTagCompound.getCompoundTag(namespace)
            .setTag(id, tag);
        cache.put(tag, value);
    }

    public T update(ItemStack stack, T defaultValue, UnaryOperator<T> updater) {
        T value = updater.apply(getOrDefault(stack, defaultValue));
        set(stack, value);
        return value;
    }

    /** Removes the value, and the namespace compound and stack tag once they are empty. */
    public void remove(ItemStack stack) {
        if (stack == null || stack.stackTagCompound == null || !stack.stackTagCompound.hasKey(namespace, 10)) {
            return;
        }
        NBTTagCompound root = stack.stackTagCompound.getCompoundTag(namespace);
        root.removeTag(id);
        if (root.hasNoTags()) {
            stack.stackTagCompound.removeTag(namespace);
        }
        if (stack.stackTagCompound.hasNoTags()) {
            stack.setTagCompound(null);
        }
    }

    @Override
    public String toString() {
        return namespace + "." + id;
    }
}
