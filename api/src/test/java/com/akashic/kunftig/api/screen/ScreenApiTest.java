package com.akashic.kunftig.api.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import com.akashic.kunftig.api.KunftigApi;
import com.akashic.kunftig.api.bukkit.camera.KunftigCamera;
import com.akashic.kunftig.api.bukkit.hud.HudElement;
import com.akashic.kunftig.api.bukkit.hud.KunftigHud;

class ScreenApiTest {

    private static final World WORLD = stub(World.class, null);
    private static final World NETHER = stub(World.class, null);

    @SuppressWarnings("unchecked")
    private static <T> T stub(Class<T> type, World world) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, (proxy, method, args) -> {
            switch (method.getName()) {
                case "equals":
                    return proxy == args[0];
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "getWorld":
                    return world;
                default:
                    return null;
            }
        });
    }

    @Test
    void hudElementsBuildAndValidate() {
        HudElement e = HudElement.builder("overkill:rank")
            .anchor(HudElement.Anchor.RIGHT)
            .offset(-10, 100000)
            .scale(20)
            .text("§6S")
            .bar(2, 0, 1, 2)
            .texture("overkill:textures/hud/s.png", 32, 32)
            .duration(-5, 999)
            .build();
        assertEquals(HudElement.Anchor.RIGHT, e.getAnchor());
        assertEquals(Short.MAX_VALUE, e.getY());
        assertEquals(8, e.getScale(), 0);
        assertEquals(1, e.getProgress(), 0);
        assertEquals(1, e.getBarWidth());
        assertEquals(0, e.getDurationTicks());
        assertEquals(255, e.getFadeTicks());
        assertEquals(-1, HudElement.builder("a:b").build().getProgress(), 0, "no bar by default");
        assertThrows(IllegalArgumentException.class, () -> HudElement.builder("Rank"));
        assertThrows(
            IllegalArgumentException.class,
            () -> HudElement.builder("a:b")
                .texture("a:b.jpg", 1, 1));
    }

    @Test
    void needTheModThenReachTheProviders() {
        KunftigHud.install(null);
        KunftigCamera.install(null);
        assertFalse(KunftigApi.isHudAvailable());
        assertFalse(KunftigApi.isCameraAvailable());
        Player p = stub(Player.class, WORLD);
        assertThrows(IllegalStateException.class, () -> KunftigHud.clear(p));

        List<String> calls = new ArrayList<>();
        KunftigHud.install(new KunftigHud.Provider() {

            @Override
            public void show(Player player, HudElement element) {
                calls.add("show " + element.getId());
            }

            @Override
            public void remove(Player player, String id) {
                calls.add("remove " + id);
            }

            @Override
            public void clear(Player player) {
                calls.add("clear");
            }

            @Override
            public boolean drawsHud(Player player) {
                return true;
            }
        });
        KunftigCamera.install(new KunftigCamera.Provider() {

            @Override
            public void lockOn(Player player, Entity target, float strength, float maxDistance, boolean marker) {
                calls.add("lock " + strength);
            }

            @Override
            public void release(Player player) {
                calls.add("release");
            }

            @Override
            public Entity getTarget(Player player) {
                return null;
            }

            @Override
            public boolean followsLockOn(Player player) {
                return true;
            }
        });
        try {
            assertTrue(KunftigApi.isHudAvailable());
            assertTrue(KunftigApi.isCameraAvailable());
            KunftigHud.show(p, HudElement.builder("overkill:combo").build());
            KunftigHud.remove(p, "overkill:combo");
            KunftigHud.clear(p);
            assertThrows(IllegalArgumentException.class, () -> KunftigHud.remove(p, "x"));
            assertThrows(IllegalArgumentException.class, () -> KunftigHud.show(p, null));
            Entity zombie = stub(Entity.class, WORLD);
            KunftigCamera.lockOn(p, zombie, 0.35F, 24, true);
            KunftigCamera.release(p);
            assertThrows(IllegalArgumentException.class, () -> KunftigCamera.lockOn(p, p, 1, 1, false));
            assertThrows(
                IllegalArgumentException.class,
                () -> KunftigCamera.lockOn(p, stub(Entity.class, NETHER), 1, 1, false));
            assertEquals(
                java.util.Arrays
                    .asList("show overkill:combo", "remove overkill:combo", "clear", "lock 0.35", "release"),
                calls);
        } finally {
            KunftigHud.install(null);
            KunftigCamera.install(null);
        }
    }
}
