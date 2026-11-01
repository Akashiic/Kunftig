package com.akashic.kunftig.common.protocol;

/**
 * Thrown when a Kunftig Nametag Protocol payload is malformed, truncated or exceeds a limit.
 * <p>
 * Decoding never trusts the peer: every length is bounded before anything is allocated.
 */
public final class KnpDecodeException extends Exception {

    private static final long serialVersionUID = 1L;

    public KnpDecodeException(String message) {
        super(message);
    }
}
