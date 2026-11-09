package com.akashic.kunftig.client.compat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/** Without 3D Futura Engine on the classpath (as in these tests), every query says "not modelled" and nothing fails. */
class FuturaModelsTest {

    @Test
    void withoutFutura3dNothingIsModelled() {
        assertFalse(FuturaModels.present());
        assertFalse(FuturaModels.drewModel(null));
        assertFalse(FuturaModels.isModelled(null));
        assertFalse(FuturaModels.hasLocator(null, FuturaModels.BLADE));
        assertNull(FuturaModels.locatorInLastDraw(null, FuturaModels.RIGHT_HAND));
        FuturaModels.keepItemWhileFishing(p -> true); // a no-op
    }
}
