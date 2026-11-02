package com.akashic.kunftig.api.bukkit.player;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Issue #8: Backhand turns every right click into PlayerItemHeldEvent 0 -> 36 -> 0 on Crucible. */
class HotbarSwitchTest {

    @Test
    void backhandRoundTripIsNotASwitch() {
        assertFalse(KunftigPlayers.isHotbarSwitch(0, 36));
        assertFalse(KunftigPlayers.isHotbarSwitch(36, 0));
        assertFalse(KunftigPlayers.isHotbarSwitch(4, 4));
        assertFalse(KunftigPlayers.isHotbarSwitch(-1, 2));
        assertFalse(KunftigPlayers.isHotbarSwitch(8, 9));
    }

    @Test
    void realSwitchesAre() {
        assertTrue(KunftigPlayers.isHotbarSwitch(0, 1));
        assertTrue(KunftigPlayers.isHotbarSwitch(8, 0));
    }
}
