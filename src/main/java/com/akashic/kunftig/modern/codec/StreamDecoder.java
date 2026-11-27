package com.akashic.kunftig.modern.codec;

/** Reads a value from a buffer. */
@FunctionalInterface
public interface StreamDecoder<B, V> {

    V decode(B buf);
}
