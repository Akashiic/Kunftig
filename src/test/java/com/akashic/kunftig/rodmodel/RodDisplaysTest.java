package com.akashic.kunftig.rodmodel;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class RodDisplaysTest {

    private static final float EPS = 1e-4F;

    private static void assertIdentity(RodMatrix m) {
        for (int r = 0; r < 4; r++) {
            for (int c = 0; c < 4; c++) {
                assertEquals(r == c ? 1 : 0, m.get(r, c), EPS, "(" + r + "," + c + ")");
            }
        }
    }

    @Test
    void matrixFollowsTheGlStack() {
        // glTranslate(1,2,3); glRotate(90, Z): the X axis goes to Y, then moves.
        RodMatrix m = RodMatrix.identity()
            .translate(1, 2, 3)
            .rotateZ(90);
        assertArrayEquals(new float[] { 1, 3, 3 }, m.transform(1, 0, 0), EPS);
        assertArrayEquals(
            new float[] { 0, 0, 1 },
            RodMatrix.identity()
                .rotateX(90)
                .transform(0, 1, 0),
            EPS);
        assertArrayEquals(
            new float[] { 0, 0, -1 },
            RodMatrix.identity()
                .rotateY(90)
                .transform(1, 0, 0),
            EPS);
        RodMatrix chain = RodDisplays.thirdPersonChain();
        assertIdentity(
            chain.copy()
                .multiply(chain.inverse()));
    }

    @Test
    void wrapTakesTheShortWay() {
        float twoPi = (float) (Math.PI * 2);
        assertEquals(0.5F, RodMatrix.wrap(0.5F), EPS);
        assertEquals(0.5F, RodMatrix.wrap(0.5F - 2 * twoPi), EPS); // a crank two turns in: no turns back
        assertEquals(-0.5F, RodMatrix.wrap(-0.5F + 3 * twoPi), EPS);
        assertEquals((float) -Math.PI, RodMatrix.wrap((float) Math.PI), EPS);
    }

    @Test
    void undoingVanillaLeavesTheDisplayAlone() {
        RodModelDef.Display d = new RodModelDef.Display(
            new float[] { 15, -45, 10 },
            new float[] { -7.5F, 3, -15 },
            0.4F);
        float[] grip = { 0, 4.5F, 0 };
        RodMatrix display = RodDisplays.display(d, grip, 0, 0);

        // First person, no swing: vanilla's transforms, then ours, is the camera translation, then the display.
        RodMatrix vanilla = RodMatrix.identity()
            .translate(0.56F, -0.52F, -0.72F)
            .rotateY(45)
            .scale(0.4F, 0.4F, 0.4F)
            .rotateY(180)
            .multiply(RodDisplays.forgeEquipped());
        RodMatrix drawn = vanilla.multiply(RodDisplays.firstPerson(d, grip, 0));
        assertMatrix(display, drawn);

        // Third person: after the arm, the display sits in the mirrored (Y up) arm frame, hand 10 px down.
        RodMatrix third = RodDisplays.thirdPersonChain()
            .multiply(RodDisplays.thirdPerson(d, grip));
        RodMatrix expected = RodMatrix.identity()
            .scale(-1, -1, 1)
            .multiply(RodDisplays.display(d, grip, 1 / 16F, -10 / 16F));
        assertMatrix(expected, third);
    }

    @Test
    void aFlatItemIsUndoneToo() {
        // A fish (not full 3D, not rotating around) gets other vanilla transforms; the display ends up the same.
        RodModelDef.Display d = new RodModelDef.Display(new float[] { 0, 90, 30 }, new float[] { 2, -1, 4 }, 1.3F);
        float[] grip = { 0, 3, 1 };
        RodMatrix vanilla = RodMatrix.identity()
            .translate(0.56F, -0.52F, -0.72F)
            .rotateY(45)
            .scale(0.4F, 0.4F, 0.4F)
            .multiply(RodDisplays.forgeEquipped());
        assertMatrix(RodDisplays.display(d, grip, 0, 0), vanilla.multiply(RodDisplays.firstPerson(d, grip, 0, false)));
        RodMatrix flatChain = RodMatrix.identity()
            .translate(-0.0625F, 0.4375F, 0.0625F)
            .translate(0.25F, 0.1875F, -0.1875F)
            .scale(0.375F, 0.375F, 0.375F)
            .rotateZ(60)
            .rotateX(-90)
            .rotateZ(20)
            .multiply(RodDisplays.forgeEquipped());
        RodMatrix expected = RodMatrix.identity()
            .scale(-1, -1, 1)
            .multiply(RodDisplays.display(d, grip, 1 / 16F, -10 / 16F));
        assertMatrix(expected, flatChain.multiply(RodDisplays.thirdPerson(d, grip, false, false)));
        // the rod's own chain is unchanged
        assertMatrix(RodDisplays.thirdPersonChain(), RodDisplays.thirdPersonChain(true, true));
    }

    @Test
    void theGripLandsOnTheFrameOrigin() {
        // Bedrock grip (0, 4.5, 0) px is GeckoLib (0, 4.5/16, 0): the display brings it to the translation.
        RodModelDef.Display d = new RodModelDef.Display(new float[] { 80, 0, 0 }, new float[] { 16, 8, 0 }, 0.6F);
        float[] grip = { 2, 4.5F, 0 };
        float[] g = RodDisplays.geckoPoint(grip);
        assertArrayEquals(new float[] { -2 / 16F, 4.5F / 16, 0 }, g, EPS);
        // X mirrored, like GeckoLib and the previews.
        assertArrayEquals(
            new float[] { -1, 0.5F, 0 },
            RodDisplays.display(d, grip, 0, 0)
                .transform(g[0], g[1], g[2]),
            EPS);
        // Bedrock rotation [-90, 0, 0] (X negative) swings what hangs down (-Y) forward (-Z): the arm of the
        // previews, whose player faces -Z.
        RodModelDef.Display forward = new RodModelDef.Display(new float[] { -90, 0, 0 }, new float[3], 1);
        assertArrayEquals(
            new float[] { 0, 0, -1 },
            RodDisplays.display(forward, new float[3], 0, 0)
                .transform(0, -1, 0),
            EPS);
        // Ground and frame undo Forge's 0.5.
        assertArrayEquals(
            new float[] { 0, 0, -2 },
            RodDisplays.entity(forward, new float[3])
                .transform(0, -1, 0),
            EPS);
    }

    private static void assertMatrix(RodMatrix expected, RodMatrix actual) {
        for (int r = 0; r < 4; r++) {
            for (int c = 0; c < 4; c++) {
                assertEquals(expected.get(r, c), actual.get(r, c), EPS, "(" + r + "," + c + ")");
            }
        }
    }
}
