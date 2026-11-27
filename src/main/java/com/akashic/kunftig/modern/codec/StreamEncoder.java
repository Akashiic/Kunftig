package com.akashic.kunftig.modern.codec;

/** Writes a value to a buffer. */
@FunctionalInterface
public interface StreamEncoder<B, V> {

    void encode(B buf, V value);
}
