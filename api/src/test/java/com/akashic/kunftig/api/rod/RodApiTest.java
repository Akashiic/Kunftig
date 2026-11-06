package com.akashic.kunftig.api.rod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import com.akashic.kunftig.api.KunftigApi;
import com.akashic.kunftig.api.bukkit.rod.KunftigRods;

class RodApiTest {

    private static Player player() {
        return (Player) Proxy.newProxyInstance(
            Player.class.getClassLoader(),
            new Class<?>[] { Player.class },
            (proxy, method, args) -> "equals".equals(method.getName()) ? proxy == args[0] : null);
    }

    @Test
    void needsTheModThenValidatesAndReachesTheProvider() {
        KunftigRods.install(null);
        assertFalse(KunftigApi.isRodsAvailable());
        Player p = player();
        assertThrows(IllegalStateException.class, () -> KunftigRods.setState(p, KunftigRods.BITE));

        List<String> calls = new ArrayList<>();
        KunftigRods.install(new KunftigRods.Provider() {

            @Override
            public void setState(Player player, String state) {
                calls.add("state " + state);
            }

            @Override
            public void setTension(Player player, float tension) {
                calls.add("tension " + tension);
            }

            @Override
            public String getState(Player player) {
                return "briga";
            }

            @Override
            public boolean drawsRodModels(Player player) {
                return true;
            }
        });
        try {
            assertTrue(KunftigApi.isRodsAvailable());
            assertEquals(9, KunftigApi.apiVersion());
            KunftigRods.setState(p, KunftigRods.FIGHT);
            KunftigRods.setState(p, "danca_da_chuva");
            KunftigRods.setTension(p, 2);
            KunftigRods.setTension(p, Float.NaN);
            KunftigRods.setTension(p, -1);
            assertEquals("briga", KunftigRods.getState(p));
            assertTrue(KunftigRods.drawsRodModels(p));
            assertThrows(IllegalArgumentException.class, () -> KunftigRods.setState(p, "Briga"));
            assertThrows(IllegalArgumentException.class, () -> KunftigRods.setState(p, ""));
            assertThrows(IllegalArgumentException.class, () -> KunftigRods.setState(null, KunftigRods.IDLE));
            assertEquals(
                java.util.Arrays.asList(
                    "state briga",
                    "state danca_da_chuva",
                    "tension 1.0",
                    "tension 0.0",
                    "tension 0.0"),
                calls);
        } finally {
            KunftigRods.install(null);
        }
    }

    @Test
    void stateNamesMatchTheWire() {
        for (String s : new String[] { KunftigRods.IDLE, KunftigRods.DRAW, KunftigRods.CAST, KunftigRods.BITE,
            KunftigRods.FIGHT, KunftigRods.REEL, KunftigRods.CATCH }) {
            assertTrue(KunftigRods.STATE_NAME.matcher(s)
                .matches(), s);
        }
    }
}
