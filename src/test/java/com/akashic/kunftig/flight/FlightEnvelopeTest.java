package com.akashic.kunftig.flight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FlightEnvelopeTest {

    /** A vanilla jump, tick by tick, as the game hands it to the movement. */
    private static double[] jump(double start) {
        double[] out = new double[12];
        double v = start;
        for (int i = 0; i < out.length; i++) {
            out[i] = v;
            v = (v - FlightEnvelope.GRAVITY) * FlightEnvelope.DRAG;
        }
        return out;
    }

    @Test
    void aJumpIsUntouched() {
        FlightEnvelope e = new FlightEnvelope();
        double[] arc = jump(0.42);
        assertEquals(0.42, e.next(true, arc[0], false), 1e-9, "sai do chão");
        for (int i = 1; i < arc.length; i++) {
            assertEquals(arc[i], e.next(false, arc[i], false), 1e-9, "tick " + i);
        }
    }

    @Test
    void thrustIsCutBackToTheJumpCurve() {
        FlightEnvelope e = new FlightEnvelope();
        double[] arc = jump(0.42);
        e.next(true, arc[0], false);
        double v = e.next(false, arc[1], false);
        for (int i = 2; i < 40; i++) {
            double thrusted = (v - FlightEnvelope.GRAVITY) * FlightEnvelope.DRAG + 0.15; // a jetpack's push
            v = e.next(false, thrusted, false);
            assertTrue(v < thrusted, "cortado no tick " + i);
            if (i < arc.length && arc[i] > 0) {
                assertEquals(arc[i], v, 1e-9, "segue a curva do pulo na subida");
            } else {
                assertTrue(v <= 0, "depois do topo, não sobe mais: " + v);
            }
        }
    }

    @Test
    void neverPushesDown() {
        // a glider holding the fall, or a swimmer whose water the client did not see: never dragged down
        FlightEnvelope e = new FlightEnvelope();
        e.next(true, -0.08, false);
        for (int i = 0; i < 200; i++) {
            assertEquals(-0.02, e.next(false, -0.02, false), 1e-9, "tick " + i);
        }
        FlightEnvelope g = new FlightEnvelope();
        g.next(true, 0.42, false);
        for (int i = 0; i < 60; i++) {
            double v = g.next(false, -0.05, false);
            assertEquals(-0.05, v, 1e-9, "o planador desce no ritmo dele, sem ser puxado");
        }
    }

    @Test
    void aPushFromOutsideIsAllowed() {
        FlightEnvelope e = new FlightEnvelope();
        e.next(true, 0, false);
        e.next(false, -0.08, false);
        double v = e.next(false, 0.9, false); // a hit or a launch by a plugin
        assertEquals(0.9, v, 1e-9);
        assertEquals(
            (0.9 - FlightEnvelope.GRAVITY) * FlightEnvelope.DRAG,
            e.next(false, 1.0, false),
            1e-9,
            "depois do empurrão, só a gravidade");
    }

    @Test
    void waterLaddersAndMountsAreNotCut() {
        FlightEnvelope e = new FlightEnvelope();
        e.next(false, -0.5, false);
        assertEquals(0.3, e.next(false, 0.3, true), 1e-9);
        assertEquals(0.3, e.next(false, 0.3, true), 1e-9);
    }
}
