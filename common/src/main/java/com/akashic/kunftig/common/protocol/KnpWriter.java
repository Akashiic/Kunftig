package com.akashic.kunftig.common.protocol;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/** Minimal big-endian writer using Minecraft's VarInt and length-prefixed UTF-8 string conventions. */
public final class KnpWriter {

    private final ByteArrayOutputStream out = new ByteArrayOutputStream(64);

    public KnpWriter writeByte(int value) {
        out.write(value & 0xFF);
        return this;
    }

    public KnpWriter writeBoolean(boolean value) {
        return writeByte(value ? 1 : 0);
    }

    public KnpWriter writeInt(int value) {
        out.write((value >>> 24) & 0xFF);
        out.write((value >>> 16) & 0xFF);
        out.write((value >>> 8) & 0xFF);
        out.write(value & 0xFF);
        return this;
    }

    public KnpWriter writeVarInt(int value) {
        int v = value;
        while ((v & ~0x7F) != 0) {
            out.write((v & 0x7F) | 0x80);
            v >>>= 7;
        }
        out.write(v);
        return this;
    }

    /**
     * Writes a VarInt byte length followed by UTF-8 bytes.
     *
     * @param maxChars the same limit the reader applies; enforced here too so a sender can never
     *                 produce a payload its peer is required to reject
     */
    public KnpWriter writeString(String value, int maxChars) {
        if (value.length() > maxChars) {
            throw new IllegalArgumentException("String of " + value.length() + " chars exceeds limit " + maxChars);
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        writeVarInt(bytes.length);
        out.write(bytes, 0, bytes.length);
        return this;
    }

    public int size() {
        return out.size();
    }

    public byte[] toByteArray() {
        return out.toByteArray();
    }
}
