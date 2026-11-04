package com.akashic.kunftig.client.nametag;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.akashic.kunftig.common.protocol.KnpCodec;
import com.akashic.kunftig.common.protocol.KnpMessage;
import com.akashic.kunftig.common.protocol.KnpProtocol;

class HelloVersionTest {

    /** A local build of a long branch has a long version; the hello must still encode (it threw on every join). */
    @Test
    void theClientVersionAlwaysFitsTheHello() {
        String v = NametagClient.clientVersion();
        assertTrue(v.startsWith("Kunftig/"), v);
        assertTrue(v.length() <= KnpProtocol.MAX_VERSION_STRING, v);
        KnpCodec.encode(new KnpMessage.Hello(KnpProtocol.MIN_VERSION, KnpProtocol.VERSION, 0, v));
    }
}
