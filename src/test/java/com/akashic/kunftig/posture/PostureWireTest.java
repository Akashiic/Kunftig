package com.akashic.kunftig.posture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class PostureWireTest {

    @Test
    void whatIsWrittenIsRead() {
        PostureWire.Posture p = new PostureWire.Posture(1234, PostureWire.LIE, 7, 1.5, -2.25, 300000.125, -90f, 0.45f);
        PostureWire.Posture q = PostureWire.decode(PostureWire.encode(p));
        assertNotNull(q);
        assertEquals(1234, q.entity);
        assertEquals(PostureWire.LIE, q.kind);
        assertEquals(7, q.display);
        assertEquals(1.5, q.x, 0);
        assertEquals(-2.25, q.y, 0);
        assertEquals(300000.125, q.z, 0);
        assertEquals(-90f, q.yaw, 0);
        assertEquals(0.45f, q.eye, 0);
    }

    @Test
    void standingEndsIt() {
        PostureWire.Posture q = PostureWire.decode(PostureWire.encode(PostureWire.stand(9)));
        assertNotNull(q);
        assertEquals(9, q.entity);
        assertEquals(PostureWire.STAND, q.kind);
        assertEquals(PostureWire.WORLD, q.display);
    }

    @Test
    void whatCannotBeReadIsNothing() {
        assertNull(PostureWire.decode(null));
        assertNull(PostureWire.decode(new byte[0]));
        byte[] good = PostureWire
            .encode(new PostureWire.Posture(1, PostureWire.SIT, PostureWire.WORLD, 0, 64, 0, 0, 1.62f));
        byte[] otherVersion = good.clone();
        otherVersion[0] = 9;
        assertNull(PostureWire.decode(otherVersion));
        assertNull(PostureWire.decode(java.util.Arrays.copyOf(good, good.length - 3)));
        byte[] badKind = good.clone();
        badKind[5] = 3;
        assertNull(PostureWire.decode(badKind));
        assertNull(
            PostureWire.decode(
                PostureWire.encode(
                    new PostureWire.Posture(1, PostureWire.SIT, PostureWire.WORLD, Double.NaN, 64, 0, 0, 1.62f))));
    }

    /** Only one who lies is lowered, and never raised: the eyes are between the point and vanilla's height. */
    @Test
    void theEyesOfOneWhoLies() {
        assertEquals(0f, PostureWire.drop(PostureWire.SIT, 0.4f), 0);
        assertEquals(1.62f - 0.4f, PostureWire.drop(PostureWire.LIE, 0.4f), 1e-6);
        assertEquals(0f, PostureWire.drop(PostureWire.LIE, 5f), 0);
        assertEquals(1.62f, PostureWire.drop(PostureWire.LIE, -1f), 0);
    }
}
