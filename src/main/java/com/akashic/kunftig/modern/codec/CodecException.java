package com.akashic.kunftig.modern.codec;

/** A value that cannot be encoded to, or decoded from, NBT. */
public class CodecException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public CodecException(String message) {
        super(message);
    }

    public CodecException(String message, Throwable cause) {
        super(message, cause);
    }
}
