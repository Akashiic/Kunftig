package com.akashic.kunftig.api.display;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import com.akashic.kunftig.api.KunftigApi;
import com.akashic.kunftig.api.bukkit.display.Display;
import com.akashic.kunftig.api.bukkit.display.KunftigDisplays;

class DisplayApiTest {

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type) {
        return (T) Proxy.newProxyInstance(
            type.getClassLoader(),
            new Class<?>[] { type },
            (p, method, args) -> "equals".equals(method.getName()) ? p == args[0] : null);
    }

    @Test
    void needsTheModThenValidatesAndReachesTheProvider() {
        KunftigDisplays.install(null);
        assertFalse(KunftigApi.isDisplaysAvailable());
        Location at = new Location(proxy(World.class), 0, 64, 0);
        assertThrows(IllegalStateException.class, () -> KunftigDisplays.spawn(at, "ns:m", "", "abrir", null));

        Display made = proxy(Display.class);
        List<String> calls = new ArrayList<>();
        KunftigDisplays.install(new KunftigDisplays.Provider() {

            @Override
            public Display spawn(Location location, String model, String variant, String state,
                List<ItemStack> items, double viewRange) {
                calls.add(model + " " + variant + " " + state + " " + viewRange);
                return made;
            }

            @Override
            public boolean drawsDisplays(Player player) {
                return true;
            }

            @Override
            public boolean drawsExtendedDisplays(Player player) {
                return false;
            }
        });
        try {
            assertTrue(KunftigApi.isDisplaysAvailable());
            assertEquals(11, KunftigApi.apiVersion());
            assertFalse(KunftigDisplays.drawsExtendedDisplays(proxy(Player.class)));
            assertSame(made, KunftigDisplays.spawn(at, "akashicfishing:caixa_da_sorte", "epico", "abrir", null));
            assertSame(made, KunftigDisplays.spawn(at, "akashicfishing:caixa_da_sorte", null, "abrir", null, 32));
            assertEquals(
                java.util.Arrays.asList(
                    "akashicfishing:caixa_da_sorte epico abrir 48.0",
                    "akashicfishing:caixa_da_sorte  abrir 32.0"),
                calls);
            assertThrows(IllegalArgumentException.class, () -> KunftigDisplays.spawn(at, "semdois", "", "a", null));
            assertThrows(IllegalArgumentException.class, () -> KunftigDisplays.spawn(at, "ns:m", "Épico", "a", null));
            assertThrows(IllegalArgumentException.class, () -> KunftigDisplays.spawn(at, "ns:m", "", "Abrir", null));
            assertThrows(
                IllegalArgumentException.class,
                () -> KunftigDisplays.spawn(at, "ns:m", "", "a", Collections.nCopies(9, null)));
            assertThrows(IllegalArgumentException.class, () -> KunftigDisplays.spawn(at, "ns:m", "", "a", null, 500));
            assertThrows(
                IllegalArgumentException.class,
                () -> KunftigDisplays.spawn(new Location(null, 0, 0, 0), "ns:m", "", "a", null));
        } finally {
            KunftigDisplays.install(null);
        }
    }
}
