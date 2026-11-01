package com.akashic.kunftig.api.bukkit.fishing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class BukkitFishingApiTest {

    private static <T> T stub(Class<T> type) {
        return type.cast(
            Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[] { type },
                (proxy, method, args) -> { throw new UnsupportedOperationException(method.getName()); }));
    }

    @AfterEach
    void uninstall() {
        KunftigFishingBukkit.install(null);
    }

    @Test
    void withoutTheModTheApiSaysSoClearly() {
        assertFalse(KunftigFishingBukkit.isAvailable());
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> KunftigFishingBukkit.of(null));
        assertTrue(
            e.getMessage()
                .contains("not available"));
    }

    @Test
    void providerDecidesAvailability() {
        final ModernFishHook hook = stub(ModernFishHook.class);
        final boolean[] active = { false };
        KunftigFishingBukkit.install(new KunftigFishingBukkit.Provider() {

            @Override
            public ModernFishHook of(org.bukkit.entity.FishHook ignored) {
                return hook;
            }

            @Override
            public boolean isActive() {
                return active[0];
            }
        });
        assertFalse(KunftigFishingBukkit.isAvailable(), "engine disabled in config");
        assertThrows(IllegalStateException.class, () -> KunftigFishingBukkit.of(null));
        active[0] = true;
        assertSame(hook, KunftigFishingBukkit.of(null));
    }

    @Test
    void eventCarriesStateHookAndCancellation() {
        Player player = stub(Player.class);
        ModernFishHook hook = stub(ModernFishHook.class);
        KunftigFishEvent event = new KunftigFishEvent(player, hook, KunftigFishEvent.State.BITE);
        assertSame(player, event.getPlayer());
        assertSame(hook, event.getHook());
        assertEquals(KunftigFishEvent.State.BITE, event.getState());
        assertFalse(event.isCancelled());
        event.setCancelled(true);
        assertTrue(event.isCancelled());
        assertSame(KunftigFishEvent.getHandlerList(), event.getHandlers());
    }

    @Test
    void statesAreTheOnes1710Lacks() {
        // FISHING, CAUGHT_FISH, CAUGHT_ENTITY and IN_GROUND come from Bukkit's own PlayerFishEvent.
        assertEquals(
            "[LURED, BITE, FAILED_ATTEMPT, REEL_IN]",
            java.util.Arrays.toString(KunftigFishEvent.State.values()));
        assertEquals(
            "[UNHOOKED, HOOKED_ENTITY, BOBBING]",
            java.util.Arrays.toString(ModernFishHook.HookState.values()));
    }
}
