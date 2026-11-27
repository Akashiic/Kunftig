package com.akashic.kunftig.modern.codec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagByte;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagDouble;
import net.minecraft.nbt.NBTTagFloat;
import net.minecraft.nbt.NBTTagInt;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagLong;
import net.minecraft.nbt.NBTTagString;

/**
 * NBT codec with the shape of Mojang's DataFixerUpper {@code Codec} (1.16+), for porting modern code: primitives,
 * lists, string-keyed maps, {@code fieldOf}/{@code optionalFieldOf} and {@link RecordCodecBuilder}. It encodes to
 * plain 1.7.10 NBT; {@link #decode} throws {@link CodecException} on malformed input and {@link #parse} does not.
 */
public interface Codec<A> {

    NBTBase encode(A value);

    A decode(NBTBase tag);

    /** Decodes without throwing. */
    default Optional<A> parse(NBTBase tag) {
        try {
            return Optional.ofNullable(decode(tag));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    Codec<Integer> INT = Codecs.primitive("int", NBTTagInt::new, NBTBase.NBTPrimitive::func_150287_d);
    Codec<Long> LONG = Codecs.primitive("long", NBTTagLong::new, NBTBase.NBTPrimitive::func_150291_c);
    Codec<Float> FLOAT = Codecs.primitive("float", NBTTagFloat::new, NBTBase.NBTPrimitive::func_150288_h);
    Codec<Double> DOUBLE = Codecs.primitive("double", NBTTagDouble::new, NBTBase.NBTPrimitive::func_150286_g);
    Codec<Boolean> BOOL = Codecs
        .primitive("bool", value -> new NBTTagByte((byte) (value ? 1 : 0)), tag -> tag.func_150290_f() != 0);

    Codec<String> STRING = new Codec<String>() {

        @Override
        public NBTBase encode(String value) {
            return new NBTTagString(value);
        }

        @Override
        public String decode(NBTBase tag) {
            if (tag instanceof NBTTagString) {
                return ((NBTTagString) tag).func_150285_a_();
            }
            throw new CodecException("Expected string, got " + tag);
        }
    };

    static Codec<Integer> intRange(int min, int max) {
        return INT.validate(
            value -> value >= min && value <= max,
            value -> "Value " + value + " outside of range [" + min + ":" + max + "]");
    }

    static Codec<Double> doubleRange(double min, double max) {
        return DOUBLE.validate(
            value -> value >= min && value <= max,
            value -> "Value " + value + " outside of range [" + min + ":" + max + "]");
    }

    static <A> Codec<A> of(Function<A, NBTBase> encoder, Function<NBTBase, A> decoder) {
        return new Codec<A>() {

            @Override
            public NBTBase encode(A value) {
                return encoder.apply(value);
            }

            @Override
            public A decode(NBTBase tag) {
                return decoder.apply(tag);
            }
        };
    }

    static <E> Codec<List<E>> list(Codec<E> element) {
        return new Codec<List<E>>() {

            @Override
            public NBTBase encode(List<E> value) {
                NBTTagList list = new NBTTagList();
                for (E entry : value) {
                    list.appendTag(element.encode(entry));
                }
                return list;
            }

            @Override
            public List<E> decode(NBTBase tag) {
                if (!(tag instanceof NBTTagList)) {
                    throw new CodecException("Expected list, got " + tag);
                }
                List<NBTBase> elements = Codecs.elements((NBTTagList) tag);
                List<E> result = new ArrayList<>(elements.size());
                for (NBTBase entry : elements) {
                    result.add(element.decode(entry));
                }
                return result;
            }
        };
    }

    /** A map stored as a compound; the key codec must encode to string tags. Decoded maps keep the tag's order. */
    static <K, V> Codec<Map<K, V>> unboundedMap(Codec<K> key, Codec<V> value) {
        return new Codec<Map<K, V>>() {

            @Override
            public NBTBase encode(Map<K, V> map) {
                NBTTagCompound compound = new NBTTagCompound();
                for (Map.Entry<K, V> entry : map.entrySet()) {
                    NBTBase encodedKey = key.encode(entry.getKey());
                    if (!(encodedKey instanceof NBTTagString)) {
                        throw new CodecException("Map keys must encode to strings: " + entry.getKey());
                    }
                    compound.setTag(((NBTTagString) encodedKey).func_150285_a_(), value.encode(entry.getValue()));
                }
                return compound;
            }

            @Override
            public Map<K, V> decode(NBTBase tag) {
                if (!(tag instanceof NBTTagCompound)) {
                    throw new CodecException("Expected compound, got " + tag);
                }
                NBTTagCompound compound = (NBTTagCompound) tag;
                Map<K, V> result = new LinkedHashMap<>();
                for (String name : Codecs.keys(compound)) {
                    result.put(key.decode(new NBTTagString(name)), value.decode(compound.getTag(name)));
                }
                return result;
            }
        };
    }

    default <B> Codec<B> xmap(Function<? super A, ? extends B> to, Function<? super B, ? extends A> from) {
        Codec<A> self = this;
        return new Codec<B>() {

            @Override
            public NBTBase encode(B value) {
                return self.encode(from.apply(value));
            }

            @Override
            public B decode(NBTBase tag) {
                return to.apply(self.decode(tag));
            }
        };
    }

    default Codec<A> validate(Predicate<A> check, Function<A, String> error) {
        Codec<A> self = this;
        return new Codec<A>() {

            @Override
            public NBTBase encode(A value) {
                if (!check.test(value)) {
                    throw new CodecException(error.apply(value));
                }
                return self.encode(value);
            }

            @Override
            public A decode(NBTBase tag) {
                A value = self.decode(tag);
                if (!check.test(value)) {
                    throw new CodecException(error.apply(value));
                }
                return value;
            }
        };
    }

    default Codec<List<A>> listOf() {
        return list(this);
    }

    default MapCodec<A> fieldOf(String name) {
        return MapCodec.required(name, this);
    }

    /** Missing or malformed values decode to {@code defaultValue}; a value equal to it is not written. */
    default MapCodec<A> optionalFieldOf(String name, A defaultValue) {
        return MapCodec.optional(name, this, defaultValue);
    }

    default MapCodec<Optional<A>> optionalFieldOf(String name) {
        return MapCodec.optional(name, this);
    }

    default MapCodec<A> lenientOptionalFieldOf(String name, A defaultValue) {
        return optionalFieldOf(name, defaultValue);
    }

    default MapCodec<Optional<A>> lenientOptionalFieldOf(String name) {
        return optionalFieldOf(name);
    }
}
