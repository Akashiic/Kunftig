package com.akashic.kunftig.client.input;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Holds the local player's movement keys back from the game (a station, API 14; a posture, API 15) and reads them
 * straight from the keyboard and the mouse.
 * <p>
 * Kunftig used to put its own {@code MovementInput} on the player. That does not survive a modpack: the Aether
 * replaces {@code player.movementInput} with its own at the end of every client tick, so the player walked away from
 * the helm, and the wrapper Kunftig read the keys from was never updated again (no key reached the server). Whatever
 * object a mod puts there reads the same thing in the end, the pressed state of the game's key bindings: so that is
 * what is held down here. While something holds the keys, each binding is unpressed at both ends of the client tick
 * and after every keyboard and mouse event (a press arrives between the start of the tick and the player's update),
 * and its real state is asked of LWJGL. When nothing holds them any more, each binding gets its real state back, so a
 * key still held goes on working. Client thread.
 */
public final class MoveKeys {

    public static final MoveKeys INSTANCE = new MoveKeys();

    /** Who holds the keys, and whether sneaking is held back too. */
    private final Map<String, Boolean> holders = new HashMap<>();
    private boolean held;
    private boolean heldSneak;

    private MoveKeys() {}

    /**
     * Holds the movement keys (forward, back, left, right, jump; with {@code sneak}, sneaking too) for {@code who},
     * or lets go of them.
     */
    public void hold(String who, boolean on, boolean sneak) {
        if (on) {
            holders.put(who, sneak);
        } else {
            holders.remove(who);
        }
        apply();
    }

    public boolean holding() {
        return !holders.isEmpty();
    }

    /** Whether a key binding's key (or mouse button) is really down, whatever its pressed state says. */
    public static boolean down(KeyBinding binding) {
        int code = binding == null ? 0 : binding.getKeyCode();
        if (code < 0) {
            int button = code + 100;
            return button >= 0 && button < Mouse.getButtonCount() && Mouse.isButtonDown(button);
        }
        return code > 0 && code < Keyboard.KEYBOARD_SIZE && Keyboard.isKeyDown(code);
    }

    private static KeyBinding[] moving(GameSettings s) {
        return new KeyBinding[] { s.keyBindForward, s.keyBindBack, s.keyBindLeft, s.keyBindRight, s.keyBindJump };
    }

    private void apply() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.gameSettings == null) {
            return;
        }
        GameSettings s = mc.gameSettings;
        boolean now = !holders.isEmpty();
        boolean sneak = holders.containsValue(Boolean.TRUE);
        if (now) {
            for (KeyBinding b : moving(s)) {
                KeyBinding.setKeyBindState(b.getKeyCode(), false);
            }
            if (sneak) {
                KeyBinding.setKeyBindState(s.keyBindSneak.getKeyCode(), false);
            }
        }
        // let go: with a screen open the game has every key up already; otherwise each key gets its real state back
        boolean playing = mc.currentScreen == null;
        if (held && !now && playing) {
            for (KeyBinding b : moving(s)) {
                KeyBinding.setKeyBindState(b.getKeyCode(), down(b));
            }
        }
        if (heldSneak && !sneak && playing) {
            KeyBinding.setKeyBindState(s.keyBindSneak.getKeyCode(), down(s.keyBindSneak));
        }
        held = now;
        heldSneak = sneak;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (held || !holders.isEmpty()) {
            apply();
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onKey(InputEvent.KeyInputEvent event) {
        if (!holders.isEmpty()) {
            apply();
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onMouse(InputEvent.MouseInputEvent event) {
        if (!holders.isEmpty()) {
            apply();
        }
    }
}
