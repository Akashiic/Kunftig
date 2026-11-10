package com.akashic.kunftig.rodmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

/** The server's rod states as 3D Futura Engine's models are told them: only changes, restarts, stale states. */
class FishingStateFeedTest {

    private final List<String> told = new ArrayList<>();
    private final FishingStateFeed.Sink sink = (key, value) -> told.add(key + "=" + value);

    @Test
    void onlyChangesGoOut() {
        FishingStateFeed feed = new FishingStateFeed();
        Object player = new Object();
        feed.update(player, null, 0, 0, 0, sink);
        assertTrue(told.isEmpty(), "nothing known, nothing told");
        feed.update(player, "mordida", 10, 0, 10, sink);
        assertEquals(Arrays.asList("fishing.state=mordida", "fishing.tension=0.00"), told);
        told.clear();
        feed.update(player, "mordida", 10, 0, 11, sink);
        assertTrue(told.isEmpty(), "the same: nothing");
        feed.update(player, "briga", 30, 0.424f, 30, sink);
        assertEquals(Arrays.asList("fishing.state=briga", "fishing.tension=0.42"), told);
        told.clear();
        feed.update(player, "briga", 30, 0.421f, 31, sink);
        assertTrue(told.isEmpty(), "the same hundredth");
        feed.update(player, "briga", 30, 0.7f, 32, sink);
        assertEquals(Arrays.asList("fishing.tension=0.70"), told);
        told.clear();
        feed.update(player, "captura", 40, 0.7f, 40, sink);
        assertEquals(Arrays.asList("fishing.state=captura"), told);
        told.clear();
        // nothing known any more (the player left the server's range): cleared
        feed.update(player, null, 0, 0, 200, sink);
        assertEquals(Arrays.asList("fishing.state=null", "fishing.tension=null"), told);
    }

    @Test
    void theSameStateStartedAgainRestarts() {
        FishingStateFeed feed = new FishingStateFeed();
        Object player = new Object();
        feed.update(player, "mordida", 10, 0, 10, sink);
        told.clear();
        // a second bite: the same name, a new start
        feed.update(player, "mordida", 50, 0, 50, sink);
        assertEquals(Arrays.asList("fishing.state=null", "fishing.state=mordida"), told);
    }

    @Test
    void aStaleStateThatPlaysOnceIsToldAsIdle() {
        FishingStateFeed feed = new FishingStateFeed();
        Object late = new Object();
        // a player who comes close long after a catch: the model does not play it late
        feed.update(late, "captura", 0, 0, 200, sink);
        assertEquals("fishing.state=parada", told.get(0));
        told.clear();
        // a fight goes on however long ago it started
        Object fighter = new Object();
        feed.update(fighter, "briga", 0, 0.5f, 400, sink);
        assertEquals("fishing.state=briga", told.get(0));
        // a fresh one plays
        told.clear();
        Object fresh = new Object();
        feed.update(fresh, "captura", 195, 0, 200, sink);
        assertEquals("fishing.state=captura", told.get(0));
        assertTrue(FishingStateFeed.loops("recolher"));
        assertTrue(FishingStateFeed.loops("briga_erro"), "AkashicFishing's miss reaction is part of the fight");
        assertFalse(FishingStateFeed.loops("mordida"));
    }
}
