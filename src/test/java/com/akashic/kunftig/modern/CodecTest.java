package com.akashic.kunftig.modern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;

import org.junit.jupiter.api.Test;

import com.akashic.kunftig.modern.codec.ByteBufCodecs;
import com.akashic.kunftig.modern.codec.Codec;
import com.akashic.kunftig.modern.codec.CodecException;
import com.akashic.kunftig.modern.codec.RecordCodecBuilder;
import com.akashic.kunftig.modern.codec.StreamCodec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

class CodecTest {

    /** A small record-like value as modern mods write them. */
    static final class Sample {

        static final Codec<Sample> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                Codec.INT.optionalFieldOf("level", 0)
                    .forGetter(s -> s.level),
                Codec.DOUBLE.optionalFieldOf("experience", 0D)
                    .forGetter(s -> s.experience),
                Codec.STRING.fieldOf("id")
                    .forGetter(s -> s.id),
                Codec.DOUBLE.optionalFieldOf("override")
                    .forGetter(s -> s.override),
                Codec.unboundedMap(Codec.STRING, Codec.list(Codec.INT))
                    .optionalFieldOf("links", new HashMap<>())
                    .forGetter(s -> s.links),
                Codec.BOOL.optionalFieldOf("enabled", true)
                    .forGetter(s -> s.enabled))
                .apply(instance, Sample::new));

        final int level;
        final double experience;
        final String id;
        final Optional<Double> override;
        final Map<String, List<Integer>> links;
        final boolean enabled;

        Sample(int level, double experience, String id, Optional<Double> override, Map<String, List<Integer>> links,
            boolean enabled) {
            this.level = level;
            this.experience = experience;
            this.id = id;
            this.override = override;
            this.links = links;
            this.enabled = enabled;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Sample)) {
                return false;
            }
            Sample s = (Sample) o;
            return level == s.level && experience == s.experience
                && id.equals(s.id)
                && override.equals(s.override)
                && links.equals(s.links)
                && enabled == s.enabled;
        }

        @Override
        public int hashCode() {
            return Objects.hash(level, experience, id, override, links, enabled);
        }
    }

    private static Sample sample() {
        Map<String, List<Integer>> links = new LinkedHashMap<>();
        links.put("0", Arrays.asList(1, 2));
        links.put("3", Arrays.<Integer>asList());
        return new Sample(14, 37.5D, "blast", Optional.of(4.5D), links, false);
    }

    @Test
    void recordRoundTripThroughRealNbtIo() throws Exception {
        NBTTagCompound tag = (NBTTagCompound) Sample.CODEC.encode(sample());
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        CompressedStreamTools.write(tag, new DataOutputStream(bytes));
        NBTTagCompound read = CompressedStreamTools
            .read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
        assertEquals(sample(), Sample.CODEC.decode(read));
    }

    @Test
    void defaultsAreNotWrittenAndComeBack() {
        Sample defaults = new Sample(0, 0D, "x", Optional.<Double>empty(), new HashMap<>(), true);
        NBTTagCompound tag = (NBTTagCompound) Sample.CODEC.encode(defaults);
        assertEquals(
            1,
            tag.func_150296_c()
                .size(),
            "only the required field is written");
        assertEquals(defaults, Sample.CODEC.decode(tag));
    }

    @Test
    void malformedOptionalFieldFallsBackToDefault() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("id", "x");
        tag.setString("level", "not a number");
        assertEquals(0, Sample.CODEC.decode(tag).level);
    }

    @Test
    void missingRequiredFieldFails() {
        assertThrows(CodecException.class, () -> Sample.CODEC.decode(new NBTTagCompound()));
        assertFalse(
            Sample.CODEC.parse(new NBTTagCompound())
                .isPresent());
    }

    @Test
    void streamCodecCompositeRoundTrip() {
        StreamCodec<ByteBuf, Map.Entry<Integer, String>> codec = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            Map.Entry::getKey,
            ByteBufCodecs.STRING_UTF8,
            Map.Entry::getValue,
            (k, v) -> new java.util.AbstractMap.SimpleEntry<>(k, v));
        ByteBuf buf = Unpooled.buffer();
        codec.encode(buf, new java.util.AbstractMap.SimpleEntry<>(42, "relic"));
        Map.Entry<Integer, String> decoded = codec.decode(buf);
        assertEquals(42, decoded.getKey());
        assertEquals("relic", decoded.getValue());
        assertFalse(buf.isReadable());
    }

    @Test
    void nbtCodecOverTheWire() {
        StreamCodec<ByteBuf, Sample> codec = ByteBufCodecs.fromCodec(Sample.CODEC);
        ByteBuf buf = Unpooled.buffer();
        codec.encode(buf, sample());
        assertEquals(sample(), codec.decode(buf));
    }

    @Test
    void listsKeepOrderAndAreNotConsumed() {
        Codec<List<String>> codec = Codec.list(Codec.STRING);
        net.minecraft.nbt.NBTTagList tag = (net.minecraft.nbt.NBTTagList) codec.encode(Arrays.asList("a", "b", "c"));
        assertEquals(Arrays.asList("a", "b", "c"), codec.decode(tag));
        assertEquals(3, tag.tagCount(), "decoding must not empty the list tag");
        assertFalse(
            codec.parse(new NBTTagCompound())
                .isPresent(),
            "a compound is not a list");
    }
}
