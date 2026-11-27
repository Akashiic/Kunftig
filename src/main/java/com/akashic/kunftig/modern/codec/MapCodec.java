package com.akashic.kunftig.modern.codec;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;

/** One or more fields inside a compound, as DFU's {@code MapCodec}. */
public abstract class MapCodec<A> {

    public abstract void encode(A value, NBTTagCompound tag);

    public abstract A decode(NBTTagCompound tag);

    public <O> RecordCodecBuilder<O, A> forGetter(Function<O, A> getter) {
        return RecordCodecBuilder.of(getter, this);
    }

    public Codec<A> codec() {
        MapCodec<A> self = this;
        return new Codec<A>() {

            @Override
            public NBTBase encode(A value) {
                NBTTagCompound tag = new NBTTagCompound();
                self.encode(value, tag);
                return tag;
            }

            @Override
            public A decode(NBTBase tag) {
                if (tag instanceof NBTTagCompound) {
                    return self.decode((NBTTagCompound) tag);
                }
                throw new CodecException("Expected compound, got " + tag);
            }
        };
    }

    public <B> MapCodec<B> xmap(Function<? super A, ? extends B> to, Function<? super B, ? extends A> from) {
        MapCodec<A> self = this;
        return new MapCodec<B>() {

            @Override
            public void encode(B value, NBTTagCompound tag) {
                self.encode(from.apply(value), tag);
            }

            @Override
            public B decode(NBTTagCompound tag) {
                return to.apply(self.decode(tag));
            }
        };
    }

    static <A> MapCodec<A> required(String name, Codec<A> codec) {
        return new MapCodec<A>() {

            @Override
            public void encode(A value, NBTTagCompound tag) {
                tag.setTag(name, codec.encode(value));
            }

            @Override
            public A decode(NBTTagCompound tag) {
                if (!tag.hasKey(name)) {
                    throw new CodecException("Missing field '" + name + "'");
                }
                try {
                    return codec.decode(tag.getTag(name));
                } catch (CodecException e) {
                    throw new CodecException("Field '" + name + "': " + e.getMessage(), e);
                }
            }
        };
    }

    static <A> MapCodec<A> optional(String name, Codec<A> codec, A defaultValue) {
        return new MapCodec<A>() {

            @Override
            public void encode(A value, NBTTagCompound tag) {
                if (!Objects.equals(value, defaultValue)) {
                    tag.setTag(name, codec.encode(value));
                }
            }

            @Override
            public A decode(NBTTagCompound tag) {
                if (!tag.hasKey(name)) {
                    return defaultValue;
                }
                return codec.parse(tag.getTag(name))
                    .orElse(defaultValue);
            }
        };
    }

    static <A> MapCodec<Optional<A>> optional(String name, Codec<A> codec) {
        return new MapCodec<Optional<A>>() {

            @Override
            public void encode(Optional<A> value, NBTTagCompound tag) {
                if (value.isPresent()) {
                    tag.setTag(name, codec.encode(value.get()));
                }
            }

            @Override
            public Optional<A> decode(NBTTagCompound tag) {
                if (!tag.hasKey(name)) {
                    return Optional.empty();
                }
                return codec.parse(tag.getTag(name));
            }
        };
    }
}
