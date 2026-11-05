package com.akashic.kunftig.rodmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RodPendulumTest {

    private static final double FRAME = 1 / 60.0;
    private static final double LENGTH = 0.5;

    @Test
    void aStillTipHangsStraightDown() {
        RodPendulum p = new RodPendulum();
        double[] tip = { 3, 70, -2 };
        double[] d = null;
        for (int i = 0; i < 120; i++) {
            d = p.update(FRAME, tip, LENGTH);
        }
        assertEquals(-1, d[1], 1e-9);
    }

    @Test
    void movingTheTipLeavesTheLineBehindThenItComesBackToTheMiddle() {
        RodPendulum p = new RodPendulum();
        double[] tip = { 0, 64, 0 };
        p.update(FRAME, tip, LENGTH);
        // the tip moves along +X for half a second
        for (int i = 0; i < 30; i++) {
            tip = new double[] { tip[0] + 2 * FRAME, 64, 0 };
            p.update(FRAME, tip, LENGTH);
        }
        double[] d = p.update(FRAME, tip, LENGTH)
            .clone();
        // the tip stops: the line swings forward past the vertical, towards +X
        double most = 0;
        for (int i = 0; i < 60; i++) {
            d = p.update(FRAME, tip, LENGTH);
            most = Math.max(most, d[0]);
        }
        assertTrue(most > 0.1, "a linha passa para a frente quando a ponta para: " + most);
        for (int i = 0; i < 600; i++) {
            d = p.update(FRAME, tip, LENGTH);
        }
        assertTrue(p.angleFromVertical() < Math.toRadians(1), "e volta ao meio: " + p.angleFromVertical());
    }

    @Test
    void acceleratingTheTipSwingsTheLineTheOtherWay() {
        RodPendulum p = new RodPendulum();
        double[] tip = { 0, 64, 0 };
        p.update(FRAME, tip, LENGTH);
        double v = 0;
        double[] d = null;
        for (int i = 0; i < 10; i++) {
            v += 6 * FRAME; // accelerating towards +Z
            tip = new double[] { 0, 64, tip[2] + v * FRAME };
            d = p.update(FRAME, tip, LENGTH);
        }
        assertTrue(d[2] < -0.05, "a linha fica para trás (−Z): " + d[2]);
    }

    @Test
    void theSmallSwingTakesAboutTheTimeOfAPendulumOfThatLength() {
        RodPendulum p = new RodPendulum();
        double[] tip = { 0, 64, 0 };
        p.update(FRAME, tip, LENGTH);
        // a short push, then count the time between two swings to the same side
        for (int i = 0; i < 3; i++) {
            tip = new double[] { tip[0] + 0.01, 64, 0 };
            p.update(FRAME, tip, LENGTH);
        }
        double previous = 0;
        double lastCross = -1;
        double period = -1;
        for (int i = 0; i < 600; i++) {
            double x = p.update(FRAME, tip, LENGTH)[0];
            if (previous < 0 && x >= 0) {
                double t = i * FRAME;
                if (lastCross >= 0 && period < 0) {
                    period = t - lastCross;
                }
                lastCross = t;
            }
            previous = x;
        }
        double expected = 2 * Math.PI * Math.sqrt(LENGTH / RodPendulum.GRAVITY);
        assertEquals(expected, period, 0.12 * expected, "período");
    }

    @Test
    void aTeleportDoesNotFlingTheLine() {
        RodPendulum p = new RodPendulum();
        double[] tip = { 0, 64, 0 };
        p.update(FRAME, tip, LENGTH);
        p.update(FRAME, tip, LENGTH);
        double[] d = p.update(FRAME, new double[] { 500, 64, 0 }, LENGTH);
        assertTrue(p.angleFromVertical() < Math.toRadians(5), "teto da aceleração: " + p.angleFromVertical());
        d = p.update(1.0, new double[] { 900, 64, 0 }, LENGTH);
        assertEquals(-1, d[1], 1e-9, "um intervalo longo recomeça parado");
    }

    @Test
    void aJitteryTipAndSecondDrawsInTheSameFrameKeepTheLineHanging() {
        RodPendulum p = new RodPendulum();
        java.util.Random random = new java.util.Random(7);
        double worst = 0;
        for (int i = 0; i < 600; i++) {
            // the hand bobs (walking: about 2 steps a second, a few hundredths of a block) with a little noise; some
            // frames draw the rod a second time, a hair apart
            double t = i * FRAME;
            double[] tip = { 0.9 + 0.03 * Math.sin(2 * Math.PI * 1.8 * t) + 0.002 * random.nextGaussian(),
                5.2 + 0.04 * Math.abs(Math.sin(2 * Math.PI * 1.8 * t)) + 0.002 * random.nextGaussian(), -1.3 };
            p.update(FRAME, tip, LENGTH);
            p.update(0.0001, new double[] { tip[0] + 0.05, tip[1] - 0.4, tip[2] }, LENGTH);
            if (i > 60) {
                worst = Math.max(worst, p.angleFromVertical());
            }
        }
        assertTrue(worst < Math.toRadians(30), "a linha continua pendurada: " + Math.toDegrees(worst));
    }

    @Test
    void theEulerAnglesTurnOneVectorOntoTheOther() {
        double[][] pairs = { { 0, -1, 0, 0, -1, 0 }, { 0, -1, 0, 0.6, -0.8, 0 }, { 0, -1, 0, 0, 0.6, -0.8 },
            { 0.3, -0.9, 0.3, -0.5, -0.5, 0.707 }, { 0, -1, 0, 0, 1, 0 }, { 1, 0, 0, 0, 0, 1 } };
        for (double[] pr : pairs) {
            double[] from = unit(pr[0], pr[1], pr[2]);
            double[] to = unit(pr[3], pr[4], pr[5]);
            float[] e = RodPendulum.eulerTurning(from, to);
            double[] got = rz(e[2], ry(e[1], rx(e[0], from)));
            for (int i = 0; i < 3; i++) {
                assertEquals(to[i], got[i], 1e-5, java.util.Arrays.toString(pr));
            }
        }
    }

    private static double[] unit(double x, double y, double z) {
        double l = Math.sqrt(x * x + y * y + z * z);
        return new double[] { x / l, y / l, z / l };
    }

    private static double[] rx(double a, double[] v) {
        double c = Math.cos(a), s = Math.sin(a);
        return new double[] { v[0], c * v[1] - s * v[2], s * v[1] + c * v[2] };
    }

    private static double[] ry(double a, double[] v) {
        double c = Math.cos(a), s = Math.sin(a);
        return new double[] { c * v[0] + s * v[2], v[1], -s * v[0] + c * v[2] };
    }

    private static double[] rz(double a, double[] v) {
        double c = Math.cos(a), s = Math.sin(a);
        return new double[] { c * v[0] - s * v[1], s * v[0] + c * v[1], v[2] };
    }
}
