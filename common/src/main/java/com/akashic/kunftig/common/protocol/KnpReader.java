package com.akashic.kunftig.common.protocol;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/** Bounds-checked reader mirroring {@link KnpWriter}. Never allocates more than the declared limits allow. */
public final class KnpReader {

    private final byte[] data;
    private int position;

    public KnpReader(byte[] data) {
        this.data = data;
    }

    public int remaining() {
        return data.length - position;
    }

    private void require(int bytes) throws KnpDecodeException {
        if (bytes < 0 || remaining() < bytes) {
            throw new KnpDecodeException("Truncated payload: need " + bytes + " byte(s), have " + remaining());
        }
    }

    public int readUnsignedByte() throws KnpDecodeException {
        require(1);
        return data[position++] & 0xFF;
    }

    public boolean readBoolean() throws KnpDecodeException {
        int value = readUnsignedByte();
        if (value > 1) {
            throw new KnpDecodeException("Invalid boolean value " + value);
        }
        return value == 1;
    }

    public int readInt() throws KnpDecodeException {
        require(4);
        int value = ((data[position] & 0xFF) << 24) | ((data[position + 1] & 0xFF) << 16)
            | ((data[position + 2] & 0xFF) << 8) | (data[position + 3] & 0xFF);
        position += 4;
        return value;
    }

    public int readVarInt() throws KnpDecodeException {
        int value = 0;
        for (int shift = 0; shift < 35; shift += 7) {
            int b = readUnsignedByte();
            value |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                return value;
            }
        }
        throw new KnpDecodeException("VarInt is longer than 5 bytes");
    }

    /** Reads a VarInt that must lie in {@code [min, max]}. */
    public int readVarInt(int min, int max) throws KnpDecodeException {
        int value = readVarInt();
        if (value < min || value > max) {
            throw new KnpDecodeException("Value " + value + " outside [" + min + ", " + max + "]");
        }
        return value;
    }

    public String readString(int maxChars) throws KnpDecodeException {
        // A char needs at most 3 UTF-8 bytes (supplementary code points use 4 bytes for 2 chars).
        int byteLength = readVarInt(0, maxChars * 3);
        require(byteLength);
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT);
        String value;
        try {
            CharBuffer chars = decoder.decode(ByteBuffer.wrap(data, position, byteLength));
            value = chars.toString();
        } catch (CharacterCodingException e) {
            throw new KnpDecodeException("Invalid UTF-8 string");
        }
        position += byteLength;
        if (value.length() > maxChars) {
            throw new KnpDecodeException("String of " + value.length() + " chars exceeds limit " + maxChars);
        }
        return value;
    }

    public void expectEnd() throws KnpDecodeException {
        if (remaining() != 0) {
            throw new KnpDecodeException(remaining() + " trailing byte(s)");
        }
    }
}
