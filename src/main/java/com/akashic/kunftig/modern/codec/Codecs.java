package com.akashic.kunftig.modern.codec;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

/** Helpers behind {@link Codec}'s constants (Java 8 interfaces cannot have private methods). */
final class Codecs {

    private Codecs() {}

    static <T> Codec<T> primitive(String name, Function<T, NBTBase> encoder,
        Function<NBTBase.NBTPrimitive, T> decoder) {
        return new Codec<T>() {

            @Override
            public NBTBase encode(T value) {
                return encoder.apply(value);
            }

            @Override
            public T decode(NBTBase tag) {
                if (tag instanceof NBTBase.NBTPrimitive) {
                    return decoder.apply((NBTBase.NBTPrimitive) tag);
                }
                throw new CodecException("Expected " + name + ", got " + tag);
            }
        };
    }

    @SuppressWarnings("unchecked")
    static List<String> keys(NBTTagCompound compound) {
        return new ArrayList<>((Set<String>) compound.func_150296_c());
    }

    /** The elements of a list tag, without removing them (1.7.10 has no generic getter). */
    static List<NBTBase> elements(NBTTagList list) {
        NBTTagList copy = (NBTTagList) list.copy();
        int size = copy.tagCount();
        NBTBase[] out = new NBTBase[size];
        // Remove from the end: each removal is O(1) on the backing ArrayList.
        for (int i = size - 1; i >= 0; i--) {
            out[i] = copy.removeTag(i);
        }
        return java.util.Arrays.asList(out);
    }
}
