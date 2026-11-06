package com.akashic.kunftig.input;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The input channel (API 9): the keys a plugin asked for travel from the client to the server, as plugin messages on
 * {@link #CHANNEL}, one per message. It is the only Kunftig channel that goes both ways. No Minecraft classes.
 *
 * <pre>
 * server -> client
 *   BIND   (0): ubyte count, count x UTF action      the actions this client should report (0: none)
 * client -> server
 *   ACTION (1): UTF action, boolean pressed, int sequence, int clientTick, ubyte movement, float yaw, float pitch
 * </pre>
 *
 * {@code sequence} grows by one per ACTION message of a connection (the server drops repeats and old ones);
 * {@code clientTick} is the client's tick counter when the key changed; {@code movement} holds the movement keys held
 * at that moment ({@link #FORWARD} ...), so a plugin can tell "back + special" from "special". Kunftig clients that
 * report input register the channel on join; unknown operations are ignored on both sides.
 */
public final class InputWire {

    /** Registered by Kunftig clients that can report input (Bukkit 1.7 allows at most 16 characters). */
    public static final String CHANNEL = "kunftig:input";

    public static final byte BIND = 0;
    public static final byte ACTION = 1;

    /** The actions a Kunftig client has keys for (Controls, category Kunftig), in the order of the menu. */
    public static final String SPECIAL = "especial";
    public static final String SKILL = "habilidade";
    public static final String DODGE = "esquiva";
    public static final String LOCK_ON = "travar";
    public static final List<String> ACTIONS = Collections
        .unmodifiableList(Arrays.asList(SPECIAL, SKILL, DODGE, LOCK_ON));

    /** Movement keys held when the action key changed. */
    public static final int FORWARD = 1;
    public static final int BACK = 1 << 1;
    public static final int LEFT = 1 << 2;
    public static final int RIGHT = 1 << 3;
    public static final int JUMP = 1 << 4;
    public static final int SNEAK = 1 << 5;
    public static final int SPRINT = 1 << 6;
    public static final int MOVEMENT_MASK = (1 << 7) - 1;

    private static final int MAX_ACTION = 32;

    private InputWire() {}

    /** Whether a name is one of {@link #ACTIONS}. */
    public static boolean isAction(String name) {
        return name != null && ACTIONS.contains(name);
    }

    /** A decoded ACTION message. */
    public static final class Action {

        public final String action;
        public final boolean pressed;
        public final int sequence;
        public final int clientTick;
        public final int movement;
        public final float yaw;
        public final float pitch;

        public Action(String action, boolean pressed, int sequence, int clientTick, int movement, float yaw,
            float pitch) {
            this.action = action;
            this.pressed = pressed;
            this.sequence = sequence;
            this.clientTick = clientTick;
            this.movement = movement & MOVEMENT_MASK;
            this.yaw = yaw;
            this.pitch = pitch;
        }

        @Override
        public String toString() {
            return "Action{" + action
                + (pressed ? " down" : " up")
                + ", seq="
                + sequence
                + ", tick="
                + clientTick
                + ", move="
                + Integer.toBinaryString(movement)
                + "}";
        }
    }

    /** BIND: the actions the client reports from now (unknown names are left out). */
    public static byte[] bind(Collection<String> actions) {
        Set<String> clean = new LinkedHashSet<>();
        if (actions != null) {
            for (String a : actions) {
                if (isAction(a)) {
                    clean.add(a);
                }
            }
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeByte(BIND);
            out.writeByte(clean.size());
            for (String a : clean) {
                out.writeUTF(a);
            }
        } catch (IOException e) {
            throw new IllegalStateException(e); // a byte array never fails
        }
        return bytes.toByteArray();
    }

    /** The actions of a BIND message, or null when {@code data} is not a well-formed BIND. */
    public static Set<String> readBind(byte[] data) {
        if (data == null || data.length < 2 || data[0] != BIND) {
            return null;
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
            in.readByte();
            int n = in.readUnsignedByte();
            if (n > ACTIONS.size() * 4) {
                return null;
            }
            Set<String> out = new LinkedHashSet<>();
            for (int i = 0; i < n; i++) {
                String a = in.readUTF();
                if (isAction(a)) {
                    out.add(a); // an action of a newer Kunftig is skipped, the rest still works
                }
            }
            return Collections.unmodifiableSet(out);
        } catch (IOException e) {
            return null;
        }
    }

    public static byte[] action(Action a) {
        if (a == null || !isAction(a.action)) {
            throw new IllegalArgumentException("unknown action: " + (a == null ? null : a.action));
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeByte(ACTION);
            out.writeUTF(a.action);
            out.writeBoolean(a.pressed);
            out.writeInt(a.sequence);
            out.writeInt(a.clientTick);
            out.writeByte(a.movement);
            out.writeFloat(a.yaw);
            out.writeFloat(a.pitch);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return bytes.toByteArray();
    }

    /**
     * The ACTION in {@code data}, or null when malformed, of another operation, of an unknown action or with angles
     * that
     * are not finite numbers (a client is never trusted: the server drops such messages).
     */
    public static Action readAction(byte[] data) {
        if (data == null || data.length < 2 || data[0] != ACTION || data.length > 64) {
            return null;
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
            in.readByte();
            String action = in.readUTF();
            if (action.length() > MAX_ACTION || !isAction(action)) {
                return null;
            }
            boolean pressed = in.readBoolean();
            int sequence = in.readInt();
            int tick = in.readInt();
            int movement = in.readUnsignedByte();
            float yaw = in.readFloat();
            float pitch = in.readFloat();
            if (!Float.isFinite(yaw) || !Float.isFinite(pitch) || (movement & ~MOVEMENT_MASK) != 0) {
                return null;
            }
            return new Action(action, pressed, sequence, tick, movement, yaw, pitch);
        } catch (IOException e) {
            return null;
        }
    }
}
