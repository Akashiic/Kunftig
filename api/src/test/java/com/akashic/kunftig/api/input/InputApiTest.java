package com.akashic.kunftig.api.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

import com.akashic.kunftig.api.KunftigApi;
import com.akashic.kunftig.api.bukkit.input.KunftigInput;
import com.akashic.kunftig.api.bukkit.input.KunftigInputEvent;

class InputApiTest {

    @SuppressWarnings("unchecked")
    private static <T> T stub(Class<T> type) {
        return (T) Proxy.newProxyInstance(
            type.getClassLoader(),
            new Class<?>[] { type },
            (proxy, method, args) -> "equals".equals(method.getName()) ? proxy == args[0] : null);
    }

    @Test
    void needsTheModThenValidatesAndReachesTheProvider() {
        KunftigInput.install(null);
        assertFalse(KunftigApi.isInputAvailable());
        Player p = stub(Player.class);
        assertThrows(IllegalStateException.class, () -> KunftigInput.bind(p, KunftigInput.SPECIAL));

        List<String> calls = new ArrayList<>();
        KunftigInput.install(new KunftigInput.Provider() {

            @Override
            public int getKeys(Player player) {
                return 0;
            }

            @Override
            public long getLastPress(Player player, int key) {
                return -1;
            }

            @Override
            public long getTick() {
                return 0;
            }


            @Override
            public void register(Plugin plugin) {
                calls.add("register");
            }

            @Override
            public void bind(Player player, Set<String> actions) {
                calls.add("bind " + actions);
            }

            @Override
            public Set<String> getBound(Player player) {
                return Collections.singleton(KunftigInput.DODGE);
            }

            @Override
            public boolean isHeld(Player player, String action) {
                return true;
            }

            @Override
            public boolean sendsInput(Player player) {
                return true;
            }
        });
        try {
            assertTrue(KunftigApi.isInputAvailable());
            assertEquals(11, KunftigApi.apiVersion());
            KunftigInput.register(stub(Plugin.class));
            KunftigInput.bind(p, KunftigInput.SPECIAL, KunftigInput.LOCK_ON, KunftigInput.SPECIAL);
            KunftigInput.bind(p);
            KunftigInput.bind(p, (java.util.Collection<String>) null);
            // the held keys are a pseudo-action, bound along with the others (Overkill binds KEYS + SPECIAL)
            KunftigInput.bind(p, KunftigInput.KEYS, KunftigInput.SPECIAL);
            assertThrows(IllegalArgumentException.class, () -> KunftigInput.bind(p, "voar"));
            assertThrows(IllegalArgumentException.class, () -> KunftigInput.bind(null, KunftigInput.SPECIAL));
            assertThrows(IllegalArgumentException.class, () -> KunftigInput.register(null));
            assertEquals(Collections.singleton(KunftigInput.DODGE), KunftigInput.getBound(p));
            assertTrue(KunftigInput.isHeld(p, KunftigInput.DODGE));
            assertTrue(KunftigInput.sendsInput(p));
            assertEquals(Arrays.asList("register", "bind [especial, travar]", "bind []", "bind []",
                "bind [teclas, especial]"), calls);
        } finally {
            KunftigInput.install(null);
        }
    }

    @Test
    void eventDecodesMovement() {
        KunftigInputEvent e = new KunftigInputEvent(
            stub(Player.class),
            KunftigInput.SPECIAL,
            true,
            3,
            77,
            KunftigInputEvent.BACK | KunftigInputEvent.JUMP,
            90,
            -10);
        assertTrue(e.isBack());
        assertTrue(e.isJumping());
        assertFalse(e.isForward());
        assertFalse(e.isSneaking());
        assertEquals(77, e.getClientTick());
        assertEquals(KunftigInputEvent.getHandlerList(), e.getHandlers());
    }
}
