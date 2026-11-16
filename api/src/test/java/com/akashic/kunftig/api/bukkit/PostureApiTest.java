package com.akashic.kunftig.api.bukkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.akashic.kunftig.api.bukkit.player.Posture;

class PostureApiTest {

    @Test
    void sittingAndLying() {
        Posture sit = Posture.sit(1, 2, 3, 90);
        assertTrue(sit.isSitting());
        assertFalse(sit.isLying());
        assertNull(sit.getDisplay());
        assertEquals(2, sit.getY(), 0);
        assertEquals(90f, sit.getYaw(), 0);
        Posture lie = Posture.lie(0, 0, 0, -45);
        assertTrue(lie.isLying());
        assertEquals(Posture.LYING_EYES, lie.getEyes(), 0);
    }

    @Test
    void theEyesStayBetweenThePointAndStanding() {
        assertEquals(0f, Posture.lie(0, 0, 0, 0).eyes(-1).getEyes(), 0);
        assertEquals(1.62f, Posture.lie(0, 0, 0, 0).eyes(9).getEyes(), 0);
        assertEquals(0.7f, Posture.lie(0, 0, 0, 0).eyes(0.7f).getEyes(), 0);
    }
}
