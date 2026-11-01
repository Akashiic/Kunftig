package com.akashic.kunftig.fishing;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LavaFishingParametersTest {

    @Test
    void offByDefaultForEveryProfileAndSettablePerHook() {
        for (FishingProfile profile : FishingProfile.values()) {
            FishingParameters parameters = new FishingParameters(profile);
            assertFalse(parameters.lavaFishing(), profile.name());
            parameters.setLavaFishing(true);
            assertTrue(parameters.lavaFishing());
            assertTrue(new FishingParameters(profile, true).lavaFishing(), "fishing.lava in kunftig.cfg");
        }
    }
}
