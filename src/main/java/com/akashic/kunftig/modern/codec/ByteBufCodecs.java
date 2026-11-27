package com.akashic.kunftig.modern.codec;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.ToIntFunction;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import cpw.mods.fml.common.network.ByteBufUtils;
import io.netty.buffer.ByteBuf;

/** Buffer codecs with the names of the modern {@code ByteBufCodecs}. */
public final class ByteBufCodecs {

    private ByteBufCodecs() {}

    public static final StreamCodec<ByteBuf, Boolean> BOOL = StreamCodec
        .of((buf, value) -> buf.writeBoolean(value), ByteBuf::readBoolean);
    public static final StreamCodec<ByteBuf, Byte> BYTE = StreamCodec
        .of((buf, value) -> buf.writeByte(value), ByteBuf::readByte);
    public static final StreamCodec<ByteBuf, Short> SHORT = StreamCodec
        .of((buf, value) -> buf.writeShort(value), ByteBuf::readShort);
    public static final StreamCodec<ByteBuf, Integer> INT = StreamCodec
        .of((buf, value) -> buf.writeInt(value), ByteBuf::readInt);
    public static final StreamCodec<ByteBuf, Integer> VAR_INT = StreamCodec
        .of((buf, value) -> ByteBufUtils.writeVarInt(buf, value, 5), buf -> ByteBufUtils.readVarInt(buf, 5));
    public static final StreamCodec<ByteBuf, Long> LONG = StreamCodec
        .of((buf, value) -> buf.writeLong(value), ByteBuf::readLong);
    public static final StreamCodec<ByteBuf, Float> FLOAT = StreamCodec
        .of((buf, value) -> buf.writeFloat(value), ByteBuf::readFloat);
    public static final StreamCodec<ByteBuf, Double> DOUBLE = StreamCodec
        .of((buf, value) -> buf.writeDouble(value), ByteBuf::readDouble);
    public static final StreamCodec<ByteBuf, String> STRING_UTF8 = StreamCodec
        .of(ByteBufUtils::writeUTF8String, ByteBufUtils::readUTF8String);
    public static final StreamCodec<ByteBuf, NBTTagCompound> COMPOUND_TAG = StreamCodec
        .of(ByteBufUtils::writeTag, ByteBufUtils::readTag);
    public static final StreamCodec<ByteBuf, ItemStack> ITEM_STACK = StreamCodec
        .of(ByteBufUtils::writeItemStack, ByteBufUtils::readItemStack);
    public static final StreamCodec<ByteBuf, UUID> UUID = StreamCodec.of((buf, value) -> {
        buf.writeLong(value.getMostSignificantBits());
        buf.writeLong(value.getLeastSignificantBits());
    }, buf -> new UUID(buf.readLong(), buf.readLong()));

    /** Sends an NBT {@link Codec} value over the wire inside a compound tag. */
    public static <T> StreamCodec<ByteBuf, T> fromCodec(Codec<T> codec) {
        return COMPOUND_TAG.map(tag -> codec.decode(tag.getTag("v")), value -> {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setTag("v", codec.encode(value));
            return tag;
        });
    }

    public static <B extends ByteBuf, V, C extends Collection<V>> StreamCodec<B, C> collection(IntFunction<C> factory,
        StreamCodec<? super B, V> element) {
        return StreamCodec.of((buf, value) -> {
            ByteBufUtils.writeVarInt(buf, value.size(), 5);
            for (V entry : value) {
                element.encode(buf, entry);
            }
        }, buf -> {
            int size = ByteBufUtils.readVarInt(buf, 5);
            C result = factory.apply(size);
            for (int i = 0; i < size; i++) {
                result.add(element.decode(buf));
            }
            return result;
        });
    }

    public static <B extends ByteBuf, V> Function<StreamCodec<? super B, V>, StreamCodec<B, List<V>>> list() {
        return element -> collection(ArrayList::new, element);
    }

    public static <B extends ByteBuf, K, V, M extends Map<K, V>> StreamCodec<B, M> map(IntFunction<? extends M> factory,
        StreamCodec<? super B, K> key, StreamCodec<? super B, V> value) {
        return StreamCodec.of((buf, map) -> {
            ByteBufUtils.writeVarInt(buf, map.size(), 5);
            for (Map.Entry<K, V> entry : map.entrySet()) {
                key.encode(buf, entry.getKey());
                value.encode(buf, entry.getValue());
            }
        }, buf -> {
            int size = ByteBufUtils.readVarInt(buf, 5);
            M result = factory.apply(size);
            for (int i = 0; i < size; i++) {
                K k = key.decode(buf);
                result.put(k, value.decode(buf));
            }
            return result;
        });
    }

    public static <B extends ByteBuf, V> StreamCodec<B, Optional<V>> optional(StreamCodec<? super B, V> codec) {
        return StreamCodec.of((buf, value) -> {
            buf.writeBoolean(value.isPresent());
            if (value.isPresent()) {
                codec.encode(buf, value.get());
            }
        }, buf -> buf.readBoolean() ? Optional.of(codec.decode(buf)) : Optional.<V>empty());
    }

    public static <T> StreamCodec<ByteBuf, T> idMapper(IntFunction<T> byId, ToIntFunction<T> toId) {
        return StreamCodec.of(
            (buf, value) -> ByteBufUtils.writeVarInt(buf, toId.applyAsInt(value), 5),
            buf -> byId.apply(ByteBufUtils.readVarInt(buf, 5)));
    }

    public static <E extends Enum<E>> StreamCodec<ByteBuf, E> enumCodec(Class<E> type) {
        E[] values = type.getEnumConstants();
        return idMapper(id -> values[Math.floorMod(id, values.length)], Enum::ordinal);
    }
}
