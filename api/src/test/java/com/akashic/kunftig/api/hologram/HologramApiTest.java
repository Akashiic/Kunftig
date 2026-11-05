package com.akashic.kunftig.api.hologram;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import com.akashic.kunftig.api.KunftigApi;
import com.akashic.kunftig.api.bukkit.hologram.Hologram;
import com.akashic.kunftig.api.bukkit.hologram.KunftigHolograms;

class HologramApiTest {

    @SuppressWarnings("unchecked")
    private static <T> T stub(Class<T> type) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, (proxy, method, args) -> {
            switch (method.getName()) {
                case "equals":
                    return proxy == args[0];
                case "hashCode":
                    return System.identityHashCode(proxy);
                default:
                    return null;
            }
        });
    }

    @Test
    void needsTheModThenReachesTheProvider() {
        KunftigHolograms.install(null);
        assertFalse(KunftigApi.isHologramsAvailable());
        World world = stub(World.class);
        Location at = new Location(world, 1, 64, 3);
        IllegalStateException e = assertThrows(
            IllegalStateException.class,
            () -> KunftigHolograms.create(at, Arrays.asList("a")));
        assertTrue(e.getMessage().contains("not available"));

        List<Object[]> created = new ArrayList<>();
        Hologram made = stub(Hologram.class);
        KunftigHolograms.install(new KunftigHolograms.Provider() {

            @Override
            public Hologram create(Location location, List<String> lines, double viewRange) {
                created.add(new Object[] { location, lines, viewRange });
                return made;
            }

            @Override
            public boolean drawsHolograms(Player player) {
                return true;
            }
        });
        try {
            assertTrue(KunftigApi.isHologramsAvailable());
            assertSame(made, KunftigHolograms.create(at, Arrays.asList("a", "b")));
            assertEquals(KunftigHolograms.DEFAULT_VIEW_RANGE, created.get(0)[2]);
            KunftigHolograms.create(at, Arrays.asList("c"), 48);
            assertEquals(48.0, created.get(1)[2]);
            assertThrows(IllegalArgumentException.class, () -> KunftigHolograms.create(at, Arrays.asList("c"), 0));
            assertThrows(IllegalArgumentException.class, () -> KunftigHolograms.create(at, Arrays.asList("c"), 200));
            assertThrows(
                IllegalArgumentException.class,
                () -> KunftigHolograms.create(new Location(null, 0, 0, 0), Arrays.asList("c")));
            assertTrue(KunftigHolograms.drawsHolograms(stub(Player.class)));
            assertEquals(8, KunftigApi.apiVersion());
        } finally {
            KunftigHolograms.install(null);
        }
    }
}
