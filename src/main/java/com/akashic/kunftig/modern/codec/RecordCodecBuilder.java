package com.akashic.kunftig.modern.codec;

import java.util.function.BiConsumer;
import java.util.function.Function;

import net.minecraft.nbt.NBTTagCompound;

/**
 * Record codecs written as in DFU:
 *
 * <pre>
 * {@code
 * RecordCodecBuilder.create(instance -> instance.group(
 *     Codec.INT.optionalFieldOf("level", 0).forGetter(Foo::getLevel),
 *     Codec.STRING.fieldOf("id").forGetter(Foo::getId)
 * ).apply(instance, Foo::new));
 * }
 * </pre>
 *
 * A builder is either one field ({@code forGetter}) or a whole record ({@code apply}).
 */
public final class RecordCodecBuilder<O, F> {

    private final BiConsumer<O, NBTTagCompound> encoder;
    private final Function<NBTTagCompound, F> decoder;

    private RecordCodecBuilder(BiConsumer<O, NBTTagCompound> encoder, Function<NBTTagCompound, F> decoder) {
        this.encoder = encoder;
        this.decoder = decoder;
    }

    static <O, F> RecordCodecBuilder<O, F> of(Function<O, F> getter, MapCodec<F> codec) {
        return new RecordCodecBuilder<>((value, tag) -> codec.encode(getter.apply(value), tag), codec::decode);
    }

    static <O> RecordCodecBuilder<O, O> root(BiConsumer<O, NBTTagCompound> encoder,
        Function<NBTTagCompound, O> decoder) {
        return new RecordCodecBuilder<>(encoder, decoder);
    }

    void encodeField(O value, NBTTagCompound tag) {
        encoder.accept(value, tag);
    }

    F decodeField(NBTTagCompound tag) {
        return decoder.apply(tag);
    }

    public static <O> Codec<O> create(Function<Instance<O>, RecordCodecBuilder<O, O>> builder) {
        return mapCodec(builder).codec();
    }

    public static <O> MapCodec<O> mapCodec(Function<Instance<O>, RecordCodecBuilder<O, O>> builder) {
        RecordCodecBuilder<O, O> fields = builder.apply(new Instance<>());
        return new MapCodec<O>() {

            @Override
            public void encode(O value, NBTTagCompound tag) {
                fields.encodeField(value, tag);
            }

            @Override
            public O decode(NBTTagCompound tag) {
                return fields.decodeField(tag);
            }
        };
    }

    /** Entry point of {@code instance.group(...)}. */
    public static final class Instance<O> extends RecordCodecBuilderGroups<O> {

        private Instance() {}
    }
}
