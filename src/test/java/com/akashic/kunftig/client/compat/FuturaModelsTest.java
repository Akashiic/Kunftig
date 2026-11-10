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
        // API 3: no external states, no animations, the blade on the biped
        assertFalse(FuturaModels.hasExternalStates());
        assertFalse(FuturaModels.setExternalState(null, FuturaModels.SLASHBLADE_HELD, "mainhand"));
        assertNull(FuturaModels.externalState(null, FuturaModels.SLASHBLADE_HELD));
        assertFalse(FuturaModels.hasAnimation(null, "slashblade:idle"));
        assertNull(FuturaModels.playingAnimation(null, "player.swing"));
        assertFalse(FuturaModels.playsBladeAnimations(null));
    }
}
