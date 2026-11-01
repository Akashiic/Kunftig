package com.akashic.kunftig.bukkit.text;

/** Text that cannot be parsed; the message is meant for the admin who wrote it. */
public final class TextParseException extends Exception {

    private static final long serialVersionUID = 1L;

    public TextParseException(String message) {
        super(message);
    }
}
