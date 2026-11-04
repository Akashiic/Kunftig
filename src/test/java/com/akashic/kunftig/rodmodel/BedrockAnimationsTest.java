package com.akashic.kunftig.rodmodel;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.junit.jupiter.api.Test;

class BedrockAnimationsTest {

    private static final float EPS = 1e-3F;
    private static final String ARM = "braco_direito";

    private static BedrockAnimations arm() throws IOException {
        return BedrockAnimations.read(RodModelIndexTest.resource("braco.animation.json"));
    }

    @Test
    void lengthsAndLoops() throws IOException {
        BedrockAnimations a = arm();
        BedrockAnimations.Clip idle = a.clip("animation.braco.parada");
        assertTrue(idle.loop);
        assertEquals(4.0, idle.length, 1e-9);
        assertEquals(80, idle.lengthTicks());
        BedrockAnimations.Clip cast = a.clip("animation.braco.lancar");
        assertFalse(cast.loop);
        assertEquals(18, cast.lengthTicks());
        // Without animation_length: the last keyframe (here a constant, so 1 tick at least).
        assertEquals(
            1,
            a.clip("animation.braco.fixa")
                .lengthTicks());
        assertEquals(
            20,
            a.clip("animation.braco.molang")
                .lengthTicks());
        assertNull(a.clip("animation.braco.nada"));
        assertTrue(idle.animates(ARM));
        assertFalse(idle.animates("cabeca"));
    }

    @Test
    void samplesLinearlyAndWrapsLoops() throws IOException {
        BedrockAnimations.Clip idle = arm().clip("animation.braco.parada");
        assertArrayEquals(new float[] { -28, 0, 0 }, idle.rotation(ARM, 0), EPS);
        assertArrayEquals(new float[] { -30, 0, 2 }, idle.rotation(ARM, 1), EPS);
        assertArrayEquals(new float[] { -32, 0, 4 }, idle.rotation(ARM, 2), EPS);
        // 5 s into a 4 s loop is 1 s.
        assertArrayEquals(new float[] { -30, 0, 2 }, idle.rotation(ARM, 5), EPS);
        assertArrayEquals(new float[3], idle.rotation("cabeca", 1), EPS);
    }

    @Test
    void stepsHoldsAndConstants() throws IOException {
        BedrockAnimations a = arm();
        BedrockAnimations.Clip cast = a.clip("animation.braco.lancar");
        // Up to the step, toward its "pre"; right after it, from its "post".
        assertArrayEquals(new float[] { -55, 0, 0 }, cast.rotation(ARM, 0.25), EPS);
        assertArrayEquals(new float[] { -60, 0, 0 }, cast.rotation(ARM, 0.50000005), EPS);
        assertArrayEquals(new float[] { -52.5F, 0, 0 }, cast.rotation(ARM, 0.6), EPS);
        // A clip that does not loop holds its last value.
        assertArrayEquals(new float[] { -30, 0, 0 }, cast.rotation(ARM, 3), EPS);
        assertArrayEquals(
            new float[] { 10, 20, 30 },
            a.clip("animation.braco.fixa")
                .rotation(ARM, 7),
            EPS);
        // Molang that is a plain number is read; any other expression is 0 here.
        assertArrayEquals(
            new float[] { -5, 0, 0 },
            a.clip("animation.braco.molang")
                .rotation(ARM, 0),
            EPS);
    }

    @Test
    void refusesWhatIsNotAnAnimationFile() {
        assertThrows(IllegalArgumentException.class, () -> BedrockAnimations.read("{\"x\": 1}"));
        assertThrows(IllegalArgumentException.class, () -> BedrockAnimations.read("[1, 2"));
    }
}
