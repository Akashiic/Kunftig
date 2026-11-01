package com.akashic.kunftig.common.protocol;

import java.util.ArrayList;
import java.util.List;

import com.akashic.kunftig.common.text.NameStyle;
import com.akashic.kunftig.common.text.TextColor;
import com.akashic.kunftig.common.text.TextDecoration;

/**
 * Binary codec for KNP version 1. Payload = VarInt packet id + body.
 * <p>
 * Decoding is strict (limits, enum ranges, no trailing bytes) except for unknown packet ids, which decode to
 * {@code null} so that newer servers can add packets without breaking older clients.
 */
public final class KnpCodec {

    private static final int FIELD_PREFIX = 1;
    private static final int FIELD_SUFFIX = 1 << 1;
    private static final int FIELD_NAME_STYLE = 1 << 2;
    private static final int FIELD_VISIBILITY = 1 << 3;
    private static final int KNOWN_FIELDS = FIELD_PREFIX | FIELD_SUFFIX | FIELD_NAME_STYLE | FIELD_VISIBILITY;

    private KnpCodec() {}

    // ---------------------------------------------------------------- serverbound

    public static byte[] encode(KnpMessage.Serverbound message) {
        KnpWriter w = new KnpWriter();
        if (message instanceof KnpMessage.Hello) {
            KnpMessage.Hello hello = (KnpMessage.Hello) message;
            w.writeVarInt(KnpProtocol.SB_HELLO)
                .writeVarInt(hello.minVersion)
                .writeVarInt(hello.maxVersion)
                .writeVarInt(hello.capabilities)
                .writeString(hello.clientVersion, KnpProtocol.MAX_VERSION_STRING);
        } else {
            throw new IllegalArgumentException("Unknown serverbound message " + message);
        }
        return finish(w);
    }

    /** Returns the decoded message, or {@code null} for an unknown packet id. */
    public static KnpMessage.Serverbound decodeServerbound(byte[] payload) throws KnpDecodeException {
        checkSize(payload);
        KnpReader r = new KnpReader(payload);
        int id = r.readVarInt();
        if (id != KnpProtocol.SB_HELLO) {
            return null;
        }
        // HELLO is decoded the same way in every version, so a version can always be negotiated.
        int min = r.readVarInt(0, Integer.MAX_VALUE);
        int max = r.readVarInt(min, Integer.MAX_VALUE);
        int caps = r.readVarInt();
        String version = r.readString(KnpProtocol.MAX_VERSION_STRING);
        // Newer clients may append fields to HELLO; they are ignored.
        return new KnpMessage.Hello(min, max, caps, version);
    }

    // ---------------------------------------------------------------- clientbound

    public static byte[] encode(KnpMessage.Clientbound message) {
        KnpWriter w = new KnpWriter();
        if (message instanceof KnpMessage.HelloAck) {
            KnpMessage.HelloAck ack = (KnpMessage.HelloAck) message;
            w.writeVarInt(KnpProtocol.CB_HELLO_ACK)
                .writeVarInt(ack.version)
                .writeString(ack.serverVersion, KnpProtocol.MAX_VERSION_STRING);
        } else if (message instanceof KnpMessage.Probe) {
            w.writeVarInt(KnpProtocol.CB_PROBE);
        } else if (message instanceof KnpMessage.Reset) {
            w.writeVarInt(KnpProtocol.CB_RESET);
        } else if (message instanceof KnpMessage.TeamStyle) {
            writeTeamStyle(w, (KnpMessage.TeamStyle) message);
        } else if (message instanceof KnpMessage.TeamStyleRemove) {
            w.writeVarInt(KnpProtocol.CB_TEAM_STYLE_REMOVE)
                .writeString(((KnpMessage.TeamStyleRemove) message).team, KnpProtocol.MAX_TEAM_NAME);
        } else {
            throw new IllegalArgumentException("Unknown clientbound message " + message);
        }
        return finish(w);
    }

    private static void writeTeamStyle(KnpWriter w, KnpMessage.TeamStyle style) {
        int fields = (style.prefixJson != null ? FIELD_PREFIX : 0) | (style.suffixJson != null ? FIELD_SUFFIX : 0)
            | (style.nameStyle != null ? FIELD_NAME_STYLE : 0) | (style.visibility != null ? FIELD_VISIBILITY : 0);
        w.writeVarInt(KnpProtocol.CB_TEAM_STYLE).writeString(style.team, KnpProtocol.MAX_TEAM_NAME).writeVarInt(fields);
        if (style.prefixJson != null) {
            w.writeString(style.prefixJson, KnpProtocol.MAX_COMPONENT_JSON);
        }
        if (style.suffixJson != null) {
            w.writeString(style.suffixJson, KnpProtocol.MAX_COMPONENT_JSON);
        }
        if (style.nameStyle != null) {
            NameStyle name = style.nameStyle;
            w.writeByte(name.decorationMask()).writeVarInt(name.colors().size());
            for (TextColor color : name.colors()) {
                w.writeInt(color.value());
            }
        }
        if (style.visibility != null) {
            w.writeByte(style.visibility.ordinal());
        }
    }

    /**
     * Decodes a clientbound payload of the negotiated {@code version}.
     *
     * @return the message, or {@code null} for an unknown packet id
     */
    public static KnpMessage.Clientbound decodeClientbound(byte[] payload, int version) throws KnpDecodeException {
        checkSize(payload);
        if (version != 1) {
            throw new KnpDecodeException("Unsupported protocol version " + version);
        }
        KnpReader r = new KnpReader(payload);
        int id = r.readVarInt();
        KnpMessage.Clientbound message;
        switch (id) {
            case KnpProtocol.CB_HELLO_ACK:
                message = new KnpMessage.HelloAck(r.readVarInt(0, Integer.MAX_VALUE),
                    r.readString(KnpProtocol.MAX_VERSION_STRING));
                break;
            case KnpProtocol.CB_PROBE:
                message = KnpMessage.Probe.INSTANCE;
                break;
            case KnpProtocol.CB_RESET:
                message = KnpMessage.Reset.INSTANCE;
                break;
            case KnpProtocol.CB_TEAM_STYLE:
                message = readTeamStyle(r);
                break;
            case KnpProtocol.CB_TEAM_STYLE_REMOVE:
                message = new KnpMessage.TeamStyleRemove(r.readString(KnpProtocol.MAX_TEAM_NAME));
                break;
            default:
                return null;
        }
        r.expectEnd();
        return message;
    }

    /** Reads only the packet id, e.g. to accept {@code HELLO_ACK} before a version is known. */
    public static int peekId(byte[] payload) throws KnpDecodeException {
        return new KnpReader(payload).readVarInt();
    }

    private static KnpMessage.TeamStyle readTeamStyle(KnpReader r) throws KnpDecodeException {
        String team = r.readString(KnpProtocol.MAX_TEAM_NAME);
        int fields = r.readVarInt();
        if ((fields & ~KNOWN_FIELDS) != 0) {
            throw new KnpDecodeException("Unknown team style fields " + Integer.toBinaryString(fields));
        }
        String prefix = (fields & FIELD_PREFIX) != 0 ? r.readString(KnpProtocol.MAX_COMPONENT_JSON) : null;
        String suffix = (fields & FIELD_SUFFIX) != 0 ? r.readString(KnpProtocol.MAX_COMPONENT_JSON) : null;
        NameStyle nameStyle = null;
        if ((fields & FIELD_NAME_STYLE) != 0) {
            int decorations = r.readUnsignedByte();
            if ((decorations & ~TextDecoration.ALL_MASK) != 0) {
                throw new KnpDecodeException("Unknown decoration bits " + decorations);
            }
            int count = r.readVarInt(0, NameStyle.MAX_STOPS);
            List<TextColor> colors = new ArrayList<TextColor>(count);
            for (int i = 0; i < count; i++) {
                int rgb = r.readInt();
                if ((rgb & ~0xFFFFFF) != 0) {
                    throw new KnpDecodeException("Color out of range " + Integer.toHexString(rgb));
                }
                colors.add(TextColor.rgb(rgb));
            }
            nameStyle = NameStyle.of(decorations, colors);
        }
        NametagVisibility visibility = (fields & FIELD_VISIBILITY) != 0
            ? NametagVisibility.byOrdinal(r.readUnsignedByte())
            : null;
        return new KnpMessage.TeamStyle(team, prefix, suffix, nameStyle, visibility);
    }

    // ---------------------------------------------------------------- shared

    private static byte[] finish(KnpWriter w) {
        if (w.size() > KnpProtocol.MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException(
                "Payload of " + w.size() + " bytes exceeds " + KnpProtocol.MAX_PAYLOAD_BYTES);
        }
        return w.toByteArray();
    }

    private static void checkSize(byte[] payload) throws KnpDecodeException {
        if (payload.length == 0) {
            throw new KnpDecodeException("Empty payload");
        }
        if (payload.length > KnpProtocol.MAX_PAYLOAD_BYTES) {
            throw new KnpDecodeException("Payload of " + payload.length + " bytes is too large");
        }
    }
}
