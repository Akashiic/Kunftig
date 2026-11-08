package com.akashic.kunftig.fishing;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class HookRangeParametersTest {

    @Test
    void vanillaRangeByDefaultClampedWhenSet() {
        FishingParameters parameters = new FishingParameters(FishingProfile.MODERN_1_21_1);
        assertEquals(32.0D, parameters.maxDistance(), "vanilla's 32 blocks (1024 squared)");
        parameters.setMaxDistance(70);
        assertEquals(70.0D, parameters.maxDistance());
        parameters.setMaxDistance(1000);
        assertEquals(FishingParameters.MAX_MAX_DISTANCE, parameters.maxDistance());
        parameters.setMaxDistance(1);
        assertEquals(FishingParameters.MIN_MAX_DISTANCE, parameters.maxDistance());
        parameters.setMaxDistance(Double.NaN);
        assertEquals(FishingParameters.MIN_MAX_DISTANCE, parameters.maxDistance(), "NaN is ignored");
    }
}
