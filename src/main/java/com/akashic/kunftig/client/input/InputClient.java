package com.akashic.kunftig.client.input;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.network.play.client.C17PacketCustomPayload;

import org.lwjgl.input.Keyboard;

import com.akashic.kunftig.Kunftig;
import com.akashic.kunftig.input.InputWire;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Client side of the input channel (API 9): the Kunftig keys in Controls, and, for the actions the server bound
 * ({@link InputWire#BIND}), a message whenever one goes down or up. Nothing is sent to a server that bound nothing,
 * and nothing while a screen is open (vanilla releases every key then, and so does this). Client thread only.
 * <p>
 * Disabled with {@code -Dkunftig.input.disable=true}: no keys, no channel.
 */
public final class InputClient {

    public static final InputClient INSTANCE = new InputClient();
    public static final String CATEGORY = "key.categories.kunftig";

    private final Map<String, KeyBinding> keys = new LinkedHashMap<>();
    private final Map<String, Boolean> down = new LinkedHashMap<>();
    private Set<String> bound = Collections.emptySet();
    private int sequence;
    private int clientTick;

    private InputClient() {}

    public static boolean disabled() {
        return Boolean.getBoolean("kunftig.input.disable");
    }

    /** At init: the keys appear in Controls, category Kunftig (names in the lang files). */
    public void registerKeys() {
        add(InputWire.SPECIAL, Keyboard.KEY_R);
        add(InputWire.SKILL, Keyboard.KEY_F);
        add(InputWire.DODGE, Keyboard.KEY_LMENU);
        add(InputWire.LOCK_ON, Keyboard.KEY_Z);
    }

    private void add(String action, int key) {
        KeyBinding binding = new KeyBinding("key.kunftig." + action, key, CATEGORY);
        ClientRegistry.registerKeyBinding(binding);
        keys.put(action, binding);
        down.put(action, Boolean.FALSE);
    }

    /** The key of an action, for messages ("press R"); null when the action has no key. */
    public KeyBinding key(String action) {
        return keys.get(action);
    }

    /** At the end of {@code handleJoinGame}: a new server, nothing bound until it says so. */
    public void onJoinGame(NetHandlerPlayClient handler) {
        bound = Collections.emptySet();
        sequence = 0;
        for (Map.Entry<String, Boolean> e : down.entrySet()) {
            e.setValue(Boolean.FALSE);
        }
        handler
            .addToSendQueue(new C17PacketCustomPayload("REGISTER", InputWire.CHANNEL.getBytes(StandardCharsets.UTF_8)));
    }

    /** A message on {@link InputWire#CHANNEL} from the server. */
    public void onPayload(byte[] data) {
        Set<String> read = InputWire.readBind(data);
        if (read == null) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        // An action that is unbound while held is released for the server first, so it never stays "held".
        for (String action : bound) {
            if (!read.contains(action) && Boolean.TRUE.equals(down.get(action))) {
                send(mc.thePlayer, action, false);
            }
        }
        bound = read;
        Kunftig.LOG.debug("Kunftig input: server bound {}", read);
    }

    /** The actions the server bound (for the debug command). */
    public Set<String> bound() {
        return bound;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        clientTick++;
        Minecraft mc = Minecraft.getMinecraft();
        EntityClientPlayerMP player = mc.thePlayer;
        boolean playing = player != null && mc.theWorld != null && mc.currentScreen == null;
        for (Map.Entry<String, KeyBinding> e : keys.entrySet()) {
            String action = e.getKey();
            KeyBinding binding = e.getValue();
            boolean tapped = false;
            while (binding.isPressed()) {
                tapped = true; // pressed and maybe released again within this tick
            }
            boolean now = playing && bound.contains(action) && binding.getIsKeyPressed();
            boolean was = Boolean.TRUE.equals(down.get(action));
            boolean tap = playing && bound.contains(action) && tapped && !now && !was;
            if (now != was || tap) {
                if (tap) {
                    send(player, action, true);
                }
                send(player, action, now);
            }
        }
    }

    private void send(EntityClientPlayerMP player, String action, boolean pressed) {
        down.put(action, pressed);
        if (player == null || player.sendQueue == null) {
            return;
        }
        int movement = movement(player);
        InputWire.Action a = new InputWire.Action(
            action,
            pressed,
            ++sequence,
            clientTick,
            movement,
            player.rotationYaw,
            player.rotationPitch);
        player.sendQueue.addToSendQueue(new C17PacketCustomPayload(InputWire.CHANNEL, InputWire.action(a)));
    }

    private static int movement(EntityClientPlayerMP player) {
        int bits = 0;
        if (player.movementInput != null) {
            if (player.movementInput.moveForward > 0) {
                bits |= InputWire.FORWARD;
            } else if (player.movementInput.moveForward < 0) {
                bits |= InputWire.BACK;
            }
            // Minecraft's strafe is positive to the left
            if (player.movementInput.moveStrafe > 0) {
                bits |= InputWire.LEFT;
            } else if (player.movementInput.moveStrafe < 0) {
                bits |= InputWire.RIGHT;
            }
            if (player.movementInput.jump) {
                bits |= InputWire.JUMP;
            }
            if (player.movementInput.sneak) {
                bits |= InputWire.SNEAK;
            }
        }
        if (player.isSprinting()) {
            bits |= InputWire.SPRINT;
        }
        return bits;
    }
}
