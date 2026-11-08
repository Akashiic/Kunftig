package com.akashic.kunftig.api.blade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.akashic.kunftig.api.KunftigApi;
import com.akashic.kunftig.api.bukkit.blade.BladeMotion;
import com.akashic.kunftig.api.bukkit.blade.KunftigBlades;
import com.akashic.kunftig.api.bukkit.blade.KunftigEffects;

class BladeApiTest {

    @Test
    void motionsBuildLikeSlashBladeStates() {
        BladeMotion a1 = BladeMotion.builder()
            .state(1, 10, false, BladeMotion.duration(1, 10, 1, 0))
            .state(10, 21, false, BladeMotion.duration(10, 21, 1, 0))
            .body(1, 41, true)
            .build();
        assertEquals(2, a1.getChain()
            .size());
        assertEquals(300, a1.getChain()
            .get(0)
            .getDurationMs());
        assertEquals(BladeMotion.STANDBY, a1.getIdle());
        assertEquals(1033, BladeMotion.STANDBY.getDurationMs());
        assertTrue(a1.hasBody());
        assertFalse(BladeMotion.builder()
            .build()
            .hasBody());
        assertEquals(416, BladeMotion.duration(1923, 1928, 0.4, 0));
        assertThrows(
            IllegalArgumentException.class,
            () -> BladeMotion.builder()
                .state(-1, 2, false, 1));
    }

    @Test
    void needsTheMod() {
        KunftigBlades.install(null);
        assertFalse(KunftigApi.isBladesAvailable());
        assertThrows(
            IllegalStateException.class,
            () -> KunftigBlades.play(
                (org.bukkit.entity.Player) java.lang.reflect.Proxy.newProxyInstance(
                    getClass().getClassLoader(),
                    new Class<?>[] { org.bukkit.entity.Player.class },
                    (p, m, a) -> null),
                BladeMotion.builder()
                    .build()));
        assertThrows(IllegalArgumentException.class, () -> KunftigBlades.play(null, null));
    }

    @Test
    void effectsUseSlashBladesProjectileRotationAndCheckTheirArguments() {
        float[] east = KunftigEffects.rotation(new org.bukkit.util.Vector(1, 0, 0));
        assertEquals(90, east[0], 1e-4, "yaw atan2(x, z): +X is 90, not Bukkit's -90");
        float[] down = KunftigEffects.rotation(new org.bukkit.util.Vector(0, -4, 0));
        assertEquals(-90, down[1], 1e-4);
        assertFalse(KunftigEffects.isAvailable());
        assertThrows(IllegalArgumentException.class, () -> KunftigEffects.judgementCut("Bad Name", null, 0));
        assertThrows(IllegalArgumentException.class, () -> KunftigEffects.judgementCut("ok", null, 0));
        assertThrows(IllegalArgumentException.class, () -> KunftigEffects.rank(null, "ok", 0));
    }
}
